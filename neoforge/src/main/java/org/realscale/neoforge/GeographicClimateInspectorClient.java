package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Adds a read-only live climate report to the in-game pause menu.
 *
 * The inspector reads the active integrated-server generator graph rather
 * than the next-world YACL draft, so it cannot accidentally report settings
 * that are not actually active in the current save.
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class GeographicClimateInspectorClient {
    private GeographicClimateInspectorClient() { }

    @SubscribeEvent
    public static void onPauseMenuInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        if (Minecraft.getInstance().level == null) return;

        int buttonWidth = 196;
        int x = (pause.width - buttonWidth) / 2;
        int y = Math.max(4, pause.height - 28);
        event.addListener(Button.builder(
                net.minecraft.network.chat.Component.literal("Geographic Climate Report"),
                button -> Minecraft.getInstance().setScreen(
                        new GeographicClimateInspectorScreen(pause)))
                .bounds(x, y, buttonWidth, 20).build());
    }

    static ActiveClimateProfile readActiveProfile(Minecraft minecraft) {
        if (minecraft.level == null) return null;

        // The first implementation intentionally reads the actual integrated
        // singleplayer server. Remote-server metadata is not guessed from a
        // possibly unrelated client-side next-world draft.
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer == null) return null;

        ServerLevel serverLevel = integratedServer.getLevel(minecraft.level.dimension());
        if (serverLevel == null) return null;

        ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) return null;

        DensityFunction temperature = noiseGenerator.generatorSettings()
                .value().noiseRouter().temperature();
        return findProfile(temperature,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static ActiveClimateProfile findProfile(
            DensityFunction function, Set<DensityFunction> visited) {
        if (function == null || !visited.add(function)) return null;

        if (function instanceof LatitudeTemperatureDensity latitude) {
            return new ActiveClimateProfile(false, latitude.equatorZ(), 0, 0,
                    latitude.poleDistance(), latitude.strength(), false, false, false);
        }
        if (function instanceof RadialLatitudeTemperatureDensity radial) {
            return new ActiveClimateProfile(true, 0, radial.centerX(), radial.centerZ(),
                    radial.poleDistance(), radial.strength(), radial.balanced(),
                    radial.areaBalanced(), radial.singlePoleArea());
        }

        // A referenced datapack density function is represented by a holder
        // wrapper in the live NoiseRouter. Follow the resolved holder value.
        if (function instanceof DensityFunctions.HolderHolder holder) {
            Holder<DensityFunction> reference = holder.function();
            return reference.isBound() ? findProfile(reference.value(), visited) : null;
        }

        // Traverse only known RealScale temperature wrappers. This is
        // deliberate: the report follows the active latitude branch without
        // walking unrelated terrain, cave, or ore density graphs.
        if (function instanceof ClimateBudgetDensity budget) {
            ActiveClimateProfile profile = findProfile(budget.adjusted(), visited);
            return profile != null ? profile : findProfile(budget.original(), visited);
        }
        if (function instanceof MountainBasinTemperatureDensity basin)
            return findProfile(basin.base(), visited);
        if (function instanceof BasinTemperatureDensity basin)
            return findProfile(basin.base(), visited);
        if (function instanceof ValleyTemperatureDensity valley)
            return findProfile(valley.base(), visited);

        return null;
    }

    public record ActiveClimateProfile(
            boolean radial, int equatorZ, int centerX, int centerZ,
            int poleDistance, double strength, boolean balanced,
            boolean areaBalanced, boolean singlePoleArea) {

        double latitudeAt(int x, int z) {
            if (!radial) return GeoMath.latitudeDeg(z, equatorZ, poleDistance);
            if (singlePoleArea)
                return GeoMath.singlePoleAreaRadialLatitudeDeg(
                        x, z, centerX, centerZ, poleDistance);
            if (areaBalanced)
                return GeoMath.areaBalancedRadialLatitudeDeg(
                        x, z, centerX, centerZ, poleDistance);
            if (balanced)
                return GeoMath.balancedRadialLatitudeDeg(
                        x, z, centerX, centerZ, poleDistance);
            return GeoMath.radialLatitudeDeg(x, z, centerX, centerZ, poleDistance);
        }

        String modeLabel() {
            if (!radial) {
                return poleDistance >= 5_000_000
                        ? "Meridional Earth scale (1:1)"
                        : "Meridional climate preview (1:20)";
            }
            if (singlePoleArea) return "Concentric equal-area (single polar crest)";
            if (areaBalanced) return "Concentric equal-area (two polar crests)";
            if (balanced) return "Concentric balanced rings";
            return poleDistance >= 5_000_000
                    ? "Concentric Earth scale"
                    : "Concentric preview";
        }

        String coordinateReference() {
            return radial
                    ? "Ring center X/Z: " + centerX + " / " + centerZ
                    : "Equator reference Z: " + equatorZ;
        }
    }
}
