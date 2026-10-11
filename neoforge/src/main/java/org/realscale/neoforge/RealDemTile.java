package org.realscale.neoforge;

import java.io.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** DEM is USGS 3DEP EPQS derived, interpolated on 32m grid (NOT 1m observations).
 *  One Minecraft block is 1m; no automatic scaling or vertical clipping.
 *  Tile grid coordinates are centered on game (0,0), not real geolocation.
 */
public final class RealDemTile {
    private final int width, height, originX, originZ, seaY;
    private final double cellMeters, offset;
    private final float[] elevations;
    private static final String RESOURCE="/data/realscale/dem/USGS_BigSur_Real_16x16_32m.rgt.gz";
    private static final String SHA256="d43f41f4343fb9606e9647bcdd1ebcd667ec5b9f0334ffc6fef007f7fef19557";
    public RealDemTile(InputStream compressed) throws IOException {
        byte[] data=compressed.readAllBytes();
        try {
            byte[] d=MessageDigest.getInstance("SHA-256").digest(data);
            if(!HexFormat.of().formatHex(d).equals(SHA256)) throw new IOException("USGS source tile integrity failed");
        } catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if(in.readInt()!=0x52475431) throw new IOException("Invalid RGT1 header");
            width=in.readInt(); height=in.readInt();originX=in.readInt();originZ=in.readInt();seaY=in.readInt();
            cellMeters=in.readDouble();offset=in.readDouble();
            long n=(long)width*height;
            if(n!=256||width!=16||height!=16||cellMeters!=32.0||seaY!=63||!Double.isFinite(offset))
                throw new IOException("Unexpected DEM layout");
            elevations=new float[(int)n];
            for(int i=0;i<n;i++){
                elevations[i]=in.readFloat();
                double y=seaY+offset+elevations[i];
                if(!Float.isFinite(elevations[i])||y<-64||y>2031)
                    throw new IOException("DEM elevations exceed 1:1 Minecraft world bounds");
            }
            // Rainfall and lithology are optional in v1.0; read and discard these arrays.
            for(int i=0;i<2*n;i++) in.readFloat();
            for(int i=0;i<n;i++) in.readByte();
            if(in.read()!=-1)throw new IOException("Trailing DEM data");
        }
    }
    public static RealDemTile load(){
        try(InputStream in=RealDemTile.class.getResourceAsStream(RESOURCE)) {
            if(in==null)throw new IOException("Missing bundled DEM: "+RESOURCE);
            return new RealDemTile(in);
        }catch(IOException e){throw new IllegalStateException("USGS DEM could not load: "+e.getMessage(),e);}
    }
    public double worldSolidYAt(double x,double z){
        double u=(x-originX)/cellMeters,v=(z-originZ)/cellMeters;
        if(u<0||v<0||u>width-1||v>height-1)
            throw new IllegalArgumentException("Outside measured DEM coverage");
        int i=Math.min(width-2,(int)Math.floor(u)),j=Math.min(height-2,(int)Math.floor(v));
        double a=u-i,b=v-j;
        int k=j*width+i;
        return seaY+offset+
                (1-b)*((1-a)*elevations[k]+a*elevations[k+1])+
                b*((1-a)*elevations[k+width]+a*elevations[k+width+1]);
    }
    /** Use a smoothstep band inside the source bounds; outside source, unmodified fallback. */
    public double influence(double x,double z,int transitionMeters){
        double right=originX+(width-1)*cellMeters, bottom=originZ+(height-1)*cellMeters;
        double edge=Math.min(Math.min(x-originX,right-x),Math.min(z-originZ,bottom-z));
        if(edge<=0)return 0.0;
        double t=Math.min(1.0,edge/transitionMeters);
        return t*t*(3.0-2.0*t);
    }
    public int originX(){return originX;} public int originZ(){return originZ;}
    public int maxX(){return originX+(int)((width-1)*cellMeters);}
    public int maxZ(){return originZ+(int)((height-1)*cellMeters);}
}
