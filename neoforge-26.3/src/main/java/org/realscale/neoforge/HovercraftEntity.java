package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A lightweight, rideable river skimmer based on vanilla boat controls.
 *
 * This is NOT a flying vehicle or a substitute for navigable channels.
 * It adds controlled buoyancy when submerged, and a small hop at wet
 * one-block banks. It never creates water or edits terrain.
 */
public final class HovercraftEntity extends Boat {
    public HovercraftEntity(EntityType<? extends Boat> type, Level level) {
        super(type, level);
        setVariant(Boat.Type.OAK);
    }

    @Override
    public Item getDropItem() {
        return RealScaleVehicles.HOVERCRAFT_ITEM.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (getFirstPassenger() == null) return;

        BlockPos at = blockPosition();
        boolean submerged = level().getFluidState(at.above()).is(FluidTags.WATER);
        // A small lift replaces the vanilla boat's tendency to get pinned
        // below turbulent, descending water. Both logical sides predict it.
        Vec3 v = getDeltaMovement();
        if (submerged && v.y < 0.14) {
            setDeltaMovement(v.x, 0.14, v.z);
            return;
        }

        // Intended for low wet banks / shallow stream lips, not sheer cliffs.
        if (horizontalCollision && onGround()
                && level().getFluidState(at).is(FluidTags.WATER)
                && v.y < 0.22) {
            setDeltaMovement(v.x, 0.22, v.z);
        }
    }
}
