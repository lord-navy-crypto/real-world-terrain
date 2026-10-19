package org.realscale.neoforge;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Actual NeoForge + Chunky acceptance harness (NOT the fake-shell smoke).
 * Requires an isolated CI gameDirectory, an officially installed dedicated
 * server, matching RealScale jars and the pinned actual Chunky NeoForge jar.
 *
 * This is intentionally a vanilla-height lifecycle/lock/save smoke to keep
 * CI bounded. The separate Measured CI covers 2096-height far chunk costs.
 * Never run against an existing player's save.
 */
public final class WorldAtlasRealChunkySmoke {
    private WorldAtlasRealChunkySmoke() {}

    private static void require(boolean ok,String msg) {
        if(!ok)throw new AssertionError(msg);
    }
    private static void await(BooleanSupplier ready,Duration limit,String reason)
            throws Exception {
        long until=System.nanoTime()+limit.toNanos();
        String previous="";
        while(System.nanoTime()<until) {
            var st=WorldAtlasOfflinePregenerator.status();
            if(st!=null) {
                if(st.state().equals("FAILED") || st.state().equals("NEEDS_ATTENTION"))
                    throw new AssertionError(reason+" "+st.state()+": "+st.detail());
                if(!st.state().equals(previous)) {
                    System.out.println("ATLAS_REAL_STATE "+st.state()+" "+st.detail());
                    previous=st.state();
                }
            }
            if(ready.getAsBoolean())return;
            Thread.sleep(1000);
        }
        throw new AssertionError(reason+" timed out; latest state "+
                WorldAtlasOfflinePregenerator.status());
    }
    private static boolean state(String expected) {
        var st=WorldAtlasOfflinePregenerator.status();
        return st!=null && st.state().equals(expected);
    }

    /** Create and then reopen a real saved world using the server's run.sh. */
    private static void bootAndStop(Path home,Path saves,String worldName)
            throws Exception {
        var ready=new AtomicBoolean(false);
        var process=new ProcessBuilder("/bin/bash",
                home.resolve("run.sh").toAbsolutePath().toString(),
                "--universe",saves.toAbsolutePath().toString(),
                "--world",worldName,"--port","0","nogui")
                .directory(home.toFile()).redirectErrorStream(true).start();
        var readThread=new Thread(()->{
            try(var input=new BufferedReader(new InputStreamReader(
                    process.getInputStream()))) {
                String line;
                while((line=input.readLine())!=null) {
                    if(line.contains("Done ("))ready.set(true);
                    if(line.contains("Done (") || line.contains("ERROR") ||
                            line.contains("Chunky") || line.contains("Exception") ||
                            line.contains("Stopping server"))
                        System.out.println("NEOFORGE_REAL "+line);
                }
            }catch(IOException ex){System.err.println(ex);}
        },"atlas-real-ci-server-log");
        readThread.setDaemon(true);
        readThread.start();
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();
        while(!ready.get()&&process.isAlive()&&System.nanoTime()<deadline)
            Thread.sleep(500);
        require(ready.get(),"Dedicated NeoForge failed to boot; inspect logs");
        try(var output=new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream()))) {
            output.write("save-all flush\n");
            output.write("stop\n");
            output.flush();
        }
        require(process.waitFor(150,TimeUnit.SECONDS),"Server didn't stop after save");
        require(process.exitValue()==0,"NeoForge server exit "+process.exitValue());
        readThread.join(2000);
    }

    private static void lockReleased(Path world) throws IOException {
        Path lock=world.resolve("session.lock");
        require(Files.isRegularFile(lock),"World session.lock missing");
        try(var channel=FileChannel.open(lock,StandardOpenOption.READ,
                StandardOpenOption.WRITE);
                FileLock held=channel.tryLock()) {
            require(held!=null,"World save lock remained held after stop");
        }
    }

    public static void main(String[] args) throws Exception {
        require(args.length==1,"Expected isolated game directory");
        Path game=Path.of(args[0]).toRealPath();
        Path home=WorldAtlasOfflinePregenerator.installDirectory(game).toRealPath();
        Path saves=game.resolve("saves").toRealPath();
        String worldName="atlas-ci";
        Path world=saves.resolve(worldName);
        require(!Files.exists(world),"Safety: never overwrite an existing save");

        // Actual matching NeoForge + Chunky + RealScale binary boot.
        bootAndStop(home,saves,worldName);
        require(Files.isRegularFile(world.resolve("level.dat")),"Real level.dat missing");
        lockReleased(world);

        require(WorldAtlasOfflinePregenerator.startWarmup(game,world,0,0),
                "Atlas refused real warm-up");
        await(()->state("WARMUP_READY"),Duration.ofMinutes(14),
                "NeoForge+Chunky warm-up");
        System.out.println("ATLAS_REAL_WARMUP_READY_PASS");

        // Subsequent larger task MUST reuse the warmed child JVM and its lock.
        require(WorldAtlasOfflinePregenerator.start(game,world,0,0,256),
                "Atlas refused same-process continuation");
        await(()->state("RUNNING"),Duration.ofMinutes(3),
                "NeoForge+Chunky continued start");
        require(WorldAtlasOfflinePregenerator.pause(),"Real Chunky pause rejected");
        await(()->state("PAUSED"),Duration.ofMinutes(5),
                "Real Chunky persisted paused task");
        System.out.println("ATLAS_REAL_PAUSE_SAVED_PASS");
        require(WorldAtlasOfflinePregenerator.resume(),"Real Chunky continue rejected");
        await(()->state("RUNNING"),Duration.ofMinutes(3),
                "Real Chunky resume");
        require(WorldAtlasOfflinePregenerator.stopAndSave(),
                "Atlas rejected Save + stop");
        await(()->state("STOPPED"),Duration.ofMinutes(8),
                "Chunky task drain, flush and NeoForge stop");
        System.out.println("ATLAS_REAL_STOP_FLUSH_PASS");
        lockReleased(world);
        require(Files.isRegularFile(world.resolve("level.dat")),
                "level.dat missing after real Chunky");
        try(var regions=Files.walk(world,4)) {
            require(regions.anyMatch(p->p.toString().endsWith(".mca")),
                    "No saved Anvil region files from real Chunky");
        }
        bootAndStop(home,saves,worldName);
        lockReleased(world);
        System.out.println("ATLAS_REAL_NEOFORGE_CHUNKY_REOPEN_PASS");
    }
}
