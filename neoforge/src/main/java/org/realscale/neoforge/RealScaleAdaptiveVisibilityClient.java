package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Opt-in client-only exposure classifier: advisory, NOT render culling.
 *
 * Features: no forced loads, variable sampling cadence, immediate fail-open
 * after teleports/unloaded samples, 5 local sky probes and at most 4 bounded
 * 24-block sightline checks to prevent many tunnel-mouth misclassifications.
 *
 * No shader integration, Sodium mixins, chunk tickets, render distance changes
 * or worldgen edits. Enabled only with:
 * -Drealscale.client.visibility.profile=true
 */
@EventBusSubscriber(modid="realscale", value=Dist.CLIENT)
public final class RealScaleAdaptiveVisibilityClient {
    private RealScaleAdaptiveVisibilityClient(){}

    private static final boolean PROFILE =
            Boolean.getBoolean("realscale.client.visibility.profile");
    private static final int REPORT_EVERY_SAMPLES = 20;
    private static final int MAX_SIGHTLINE_REACH = 24;
    private static final int[][] SKY_PROBES = {
        {0,0},{8,0},{-8,0},{0,8},{0,-8}
    };
    private static final int[][] SIGHT_DIRECTIONS = {
        {1,0},{-1,0},{0,1},{0,-1}
    };
    private static final AdaptiveVisibilityBudget.Tracker TRACKER =
            new AdaptiveVisibilityBudget.Tracker();

    private static AdaptiveVisibilityTelemetry telemetry =
            new AdaptiveVisibilityTelemetry();
    private static ClientLevel lastLevel;
    private static BlockPos lastSamplePos;
    private static int ticks;
    private static int sinceLog;

