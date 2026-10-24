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

    private static void insertCompact(long[] data,int bits,int column,int encoded) {
        int offset=column*bits,word=offset>>>6,shift=offset&63;
        data[word]|=((long)encoded)<<shift;
        if(shift+bits>64)
            data[word+1]|=((long)encoded)>>>(64-shift);
    }

    private static long[] packedHeightCompact(int y,int bits) {
        long[] values=new long[(256*bits+63)/64];
        insertCompact(values,bits,8+8*16,y+64);
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
                              boolean withLevel,boolean compact) throws IOException {
        ByteArrayOutputStream raw=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(raw)) {
            out.writeByte(10);out.writeUTF("");
            out.writeByte(3);out.writeUTF("DataVersion");out.writeInt(3955);
            if(withLevel){out.writeByte(10);out.writeUTF("Level");}
            out.writeByte(10);out.writeUTF("Heightmaps");
            writeLongs(out,"WORLD_SURFACE",compact?
                    packedHeightCompact(surface,12):packedHeight(surface,12));
            writeLongs(out,"OCEAN_FLOOR",compact?
                    packedHeightCompact(oceanFloor,12):packedHeight(oceanFloor,12));
            out.writeByte(0); // Heightmaps compound
            if(withLevel)out.writeByte(0);
            out.writeByte(0); // root
        }
        return raw.toByteArray();
    }

    private static void place(Path world,int chunkX,int chunkZ,int height,
                              int floor,int compression,boolean oldLayout)
            throws IOException {
        place(world,chunkX,chunkZ,height,floor,compression,oldLayout,false);
    }

    private static void place(Path world,int chunkX,int chunkZ,int height,
                              int floor,int compression,boolean oldLayout,
                              boolean compact) throws IOException {
        byte[] body=nbt(height,floor,oldLayout,compact);
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
            place(world,0,0,1577,1577,2,false); // modern zlib NBT
            place(world,-1,-1,63,42,1,true); // older gzip Level wrapper
            place(world,32,32,210,210,3,false); // uncompressed NBT

            check(WorldAtlasHeightmapReader.bitsFor(52)==12
                    && WorldAtlasHeightmapReader.bitsFor(48)==12,
                    "RealScale 2096-height map needs both compact/padded 12-bit layouts");
            check(WorldAtlasHeightmapReader.bitsFor(37)==9
                    && WorldAtlasHeightmapReader.bitsFor(36)==9,
                    "Vanilla 384-height map needs both compact/padded 9-bit layouts");
            long[] compact12=packedHeightCompact(1490,12);
            insertCompact(compact12,12,5,1234);
            insertCompact(compact12,12,10,2047);
            check(WorldAtlasHeightmapReader.unpack(compact12,5,12)==1234
                    &&WorldAtlasHeightmapReader.unpack(compact12,10,12)==2047
                    &&WorldAtlasHeightmapReader.unpack(compact12,136,12)==1554,
                    "Failed actual compact 12-bit height values across long boundaries");
            long[] compact9=packedHeightCompact(65,9);
            insertCompact(compact9,9,7,321);
            check(WorldAtlasHeightmapReader.unpack(compact9,7,9)==321,
                    "Failed compact 9-bit vanilla heightmap long-boundary crossing");
            byte[] original=Files.readAllBytes(
                    world.resolve("region/r.0.0.mca"));

            var peak=WorldAtlasHeightmapReader.read(world,0,0);
            check(peak!=null && peak.surfaceY()==1577
                    && peak.oceanFloorY()==1577
                    && !peak.water(),"Lost exact high-terrain NBT sample");
            var ocean=WorldAtlasHeightmapReader.read(world,-1,-1);
            check(ocean!=null && ocean.surfaceY()==63
                    && ocean.oceanFloorY()==42 && ocean.water(),
                    "Negative-coordinate gzip ocean sample failed");
            var plain=WorldAtlasHeightmapReader.read(world,32,32);
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

            // Test.42: Chunky (or Minecraft) can save NEW distant chunks
            // after the first read-only raster finishes. Refresh must
            // open fresh Anvil file handles and redraw both changed
            // existing heights and newly available far-region pixels,
            // without holding onto the previous stale raster or writes.
            int formerCenter=picture.color(24,24);
            int formerAvailable=picture.available();
            place(world,0,0,75,75,2,false);
            place(world,-32,0,1100,1100,2,false,true);
            check(WorldAtlasHeightmapReader.read(world,0,0).surfaceY()==75,
                    "Updated stored mountain sample was not persisted");
            check(WorldAtlasHeightmapReader.read(world,-32,0).surfaceY()==1100,
                    "New far-region Anvil sample was not persisted");
            byte[] refreshedBytes=Files.readAllBytes(
                    world.resolve("region/r.0.0.mca"));
            byte[] farBytes=Files.readAllBytes(
                    world.resolve("region/r.-1.0.mca"));
            check(WorldAtlasBackgroundPainter.start(world,0,0,512),
                    "Completed Atlas draw must permit a fresh saved-world rescan");
            WorldAtlasBackgroundPainter.View refreshed=null;
            deadline=System.nanoTime()+15_000_000_000L;
            while(System.nanoTime()<deadline) {
                refreshed=WorldAtlasBackgroundPainter.current();
                if(refreshed!=null && refreshed.state().equals("DONE"))break;
                if(refreshed!=null && refreshed.state().equals("FAILED"))
                    throw new AssertionError("Saved distant raster refresh failed");
                Thread.sleep(10);
            }
            check(refreshed!=null && refreshed.state().equals("DONE"),
                    "Refreshed distant saved region map never completed");
            check(refreshed.color(24,24)==
                    WorldAtlasBackgroundPainter.heightColor(
                            new WorldAtlasHeightmapReader.Sample(75,75)),
                    "Refreshed map retained STALE center block height color");
            check(refreshed.color(24,24)!=formerCenter,
                    "Refreshed mountain color must change after real save");
            check(refreshed.available()>formerAvailable,
                    "Refreshed map omitted genuinely new far saved region");
            check(Arrays.equals(refreshedBytes,Files.readAllBytes(
                    world.resolve("region/r.0.0.mca"))),
                    "Refresh must NEVER rewrite updated real Anvil terrain");
            check(Arrays.equals(farBytes,Files.readAllBytes(
                    world.resolve("region/r.-1.0.mca"))),
                    "Refresh must NEVER modify newly generated far region");
            System.out.println("TVA_ATLAS_REAL_SAVED_FAR_REFRESH_PASS "
                    +"previousPixels="+formerAvailable
                    +" refreshedPixels="+refreshed.available()
                    +" updatedY=75 newFarSavedY=1100 compact48=pass noWrite=true");
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
