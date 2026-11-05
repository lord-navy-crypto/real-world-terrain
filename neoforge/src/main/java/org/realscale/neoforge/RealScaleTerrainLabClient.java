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
import java.util.LinkedHashMap;
import java.util.Map;

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

    // These THREE editable presets keep their tuning in independent slots.
    // The original top-level fields remain in the JSON for older YACL drafts,
    // the standalone exporter, and existing CLI --from-yacl callers.
    private static final String[] EDITABLE_FAMILIES = {"landscape", "alpine", "canyon"};

    private static final class FamilySettings {
        int blendPercent=25;
        int reliefPercent=100;
        int elevation=0;
        int caves=50;
        int riverGrade=6;
        int ridgePercent=0;
        int erosionPercent=0;
        int strataPercent=0;
        int climatePercent=65;
        int valleyPercent=0;
        int basinPercent=0;
        boolean inland=true;
        boolean lithostitchedBlend=false;
        boolean lithostitchedGeology=false;
        boolean fantasyDimensions=false;

        static FamilySettings defaults(String family) {
            FamilySettings f=new FamilySettings();
            switch (family) {
                case "landscape" -> {
                    f.blendPercent=25; f.reliefPercent=100;
                    f.ridgePercent=6; f.erosionPercent=5; f.strataPercent=4;
                    f.caves=50; f.riverGrade=6;
                    f.climatePercent=60; f.valleyPercent=15; f.basinPercent=10;
                }
                case "alpine" -> {
                    f.blendPercent=30; f.reliefPercent=110; f.elevation=10;
                    f.ridgePercent=18; f.erosionPercent=8; f.strataPercent=8;
                    f.caves=55; f.riverGrade=3;
                    f.climatePercent=70; f.valleyPercent=25; f.basinPercent=10;
                }
                case "canyon" -> {
                    f.blendPercent=30; f.reliefPercent=105;
                    f.ridgePercent=6; f.erosionPercent=18; f.strataPercent=12;
                    f.caves=45; f.riverGrade=6;
                    f.climatePercent=35; f.valleyPercent=35; f.basinPercent=30;
                }
                default -> throw new IllegalArgumentException("Not an editable family: "+family);
            }
            return f;
        }

        static FamilySettings fromDraft(Draft d) {
            FamilySettings f=new FamilySettings();
            f.blendPercent=d.blendPercent; f.reliefPercent=d.reliefPercent;
            f.elevation=d.elevation; f.caves=d.caves; f.riverGrade=d.riverGrade;
            f.ridgePercent=d.ridgePercent; f.erosionPercent=d.erosionPercent;
            f.strataPercent=d.strataPercent; f.climatePercent=d.climatePercent;
            f.valleyPercent=d.valleyPercent; f.basinPercent=d.basinPercent;
            f.inland=d.inland; f.lithostitchedBlend=d.lithostitchedBlend;
            f.lithostitchedGeology=d.lithostitchedGeology;
            f.fantasyDimensions=d.fantasyDimensions;
            return f;
        }

        void copyTo(Draft d) {
            d.blendPercent=blendPercent; d.reliefPercent=reliefPercent;
            d.elevation=elevation; d.caves=caves; d.riverGrade=riverGrade;
            d.ridgePercent=ridgePercent; d.erosionPercent=erosionPercent;
            d.strataPercent=strataPercent; d.climatePercent=climatePercent;
            d.valleyPercent=valleyPercent; d.basinPercent=basinPercent;
            d.inland=inland; d.lithostitchedBlend=lithostitchedBlend;
            d.lithostitchedGeology=lithostitchedGeology;
            d.fantasyDimensions=fantasyDimensions;
        }

        boolean valid() {
            return blendPercent>=0 && blendPercent<=50
                && (!lithostitchedGeology || blendPercent>0)
                && ridgePercent>=0 && ridgePercent<=40
                && erosionPercent>=0 && erosionPercent<=30
                && strataPercent>=0 && strataPercent<=20
                && climatePercent>=0 && climatePercent<=100
                && valleyPercent>=0 && valleyPercent<=100
                && basinPercent>=0 && basinPercent<=100
                && reliefPercent>=80 && reliefPercent<=125
                && elevation>=-48 && elevation<=48
                && caves>=20 && caves<=80
                && (riverGrade==0 || riverGrade==3 || riverGrade==6 || riverGrade==9);
        }
    }

    private static class Draft {
        String preset = "landscape";
        int blendPercent = 25;
        boolean lithostitchedBlend = false;
        boolean lithostitchedGeology = false;
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
        // Global next-world selection for built-in structure_set datapacks.
        // Saved world datapack choices remain owned by each world.
        int structureDensity = 4;
        int dimensionStructureDensity = 2;
        Map<String,FamilySettings> profiles = new LinkedHashMap<>();

        static Draft defaults() {
            Draft d = new Draft();
            for (String family : EDITABLE_FAMILIES)
                d.profiles.put(family, FamilySettings.defaults(family));
            d.profiles.get(d.preset).copyTo(d);
            return d;
        }

        static Draft read() {
            if (!Files.exists(CONFIG)) return defaults();
            try {
                Draft data = JSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), Draft.class);
                if (data == null) return defaults();
                migrateLegacyDraft(data);
                if (!valid(data)) return defaults();
                data.initializeProfiles();
                return data;
            } catch (IOException | JsonSyntaxException ex) {
                return defaults();
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

        void initializeProfiles() {
            if (profiles==null) profiles=new LinkedHashMap<>();
            boolean hasPreviouslySavedProfiles=!profiles.isEmpty();
            for (String family : EDITABLE_FAMILIES) {
                FamilySettings entry=profiles.get(family);
                if (entry==null || !entry.valid()) {
                    // Old one-slot drafts retain exactly the user's ACTIVE
                    // settings. Only other family slots get new defaults.
                    profiles.put(family, !hasPreviouslySavedProfiles && family.equals(preset)
                        ? FamilySettings.fromDraft(this) : FamilySettings.defaults(family));
                }
            }
        }

        private static boolean valid(Draft d) {
            if (!RealScaleStructurePacks.validOverworld(d.structureDensity)
                    || !RealScaleStructurePacks.validDimensions(d.dimensionStructureDensity))
                return false;
            return switch (d.preset) {
                case "landscape", "alpine", "canyon" ->
                        d.blendPercent >= 0 && d.blendPercent <= 50
                        && (!d.lithostitchedGeology || d.blendPercent > 0)
                        && d.ridgePercent >= 0 && d.ridgePercent <= 40
                        && d.erosionPercent >= 0 && d.erosionPercent <= 30
                        && d.strataPercent >= 0 && d.strataPercent <= 20
                        && d.climatePercent >= 0 && d.climatePercent <= 100
                        && d.valleyPercent >= 0 && d.valleyPercent <= 100
                        && d.latitudeMode != null
                        && (d.latitudeMode.equals("off") || d.latitudeMode.equals("earth")
                            || d.latitudeMode.equals("preview")
                            || d.latitudeMode.equals("radial_earth")
                            || d.latitudeMode.equals("radial_preview") || d.latitudeMode.equals("radial_balanced")
                            || d.latitudeMode.equals("radial_equal_area")
                            || d.latitudeMode.equals("radial_equal_area_single_pole"))
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
            initializeProfiles();
            profiles.get(preset).copyTo(this);
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

    private static ConfigCategory familyCategory(String label, FamilySettings f) {
        return ConfigCategory.createBuilder()
            .name(Component.literal(label))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Secondary Landform Mix (%)"))
                .description(description("0-50%. Change this family's blend without altering the other two families."))
                .binding(25, () -> f.blendPercent, v -> f.blendPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,50).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Terrain Relief (%)"))
                .description(description("80-125%; scales NEW world's landform relief, never the existing world."))
                .binding(100, () -> f.reliefPercent, v -> f.reliefPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(80,125).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Elevation Offset"))
                .description(description("-48..48; adds land elevation offset; ocean sea level stays Y=63."))
                .binding(0, () -> f.elevation, v -> f.elevation=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(-48,48).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Ridge Enhancement"))
                .description(description("0-40; gently intensifies secondary mountain ridge patterns."))
                .binding(0, () -> f.ridgePercent, v -> f.ridgePercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,40).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Erosion Incision"))
                .description(description("0-30; limited terrain incision, NOT full hydraulic erosion."))
                .binding(0, () -> f.erosionPercent, v -> f.erosionPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,30).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Geologic Strata"))
                .description(description("0-20; adjusts underground solidity layers; does not swap stone blocks."))
                .binding(0, () -> f.strataPercent, v -> f.strataPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,20).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Cave Activity"))
                .description(description("20-80. Underground caves may become more open; banks stay guarded."))
                .binding(50, () -> f.caves, v -> f.caves=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(20,80).step(1)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("River Grade"))
                .description(description("0 off / 3 conservative / 6 standard / 9 flexible. Rivers still need protected banks."))
                .binding(6, () -> f.riverGrade, v -> f.riverGrade=v)
                .controller(o -> CyclingListControllerBuilder.create(o).values(0,3,6,9)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Altitude-Aware Biomes"))
                .description(description("0-100; temp drop at high elevation; climate mix is world-specific."))
                .binding(65, () -> f.climatePercent, v -> f.climatePercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Valley Warming (Optional)"))
                .description(description("0-100; bounded relative valley temperature adjustment."))
                .binding(0, () -> f.valleyPercent, v -> f.valleyPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Enclosed Basin Warming (Optional)"))
                .description(description("0-100; locally warms enclosed basins only where topography qualifies."))
                .binding(0, () -> f.basinPercent, v -> f.basinPercent=v)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Prefer Inland Spawn"))
                .description(description("Avoid placing the NEW world's first spawn near ocean/coast where possible."))
                .binding(true, () -> f.inland, v -> f.inland=v)
                .controller(BooleanControllerBuilder::create).build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Lithostitched Adaptive Blend (Opt-in)"))
                .description(description("OFF by default. Small spatial changes to the two height families."))
                .binding(false, () -> f.lithostitchedBlend, v -> f.lithostitchedBlend=v)
                .controller(BooleanControllerBuilder::create).build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Lithostitched Deep Geology (Experimental)"))
                .description(description("Requires landform mix > 0; uses bounded Perlin, shift and layer functions. Tested only on new Lab worlds."))
                .binding(false, () -> f.lithostitchedGeology, v -> f.lithostitchedGeology=v)
                .controller(BooleanControllerBuilder::create).build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Fantasy Nether & End (Experimental)"))
                .description(description("Optional extended Nether/End generation. Never changes existing worlds."))
                .binding(false, () -> f.fantasyDimensions, v -> f.fantasyDimensions=v)
                .controller(BooleanControllerBuilder::create).build())
            .build();
    }

    private static Screen createScreen(Screen parent) {
        Draft d=Draft.read();
        d.initializeProfiles();
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("RealScale | 4 Terrain Types / 3 Custom Families"))
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Choose World Type"))
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Editable Terrain Family"))
                    .description(description("Natural blends Realism MAX + GeoReal; Alpine blends Himalayan + Realistic Mountains; Canyon blends Grand Canyon + Continental. Edit each family in its OWN tab. Save exports only the chosen family. To apply it, exit Minecraft and run the matching RealScale-Config-Exporter.pyz with the mod JAR and saved YACL JSON; enable its ZIP when creating a NEW world. Fourth type, USGS Big Sur v2, is measured/read-only: choose it directly under Create New World > World Type. Legacy world IDs remain compatible."))
                    .binding("landscape", () -> d.preset, v -> d.preset=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(
                        "landscape","alpine","canyon")).build())
                .build())
            .category(familyCategory("Natural | Landscape",d.profiles.get("landscape")))
            .category(familyCategory("Alpine | Mountains",d.profiles.get("alpine")))
            .category(familyCategory("Canyon | Plateaus",d.profiles.get("canyon")))
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Built-in Vanilla Structures"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Overworld Structures (next world)"))
                    .description(description("Vanilla=0, Light=2x, Active=3x, Grand=4x (default), Epic=6x. "
                        + "All six structure packs are INCLUDED in the RealScale mod JAR. "
                        + "Save and restart Minecraft before creating a new world; selected pack is "
                        + "enabled automatically for that new world. This is a candidate-placement "
                        + "multiplier, NOT a guarantee of actual buildings. Existing saves keep their "
                        + "own datapack selections; do not use this to retune existing worlds."))
                    .binding(4, () -> d.structureDensity, v -> d.structureDensity=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(0,2,3,4,6)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Nether + End Structures (next world)"))
                    .description(description("Vanilla=0, Grand=2x (default), Epic=3x. "
                        + "Preserves fortress/bastion weights and End City placement type. "
                        + "Save and restart before creating your next world. "
                        + "Only NEW worlds use the updated auto-enabled pack selection."))
                    .binding(2, () -> d.dimensionStructureDensity,
                             v -> d.dimensionStructureDensity=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(0,2,3)).build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Shared Climate & Latitude / Export"))
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Latitude Climate Cycle"))
                    .description(description("Shared latitude controls; equal-area supports legacy two-crest and PR25 single-pole ring geometry."))
                    .binding("off", () -> d.latitudeMode, v -> d.latitudeMode=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(
                        "off","earth","preview","radial_earth","radial_preview","radial_balanced",
                        "radial_equal_area","radial_equal_area_single_pole")).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Latitude Strength"))
                    .description(description("0-100; regional pole/equator temperature differences."))
                    .binding(70, () -> d.latitudeStrength, v -> d.latitudeStrength=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Equator Z / Ring Center Z"))
                    .description(description("Center Z of concentric latitude rings; world spawn may be elsewhere."))
                    .binding(0, () -> d.equatorZ, v -> d.equatorZ=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(-4096,4096).step(128)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Ring Center X"))
                    .description(description("Concentric latitude center X."))
                    .binding(0, () -> d.radialCenterX, v -> d.radialCenterX=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(-4096,4096).step(128)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Radial Cold Climate Limit"))
                    .description(description("0-100; limits extra combined polar and mountain cooling."))
                    .binding(60, () -> d.coldLimitPercent, v -> d.coldLimitPercent=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
                .build())
            // Saves an immutable next-world configuration DRAFT only. Players
            // use matching Config Exporter to create the new world's datapack.
            .save(d::save)
            .build()
            .generateScreen(parent);
    }
}
