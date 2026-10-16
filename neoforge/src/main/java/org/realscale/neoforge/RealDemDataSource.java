package org.realscale.neoforge;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Real observed USGS 3DEP-derived DEM elevation samples at 32-m X/Z
 * resolution. The original bundled 16x16 pilot is preserved for legacy
 * worlds. New USGS v2 may load a larger verified external 3DEP dataset.
 *
 * USGS 3DEP samples are interpolated bare-earth elevations, not precise
 * individual survey points. Minecraft sea level 63 corresponds to
 * approximately 0m in the source elevation datum.
 */
final class RealDemDataSource {
    private final int width, height, originX, originZ, seaY;
    private final double cellMeters, offset;
    private final float[] elevations;
    private final RealDemTile legacy;

    private static final Path EXTERNAL = Path.of("config","realscale","usgs_3dep.rgt.gz");
    private static final Path CHECKSUM = Path.of("config","realscale","usgs_3dep.sha256");
    private static final Path PROVENANCE = Path.of("config","realscale","usgs_3dep.json");
    private static final int EXTERNAL_MAX_BYTES = 10*1024*1024;

    private RealDemDataSource(RealDemTile previous) {
        legacy=previous;
        width=16; height=16; originX=previous.originX();originZ=previous.originZ();
        seaY=63;cellMeters=32.0;offset=0.0;elevations=null;
    }

