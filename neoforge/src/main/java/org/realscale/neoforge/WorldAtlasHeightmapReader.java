package org.realscale.neoforge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Read-only, bounded Anvil chunk heightmap sampler. No Minecraft server,
 * chunk generation, fake terrain prediction, or world-save mutations.
 *
 * Reads WORLD_SURFACE and OCEAN_FLOOR from persisted NBT (when available).
 * Returns null for missing, partial, unsupported-external or damaged chunks.
 * This is a sampled surface map, NOT a complete biome/structure map.
 */
public final class WorldAtlasHeightmapReader {
    private WorldAtlasHeightmapReader() { }
    private static final int MAX_CHUNK_NBT = 4*1024*1024;
    private static final int MAX_COMPRESSED = 255*4096;
    private static final int MAX_DEPTH = 32;
    // Keep region handles bounded while drawing raster rows. Without this
    // cache, a 48x48 map can reopen the same few .mca files hundreds of times.
    private static final int MAX_OPEN_REGIONS = 32;
    // Read-only map pixels frequently sample identical saved chunks when
    // zoomed in. One sampler task caches only POSITIVE NBT decode results:
    // an absent/unfinished chunk is always rechecked so newly generated
    // terrain remains visible while the offline map paints.
    private static final int HEIGHT_CACHE_SLOTS = 2048;

    public record Sample(int surfaceY, int oceanFloorY) {
        // Conservative coastal-water hint: tree canopies must not turn alpine forests blue.
        public boolean water() { return surfaceY<=67 && surfaceY-oceanFloorY>=2; }
    }

    /** One task-scoped sampler; close it to release all cached region handles. */
    public static final class Sampler implements AutoCloseable {
        private final Path world;
        private final LinkedHashMap<Path,RandomAccessFile> regions =
                new LinkedHashMap<>(16,0.75f,true);
        private boolean closed;
        private final long[] decodedKeys=new long[HEIGHT_CACHE_SLOTS];
        private final Sample[] decodedHeights=new Sample[HEIGHT_CACHE_SLOTS];
        long decodedHits, decodedMisses;

        private Sampler(Path world) { this.world=world; }

        public Sample read(int chunkX,int chunkZ) throws IOException {
            if(closed)throw new IOException("Heightmap sampler is closed");
            // Signed world coordinate keys, no cached null or incomplete NBT.
            long key=((long)chunkX<<32)|(chunkZ&0xffffffffL);
            int slot=(int)mixChunkKey(key)&(HEIGHT_CACHE_SLOTS-1);
            Sample previous=decodedHeights[slot];
            if(previous!=null && decodedKeys[slot]==key){
                decodedHits++;
                return previous;
            }
            decodedMisses++;
            Sample found=readUncached(chunkX,chunkZ);
            if(found!=null){
                decodedKeys[slot]=key;
                decodedHeights[slot]=found;
            }
            return found;
        }
        private static long mixChunkKey(long h){
            h^=h>>>33;h*=0xff51afd7ed558ccdL;
            h^=h>>>33;h*=0xc4ceb9fe1a85ec53L;
            return h^(h>>>33);
        }

        private Sample readUncached(int chunkX,int chunkZ) throws IOException {
            Path file=world.resolve("region").resolve("r."
                    +Math.floorDiv(chunkX,32)+"."
                    +Math.floorDiv(chunkZ,32)+".mca");
            RandomAccessFile in=regions.get(file);
            if(in==null) {
                // An already open, validated region needs no repeated stat;
                // missing regions remain uncached and can appear during paint.
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return null;
                in=new RandomAccessFile(file.toFile(),"r");
                regions.put(file,in);
                trimRegions();
            }
            int rx=Math.floorMod(chunkX,32),rz=Math.floorMod(chunkZ,32);
            int header=4*(rx+32*rz);
            long total=in.length();
            if(total<8192)return null;
            in.seek(header);
            int first=in.readInt();
            int sector=first>>>8, sectors=first&255;
            if(sector<2 || sectors==0 ||
                    (long)(sector+sectors)*4096L>total)return null;
            in.seek((long)sector*4096);
            int n=in.readInt();
            int compression=in.readUnsignedByte();
            // Vanilla compression types 1=gzip, 2=zlib, 3=raw.
            // External .mcc streams are NOT followed or modified.
            if((compression&0x80)!=0 || n<=1 ||
                    n>MAX_COMPRESSED || n+4>sectors*4096 ||
                    (compression!=1 && compression!=2 && compression!=3))
                return null;
            byte[] packed=new byte[n-1];
            in.readFully(packed);
            return decode(packed,compression);
        }

        private void trimRegions() {
            Iterator<Map.Entry<Path,RandomAccessFile>> iterator =
                    regions.entrySet().iterator();
            while(regions.size()>MAX_OPEN_REGIONS && iterator.hasNext()) {
                RandomAccessFile old=iterator.next().getValue();
                iterator.remove();
                try { old.close(); } catch(IOException ignored) { }
            }
        }

        @Override public void close() throws IOException {
            if(closed)return;
            closed=true;
            IOException failure=null;
            for(RandomAccessFile region:regions.values()) {
                try { region.close(); }
                catch(IOException ex) {
                    if(failure==null)failure=ex;
                    else failure.addSuppressed(ex);
                }
            }
            regions.clear();
            java.util.Arrays.fill(decodedHeights,null);
            if(failure!=null)throw failure;
        }
    }

