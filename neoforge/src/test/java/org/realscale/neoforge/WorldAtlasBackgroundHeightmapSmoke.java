package org.realscale.neoforge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/** Creates actual Anvil+NBT test fixtures, NOT fabricated production maps. */
public final class WorldAtlasBackgroundHeightmapSmoke {
    private WorldAtlasBackgroundHeightmapSmoke() {}

    private static void check(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }

    private static long[] packedHeight(int y,int bits) {
        int perLong=64/bits;
        long[] values=new long[(256+perLong-1)/perLong];
        int column=8+8*16;
        values[column/perLong]=((long)(y+64)) << (bits*(column%perLong));
        return values;
    }

    private static void writeLongs(DataOutputStream out,String name,long[] array)
            throws IOException {
        out.writeByte(12);
        out.writeUTF(name);
        out.writeInt(array.length);
        for(long entry:array)out.writeLong(entry);
    }

    private static byte[] nbt(int surface,int oceanFloor,
                              boolean withLevel) throws IOException {
        ByteArrayOutputStream raw=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(raw)) {
            out.writeByte(10);out.writeUTF("");
            out.writeByte(3);out.writeUTF("DataVersion");out.writeInt(3955);
            if(withLevel){out.writeByte(10);out.writeUTF("Level");}
            out.writeByte(10);out.writeUTF("Heightmaps");
            writeLongs(out,"WORLD_SURFACE",packedHeight(surface,12));
            writeLongs(out,"OCEAN_FLOOR",packedHeight(oceanFloor,12));
            out.writeByte(0); // Heightmaps compound
            if(withLevel)out.writeByte(0);
            out.writeByte(0); // root
        }
        return raw.toByteArray();
    }

    private static void place(Path world,int chunkX,int chunkZ,int height,
                              int floor,int compression,boolean oldLayout)
            throws IOException {
        byte[] body=nbt(height,floor,oldLayout);
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        if(compression==1) {
            try(GZIPOutputStream zip=new GZIPOutputStream(compressed)){
                zip.write(body);
            }
        } else if(compression==2) {
            try(DeflaterOutputStream zip=new DeflaterOutputStream(compressed)){
                zip.write(body);
            }
        } else compressed.write(body);
        byte[] packed=compressed.toByteArray();
        int payload=packed.length+1,allocated=(payload+4+4095)/4096;
        byte[] region=new byte[(2+allocated)*4096];
        int localX=Math.floorMod(chunkX,32),localZ=Math.floorMod(chunkZ,32);
        int header=4*(localX+localZ*32);
        region[header+2]=2;region[header+3]=(byte)allocated;
        int offset=8192,n=payload;
        region[offset++]=(byte)(n>>>24);
        region[offset++]=(byte)(n>>>16);
        region[offset++]=(byte)(n>>>8);
        region[offset++]=(byte)n;
        region[offset++]=(byte)compression;
        System.arraycopy(packed,0,region,offset,packed.length);
        Path out=world.resolve("region").resolve(
                "r."+Math.floorDiv(chunkX,32)+"."
                   +Math.floorDiv(chunkZ,32)+".mca");
        Files.write(out,region);
    }

    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("atlas-heightmap-smoke");
        try {
            Path world=temp.resolve("RealScale2096");
            Files.createDirectories(world.resolve("region"));
            Files.write(world.resolve("level.dat"),new byte[]{10,0,0,0});
            place(world,0,0,1577,1568,2,false); // modern zlib NBT
            place(world,-1,-1,63,42,1,true); // older gzip Level wrapper
            place(world,1,1,210,210,3,false); // uncompressed NBT

            check(WorldAtlasHeightmapReader.bitsFor(52)==12,
                    "2096-tall heightmap needs 12 packed bits");
            check(WorldAtlasHeightmapReader.bitsFor(37)==9,
                    "Vanilla 384-height map still needs 9 bits");
            byte[] original=Files.readAllBytes(
                    world.resolve("region/r.0.0.mca"));

            var peak=WorldAtlasHeightmapReader.read(world,0,0);
            check(peak!=null && peak.surfaceY()==1577
                    && peak.oceanFloorY()==1568
                    && peak.water(),"Lost exact high-terrain NBT sample");
            var ocean=WorldAtlasHeightmapReader.read(world,-1,-1);
            check(ocean!=null && ocean.surfaceY()==63
                    && ocean.oceanFloorY()==42 && ocean.water(),
                    "Negative-coordinate gzip ocean sample failed");
            var plain=WorldAtlasHeightmapReader.read(world,1,1);
            check(plain!=null && plain.surfaceY()==210 && !plain.water(),
                    "Uncompressed Anvil surface failed");
            check(WorldAtlasHeightmapReader.read(world,9,9)==null,
                    "Nonexistent chunk must not create fake terrain");

            boolean started=WorldAtlasBackgroundPainter.start(world,0,0,512);
            check(started,"Background draw should launch");
            check(!WorldAtlasBackgroundPainter.start(world,0,0,512),
                    "Duplicate background jobs should never launch");
            long deadline=System.nanoTime()+15_000_000_000L;
            WorldAtlasBackgroundPainter.View picture=null;
            while(System.nanoTime()<deadline) {
                picture=WorldAtlasBackgroundPainter.current();
                if(picture!=null && picture.state().equals("DONE"))break;
                if(picture!=null && picture.state().equals("FAILED"))
                    throw new AssertionError("Painter task failed");
                Thread.sleep(10);
            }
            check(picture!=null&&picture.state().equals("DONE"),
                    "Background map never completed");
            check(picture.finished()==WorldAtlasBackgroundPainter.PIXELS,
                    "Painter didn't scan every map pixel");
            check(picture.available()>=2,"Painter did not read real stored heightmaps");
            check(picture.color(24,24)!=0,"Center saved chunk pixel invisible");
            check(WorldAtlasBackgroundPainter.heightColor(ocean)!=0,
                    "Water must have an actual color");
            check(Arrays.equals(original,Files.readAllBytes(
                    world.resolve("region/r.0.0.mca"))),
                    "Background drawing must NEVER modify the world");
            check(!WorldAtlasBackgroundPainter.pause(),
                    "Can't pause a finished task");
            System.out.println("TVA_ATLAS_BACKGROUND_NBT_PASS saved samples="+
                    picture.available()+" pixels="+picture.finished()
                    +" real y=1577, gzip/zlib/raw, no-write, async task");
        } finally {
            try(var files=Files.walk(temp)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new RuntimeException(e);}
                });
            }
        }
    }
}