    private RealDemDataSource(InputStream compressed, String expectedSha,
                        boolean external) throws IOException {
        legacy=null;
        byte[] data=compressed.readAllBytes();
        if(data.length>EXTERNAL_MAX_BYTES)
            throw new IOException("DEM input exceeds size limit");
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);
            if(!HexFormat.of().formatHex(hash).equals(expectedSha))
                throw new IOException("USGS source tile integrity failed");
        } catch(java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try(DataInputStream in=new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if(in.readInt()!=0x52475431) throw new IOException("Invalid RGT1 header");
            width=in.readInt();height=in.readInt();
            originX=in.readInt();originZ=in.readInt();seaY=in.readInt();
            cellMeters=in.readDouble();offset=in.readDouble();
            long n=(long)width*height;
            if(external) {
                if(width!=height || width<65 || width>513 || (width&1)!=1
                        || !(width==65||width==129||width==257||width==513)
                        || (cellMeters!=16.0 && cellMeters!=32.0)
                        || originX!=-(int)(width/2*cellMeters)
                        || originZ!=-(int)(height/2*cellMeters)
                        || seaY!=63 || offset!=0.0)
                    throw new IOException("Unsupported georeferenced USGS 3DEP DEM layout");
            } else if(n!=256 || width!=16 || height!=16
                       || cellMeters!=32.0 || seaY!=63 || !Double.isFinite(offset)) {
                throw new IOException("Unexpected legacy DEM layout");
            }
            if(n<=0 || n>513L*513L)
                throw new IOException("Invalid DEM raster size");
            elevations=new float[(int)n];
            for(int i=0;i<n;i++) {
                elevations[i]=in.readFloat();
                double y=seaY+offset+elevations[i];
                if(!Float.isFinite(elevations[i])||y<-64||y>2031)
                    throw new IOException("DEM elevations exceed 1:1 Minecraft world bounds");
            }
            // Legacy RGT1 contextual arrays: neutral when only measured
            // elevation is provided. No invented rainfall or rock type.
            for(long i=0;i<2*n;i++) {
                float context=in.readFloat();
                if(!Float.isFinite(context))
                    throw new IOException("Invalid auxiliary raster value");
            }
            for(long i=0;i<n;i++)in.readByte();
            if(in.read()!=-1)throw new IOException("Trailing DEM data");
        }
    }

    /** Extract only simple string metadata fields without a Minecraft or Gson dependency.
     * The raster is separately SHA-pinned; metadata is not used to generate heights.
     */
    private static String jsonString(String raw,String key) throws IOException {
        Pattern pattern=Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"\\\\]*)\\\"");
        Matcher match=pattern.matcher(raw);
        if(!match.find())throw new IOException("Missing DEM provenance field: "+key);
        return match.group(1);
    }

    /** Keep the exact SHA-locked original RealDemTile code unchanged. */
    private static RealDemDataSource legacy() {
        return new RealDemDataSource(RealDemTile.load());
    }

    /**
     * Only USGS v2 uses this opt-in local import. It is immutable per JVM:
     * copy the measured raster onto client/server BEFORE starting Minecraft.
     * Corrupt external data FAILS CLOSED; silently switching to procedural
     * or a different raster could produce broken save/chunk borders.
     */
    public static RealDemDataSource loadMeasured() {
        if(!Files.exists(EXTERNAL) && !Files.exists(CHECKSUM)
                && !Files.exists(PROVENANCE))return legacy();
        if(!Files.isRegularFile(EXTERNAL)||!Files.isRegularFile(CHECKSUM)
                ||!Files.isRegularFile(PROVENANCE))
            throw new IllegalStateException("USGS import requires data, SHA and provenance JSON");
        try {
            if(Files.size(EXTERNAL)>EXTERNAL_MAX_BYTES)
                throw new IOException("USGS DEM too large");
            String sha=Files.readString(CHECKSUM,StandardCharsets.US_ASCII).trim();
            if(!sha.matches("[0-9a-f]{64}"))
                throw new IOException("USGS DEM SHA-256 sidecar invalid");
            String metadata=Files.readString(PROVENANCE,StandardCharsets.UTF_8);
            if(Files.size(PROVENANCE)>16384)
                throw new IOException("USGS provenance is unexpectedly large");
            if(!sha.equals(jsonString(metadata,"rgt1_sha256"))
                    || !jsonString(metadata,"source_url").startsWith(
                        "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/exportImage?")
                    || !jsonString(metadata,"coordinate_reference_system").startsWith("EPSG:26910")
                    || !jsonString(metadata,"elevation_units").equals("metres")
                    || !jsonString(metadata,"source_sha256").matches("[0-9a-f]{64}"))
                throw new IOException("USGS import provenance or checksum mismatch");
            try(InputStream in=Files.newInputStream(EXTERNAL)) {
                return new RealDemDataSource(in,sha,true);
            }
        } catch(IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid external 3DEP DEM; "
                    +"remove/correct the data before opening the world: "
                    +e.getMessage(),e);
        }
    }

    public double worldSolidYAt(double x,double z) {
        if(legacy!=null)return legacy.worldSolidYAt(x,z);
        double u=(x-originX)/cellMeters,v=(z-originZ)/cellMeters;
        if(u<0||v<0||u>width-1||v>height-1)
            throw new IllegalArgumentException("Outside measured DEM coverage");
        int i=Math.min(width-2,(int)Math.floor(u));
        int j=Math.min(height-2,(int)Math.floor(v));
        double a=u-i,b=v-j;
        int k=j*width+i;
        return seaY+offset+
                (1-b)*((1-a)*elevations[k]+a*elevations[k+1])+
                b*((1-a)*elevations[k+width]+a*elevations[k+width+1]);
    }

    public double influence(double x,double z,int transitionMeters) {
        double right=originX+(width-1)*cellMeters;
        double bottom=originZ+(height-1)*cellMeters;
        double edge=Math.min(Math.min(x-originX,right-x),
                             Math.min(z-originZ,bottom-z));
        if(edge<=0)return 0.0;
        double t=Math.min(1.0,edge/transitionMeters);
        return t*t*(3.0-2.0*t);
    }

    public int width(){return width;}
    public int height(){return height;}
    public double cellMeters(){return cellMeters;}
    public int originX(){return originX;}
    public int originZ(){return originZ;}
    public int maxX(){return originX+(int)((width-1)*cellMeters);}
    public int maxZ(){return originZ+(int)((height-1)*cellMeters);}
}
