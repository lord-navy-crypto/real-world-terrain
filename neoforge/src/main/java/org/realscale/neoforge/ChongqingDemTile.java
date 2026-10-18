package org.realscale.neoforge;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/**
 * An immutable, verifiable REAL DEM of central Chongqing.
 *
 * Data are sourced from public AWS/Mapzen Terrarium raster elevation tiles,
 * not from the seed/noise or an AI heightmap. The grid is translated
 * horizontally in metres, with bilinear interpolation. Outside the
 * measured extent, returns NaN; no fictional Chinese terrain is invented.
 */
public final class ChongqingDemTile {
    private static final String RESOURCE =
            "/assets/realscale/terrain/chongqing_chaotianmen_dem_v1.properties";
    public final int width,step,minX,minZ;
    private final short[] metres;

    private ChongqingDemTile(int width,int step,int minX,int minZ,short[] metres) {
        this.width=width;this.step=step;this.minX=minX;this.minZ=minZ;
        this.metres=metres;
    }

    public static ChongqingDemTile load() {
        Properties props=new Properties();
        try(InputStream stream=ChongqingDemTile.class.getResourceAsStream(RESOURCE)) {
            if(stream==null)throw new IllegalStateException(
                    "Authentic Chongqing DEM resource missing: "+RESOURCE);
            props.load(stream);
            if(!"CQDEM1".equals(props.getProperty("format")))
                throw new IOException("Chongqing DEM version mismatch");
            int width=Integer.parseInt(props.getProperty("width"));
            int spacing=Integer.parseInt(props.getProperty("spacing_m"));
            int minX=Integer.parseInt(props.getProperty("min_x"));
            int minZ=Integer.parseInt(props.getProperty("min_z"));
            if(width<2 || width>1025 || spacing<1 || spacing>256 ||
                    (long)width*width>1_050_625)
                throw new IOException("Invalid Chongqing DEM grid geometry");
            byte[] compressed=Base64.getDecoder().decode(
                    props.getProperty("heights_gzip_base64"));
            int expected=width*width*2;
            byte[] data=new byte[expected+1];
            int got=0;
            try(GZIPInputStream gzip=new GZIPInputStream(
                    new ByteArrayInputStream(compressed))) {
                while(got<data.length) {
                    int n=gzip.read(data,got,data.length-got);
                    if(n<0)break;
                    got+=n;
                }
                if(got!=expected || gzip.read()!=-1)
                    throw new IOException("Truncated/oversize Chongqing DEM");
            }
            String actual=HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            java.util.Arrays.copyOf(data,expected)));
            if(!actual.equals(props.getProperty("source_raw_sha256")))
                throw new IOException("Chongqing DEM integrity SHA-256 mismatch");
            short[] values=new short[width*width];
            try(DataInputStream input=new DataInputStream(
                    new ByteArrayInputStream(data,0,expected))) {
                for(int i=0;i<values.length;i++)values[i]=input.readShort();
            }
            return new ChongqingDemTile(width,spacing,minX,minZ,values);
        }catch(IOException|NoSuchAlgorithmException|NumberFormatException ex) {
            throw new IllegalStateException("Invalid REAL Chongqing DEM: "+ex,ex);
        }
    }

    public int maxX() {return minX+(width-1)*step;}
    public int maxZ() {return minZ+(width-1)*step;}

    public boolean contains(double x,double z) {
        return x>=minX && x<=maxX() && z>=minZ && z<=maxZ();
    }
    /** Real metres above sea level, no terrain when outside the source. */
    public double metresAt(double x,double z) {
        if(!contains(x,z))return Double.NaN;
        double gx=(x-minX)/step,gz=(z-minZ)/step;
        int ix=Math.min(width-2,(int)Math.floor(gx));
        int iz=Math.min(width-2,(int)Math.floor(gz));
        double u=gx-ix,v=gz-iz;
        int base=iz*width+ix;
        double a=metres[base],b=metres[base+1];
        double c=metres[base+width],d=metres[base+width+1];
        // Linear interpolation preserves the genuine source range and
        // cannot invent a DEM ridge higher than all four measured points.
        return (a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v;
    }

    public int sampleRaw(int x,int z) {
        if(x<0||x>=width||z<0||z>=width)
            throw new IndexOutOfBoundsException();
        return metres[z*width+x];
    }
}
