package org.realscale.neoforge;

/**
 * Small per-density-function, per-worldgen-worker regional relief memo.
 *
 * Lithostitched's jigsaw structure planning may query far-apart preliminary
 * surface columns in alternating order. A single "last region" cache then
 * rebuilds the same 2048x2048 geometry and re-runs its seeded classifier.
 *
 * Entries contain only immutable hash geometry plus the *same* lazy
 * classification originally cached in MountainReliefDensity.Site.
 * Eviction changes computation cost, never sampled terrain. Callers must
 * allocate a distinct cache per mapped density function and worldgen thread.
 */
final class MountainReliefSiteCache {
    static final int SLOTS=64; // fixed memory; collisions can only cause misses
    static final class Site {
        final int gx,gz;
        final long hash;
        final double centerX,centerZ,radius;
        final double envelopeSquared;
        boolean classified;
        int type=MountainReliefMath.NONE;
        double anchorHeight;

        Site(int gx,int gz) {
            this.gx=gx;
            this.gz=gz;
            hash=MountainReliefMath.siteHash(gx,gz);
            long x=(long)gx*MountainReliefMath.CELL
                    +MountainReliefMath.CELL/2
                    +MountainReliefMath.centerOffsetX(hash);
            long z=(long)gz*MountainReliefMath.CELL
                    +MountainReliefMath.CELL/2
                    +MountainReliefMath.centerOffsetZ(hash);
            // Match the old exact-int coordinate checks and double rounding.
            centerX=Math.toIntExact(x);
            centerZ=Math.toIntExact(z);
            radius=MountainReliefMath.radius(hash);
            double conservative=radius*1.100000001;
            envelopeSquared=conservative*conservative;
        }

        boolean definitelyOutside(double dx,double dz) {
            return dx*dx+dz*dz>=envelopeSquared;
        }
    }

    // Two-way set association preserves both hot mountain cells when their
    // old direct-mapped hash collides during multi-column structure probes.
    // Total capacity remains exactly 64 sites per mapped density and worker.
    // Eviction changes only reuse; the original site geometry is untouched.
    private static final int BUCKETS=SLOTS/2;
    private final Site[] sites=new Site[SLOTS];
    private final byte[] victim=new byte[BUCKETS];
    private Site last;

    private static int bucket(int gx,int gz) {
        int hash=gx*0x9e3779b9 ^ gz*0x85ebca6b;
        return hash & (BUCKETS-1);
    }

    Site get(int gx,int gz) {
        Site prev=last;
        if(prev!=null && prev.gx==gx && prev.gz==gz)return prev;
        int bucket=bucket(gx,gz),first=bucket<<1;
        Site a=sites[first],b=sites[first+1];
        if(a!=null && a.gx==gx && a.gz==gz) {
            last=a;
            return a;
        }
        if(b!=null && b.gx==gx && b.gz==gz) {
            last=b;
            return b;
        }
        int slot;
        if(a==null)slot=first;
        else if(b==null)slot=first+1;
        else {
            slot=first+(victim[bucket]&1);
            victim[bucket]^=1;
        }
        Site site=new Site(gx,gz);
        sites[slot]=site;
        last=site;
        return site;
    }
}