    public static Sampler openSampler(Path world) {
        return new Sampler(world);
    }

    /** Convenience API for isolated reads and existing smoke tests. */
    public static Sample read(Path world,int chunkX,int chunkZ) throws IOException {
        try(Sampler sampler=openSampler(world)) {
            return sampler.read(chunkX,chunkZ);
        }
    }

    private static Sample decode(byte[] bytes,int method) throws IOException {
        try(InputStream raw=new ByteArrayInputStream(bytes);
            InputStream decoded=switch(method) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                default -> raw;
            }) {
            // Hard cap prevents corrupt/hostile compressed NBT from consuming
            // unbounded RAM or blocking the Atlas worker indefinitely.
            ByteArrayOutputStream out=new ByteArrayOutputStream(8192);
            byte[] buffer=new byte[8192];
            int count=0,n;
            while((n=decoded.read(buffer))!=-1) {
                count+=n;
                if(count>MAX_CHUNK_NBT) return null;
                out.write(buffer,0,n);
            }
            try(DataInputStream in=new DataInputStream(
                    new ByteArrayInputStream(out.toByteArray()))) {
                if(in.readUnsignedByte()!=10)return null;
                readString(in); // NBT root name (normally empty)
                Heightmaps found=new Heightmaps();
                readCompound(in,0,found,false);
                if(found.surface==null)return null;
                int bits=bitsFor(found.surface.length);
                if(bits<1)return null;
                int surface=unpack(found.surface,8+8*16,bits)-64;
                int floor=found.floor==null?surface:
                        unpack(found.floor,8+8*16,bitsFor(found.floor.length))-64;
                return new Sample(surface,floor);
            }
        }catch(IllegalArgumentException e) {
            return null;
        }
    }

    private static class Heightmaps {
        long[] surface,floor;
    }

    private static void readCompound(DataInputStream in,int depth,
                                     Heightmaps found,boolean heightmaps)
            throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("NBT too deeply nested");
        while(true) {
            int type=in.readUnsignedByte();
            if(type==0)return;
            String name=readString(in);
            if(type==10 && !heightmaps &&
                    (name.equals("Heightmaps") || name.equals("Level"))) {
                readCompound(in,depth+1,found,name.equals("Heightmaps"));
            } else if(heightmaps && type==12 &&
                    (name.equals("WORLD_SURFACE") ||
                     name.equals("OCEAN_FLOOR"))) {
                int length=length(in,MAX_CHUNK_NBT/8);
                long[] heights=new long[length];
                for(int i=0;i<length;i++)heights[i]=in.readLong();
                if(name.equals("WORLD_SURFACE"))found.surface=heights;
                else found.floor=heights;
            } else skipPayload(in,type,depth+1);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int n=in.readUnsignedShort();
        if(n>2048)throw new IOException("NBT name is too long");
        byte[] bytes=new byte[n];
        in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }

    private static int length(DataInputStream in,int max) throws IOException {
        int n=in.readInt();
        if(n<0||n>max)throw new IOException("Invalid NBT length");
        return n;
    }

    private static void skipPayload(DataInputStream in,int type,int depth)
            throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("NBT nesting limit");
        switch(type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3,5 -> in.skipNBytes(4);
            case 4,6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(length(in,MAX_CHUNK_NBT));
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                int element=in.readUnsignedByte();
                int n=length(in,MAX_CHUNK_NBT);
                if(element==0 && n!=0)throw new IOException("NBT invalid list");
                for(int i=0;i<n;i++)skipPayload(in,element,depth+1);
            }
            case 10 -> {
                while(true) {
                    int child=in.readUnsignedByte();
                    if(child==0)break;
                    readString(in);
                    skipPayload(in,child,depth+1);
                }
            }
            case 11 -> in.skipNBytes((long)length(in,MAX_CHUNK_NBT/4)*4);
            case 12 -> in.skipNBytes((long)length(in,MAX_CHUNK_NBT/8)*8);
            default -> throw new IOException("Unknown NBT tag "+type);
        }
    }

    /** Minecraft 1.18+ padded-packed heightmap: whole entries per long. */
    static int bitsFor(int count) {
        // Vanilla Overworld: 384-high => 9 bits / 37 longs.
        // RealScale Overworld: 2096-high => 12 bits / 52 longs.
        // b=11 also needs 52 longs, but RealScale's 2096-high world uses b=12.
        if(count==52)return 12;
        for(int bits=1;bits<=16;bits++) {
            int entries=64/bits;
            if((256+entries-1)/entries==count)return bits;
        }
        return -1;
    }

    static int unpack(long[] data,int column,int bits) {
        if(bits<1 || bits>16 || column<0 || column>=256)
            throw new IllegalArgumentException("Heightmap out of bounds");
        int perLong=64/bits;
        int word=column/perLong;
        if(word>=data.length)throw new IllegalArgumentException("Incomplete heightmap");
        return (int)((data[word]>>>(bits*(column%perLong)))
                &((1L<<bits)-1));
    }
}
