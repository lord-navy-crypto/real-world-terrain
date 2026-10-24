package org.realscale.neoforge;

/**
 * Test.34 production-path strength-only A/B proof.
 * Same exact X/Z/Y and warp inputs are sent to:
 *   Test.30's ORIGINAL 19-bit horizontal candidate mask, and
 *   Test.34's conservative 19-bit horizontal AND Y intersection.
 * The equality gate is mandatory; all elapsed values are informational
 * density microbenchmarks, NOT actual Minecraft cold chunk throughput.
 */
public final class GrandNetherXYZStrengthPerfSmoke {
    private GrandNetherXYZStrengthPerfSmoke() {}
    private static volatile long sink;
    private static long count,strict,partial,positive;
    private static final int N=416;
    private static final double[] SHIFTS={-1.0,-0.62,-0.12,0.0,0.39,1.0};
    private static long raw(double x){return Double.doubleToRawLongBits(x);}
    private static void need(boolean c,String msg){if(!c)throw new AssertionError(msg);}
    public static void main(String[] args){
        int[] x=new int[N],y=new int[N],z=new int[N],oldMask=new int[N],
                xyzMask=new int[N];
        // Construct cave-rich and edge/band-heavy probes from seeded halls
        // in both connected strata, without cherry-picking only carved voxels.
        for(int i=0;i<N;i++){
            int gx=i%13-6,gz=(i/13)%13-6,layer=(i/169)%2;
            var center=GrandNetherCaveNetwork.hall(gx,gz,layer);
            x[i]=(int)center.x()+(i%7-3)*14;
            z[i]=(int)center.z()+((i/7)%7-3)*12;
            y[i]=(int)center.y()+((i/49)%9-4)*23;
            if(i%13==0)y[i]=144;
            else if(i%13==1)y[i]=440;
            oldMask[i]=GrandNetherCaveNetwork.componentMaskForColumn(x[i],z[i]);
            xyzMask[i]=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                    x[i],y[i],z[i]);
            need((oldMask[i]==0)==(xyzMask[i]==0)
                         && (oldMask[i]==-1)==(xyzMask[i]==-1)
                         && (oldMask[i]==-1
                             || (xyzMask[i]&~oldMask[i])==0),
                    "XYZ gate changed original candidate support");
            if(oldMask[i]>0 && xyzMask[i]>0 && oldMask[i]!=xyzMask[i])
                partial++;
        }
        long oldNs=0,xyzNs=0;
        // Explicit JIT warmup uses the SAME x/y/z warped points as sampling.
        // Compare raw bits first; timing follows on the identical live graph.
        for(int pass=0;pass<26;pass++) {
            long a=System.nanoTime();
            for(int i=0;i<N;i++){
                double shift=SHIFTS[(i+pass)%SHIFTS.length];
                double wx=x[i]+shift*5.0,wy=y[i]+shift*2.0,
                        wz=z[i]-shift*4.0;
                double d=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                        x[i],z[i],wx,wy,wz,oldMask[i]);
                sink^=raw(d);
            }
            long b=System.nanoTime();
            for(int i=0;i<N;i++){
                double shift=SHIFTS[(i+pass)%SHIFTS.length];
                double wx=x[i]+shift*5.0,wy=y[i]+shift*2.0,
                        wz=z[i]-shift*4.0;
                double d=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                        x[i],z[i],wx,wy,wz,xyzMask[i]);
                sink^=raw(d);
            }
            long c=System.nanoTime();
            if(pass>=6){oldNs+=b-a;xyzNs+=c-b;}
        }
        for(int pass=0;pass<18;pass++)for(int i=0;i<N;i++){
            double shift=SHIFTS[(i+pass)%SHIFTS.length];
            double wx=x[i]+shift*5.0,wy=y[i]+shift*2.0,
                    wz=z[i]-shift*4.0;
            double old=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                    x[i],z[i],wx,wy,wz,oldMask[i]);
            double narrowed=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                    x[i],z[i],wx,wy,wz,xyzMask[i]);
            double full=GrandNetherCaveNetwork.strength(wx,wy,wz);
            need(raw(old)==raw(narrowed)&&raw(full)==raw(narrowed),
                    "Original full 3D Nether density changed by narrowed mask"
                    +" at "+x[i]+","+y[i]+","+z[i]+" displacement="+shift);
            strict++;
            if(full>0)positive++;
        }
        count=20L*N;
        need(strict>7000 && partial>0 && positive>30,
                "Missing nontrivial partial-mask or true-cave coverage: partial="
                +partial+" strength="+strict+" carved="+positive);
        System.out.println("NETHER_NARROWED_XYZ_3D_DENSITY_AB_PASS"
                +" strictRawParity="+strict+" liveSampled="+N
                +" narrowedNonzeroMasks="+partial+" positiveSamples="+positive
                +" oldHorizontalStrength_ns="+oldNs
                +" narrowedXYZStrength_ns="+xyzNs
                +" timedEvaluationsPerArm="+count
                +" densityMicrobenchmarkOnly=true");
    }
}