    private enum Exterior { NONE, VISIBLE, UNKNOWN }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!PROFILE) return; // no client queries or sampling when disabled
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level != lastLevel) {
            TRACKER.reset();
            telemetry = new AdaptiveVisibilityTelemetry();
            lastLevel = level;
            lastSamplePos = null;
            ticks = 0;
            sinceLog = 0;
        }
        if (level == null || player == null) return;

        // Cheap scalar displacement every tick; all block/sky/ray queries are
        // deferred. Teleport invalidates stale surface/cave policy IMMEDIATELY.
        double dx = 0,dy = 0,dz = 0, horizontalSq = 0;
        if (lastSamplePos != null) {
            dx=player.getX()-lastSamplePos.getX();
            dy=player.getEyeY()-lastSamplePos.getY();
            dz=player.getZ()-lastSamplePos.getZ();
            horizontalSq=dx*dx+dz*dz;
            if (horizontalSq>24.0*24.0 || Math.abs(dy)>24.0) {
                AdaptiveVisibilityBudget.Mode previousMode=TRACKER.mode();
                TRACKER.observe(unknown());
                lastSamplePos=BlockPos.containing(player.getEyePosition());
                ticks=0;
                if(previousMode!=AdaptiveVisibilityBudget.Mode.TRANSITION)
                    System.err.println("[RealScale adaptive visibility] "+
                            previousMode+" -> TRANSITION (teleport/move beyond sample range)");
                return;
            }
        }

        int interval=AdaptiveVisibilityBudget.samplingIntervalTicks(
                TRACKER.mode(),horizontalSq+dy*dy);
        if (++ticks<interval) return;
        ticks=0;

        BlockPos eye=BlockPos.containing(player.getEyePosition());
        lastSamplePos=eye;
        long started=System.nanoTime();
        AdaptiveVisibilityBudget.Observation sample=observe(level,player,eye);
        AdaptiveVisibilityBudget.Mode before=TRACKER.mode();
        AdaptiveVisibilityBudget.Mode mode=TRACKER.observe(sample);
        telemetry.recordSample(mode,sample.known(),System.nanoTime()-started);
        if(mode!=before)
            System.err.println("[RealScale adaptive visibility] "+before+
                    " -> "+mode+" at "+eye.toShortString()+
                    " (ADVISORY only; no renderer changes)");
        if (++sinceLog>=REPORT_EVERY_SAMPLES) {
            sinceLog=0;
            System.err.println("[RealScale adaptive visibility] "+telemetry.summary()+
                    " at "+eye.toShortString());
        }
    }

    private static AdaptiveVisibilityBudget.Observation observe(
            ClientLevel level,LocalPlayer player,BlockPos eye) {
        // Not all dimensions have the same sky rules. This intentionally
        // does not classify the Nether, End or submerged viewpoints.
        if(!level.dimension().equals(Level.OVERWORLD) || player.isUnderWater())
            return unknown();
        int skyCount=0;
        boolean skyAtCamera=false;
        for(int i=0;i<SKY_PROBES.length;i++){
            BlockPos sample=eye.offset(SKY_PROBES[i][0],0,SKY_PROBES[i][1]);
            if(!loaded(level,sample))return unknown();
            boolean exposed=level.canSeeSky(sample);
            if(i==0)skyAtCamera=exposed;
            if(exposed)skyCount++;
        }
        // Mixed sky coverage is an obvious cave mouth, ravine, shaft or
        // irregular ridge: avoid the costly heightmap and LOS checks.
        if(skyCount>0 && skyCount<SKY_PROBES.length)
            return new AdaptiveVisibilityBudget.Observation(
                    true,skyAtCamera,true,false,true,false);
        // Exposed, open surface: sky is positive in all five sample columns.
        // No heightmap query is required to infer the absence of a roof.
        if(skyCount==SKY_PROBES.length)
            return new AdaptiveVisibilityBudget.Observation(
                    true,true,true,false,false,false);
        // All five probes are covered. Check roof thickness first: a thin
        // roof or covered edge is ambiguous and needs normal priorities.
        int surfaceY=level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                eye.getX(),eye.getZ());
        boolean thickCover=surfaceY>eye.getY()+12;
        if(!thickCover)
            return new AdaptiveVisibilityBudget.Observation(
                    true,false,false,false,true,false);
        Exterior exterior=exteriorInLoadedRange(level,player,eye);
        if(exterior==Exterior.UNKNOWN)return unknown();
        return new AdaptiveVisibilityBudget.Observation(
                true,false,false,true,false,false,
                exterior==Exterior.VISIBLE);
    }

    private static boolean loaded(ClientLevel level,BlockPos p) {
        return p.getY()>=level.getMinBuildHeight()
                && p.getY()<level.getMaxBuildHeight()
                && level.hasChunk(Math.floorDiv(p.getX(),16),
                                  Math.floorDiv(p.getZ(),16));
    }

    /**
     * Only a *conservative positive* outward sky line of sight.
     * At most four rays, and ONLY for strong sealed-cave candidates.
     * Verify every 8-block interval has a loaded chunk BEFORE calling
     * Minecraft's vanilla clip(): never request/force a missing chunk.
     */
    private static Exterior exteriorInLoadedRange(
            ClientLevel level,LocalPlayer player,BlockPos eye) {
        Vec3 from=player.getEyePosition();
        for(int[] dir:SIGHT_DIRECTIONS){
            for(int d=8;d<=MAX_SIGHTLINE_REACH;d+=8)
                if(!loaded(level,eye.offset(dir[0]*d,0,dir[1]*d)))
                    return Exterior.UNKNOWN;
            BlockPos endpoint=eye.offset(
                    dir[0]*MAX_SIGHTLINE_REACH,0,
                    dir[1]*MAX_SIGHTLINE_REACH);
            if(!level.canSeeSky(endpoint))continue;
            Vec3 to=new Vec3(endpoint.getX()+0.5,from.y,
                    endpoint.getZ()+0.5);
            var hit=level.clip(new ClipContext(from,to,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()==HitResult.Type.MISS)
                return Exterior.VISIBLE;
            // Glass/foliage/many modded blocks are see-through but may
            // still have collision. Never use them to prove an opaque wall.
            BlockState wall=level.getBlockState(((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos());
            if(!wall.canOcclude())return Exterior.UNKNOWN;
        }
        return Exterior.NONE;
    }

    /** Read-only context for opt-in FPS attribution, never a renderer hook. */
    public static AdaptiveVisibilityBudget.Mode currentMode() {
        return PROFILE ? TRACKER.mode() :
                AdaptiveVisibilityBudget.Mode.TRANSITION;
    }

    private static AdaptiveVisibilityBudget.Observation unknown() {
        return new AdaptiveVisibilityBudget.Observation(
                false,false,false,false,true,false);
    }
}
