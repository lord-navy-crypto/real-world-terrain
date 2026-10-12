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
 * On the NEXT Gradle client launch, prepareTerrainLabDraft produces an immutable
 * world preset before Minecraft loads worldgen registries.
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
        int ridgePercent = 0;
        int erosionPercent = 0;
        int strataPercent = 0;
        int reliefPercent = 100;
        int elevation = 0;
        int caves = 50;
        int riverGrade = 6;
        boolean inland = true;

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
            try {
                Files.createDirectories(CONFIG.getParent());
                Files.writeString(CONFIG, JSON.toJson(this), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot write next-world Terrain Lab draft", ex);
            }
        }
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static Screen createScreen(Screen parent) {
        Draft d = Draft.read();
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("RealScale Terrain Lab - Next New World"))
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Landforms and Height"))
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Base Terrain"))
                    .description(description("Three merged families blend existing terrain noise fields. USGS measured DEM is separate and read-only."))
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
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Caves and Rivers"))
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
                    .name(Component.literal("Prefer Inland Spawn"))
                    .description(description("Biases NEW world initial spawn away from coastlines."))
                    .binding(true, () -> d.inland, v -> d.inland = v)
                    .controller(BooleanControllerBuilder::create)
                    .build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Applying Settings"))
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Restart/Build Required"))
                    .description(description("Save the draft, EXIT Minecraft, then relaunch via Gradle runClient. "
                        + "The new RealScale Lab world type appears under Create World > World Type. "
                        + "Never modifies an existing world."))
                    .binding(true, () -> true, v -> {})
                    .controller(BooleanControllerBuilder::create)
                    .available(false)
                    .build())
                .build())
            .save(d::save)
            .build()
            .generateScreen(parent);
    }
}
