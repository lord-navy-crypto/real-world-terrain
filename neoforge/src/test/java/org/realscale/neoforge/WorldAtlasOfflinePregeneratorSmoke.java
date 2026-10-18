package org.realscale.neoforge;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Uses a FAKE local shell server to test command/lifecycle safety.
 * It does NOT prove Minecraft or Chunky work on a real dedicated server.
 */
public final class WorldAtlasOfflinePregeneratorSmoke {
    private WorldAtlasOfflinePregeneratorSmoke() { }

    private static void require(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    private static void await(java.util.function.BooleanSupplier ready,String why)
            throws Exception {
        long end=System.nanoTime()+12_000_000_000L;
        while(System.nanoTime()<end) {
            if(ready.getAsBoolean())return;
            Thread.sleep(25);
        }
        throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("atlas-offline-pregen-smoke");
        try {
            Path saves=Files.createDirectories(root.resolve("saves"));
            Path world=Files.createDirectories(saves.resolve("Test World"));
            Path level=world.resolve("level.dat");
            Files.write(level,new byte[]{42,31,57});
            Path session=world.resolve("session.lock");
            Files.write(session,new byte[]{1,2,3});
            byte[] original=Files.readAllBytes(level);

            // Don't launch merely because the client has the Chunky mod.
            try {
                WorldAtlasOfflinePregenerator.start(root,world,0,0,512);
                throw new AssertionError("Missing dedicated server was accepted");
            }catch(IOException expected) {
                require(expected.getMessage().contains("run.sh"),
                        "Missing server should tell the user how to configure it");
            }

            Path home=WorldAtlasOfflinePregenerator.installDirectory(root);
            Path mods=Files.createDirectories(home.resolve("mods"));
            Files.write(mods.resolve("chunky-test.jar"),new byte[]{1});
            Files.write(mods.resolve("realscale-test.jar"),new byte[]{1});
            Files.writeString(home.resolve("eula.txt"),"eula=true\n");
            Files.writeString(home.resolve("server.properties"),
                    "server-ip=127.0.0.1\nenable-rcon=false\n");
            // Chunky's TASK_STOPPED arrives before its saveTask(); mimic
            // a full drain, then create a durable saved-task marker.
            String fakeServer = "#!/bin/sh\n"
                    + "printf '%s\\n' \"$@\" > atlas-argv.txt\n"
                    + "printf '[Server thread/INFO]: Done (0.1s)! For help, type help\\n'\n"
                    + "active=0\n"
                    + "checks=0\n"
                    + "while IFS= read -r cmd; do\n"
                    + "  printf '%s\\n' \"$cmd\" >> atlas-commands.txt\n"
                    + "  case \"$cmd\" in\n"
                    + "    'chunky start'*) active=1; printf 'Task started in minecraft:overworld for square\\n';;\n"
                    + "    'chunky continue'*) active=1; printf 'Task continuing for minecraft:overworld.\\n';;\n"
                    + "    'chunky pause'*)\n"
                    + "      if [ \"$active\" -eq 0 ]; then printf 'No tasks to pause.\\n';\n"
                    + "      else active=2; checks=0; printf 'Task paused for minecraft:overworld.\\n'; printf 'Task stopped for minecraft:overworld.\\n'; fi;;\n"
                    + "    'chunky progress'*)\n"
                    + "      if [ \"$active\" -eq 2 ]; then\n"
                    + "        checks=$((checks+1))\n"
                    + "        if [ \"$checks\" -lt 2 ]; then printf 'Task running for minecraft:overworld. Processed: 5 chunks (2.00%%), ETA: 00:00:30, Rate: 1.5 cps, Current: 0, 0\\n';\n"
                    + "        else active=0; printf 'saved\\n' > chunky-task.saved; printf 'No tasks running.\\n'; fi\n"
                    + "      elif [ \"$active\" -eq 1 ]; then printf 'Task running for minecraft:overworld. Processed: 1,500 chunks (12.50%%), ETA: 00:01:20, Rate: 13.6 cps, Current: 3, 5\\n';\n"
                    + "      else printf 'No tasks running.\\n'; fi;;\n"
                    + "    stop) exit 0;;\n"
                    + "  esac\n"
                    + "done\n";
            Files.writeString(home.resolve("run.sh"),fakeServer,StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(session,
                    StandardOpenOption.READ,StandardOpenOption.WRITE);
                FileLock lock=channel.lock()) {
                try {
                    WorldAtlasOfflinePregenerator.start(root,world,0,0,512);
                    throw new AssertionError("Concurrent save lock was ignored");
                }catch(IOException expected) {
                    require(expected.getMessage().toLowerCase().contains("open"),
                            "World lock error must be clear");
                }
            }
            // Disallow a public server bind before launching.
            Files.writeString(home.resolve("server.properties"),
                    "server-ip=0.0.0.0\nenable-rcon=false\n");
            try {
                WorldAtlasOfflinePregenerator.start(root,world,0,0,512);
                throw new AssertionError("Public server bind was permitted");
            }catch(IOException expected) {
                require(expected.getMessage().contains("127.0.0.1"),
                        "Offline generation must bind localhost only");
            }
            Files.writeString(home.resolve("server.properties"),
                    "server-ip=127.0.0.1\nenable-rcon=false\n");

            require(WorldAtlasOfflinePregenerator.start(root,world,32,64,512),
                    "Valid fake server did not start");
            require(!WorldAtlasOfflinePregenerator.start(root,world,32,64,512),
                    "Concurrent generator launch should be rejected");
            await(() -> {
                var status=WorldAtlasOfflinePregenerator.status();
                return status!=null && status.state().equals("RUNNING");
            },"Server log ready marker must start Chunky generation");
            require(WorldAtlasOfflinePregenerator.pause(),
                    "Running Chunky task should support pause");
            await(() -> WorldAtlasOfflinePregenerator.status().state().equals("PAUSED"),
                    "Pause acknowledgement should precede continue");
            require(WorldAtlasOfflinePregenerator.resume(),
                    "Paused Chunky task should support continue");
            await(() -> WorldAtlasOfflinePregenerator.status().state().equals("RUNNING"),
                    "Continue acknowledgement should precede progress");
            require(WorldAtlasOfflinePregenerator.pollProgress(),
                    "Running task should have progress command");
            var progress=WorldAtlasOfflinePregenerator.parseChunkyProgress(
                    "[Chunky] Task running for minecraft:overworld. Processed: "+
                    "1,500 chunks (12.50%), ETA: 00:01:20, Rate: 13.6 cps, "+
                    "Current: 3, 5");
            require(progress!=null && progress.processedChunks()==1500 &&
                    progress.percent()==12.5 && progress.chunksPerSecond()==13.6,
                    "Actual Chunky console percentage must parse without guessing");
            require(WorldAtlasOfflinePregenerator.parseChunkyProgress(
                    "Invented completion: 91%") == null,
                    "Do not accept invented completion data");
            require(WorldAtlasOfflinePregenerator.stopAndSave(),
                    "Stop must pause task then save server");
            await(() -> {
                var status=WorldAtlasOfflinePregenerator.status();
                return status!=null && status.state().equals("STOPPED");
            },"Server did not gracefully shut down");
            require(Files.isRegularFile(home.resolve("chunky-task.saved")),
                    "Stopping must wait for saved Chunky task marker");
            String commands=Files.readString(home.resolve("atlas-commands.txt"));
            require(commands.lastIndexOf("chunky progress\n")<
                    commands.indexOf("save-all flush\n"),
                    "World save flush MUST wait for Chunky task-map removal");
            require(commands.contains("chunky start minecraft:overworld square 32 64 512\n"),
                    "Must use Chunky NeoForge's actual world key");
            require(commands.contains("chunky pause minecraft:overworld\n"),
                    "Stop must preserve a resumable Chunky task");
            require(commands.contains("chunky continue minecraft:overworld\n"),
                    "Resume must delegate to existing Chunky");
            require(commands.contains("save-all flush\n")
                    && commands.endsWith("stop\n"),
                    "Stop MUST flush save and terminate server");
            String argv=Files.readString(home.resolve("atlas-argv.txt"));
            require(argv.contains("--universe\n") &&
                    argv.contains(saves.toString()+"\n") &&
                    argv.contains("--world\nTest World\n") &&
                    argv.contains("--port\n0\n") &&
                    argv.contains("nogui\n"),
                    "Server must start headlessly against EXACT world folder");
            require(Arrays.equals(original,Files.readAllBytes(level)),
                    "Supervisor itself must not overwrite world metadata");
            System.out.println("TVA_ATLAS_OFFLINE_SERVER_SAFE_PASS: exclusive save lock, "+
                    "localhost, actual Chunky overworld CLI, pause/save/stop, "+
                    "no duplicate generator, no overwriting level.dat");
        }finally {
            try(var paths=Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new RuntimeException(e);}
                });
            }
        }
    }
}
