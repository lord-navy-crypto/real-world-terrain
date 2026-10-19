package org.realscale.neoforge;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One low-priority daemon worker with bounded region-handle reuse for the REAL saved-chunk sampled heightmap.
 *
 * Drawing continues if the Atlas screen closes while Minecraft stays open.
 * It NEVER starts a Minecraft server, imports Chunky or touches the save.
 * No PNG/image files are written into the world directory.
 */
public final class WorldAtlasBackgroundPainter {
    private WorldAtlasBackgroundPainter() {}
    public static final int CELLS=WorldAtlasRegionIndex.MAP_CELLS;
    public static final int PIXELS=CELLS*CELLS;

    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"RealScale-WorldAtlas-Painter");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile Task active;

    public record View(Path world,int centerX,int centerZ,int radius,
                       String state,int finished,int available,int errors,
                       int[] colors) {
        public int color(int x,int z) {
            return colors[z*CELLS+x];
        }
        public double percent() { return 100.0*finished/PIXELS; }
        public boolean matches(Path path,int x,int z,int r) {
            return world.equals(path)&&centerX==x&&centerZ==z&&radius==r;
        }
    }

    private static class Task {
        final Path world;
        final int x,z,radius;
        final AtomicBoolean cancelled=new AtomicBoolean(false);
        final AtomicBoolean paused=new AtomicBoolean(false);
        volatile View view;
        Task(Path path,int x,int z,int radius) {
            world=path;x=ensureBlock(x);z=ensureBlock(z);
            this.x=x;this.z=z;this.radius=radius;
            view=new View(world,x,z,radius,"QUEUED",0,0,0,new int[PIXELS]);
        }
        private static int ensureBlock(int n) {
            if(Math.abs((long)n)>29_995_000L)
                throw new IllegalArgumentException("Outside Minecraft border");
            return n;
        }
        void publish(String state,int done,int found,int errors,int[] colors) {
            view=new View(world,x,z,radius,state,done,found,errors,colors.clone());
        }
    }

    public static synchronized boolean start(Path world,int x,int z,int radius) {
        Task previous=active;
        if(previous!=null && !terminal(previous.view.state()))return false;
        if(radius<128||radius>WorldAtlasRegionIndex.MAX_RADIUS_BLOCKS)
            throw new IllegalArgumentException("Atlas draw radius out of bounds");
        if(!Files.isRegularFile(world.resolve("level.dat"),
                LinkOption.NOFOLLOW_LINKS))return false;
        Task task=new Task(world,x,z,radius);
        active=task;
        WORKER.execute(()->paint(task));
        return true;
    }

    public static View current() {
        Task task=active;
        return task==null?null:task.view;
    }

    public static boolean pause() {
        Task task=active;
        if(task==null||terminal(task.view.state()))return false;
        task.paused.set(true);
        return true;
    }

    public static boolean resume() {
        Task task=active;
        if(task==null||terminal(task.view.state()))return false;
        task.paused.set(false);
        return true;
    }

    public static boolean cancel() {
        Task task=active;
        if(task==null||terminal(task.view.state()))return false;
        task.cancelled.set(true);
        task.paused.set(false);
        return true;
    }

    private static boolean terminal(String name) {
        return name.equals("DONE") || name.equals("CANCELLED")
                || name.equals("FAILED");
    }

    private static void paint(Task task) {
        int[] output=new int[PIXELS];
        int seen=0,done=0,errors=0;
        try (WorldAtlasHeightmapReader.Sampler sampler =
                     WorldAtlasHeightmapReader.openSampler(task.world)) {
            for(int z=0;z<CELLS;z++) {
                for(int x=0;x<CELLS;x++) {
                    if(task.cancelled.get()) {
                        task.publish("CANCELLED",done,seen,errors,output);
                        return;
                    }
                    while(task.paused.get() && !task.cancelled.get()) {
                        task.publish("PAUSED",done,seen,errors,output);
                        Thread.sleep(80);
                    }
                    if(task.cancelled.get()) {
                        task.publish("CANCELLED",done,seen,errors,output);
                        return;
                    }
                    // One REPRESENTATIVE saved chunk per pixel, independent
                    // of server chunk tickets and biome/noise generation.
                    long bx=(long)task.x-task.radius+
                            (long)(2*task.radius)*(2*x+1)/(2*CELLS);
                    long bz=(long)task.z-task.radius+
                            (long)(2*task.radius)*(2*z+1)/(2*CELLS);
                    int cx=Math.toIntExact(Math.floorDiv(bx,16));
                    int cz=Math.toIntExact(Math.floorDiv(bz,16));
                    try {
                        var sample=sampler.read(cx,cz);
                        if(sample!=null) {
                            output[z*CELLS+x]=heightColor(sample);
                            seen++;
                        }
                    }catch(java.io.IOException ex) {
                        // A concurrently changing region, damaged chunk or
                        // permission issue is a failed sample, not a crash.
                        errors++;
                    }
                    done++;
                }
                task.publish("RUNNING",done,seen,errors,output);
            }
            task.publish("DONE",done,seen,errors,output);
        }catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            task.publish("CANCELLED",done,seen,errors,output);
        }catch(java.io.IOException ex) {
            task.publish("FAILED",done,seen,errors+1,output);
        }catch(RuntimeException ex) {
            task.publish("FAILED",done,seen,errors+1,output);
        }
    }

    /** Interpret real saved WORLD_SURFACE + OCEAN_FLOOR only. */
    public static int heightColor(WorldAtlasHeightmapReader.Sample sample) {
        int y=sample.surfaceY();
        if(sample.water()) {
            int depth=Math.max(0,Math.min(80,
                    sample.surfaceY()-sample.oceanFloorY()));
            return rgb(20,Math.max(45,121-depth),Math.max(112,205-depth));
        }
        if(y<65) return rgb(156,167,115);
        if(y<120) return rgb(72,143,82);
        if(y<280) return rgb(109,157,94);
        if(y<520) return rgb(170,159,111);
        if(y<960) return rgb(140,133,118);
        if(y<1450) return rgb(174,170,163);
        return rgb(233,232,226);
    }

    private static int rgb(int r,int g,int b) {
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
}
