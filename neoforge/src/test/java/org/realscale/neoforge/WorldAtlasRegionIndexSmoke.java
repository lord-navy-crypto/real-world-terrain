package org.realscale.neoforge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Real Anvil-header test using synthetic SAVED region files, no Minecraft. */
public final class WorldAtlasRegionIndexSmoke {
    private WorldAtlasRegionIndexSmoke() { }

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }

    private static Path region(Path world,int rx,int rz,
                               int localX,int localZ,
                               int sector,int sectors,int bytes)
            throws Exception {
        Path path=world.resolve("region").resolve("r."+rx+"."+rz+".mca");
        byte[] data=new byte[bytes];
        int offset=4*(localX+localZ*32);
        data[offset]=(byte)(sector>>>16);
        data[offset+1]=(byte)(sector>>>8);
        data[offset+2]=(byte)sector;
        data[offset+3]=(byte)sectors;
        Files.write(path,data);
        return path;
    }

    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("atlas-coverage-");
        try {
            Path saves=root.resolve("saves"),world=saves.resolve("Valid");
            Files.createDirectories(world.resolve("region"));
            Files.write(world.resolve("level.dat"),new byte[]{1,2});
            Path incomplete=saves.resolve("NoLevelDat");
            Files.createDirectories(incomplete);
            require(WorldAtlasRegionIndex.findLocalWorlds(saves).equals(
                    java.util.List.of(world)),
                    "World picker must only show saved Minecraft worlds");
            Path positive=region(world,0,0,0,0,2,1,3*4096);
            Path negative=region(world,-1,-1,31,31,2,1,3*4096);
            // Malformed sector allocation beyond EOF cannot count as saved.
            region(world,0,-1,0,31,20,1,8192);
            // Timestamp-only file is NOT a saved chunk.
            region(world,-1,0,0,0,0,0,8192);
            byte[] before=Files.readAllBytes(positive);
            var result=WorldAtlasRegionIndex.scan(world,0,0,512);
            require(result.savedChunks()==2,
                    "Anvil location headers must count 2 real saved slots");
            require(result.targetChunks()==4096,
                    "512-block radius should cover exactly 64x64 chunks");
            require(result.regionFiles()==4,"All valid-size headers visited");
            require(result.cellCoverage(24,24)>0
                    && result.cellCoverage(23,23)>0,
                    "Map needs correct center and negative coordinate cells");
            require(result.cellCoverage(0,0)==0,
                    "Missing chunks must stay visibly ungenerated");
            require(result.percentSaved()>0 && result.percentSaved()<1,
                    "Never confuse partial map with completed generation");
            require(Arrays.equals(before,Files.readAllBytes(positive)),
                    "Offline Atlas must NEVER mutate region files");
            require(Files.exists(negative),"Negative region silently lost");
            try {
                WorldAtlasRegionIndex.scan(world,0,0,5000);
                throw new AssertionError("Unbounded scan accepted");
            }catch(IllegalArgumentException expected) { }
            require(WorldAtlasRegionIndex.scan(world,0,0,4096)
                    .targetChunks()==262_144,
                    "Large atlas radius should remain bounded and exact");
            System.out.println("TVA_WORLD_ATLAS_REGION_SMOKE_PASS: "+result.savedChunks()
                    +" persisted slots / "+result.targetChunks()
                    +" target chunks, immutable Anvil region headers");
        }finally{
            try(var paths=Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(java.io.IOException e){throw new RuntimeException(e);}
                });
            }
        }
    }
}
