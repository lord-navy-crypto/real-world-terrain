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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final ScheduledExecutorService CHECKS=
            Executors.newSingleThreadScheduledExecutor(job -> {
                Thread thread=new Thread(job,"RealScale-Atlas-Chunky-Drain");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private static final Pattern CHUNKY_PROGRESS=Pattern.compile(
            "Task running for minecraft:overworld\\. Processed: ([0-9,]+) chunks "+
            "\\(([0-9]+(?:\\.[0-9]+)?)%\\).*?Rate: "+
            "([0-9]+(?:\\.[0-9]+)?) cps");
    private static volatile Session active;

    /** Parsed actual Chunky console progress. No estimates or invented percentages. */
    public record Progress(long processedChunks,double percent,double chunksPerSecond) { }

    public static Progress parseChunkyProgress(String line) {
        if(line==null)return null;
        Matcher found=CHUNKY_PROGRESS.matcher(line);
        if(!found.find())return null;
        try {
            long chunks=Long.parseLong(found.group(1).replace(",",""));
            double pct=Double.parseDouble(found.group(2));
            double rate=Double.parseDouble(found.group(3));
            if(chunks<0 || !Double.isFinite(pct) || pct<0 || pct>100 ||
                    !Double.isFinite(rate) || rate<0)return null;
            return new Progress(chunks,pct,rate);
        }catch(NumberFormatException ex) {
            return null;
        }
    }

    public static Progress progress() {
        Session session=active;
        return session==null?null:session.progress;
    }

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
        volatile Progress progress;
        volatile boolean waitingForTaskRemoval;
        volatile boolean saveStopIssued;
        volatile boolean cleanStopRequested;
        volatile ScheduledFuture<?> pendingChecks;
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
                    Progress observed=parseChunkyProgress(line);
                    if(observed!=null)s.progress=observed;
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
                        // A console request is not proof that Chunky accepted it.
                        s.state("START_REQUESTED",
                                "Awaiting Chunky task-start acknowledgement");
                    } else if(startedChunky && !s.stopping.get() &&
                            safe.contains("Task started in ")) {
                        s.state("RUNNING",safe);
                        armChecks(s);
                    } else if(!s.stopping.get() &&
                            safe.contains("Task paused for minecraft:overworld")) {
                        s.state("PAUSED",safe);
                    } else if(!s.stopping.get() &&
                            safe.contains("Task continuing for minecraft:overworld")) {
                        s.state("RUNNING",safe);
                        armChecks(s);
                    } else if(s.stopping.get() &&
                            (safe.contains("Task stopped for minecraft:overworld") ||
                             safe.contains("No tasks to pause.") ||
                             safe.contains("Task finished for minecraft:overworld"))) {
                        // Upstream Chunky GenerationTask.run() prints
                        // TASK_STOPPED *before* saveTask(this) and map.remove().
                        // Never shut down here: wait until Chunky reports
                        // zero active tasks AFTER its synchronous saveTask().
                        s.waitingForTaskRemoval=true;
                        s.state("DRAINING","Waiting for Chunky task to save");
                        try{s.command("chunky progress");}
                        catch(IOException ex){s.state("NEEDS_ATTENTION",ex.getMessage());}
                    } else if(s.stopping.get() && s.waitingForTaskRemoval &&
                            safe.contains("No tasks running.")) {
                        // ProgressCommand checks generationTasks AFTER
                        // GenerationTask.saveTask(...) and map.remove(...).
                        if(!s.saveStopIssued) {
                            s.saveStopIssued=true;
                            try {
                                s.command("save-all flush");
                                s.command("stop");
                                s.cleanStopRequested=true;
                                s.state("STOPPING",
                                        "Chunky task saved; flushing world and stopping");
                            }catch(IOException ex){
                                s.state("NEEDS_ATTENTION",ex.getMessage());
                            }
                        }
                    }else if(!s.stopping.get() && startedChunky &&
                            s.status.state().equals("RUNNING") &&
                            safe.contains("No tasks running.")) {
                        // Chunky naturally finished all work: same safe
                        // task-map drained save/stop handshake as manual stop.
                        requestDrain(s);
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
                        if(observed!=null) {
                            s.state("RUNNING",safe);
                        }else {
                            s.state(s.status.state(),safe);
                        }
                    }
                    if(!s.stopping.get() &&
                            safe.contains("Task finished for minecraft:overworld")) {
                        // Natural completion is also asynchronous: wait for
                        // the task to be removed/saved before stopping the server.
                        requestDrain(s);
                    }
                }
            }
            int exit=process.waitFor();
            if(s.pendingChecks!=null)s.pendingChecks.cancel(false);
            boolean clean=exit==0 && s.cleanStopRequested;
            s.state(clean?"STOPPED":"FAILED",
                    "Offline server exited ("+exit+"). " +
                    (clean?"Chunky task saved and world stopped.":(
                        "No verified safe-save handshake; inspect logs and save lock.")));
        }catch(Exception e) {
            if(s.pendingChecks!=null)s.pendingChecks.cancel(false);
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
    public static boolean pause() { return send("chunky pause minecraft:overworld","PAUSE_REQUESTED"); }
    public static boolean resume() { return send("chunky continue minecraft:overworld","CONTINUE_REQUESTED"); }
    public static boolean pollProgress() {
        Session s=active;
        if(s==null || s.status.terminal() || s.stopping.get())return false;
        try{s.command("chunky progress");return true;}
        catch(IOException e){return false;}
    }
    private static synchronized void armChecks(Session s) {
        if(s.pendingChecks!=null)return;
        s.pendingChecks=CHECKS.scheduleAtFixedRate(()-> {
            if(s.saveStopIssued || s.status.terminal() ||
                    s.process==null || !s.process.isAlive())return;
            // Draw real Chunky telemetry even when the Atlas screen closes.
            // During stop, keep checking until the task has been removed
            // from Chunky's task map AFTER saveTask() returns.
            if((s.stopping.get() && s.waitingForTaskRemoval) ||
                    (!s.stopping.get() &&
                     s.status.state().equals("RUNNING"))) {
                try{s.command("chunky progress");}
                catch(IOException e){s.state("NEEDS_ATTENTION",e.getMessage());}
            }
        },2,4,TimeUnit.SECONDS);
    }

    public static boolean stopAndSave() {
        Session s=active;
        return s!=null && !s.status.terminal() && requestDrain(s);
    }

    private static boolean requestDrain(Session s) {
        if(!s.stopping.compareAndSet(false,true))return false;
        s.state("DRAINING","Waiting for Chunky durable task save...");
        // When stop occurs before the child process starts, the supervisor
        // handles it without ever loading or modifying the save.
        if(s.process==null || s.input==null)return true;
        try {
            s.command("chunky pause minecraft:overworld");
            // The worker may still be finishing an expensive 2096-high
            // chunk. Poll only Chunky's own task map (never create chunks).
            armChecks(s);
            return true;
        }catch(IOException e) {
            s.state("NEEDS_ATTENTION",
                    "Server console unavailable; check save lock and process");
            return false;
        }
    }
    private static boolean send(String command,String state) {
        Session s=active;
        if(s==null||s.status.terminal()||s.stopping.get() ||
                s.process==null||!s.process.isAlive() ||
                (!s.status.state().equals("RUNNING") &&
                 !s.status.state().equals("PAUSED") &&
                 !(s.status.state().equals("NEEDS_ATTENTION") &&
                   command.startsWith("chunky continue"))))return false;
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
