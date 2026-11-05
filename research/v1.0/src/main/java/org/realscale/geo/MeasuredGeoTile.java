package org.realscale.geo;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
/**
 * Measured topographic data from an explicitly sourced GeoTIFF, not invented by noise.
 * Each grid sample retains the source DEM altitude, in meters in its stated vertical
 * datum. Minecraft coords are in metres: +X east and +Z south on the locally projected
 * grid. Interpolation is NOT a claim of additional observation resolution.
 */
public final class MeasuredGeoTile {
    private final int cols,rows,worldX0,worldZ0,seaY;
    private final double gridMeters,verticalOffsetMeters;
    private final float[] elevationMeters, rainMetersPerYear, rockResistance;
    private final byte[] riverMask;
    private MeasuredGeoTile(int cols,int rows,int worldX0,int worldZ0,int seaY,double cell,double offset,
                            float[] e,float[] r,float[] rock,byte[] mask) {
        this.cols=cols;this.rows=rows;this.worldX0=worldX0;this.worldZ0=worldZ0;
        this.seaY=seaY;this.gridMeters=cell;this.verticalOffsetMeters=offset;
        this.elevationMeters=e;this.rainMetersPerYear=r;this.rockResistance=rock;this.riverMask=mask;
    }
    public static MeasuredGeoTile read(Path file) throws IOException {
        try(var d=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            byte[] magic=new byte[4];d.readFully(magic);
            if(magic[0]!='R'||magic[1]!='G'||magic[2]!='T'||magic[3]!='1')
                throw new IOException("Unsupported DEM tile version");
            int cols=d.readInt(),rows=d.readInt(),x0=d.readInt(),z0=d.readInt(),seaY=d.readInt();
            double cell=d.readDouble(),datumOffset=d.readDouble();
            long n=(long)cols*rows;
            if(cols<8||rows<8||n>4_000_000L||!Double.isFinite(cell)||cell<=0||
               !Double.isFinite(datumOffset))throw new IOException("Invalid real DEM header");
            float[][] fields=new float[3][(int)n];
            for(int k=0;k<3;k++)for(int i=0;i<n;i++)fields[k][i]=d.readFloat();
            byte[] mask=new byte[(int)n];d.readFully(mask);
            if(d.read()!=-1)throw new IOException("Trailing or altered DEM payload");
            for(int i=0;i<n;i++) {
                if(!Float.isFinite(fields[0][i]))throw new IOException("Missing elevation at index "+i);
                double y=seaY+datumOffset+fields[0][i];
                if(y<-64||y>2031)throw new IOException("No silent height compression: y="+y);
                if(Float.isFinite(fields[1][i])&&fields[1][i]<0)throw new IOException("Negative rainfall");
                if(Float.isFinite(fields[2][i])&&fields[2][i]<=0)throw new IOException("Invalid rock resistance");
            }
            return new MeasuredGeoTile(cols,rows,x0,z0,seaY,cell,datumOffset,
                  fields[0],fields[1],fields[2],mask);
        }
    }
    public int cols(){return cols;}
    public int rows(){return rows;}
    public int worldX0(){return worldX0;}
    public int worldZ0(){return worldZ0;}
    public int worldSeaY(){return seaY;}
    public double gridMeters(){return gridMeters;}
    private int at(int i,int j){return j*cols+i;}
    private double sample(float[] a,double wx,double wz) {
        double x=(wx-worldX0)/gridMeters,z=(wz-worldZ0)/gridMeters;
        if(x<0||z<0||x>cols-1||z>rows-1)
            throw new IllegalArgumentException("Outside measured DEM coverage: x="+wx+", z="+wz);
        int ix=Math.min(cols-2,(int)Math.floor(x)), iz=Math.min(rows-2,(int)Math.floor(z));
        double fx=x-ix,fz=z-iz;
        double a00=a[at(ix,iz)],a10=a[at(ix+1,iz)];
        double a01=a[at(ix,iz+1)],a11=a[at(ix+1,iz+1)];
        if(!Double.isFinite(a00)||!Double.isFinite(a10)||!Double.isFinite(a01)||!Double.isFinite(a11))
            return Double.NaN;
        return (a00*(1-fx)+a10*fx)*(1-fz)+(a01*(1-fx)+a11*fx)*fz;
    }
    public double measuredAltitudeAt(double x,double z){return sample(elevationMeters,x,z);}
    public double worldSolidYAt(double x,double z){return seaY+verticalOffsetMeters+measuredAltitudeAt(x,z);}
    /** NaN if provider precipitation wasn't supplied; caller MUST disclose fallback. */
    public double annualRainfallAt(double x,double z){return sample(rainMetersPerYear,x,z);}
    /** NaN if provider lithology calibration wasn't supplied. */
    public double resistanceAt(double x,double z){return sample(rockResistance,x,z);}
    public boolean riverEvidenceAtGrid(int ix,int iz){return riverMask[at(ix,iz)]!=0;}
}
