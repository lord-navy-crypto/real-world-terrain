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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;
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
        volatile int x,z,radius;
        volatile boolean warmup;
        final AtomicBoolean stopping=new AtomicBoolean(false);
        volatile Status status;
        volatile Progress progress;
        volatile boolean waitingForTaskRemoval;
        volatile boolean pausedAwaitingTaskSave;
        volatile boolean awaitingWarmupDrain;
        volatile boolean saveStopIssued;
        volatile boolean cleanStopRequested;
        volatile ScheduledFuture<?> pendingChecks;
        volatile Process process;
        volatile BufferedWriter input;
        Session(Path world,Path serverHome,int x,int z,int radius,
                boolean warmup) {
            this.world=world;
            this.serverHome=serverHome;
            this.x=x;this.z=z;this.radius=radius;
            this.warmup=warmup;
            this.status=new Status(world,x,z,radius,"QUEUED",
                    warmup?"Small warm-up region scheduled":
                            "Dedicated server scheduled",
                    System.currentTimeMillis());
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

    /** Start the selected radius, or continue from a completed warm-up server. */
    public static boolean start(Path gameDirectory,Path world,int centerX,
                                int centerZ,int radius) throws IOException {
        return startInternal(gameDirectory,world,centerX,centerZ,radius,false);
    }

    /**
     * Warm-up means a small real chunk pass in the same supervised server JVM.
     * The server stays alive when that pass completes so the user can continue
     * with a larger pregeneration radius without throwing away its warmed JVM.
     */
    public static boolean startWarmup(Path gameDirectory,Path world,
                                      int centerX,int centerZ) throws IOException {
        return startInternal(gameDirectory,world,centerX,centerZ,128,true);
    }

    private static synchronized boolean startInternal(
            Path gameDirectory,Path world,int centerX,int centerZ,int radius,
            boolean warmup) throws IOException {
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

        Session old=active;
        if(old!=null && !old.status.terminal()) {
            if(!"WARMUP_READY".equals(old.status.state()))return false;
            if(warmup)return false;
            if(!old.world.equals(target))
                throw new IOException("Stop the warm-up server for the other world first");
            if(old.process==null || !old.process.isAlive()) {
                old.state("FAILED","Warmed server is no longer running; inspect the save lock");
                throw new IOException("Warmed server is no longer running; restart the warm-up");
            }
            // Serialize with Save + stop: a start command must never be
            // queued after a concurrent shutdown request.
            synchronized(old) {
                if(old.stopping.get() ||
                        !"WARMUP_READY".equals(old.status.state()))return false;
                if(old.process==null || !old.process.isAlive()) {
                    old.state("FAILED",
                            "Warmed server is no longer running; inspect the save lock");
                    throw new IOException(
                            "Warmed server is no longer running; restart the warm-up");
                }
                // Reuse the SAME server process and save lock. This keeps the
                // JVM warm and avoids launching a second writer for this world.
                old.x=centerX;old.z=centerZ;old.radius=radius;
                old.warmup=false;old.awaitingWarmupDrain=false;old.progress=null;
                old.state("START_REQUESTED",
                        "Continuing pregeneration on the warmed server");
                try {
                    old.command("chunky start minecraft:overworld square "+
                            centerX+" "+centerZ+" "+radius);
                }catch(IOException ex) {
                    old.state("NEEDS_ATTENTION",ex.getMessage());
                    throw ex;
                }
            }
            armChecks(old);
            return true;
        }
        if(old!=null && old.process!=null && old.process.isAlive())return false;

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
        // Do NOT risk writing an existing 2096-high world with a different
        // RealScale build. Exact binary equality matters, not JAR filenames.
        requireMatchingRealScaleBinary(gameDirectory.resolve("mods"),mods);
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
        Session task=new Session(target,server,centerX,centerZ,radius,warmup);
        active=task;
        SUPERVISOR.execute(()->supervise(task,launcher,base,
                target.getFileName().toString()));
        return true;
    }

    private static void requireMatchingRealScaleBinary(
            Path clientMods,Path serverMods) throws IOException {
        Path client=uniqueMod(clientMods,"realscale");
        Path server=uniqueMod(serverMods,"realscale");
        if(client==null || server==null)
            throw new IOException("Cannot verify installed RealScale JAR "+
                    "in both client and offline server mods; "+
                    "copy the exact same mod build before starting");
        byte[] a=digest(client),b=digest(server);
        if(!MessageDigest.isEqual(a,b))
            throw new IOException("RealScale client/server JAR bytes differ; "+
                    "refusing to write this existing world with "+
                    "a different generator version");
    }

    private static Path uniqueMod(Path folder,String prefix) throws IOException {
        if(!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))return null;
        try(Stream<Path> list=Files.list(folder)) {
            List<Path> matches=list.filter(p ->
                    Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) &&
                    p.getFileName().toString().toLowerCase(Locale.ROOT)
                      .startsWith(prefix) &&
                    p.getFileName().toString().toLowerCase(Locale.ROOT)
                      .endsWith(".jar")).limit(2).toList();
            if(matches.size()>1)
                throw new IOException("Multiple "+prefix+" JARs in "+folder+
                        "; remove duplicates before opening any world");
            return matches.isEmpty()?null:matches.get(0);
        }
    }

    private static byte[] digest(Path path) throws IOException {
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(path)) {
                byte[] buffer=new byte[16384];
                int n;
                while((n=input.read(buffer))!=-1)
                    sha.update(buffer,0,n);
            }
            return sha.digest();
        }catch(NoSuchAlgorithmException ex) {
            throw new IOException("JVM does not support SHA-256",ex);
        }
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
            s.state("STARTING",s.warmup
                    ?"Launching local NeoForge for a small warm-up pass"
                    :"Launching local NeoForge for offline pregeneration");
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
            if(s.stopping.get()) {
                // A stop requested during server startup still gets an
                // explicit world flush; never start Chunky in this path.
                s.state("STOPPING","Stop requested during server startup");
                s.command("save-all flush");
                s.command("stop");
                s.cleanStopRequested=true;
            }
            try(BufferedReader output=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while((line=output.readLine())!=null) {
                    String safe=limit(line.replaceAll("\\u001b\\[[;\\d]*m",""));
                    Progress observed=parseChunkyProgress(line);
                    if(observed!=null)s.progress=observed;
                    if(!startedChunky && line.contains("Done (")) {
                        // Serialize task start with Save + stop. If stop won
                        // the race, never enqueue a new generator task.
                        synchronized(s) {
                            if(!startedChunky && !s.stopping.get()) {
                                // Use the OVERWORLD dimension key, not the
                                // save-folder name: Chunky expects
                                // minecraft:overworld for this NeoForge world.
                                startedChunky=true;
                                s.state("START_REQUESTED",s.warmup
                                        ?"Awaiting warm-up task-start acknowledgement"
                                        :"Awaiting Chunky task-start acknowledgement");
                                s.command("chunky start minecraft:overworld square "+
                                        s.x+" "+s.z+" "+s.radius);
                            }
                        }
                    } else if(startedChunky && !s.stopping.get() &&
                            safe.contains("Task started in ")) {
                        s.state("RUNNING",safe);
                        armChecks(s);
                    } else if(!s.stopping.get() &&
                            safe.contains("Task paused for minecraft:overworld")) {
                        // Chunky's 'Task paused' is emitted by the command
                        // handler BEFORE its worker writes the resume task.
                        // Do not let 'Continue' race that asynchronous save.
                        s.pausedAwaitingTaskSave=true;
                        s.state("PAUSE_DRAINING",
                                "Waiting for paused Chunky task to persist");
                        armChecks(s);
                    } else if(!s.stopping.get() &&
                            s.pausedAwaitingTaskSave &&
                            safe.contains("No tasks running.")) {
                        // GenerationTask.saveTask -> map.remove -> Progress.
                        s.pausedAwaitingTaskSave=false;
                        s.state("PAUSED",
                                "Chunky progress saved; safe to continue");
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
                        synchronized(s) {
                            if(!s.saveStopIssued) {
                                s.saveStopIssued=true;
                                if(s.pendingChecks!=null)
                                    s.pendingChecks.cancel(false);
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
                        }
                    }else if(!s.stopping.get() &&
                            s.awaitingWarmupDrain &&
                            safe.contains("No tasks running.")) {
                        // This progress response is emitted only after Chunky
                        // removed its completed task from the active task map.
                        finishWarmup(s);
                    }else if(!s.stopping.get() && startedChunky &&
                            s.status.state().equals("RUNNING") &&
                            safe.contains("No tasks running.")) {
                        if(s.warmup)beginWarmupDrain(s);
                        else beginNaturalDrain(s);
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
                        // Telemetry must never overwrite command-transition
                        // states such as PAUSE_REQUESTED or PAUSE_DRAINING.
                        // Otherwise an in-flight progress line could re-enable
                        // Continue before Chunky has persisted the paused task.
                        if(s.status.state().equals("RUNNING"))
                            s.state("RUNNING",safe);
                    }
                    if(!s.stopping.get() &&
                            safe.contains("Task finished for minecraft:overworld")) {
                        // The 100% message can arrive before Chunky finishes
                        // saveTask() and removes its task. Verify via progress.
                        if(s.warmup)beginWarmupDrain(s);
                        else beginNaturalDrain(s);
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

    private static void beginWarmupDrain(Session s) {
        synchronized(s) {
            if(s.stopping.get() || !s.warmup)return;
            s.awaitingWarmupDrain=true;
            s.state("WARMUP_DRAINING",
                    "Warm-up reached its target; verifying Chunky task save");
        }
        armChecks(s);
        synchronized(s) {
            if(s.stopping.get() || !s.awaitingWarmupDrain)return;
            try{s.command("chunky progress");}
            catch(IOException ex){s.state("NEEDS_ATTENTION",ex.getMessage());}
        }
    }

    private static void finishWarmup(Session s) {
        synchronized(s) {
            if(s.stopping.get() || !s.warmup)return;
            try {
                // Persist the warm-up chunks while keeping this same JVM alive.
                // The next Pre-generate click reuses this process and lock.
                s.command("save-all flush");
                s.awaitingWarmupDrain=false;
                s.progress=null; // Do not display the completed warm-up's stale rate.
                if(!s.stopping.get())
                    s.state("WARMUP_READY",
                            "Warm-up saved; server remains open. Continue with Pre-generate or Save + stop.");
            }catch(IOException ex) {
                s.awaitingWarmupDrain=false;
                s.state("NEEDS_ATTENTION",
                        "Warm-up task ended but world flush failed: "+ex.getMessage());
            }
        }
    }

    private static void beginNaturalDrain(Session s) {
        synchronized(s) {
            if(!s.stopping.compareAndSet(false,true))return;
            s.waitingForTaskRemoval=true;
            s.state("DRAINING",
                    "Chunky reached its target; verifying durable task save before shutdown");
        }
        armChecks(s);
        synchronized(s) {
            if(s.saveStopIssued || s.status.terminal())return;
            try{s.command("chunky progress");}
            catch(IOException ex){s.state("NEEDS_ATTENTION",ex.getMessage());}
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
                     (s.pausedAwaitingTaskSave || s.awaitingWarmupDrain ||
                      s.status.state().equals("RUNNING")))) {
                synchronized(s) {
                    // Serialize telemetry with save-all/stop: the periodic
                    // watcher must never enqueue another Chunky command
                    // *after* the final world-save handshake begins.
                    if(s.saveStopIssued || s.status.terminal())return;
                    try{s.command("chunky progress");}
                    catch(IOException e){s.state("NEEDS_ATTENTION",e.getMessage());}
                }
            }
        },2,4,TimeUnit.SECONDS);
    }

    public static boolean stopAndSave() {
        Session s=active;
        return s!=null && !s.status.terminal() && requestDrain(s);
    }

    private static boolean requestDrain(Session s) {
        synchronized(s) {
            if(s.status.terminal() || s.saveStopIssued)return false;
            if(!s.stopping.get()) {
                s.stopping.set(true);
                s.state("DRAINING","Waiting for Chunky durable task save...");
            } else if(s.status.state().equals("NEEDS_ATTENTION")) {
                // A failed console write is recoverable: allow the user to
                // retry Save + stop rather than permanently wedging the lock.
                s.state("DRAINING","Retrying the safe-save handshake...");
            } else {
                return false;
            }
        }
        // When stop occurs before the child process starts, the supervisor
        // handles it without ever loading or modifying the save.
        if(s.process==null || s.input==null)return true;
        try {
            if(s.waitingForTaskRemoval)s.command("chunky progress");
            else s.command("chunky pause minecraft:overworld");
            // The worker may still be finishing an expensive 2096-high
            // chunk. Poll only Chunky's own task map (never create chunks).
            armChecks(s);
            return true;
        }catch(IOException e) {
            s.state("NEEDS_ATTENTION",
                    "Server console unavailable; retry Save + stop and inspect the save lock");
            return false;
        }
    }
    private static boolean send(String command,String state) {
        Session s=active;
        if(s==null)return false;
        synchronized(s) {
            if(s.status.terminal()||s.stopping.get() ||
                    s.process==null||!s.process.isAlive())return false;
            String current=s.status.state();
            boolean allowed=command.startsWith("chunky pause")
                    ?current.equals("RUNNING"):current.equals("PAUSED");
            if(!allowed)return false;
            // Set REQUESTED before subprocess I/O. The session lock prevents
            // a concurrent completion transition from being overwritten.
            s.state(state,command+" requested");
            try {s.command(command);return true;}
            catch(IOException e){s.state("NEEDS_ATTENTION",e.getMessage());return false;}
        }
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
