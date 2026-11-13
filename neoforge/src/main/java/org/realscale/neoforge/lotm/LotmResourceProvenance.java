package org.realscale.neoforge.lotm;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension provenance ledger for player-placed occult ores and flowers.
 * Unlike player NBT, this also rejects blocks placed by one player and mined by another.
 * A removed placement is pruned from the save rather than retained forever.
 */
public final class LotmResourceProvenance extends SavedData {
    private static final String FILE_NAME = "realscale_lotm_placed_resources";
    private static final String POSITIONS_KEY = "positions";
    private final Set<Long> placed = new HashSet<>();

    public static LotmResourceProvenance get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LotmResourceProvenance::new, LotmResourceProvenance::load),
                FILE_NAME);
    }

    public static LotmResourceProvenance load(CompoundTag tag, HolderLookup.Provider registries) {
        LotmResourceProvenance data = new LotmResourceProvenance();
        for (long value : tag.getLongArray(POSITIONS_KEY)) data.placed.add(value);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(POSITIONS_KEY, placed.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public void markPlaced(BlockPos pos) {
        if (placed.add(pos.asLong())) setDirty();
    }

    /** Returns true if player-placed, and consumes the mark after removal. */
    public boolean consumePlacement(BlockPos pos) {
        if (placed.remove(pos.asLong())) {
            setDirty();
            return true;
        }
        return false;
    }

    int trackedPositions() {
        return placed.size();
    }
}
