package org.realscale.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YACL is strictly client-only. Saving here writes a NEXT-WORLD draft.
 * Standalone JAR players run the matching Config Exporter to create an
 * immutable NEW-WORLD datapack. Development builds can use Gradle to bake
 * the same draft before Minecraft loads worldgen registries.
 *
 * Never change generator settings of a loaded world in-place.
 */
@Mod(value = "realscale", dist = Dist.CLIENT)
public final class RealScaleTerrainLabClient {
    private static final Path CONFIG = Path.of("config", "realscale-terrain-lab.json");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public RealScaleTerrainLabClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (mod, parent) -> createScreen(parent));
    }

    private static class Draft {
        String preset = "landscape";
        int blendPercent = 25;
        boolean lithostitchedBlend = false;
        int ridgePercent = 0;
        int erosionPercent = 0;
        int strataPercent = 0;
        int climatePercent = 65;
        int valleyPercent = 0;
        String latitudeMode = "off";
        int latitudeStrength = 70;
        int equatorZ = 0;
        int radialCenterX = 0;
        int coldLimitPercent = 60;
        int basinPercent = 0;
        int reliefPercent = 100;
        int elevation = 0;
        int caves = 50;
        int riverGrade = 6;
        boolean inland = true;
        boolean fantasyDimensions = false;

        static Draft read() {
            if (!Files.exists(CONFIG)) return new Draft();
            try {
                Draft data = JSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), Draft.class);
                if (data == null) return new Draft();
                migrateLegacyDraft(data);
                if (!valid(data)) return new Draft();
                return data;
            } catch (IOException | JsonSyntaxException ex) {
                return new Draft();
            }
        }

        private static void migrateLegacyDraft(Draft d) {
            // Saved YACL drafts from v1.4.1 remain readable. These names are
            // normalized only in the next-world draft, never in existing saves.
            if (d.preset == null) return;
            switch (d.preset) {
                case "realism_max", "georeal_ultimate", "ultimate" -> d.preset = "landscape";
                case "himalayan", "realistic_mountains" -> d.preset = "alpine";
                case "grand_canyon", "continental" -> d.preset = "canyon";
                default -> { }
            }
            // Prior drafts predate this field, so Gson initializes it to 0.
            // Retain 0 as a valid "original-mode only" blend choice.
        }

        private static boolean valid(Draft d) {
            return switch (d.preset) {
                case "landscape", "alpine", "canyon" ->
                        d.blendPercent >= 0 && d.blendPercent <= 50
                        && d.ridgePercent >= 0 && d.ridgePercent <= 40
                        && d.erosionPercent >= 0 && d.erosionPercent <= 30
                        && d.strataPercent >= 0 && d.strataPercent <= 20
                        && d.climatePercent >= 0 && d.climatePercent <= 100
                        && d.valleyPercent >= 0 && d.valleyPercent <= 100
                        && d.latitudeMode != null
                        && (d.latitudeMode.equals("off") || d.latitudeMode.equals("earth")
                            || d.latitudeMode.equals("preview")
                            || d.latitudeMode.equals("radial_earth")
                            || d.latitudeMode.equals("radial_preview") || d.latitudeMode.equals("radial_balanced"))
                        && d.latitudeStrength >= 0 && d.latitudeStrength <= 100
                        && d.equatorZ >= -4096 && d.equatorZ <= 4096
                        && d.radialCenterX >= -4096 && d.radialCenterX <= 4096
                        && d.coldLimitPercent >= 0 && d.coldLimitPercent <= 100
                        && d.basinPercent >= 0 && d.basinPercent <= 100
                        && d.reliefPercent >= 80 && d.reliefPercent <= 125
                        && d.elevation >= -48 && d.elevation <= 48
                        && d.caves >= 20 && d.caves <= 80
                        && (d.riverGrade == 0 || d.riverGrade == 3
                            || d.riverGrade == 6 || d.riverGrade == 9);
                default -> false;
            };
        }

        void save() {
            if (!valid(this)) throw new IllegalArgumentException("Terrain Lab draft is invalid");
            Path temp = CONFIG.resolveSibling(CONFIG.getFileName().toString() + ".tmp");
            try {
                Files.createDirectories(CONFIG.getParent());
                Files.writeString(temp, JSON.toJson(this), StandardCharsets.UTF_8);
                // Do not leave a half-written config if the game exits mid-save.
                try {
                    Files.move(temp, CONFIG, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp, CONFIG, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot save next-world Terrain Lab draft", ex);
            } finally {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static Screen createScreen(Screen parent) {
        Draft d = Draft.read();
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("RealScale Terrain Lab | SAVE DRAFT for NEXT WORLD"))
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Terrain Shape & Height"))
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Base Terrain"))
                    .description(description("Configure a NEW Lab world, not a loaded save. Press Save, quit Minecraft, then use the bundled RealScale-Config-Exporter.pyz on this instance\u0027s config/realscale-terrain-lab.json and matching mod JAR. Enable its ZIP under Create New World > Data Packs, THEN choose the new Lab World Type. USGS measured DEM is read-only."))
                    .binding("landscape", () -> d.preset, v -> d.preset = v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(
                        "landscape", "alpine", "canyon"))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Secondary Landform Mix (%)"))
                    .description(description("0..50%; smoothly combine two real density fields before terrain height scaling."))
                    .binding(25, () -> d.blendPercent, v -> d.blendPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,50).step(1))
                    .build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Lithostitched Adaptive Blend (Opt-in)"))
                    .description(description("NEW Lab worlds ONLY: use Lithostitched's mix density codec for softly varying terrain blend across regions (+/-5 percentage points). OFF keeps older world profile IDs unchanged. Save a draft; export a new world datapack after leaving Minecraft."))
                    .binding(false, () -> d.lithostitchedBlend, v -> d.lithostitchedBlend = v)
                    .controller(BooleanControllerBuilder::create)
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Ridge Enhancement"))
                    .description(description("0..40; seed-dependent ridge shaping. Approximation, not hydraulic erosion."))
                    .binding(0, () -> d.ridgePercent, v -> d.ridgePercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,40).step(1))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Erosion Incision"))
                    .description(description("0..30; seed-dependent limited terrain cutting. Not a watershed solver."))
                    .binding(0, () -> d.erosionPercent, v -> d.erosionPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,30).step(1))
                    .build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Climate & Latitude"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Altitude-Aware Biomes"))
                    .description(description("0 = vanilla climate. Higher settings cool elevated terrain in newly generated worlds; coastal and low valley climates remain unchanged."))
                    .binding(65, () -> d.climatePercent, v -> d.climatePercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Valley Warming (Optional)"))
                    .description(description("0=off. Bounded local-valley warmth on inland terrain, using nearby terrain signals. Cold-air inversions are not simulated; requires restart and new world."))
                    .binding(0, () -> d.valleyPercent, v -> d.valleyPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5))
                    .build())
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Latitude Climate Cycle"))
                    .description(description("Off or north-south Earth/Preview or your concentric Radial Earth/Preview. Radial latitude follows distance from fixed X/Z center: equator -> circular pole -> next equator. 1 block=1m in Earth modes."))
                    .binding("off", () -> d.latitudeMode, v -> d.latitudeMode = v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values("off","earth","preview","radial_earth","radial_preview","radial_balanced"))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Latitude Climate Strength"))
                    .description(description("0..100; equator modestly warmer, poles much colder. Existing biomes and humidity remain in the climate sampler."))
                    .binding(70, () -> d.latitudeStrength, v -> d.latitudeStrength = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Equator Line Z / Radial Center Z"))
                    .description(description("For radial modes this is the center Z coordinate. For North/South modes this is equator Z. Actual auto-spawn need not be at the center."))
                    .binding(0, () -> d.equatorZ, v -> d.equatorZ = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(-4096,4096).step(128))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Radial Ring Center X"))
                    .description(description("Ring origin X; set alongside the Z center. This is not guaranteed to match the game's random spawn position."))
                    .binding(0, () -> d.radialCenterX, v -> d.radialCenterX = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(-4096,4096).step(128))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Radial Cold Climate Limit"))
                    .description(description("Default 60; caps combined polar and mountain cooling relative to original temperature. 0=off. Applies only to concentric latitude modes."))
                    .binding(60, () -> d.coldLimitPercent, v -> d.coldLimitPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Enclosed Basin Warming (Optional)"))
                    .description(description("Default OFF; only when all eight surrounding sample points form a basin rim, inland. Cold air pooling and temperature inversions not simulated."))
                    .binding(0, () -> d.basinPercent, v -> d.basinPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5))
                    .build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Height, Caves & Rivers"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Terrain Relief (percent)"))
                    .description(description("80..125% of original land-height density; affects NEW world only."))
                    .binding(100, () -> d.reliefPercent, v -> d.reliefPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(80,125).step(1))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Elevation Offset"))
                    .description(description("Signed terrain-density offset; Y=63 sea level stays fixed."))
                    .binding(0, () -> d.elevation, v -> d.elevation = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(-48,48).step(1))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Warped Geologic Strata"))
                    .description(description("0..20; subtle layered solidity bands. Does not swap rock materials."))
                    .binding(0, () -> d.strataPercent, v -> d.strataPercent = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,20).step(1))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Cave Activity"))
                    .description(description("20..80; higher tends to permit more rock cavities."))
                    .binding(50, () -> d.caves, v -> d.caves = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(20,80).step(1))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("River Grade"))
                    .description(description("0 off, 3 conservative, 6 standard, 9 flexible. Banks are still required."))
                    .binding(6, () -> d.riverGrade, v -> d.riverGrade = v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(0,3,6,9))
                    .build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Fantasy Nether and End Expansion (Experimental)"))
                    .description(description("Opt-in for NEXT NEW WORLD only. Nether terrain fills 256 levels; outer End gains tiered islands beyond 1152 blocks. Preserve old worlds; dragon and portal gameplay require in-client testing."))
                    .binding(false, () -> d.fantasyDimensions, v -> d.fantasyDimensions = v)
                    .controller(BooleanControllerBuilder::create)
                    .build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Prefer Inland Spawn"))
                    .description(description("Biases NEW world initial spawn away from coastlines."))
                    .binding(true, () -> d.inland, v -> d.inland = v)
                    .controller(BooleanControllerBuilder::create)
                    .build())
                .build())
            .save(d::save)
            .build()
            .generateScreen(parent);
    }
}
