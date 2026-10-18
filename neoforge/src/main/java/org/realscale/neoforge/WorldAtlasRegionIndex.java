package org.realscale.neoforge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pure Java, read-only Anvil region *coverage* index for saved Minecraft worlds.
 *
 * This reads the 4096-byte region location table only, never decompresses
 * chunk payloads, mutates a save, loads a Minecraft world, or generates chunks.
 * A saved chunk slot is NOT a guarantee that terrain reached FULL status:
 * "saved coverage" is deliberately not called "completed pregeneration".
 */
public final class WorldAtlasRegionIndex {
    private WorldAtlasRegionIndex() { }

    public static final int MAP_CELLS = 48;
    public static final int MAX_RADIUS_BLOCKS = 4096;

    public record Snapshot(int centerX, int centerZ, int radiusBlocks,
                           int minChunkX, int minChunkZ,
                           int targetChunks, int savedChunks, int regionFiles,
                           byte[] coverage) {
        public int cellCoverage(int gridX, int gridZ) {
            if(gridX<0 || gridX>=MAP_CELLS || gridZ<0 || gridZ>=MAP_CELLS)
                throw new IndexOutOfBoundsException();
            return coverage[gridZ*MAP_CELLS+gridX] & 0xff;
        }
        public double percentSaved() {
            return targetChunks==0 ? 0 : 100.0*savedChunks/targetChunks;
        }
    }

    public static List<Path> findLocalWorlds(Path savesDirectory) throws IOException {
        if (!Files.isDirectory(savesDirectory, LinkOption.NOFOLLOW_LINKS))
            return List.of();
        try (Stream<Path> entries=Files.list(savesDirectory)) {
            // Never follow directory symlinks or expose arbitrary external files.
            return entries.filter(p -> Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)
                            && Files.isRegularFile(p.resolve("level.dat"),
                                LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .limit(2000).toList();
        }
    }

    public static Snapshot scan(Path world, int centerX, int centerZ,
                                int radiusBlocks) throws IOException {
        if(radiusBlocks<128 || radiusBlocks>MAX_RADIUS_BLOCKS)
            throw new IllegalArgumentException("World Atlas radius must be 128..4096");
        if(!Files.isDirectory(world,LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(world.resolve("level.dat"),
                    LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Not a saved local Minecraft world");

        // Square, exact radius blocks around the requested centre.
        // long math avoids wrapping on signed world-border coordinates.
        long minBlockX=(long)centerX-radiusBlocks;
        long minBlockZ=(long)centerZ-radiusBlocks;
        long maxBlockX=(long)centerX+radiusBlocks-1;
        long maxBlockZ=(long)centerZ+radiusBlocks-1;
        int minX=Math.toIntExact(Math.floorDiv(minBlockX,16));
        int minZ=Math.toIntExact(Math.floorDiv(minBlockZ,16));
        int maxX=Math.toIntExact(Math.floorDiv(maxBlockX,16));
        int maxZ=Math.toIntExact(Math.floorDiv(maxBlockZ,16));
        int width=maxX-minX+1, depth=maxZ-minZ+1;
        if(width<=0 || depth<=0 || (long)width*depth>1_048_576L)
            throw new IllegalArgumentException("World Atlas scan is too large");
        int[] target=new int[MAP_CELLS*MAP_CELLS];
        int[] saved=new int[MAP_CELLS*MAP_CELLS];
        for(int z=minZ;z<=maxZ;z++)
            for(int x=minX;x<=maxX;x++)
                target[index(x,z,minX,minZ,width,depth)]++;
        Path regions=world.resolve("region");
        int files=0, count=0;
        for(int rz=Math.floorDiv(minZ,32);rz<=Math.floorDiv(maxZ,32);rz++) {
            for(int rx=Math.floorDiv(minX,32);rx<=Math.floorDiv(maxX,32);rx++) {
                Path path=regions.resolve("r."+rx+"."+rz+".mca");
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                long length=Files.size(path);
                if(length<8192)continue; // torn/truncated region, not proof of coverage
                byte[] header;
                try(InputStream input=Files.newInputStream(path)) {
                    header=input.readNBytes(4096); // location slots only
                }
                if(header.length!=4096)continue;
                files++;
                for(int z=0;z<32;z++)
                    for(int x=0;x<32;x++) {
                        int cx=rx*32+x,cz=rz*32+z;
                        if(cx<minX||cx>maxX||cz<minZ||cz>maxZ)continue;
                        int at=4*(x+z*32);
                        int sector=((header[at]&255)<<16)
                                |((header[at+1]&255)<<8)|(header[at+2]&255);
                        int sectors=header[at+3]&255;
                        // Only actually allocated slots whose complete sectors
                        // fit within the region file count as saved chunks.
                        if(sector<2 || sectors<1 ||
                            (long)(sector+sectors)*4096L>length)continue;
                        saved[index(cx,cz,minX,minZ,width,depth)]++;
                        count++;
                    }
            }
        }
        byte[] image=new byte[target.length];
        for(int i=0;i<target.length;i++) {
            // Rounded coverage [0,255] makes coarse zoom show partial fills.
            image[i]=(byte)(target[i]==0?0:
                    (255*saved[i]+target[i]/2)/target[i]);
        }
        return new Snapshot(centerX,centerZ,radiusBlocks,minX,minZ,
                width*depth,count,files,image);
    }

    private static int index(int x,int z,int minX,int minZ,
                             int width,int depth) {
        int col=(int)((long)(x-minX)*MAP_CELLS/width);
        int row=(int)((long)(z-minZ)*MAP_CELLS/depth);
        return Math.min(MAP_CELLS-1,row)*MAP_CELLS
                +Math.min(MAP_CELLS-1,col);
    }
}
