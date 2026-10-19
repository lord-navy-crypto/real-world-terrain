package org.realscale.neoforge;

import net.minecraft.world.entity.EntityType;

/** Approximate Minecraft habitat preferences; not a real-ocean population model. */
final class MarineMarineLifeMath {
    private MarineMarineLifeMath() {}
    static double suitability(EntityType<?> type, String biome,
                              MarineEcologyZonationMath.Habitat habitat,
                              int column, int aboveFloor) {
        boolean warm=biome.contains("warm_ocean") || biome.contains("lukewarm_ocean");
        boolean cold=biome.contains("cold_ocean") || biome.contains("frozen_ocean");
        boolean reef=habitat==MarineEcologyZonationMath.Habitat.TROPICAL_REEF
                || habitat==MarineEcologyZonationMath.Habitat.TROPICAL_SEAGRASS;
        boolean vegetation=habitat==MarineEcologyZonationMath.Habitat.TEMPERATE_KELP
                || habitat==MarineEcologyZonationMath.Habitat.TEMPERATE_SEAGRASS;
        // We only thin native populations; never increase vanilla spawn rates.
        if(type==EntityType.TROPICAL_FISH)return reef ? 1.0 : warm ? 0.65 : 0.40;
        if(type==EntityType.PUFFERFISH)return reef ? 1.0 : warm ? 0.70 : 0.50;
        if(type==EntityType.COD)return cold ? 1.0 : vegetation ? 0.95 : 0.80;
        if(type==EntityType.SALMON)return cold && column<32 ? 1.0 : 0.80;
        if(type==EntityType.DOLPHIN)return aboveFloor>4 && column>=10 ? 1.0 : 0.60;
        if(type==EntityType.SQUID)return column>=12 && aboveFloor>3 ? 1.0 : 0.85;
        return 1.0;
    }
}
