package org.realscale.neoforge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A supervised, one-at-a-time offline Chunky *client* for an independently
 * installed NeoForge server, NOT a second chunk generation algorithm.
 *
 * Never launch two servers on one save. Vanilla session.lock is authoritative:
 * preflight is advisory; the actual dedicated server must acquire that lock.
 * No world files, EULA or server configuration are written by this class.
 *
 * Requires a separately installed, matching NeoForge+RealScale+Chunky server
 * at <gameDirectory>/realscale-atlas-server/ (POSIX v0.3).
 */
public final class WorldAtlasOfflinePregenerator {
    private WorldAtlasOfflinePregenerator() { }
    private static final ExecutorService SUPERVISOR =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread=new Thread(task,"RealScale-Atlas-OffLine-Server");
                thread.setDaemon(true);
                return thread;
            });
    private static volatile Session active;

    public record Status(Path world,int centerX,int centerZ,int radius,
                         String state,String detail,long startedMs) {
        public boolean terminal() {
            return state.equals("STOPPED") || state.equals("FAILED");
        }
    }
    private static final class Session {
        final Path world,serverHome;
        final int x,z,radius;
        final AtomicBoolean stopping=new AtomicBoolean(false);
        volatile Status status;
        volatile Process process;
        volatile BufferedWriter input;
        Session(Path world,Path serverHome,int x,int z,int radius) {
            this.world=world;
            this.serverHome=serverHome;
            this.x=x;this.z=z;this.radius=radius;
            this.status=new Status(world,x,z,radius,"QUEUED",
                    "Dedicated server scheduled",System.currentTimeMillis());
        }
        void state(String name,String detail) {
            status=new Status(world,x,z,radius,name,limit(detail),
                    status.startedMs());
        }
        synchronized void command(String line) throws IOException {
            if(input==null || process==null || !process.isAlive())
                throw new IOException("No running server console");
            input.write(line);input.newLine();input.flush();
        }
    }

    public static Path installDirectory(Path gameDirectory) {
        return gameDirectory.resolve("realscale-atlas-server");
    }

    public static synchronized boolean start(Path gameDirectory,Path world,
                                             int centerX,int centerZ,int radius)
            throws IOException {
        Session old=active;
        if(old!=null && !old.status.terminal())return false;
        if(old!=null && old.process!=null && old.process.isAlive())return false;
        if(radius<128 || radius>4096 ||
                Math.abs((long)centerX)+radius>29_995_000L ||
                Math.abs((long)centerZ)+radius>29_995_000L)
            throw new IOException("Radius or map center exceeds safe world limits");
        Path base=gameDirectory.resolve("saves").toRealPath();
        Path target=world.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if(Files.isSymbolicLink(world) || !target.getParent().equals(base) ||
                !Files.isRegularFile(target.resolve("level.dat"),
                    LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Atlas only starts an existing local, non-symlink save");
        // A different Minecraft instance may already have this save open.
        // Checking MUST NOT create session.lock or alter the saved world.
        Path lock=target.resolve("session.lock");
        if(Files.exists(lock,LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Unsafe world lock file");
            try(FileChannel channel=FileChannel.open(lock,
                    StandardOpenOption.READ,StandardOpenOption.WRITE)) {
                try(FileLock lease=channel.tryLock()) {
                    if(lease==null)throw new IOException(
                            "World is already open; close it before pregenerating");
                }catch(OverlappingFileLockException ex) {
                    throw new IOException("This world is already open",ex);
                }
            }
        }
        Path server=installDirectory(gameDirectory);
        Path launcher=server.resolve("run.sh");
        if(!Files.isRegularFile(launcher,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Install matching NeoForge server at "+
                    server+" (missing run.sh)");
        Path mods=server.resolve("mods");
        if(!hasMod(mods,"chunky") || !hasMod(mods,"realscale"))
            throw new IOException("Offline NeoForge server needs matching "+
                    "RealScale and Chunky JARs in its mods folder");
        Properties properties=new Properties();
        Path config=server.resolve("server.properties");
        if(!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing server.properties in "+server);
        try(var reader=Files.newBufferedReader(config)) {
            properties.load(reader);
        }
        if(!"127.0.0.1".equals(properties.getProperty("server-ip","").trim())
                || !"false".equalsIgnoreCase(properties.getProperty(
                    "enable-rcon","false").trim()))
            throw new IOException("Offline server must use server-ip=127.0.0.1 "+
                    "and enable-rcon=false");
        Path eula=server.resolve("eula.txt");
        if(!Files.isRegularFile(eula,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Install and configure the server EULA yourself");
        Properties eulaProps=new Properties();
        try(var reader=Files.newBufferedReader(eula)) {
            eulaProps.load(reader);
        }
        if(!"true".equals(eulaProps.getProperty("eula")))
            throw new IOException("Read and accept Minecraft EULA in "+
                    "server eula.txt before starting");
        Session task=new Session(target,server,centerX,centerZ,radius);
        active=task;
        SUPERVISOR.execute(()->supervise(task,launcher,base,
                target.getFileName().toString()));
        return true;
    }

    private static boolean hasMod(Path folder,String prefix) throws IOException {
        if(!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))return false;
        try(var list=Files.list(folder)) {
            return list.anyMatch(p->Files.isRegularFile(
                    p,LinkOption.NOFOLLOW_LINKS) &&
                    p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .startsWith(prefix) &&
                    p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(".jar"));
        }
    }

    private static void supervise(Session s,Path launcher,Path saves,
                                  String worldName) {
        boolean startedChunky=false;
        try {
            if(s.stopping.get()) {
                s.state("STOPPED","Task stopped before launch");
                return;
            }
            s.state("STARTING","Launching local NeoForge (no player joins)");
            // Use an exact filesystem path, no shell-interpolated world names
            // or remote endpoints. Dedicated server keeps its own world lock.
            ProcessBuilder pb=new ProcessBuilder("/bin/bash",
                    launcher.toAbsolutePath().toString(),
                    "--universe",saves.toString(),
                    "--world",worldName,"--port","0","nogui");
            pb.directory(s.serverHome.toFile());
            pb.redirectErrorStream(true);
            Process process=pb.start();
            s.process=process;
            synchronized(s) {
                s.input=new BufferedWriter(new OutputStreamWriter(
                        process.getOutputStream(),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
            // If the user pressed Stop while the server was starting, do
            // not leave a detached world writer holding session.lock.
            if(s.stopping.get())s.command("stop");
            try(BufferedReader output=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while((line=output.readLine())!=null) {
                    String safe=limit(line.replaceAll("\\u001b\\[[;\\d]*m",""));
                    if(!startedChunky && !s.stopping.get()
                            && line.contains("Done (")) {
                        // Underlying NeoForge world may auto-create on bad
                        // '--world' argument; startup has prevalidated save.
                        // Explicitly use the OVERWORLD key, not world folder,
                        // as Chunky's NeoForgeWorld.getName() returns a
                        // dimension identifier such as minecraft:overworld.
                        s.command("chunky start minecraft:overworld square "+
                                s.x+" "+s.z+" "+s.radius);
                        startedChunky=true;
                        s.state("RUNNING","Chunky requested for overworld: "+s.radius+
                                " block radius; see live console status");
                    }else if(line.toLowerCase(Locale.ROOT).contains("confirm") &&
                            startedChunky && !s.stopping.get()) {
                        s.state("NEEDS_ATTENTION",
                                "Existing Chunky task detected. Stop and "+
                                "use Chunky's saved-task continuation safely.");
                    }else if(line.toLowerCase(Locale.ROOT)
                                .contains("unknown command") && startedChunky) {
                        s.state("NEEDS_ATTENTION",
                                "Chunky not active on server; stop and verify mods");
                    }else if(!s.stopping.get() && startedChunky &&
                            (safe.contains("chunk") || safe.contains("%"))) {
                        s.state(s.status.state(),safe);
                    }
                }
            }
            int exit=process.waitFor();
            s.state(exit==0?"STOPPED":"FAILED",
                    "Offline server exited ("+exit+"). " +
                    (startedChunky?"Check saved Chunky task status.":"Chunky never started."));
        }catch(Exception e) {
            s.state("FAILED",e.getMessage()==null?e.getClass().getSimpleName():
                    e.getMessage());
            Process p=s.process;
            if(p!=null && p.isAlive()) {
                try{s.command("stop");}catch(IOException ignored){}
            }
        }
    }

    public static Status status() {
        Session s=active;
        return s==null?null:s.status;
    }
    public static boolean pause() { return send("chunky pause minecraft:overworld","PAUSED"); }
    public static boolean resume() { return send("chunky continue minecraft:overworld","RUNNING"); }
    public static boolean pollProgress() {
        Session s=active;
        if(s==null || s.status.terminal() || s.stopping.get())return false;
        try{s.command("chunky progress");return true;}
        catch(IOException e){return false;}
    }
    public static boolean stopAndSave() {
        Session s=active;
        if(s==null || s.status.terminal())return false;
        if(!s.stopping.compareAndSet(false,true))return false;
        s.state("STOPPING","Pausing Chunky and saving the world...");
        // The supervisor will see stopping=true before/during spawn and
        // either cancel launch or send stop immediately after attaching stdin.
        if(s.process==null || s.input==null)return true;
        try {
            s.command("chunky pause minecraft:overworld");
            s.command("save-all flush");
            s.command("stop");
            return true;
        } catch(IOException e) {
            s.state("NEEDS_ATTENTION",
                    "Server console unavailable; check save lock and process");
            return false;
        }
    }
    private static boolean send(String command,String state) {
        Session s=active;
        if(s==null||s.status.terminal()||s.stopping.get() ||
                s.process==null||!s.process.isAlive())return false;
        try {s.command(command);s.state(state,command+" requested");return true;}
        catch(IOException e){s.state("NEEDS_ATTENTION",e.getMessage());return false;}
    }
    private static String limit(String input) {
        if(input==null)return "";
        String value=input.replace('\r',' ').replace('\n',' ');
        return value.length()>220?value.substring(value.length()-220):value;
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Do not hard-kill the save writer. User can leave Minecraft
            // while the external server exits cleanly after 'stop'.
            stopAndSave();
            Session s=active;
            Process p=s==null?null:s.process;
            if(p!=null && p.isAlive()) {
                try{p.waitFor(12,TimeUnit.SECONDS);}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
        },"RealScale-Atlas-OffLine-Shutdown"));
    }
}
