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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;


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
@Mod.EventBusSubscriber(modid = "realscale", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RealScaleTerrainLabClient {
    private static final Path CONFIG = Path.of("config", "realscale-terrain-lab.json");
    private static final Path PERFORMANCE_CONFIG = Path.of("config", "realscale-performance.json");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> createScreen(parent))
        ));
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
        String preset = "natural_automate";
        int naturalMorphologyPercent = 100;
        int naturalReliefPercent = 100;
        boolean naturalInlandSpawn = true;
        int blendPercent = 25;
        boolean lithostitchedBlend = false;
        boolean lithostitchedGeology = false;
        int ridgePercent = 0;
        int erosionPercent = 0;
        int strataPercent = 0;
        int climatePercent = 65;
        int valleyPercent = 0;
        String latitudeMode = "fixed_1_20";
        int latitudeStrength = 58;
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
        Boolean architectureStructuresEnabled = Boolean.TRUE;
        // OFF by default; new high-rise district generation is experimental.
        Boolean urbanDistrictsEnabled = Boolean.FALSE;
        String performanceProfile = "balanced";
        boolean performanceDiagnostics = false;
        boolean predictiveUpload = false;
        boolean worldgenDiagnostics = false;
        Map<String,FamilySettings> profiles = new LinkedHashMap<>();

        static Draft defaults() {
            Draft d = new Draft();
            for (String family : EDITABLE_FAMILIES)
                d.profiles.put(family, FamilySettings.defaults(family));
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
            // Normalize historical latitude choices to the one fixed WGS84
            // 1:20 meridional climate model. Strength 0 disables its contribution.
            d.latitudeMode = "fixed_1_20";
            // Gson leaves missing boxed fields null in older config files.
            if (d.architectureStructuresEnabled == null)
                d.architectureStructuresEnabled = Boolean.TRUE;
            if (d.urbanDistrictsEnabled == null)
                d.urbanDistrictsEnabled = Boolean.FALSE;
            if (d.performanceProfile == null) d.performanceProfile = "balanced";
            // Saved YACL drafts from v1.4.1 remain readable. These names are
            // normalized only in the next-world draft, never in existing saves.
            if (d.preset == null) d.preset = "natural_automate";
            switch (d.preset) {
                case "realism_max", "georeal_ultimate", "ultimate" -> d.preset = "landscape";
                case "himalayan", "realistic_mountains" -> d.preset = "alpine";
                case "grand_canyon", "continental" -> d.preset = "canyon";
                default -> { }
            }
            // The three historical blend families remain readable by the
            // exporter, but are no longer player-selectable generation modes.
            // Preserve the last valid legacy draft before normalizing the UI
            // to the single public Natural Automate engine.
            if ("landscape".equals(d.preset) || "alpine".equals(d.preset)
                    || "canyon".equals(d.preset)) {
                if (d.profiles == null) d.profiles = new LinkedHashMap<>();
                FamilySettings saved = d.profiles.get(d.preset);
                if (saved == null || !saved.valid())
                    d.profiles.put(d.preset, FamilySettings.fromDraft(d));
                d.preset = "natural_automate";
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
            if (!("balanced".equals(d.performanceProfile)
                    || "low_memory".equals(d.performanceProfile)
                    || "smooth_render".equals(d.performanceProfile)
                    || "low_memory_smooth_render".equals(d.performanceProfile))) return false;
            if (!RealScaleStructurePacks.validOverworld(d.structureDensity)
                    || !RealScaleStructurePacks.validDimensions(d.dimensionStructureDensity))
                return false;
            return switch (d.preset) {
                case "natural_automate" -> d.naturalMorphologyPercent >= 0
                        && d.naturalMorphologyPercent <= 135
                        && d.naturalReliefPercent >= 80 && d.naturalReliefPercent <= 125
                        && d.latitudeStrength >= 0 && d.latitudeStrength <= 100
                        && "fixed_1_20".equals(d.latitudeMode)
                        && d.architectureStructuresEnabled != null
                        && d.urbanDistrictsEnabled != null;
                case "landscape", "alpine", "canyon" ->
                        d.blendPercent >= 0 && d.blendPercent <= 50
                        && (!d.lithostitchedGeology || d.blendPercent > 0)
                        && d.ridgePercent >= 0 && d.ridgePercent <= 40
                        && d.erosionPercent >= 0 && d.erosionPercent <= 30
                        && d.strataPercent >= 0 && d.strataPercent <= 20
                        && d.climatePercent >= 0 && d.climatePercent <= 100
                        && d.valleyPercent >= 0 && d.valleyPercent <= 100
                        && "fixed_1_20".equals(d.latitudeMode)
                        && d.latitudeStrength >= 0 && d.latitudeStrength <= 100
                        && d.architectureStructuresEnabled != null
                        && d.urbanDistrictsEnabled != null
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
            if (profiles.containsKey(preset)) profiles.get(preset).copyTo(this);
            else if ("natural_automate".equals(preset)) {
                reliefPercent=naturalReliefPercent;
                inland=naturalInlandSpawn;
            }
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
            savePerformanceSettings();
        }

        private void savePerformanceSettings() {
            Map<String,Object> settings=new LinkedHashMap<>();
            settings.put("profile",performanceProfile);
            settings.put("diagnostics",performanceDiagnostics);
            settings.put("predictiveUpload",predictiveUpload);
            settings.put("worldgenDiagnostics",worldgenDiagnostics);
            Path temp=PERFORMANCE_CONFIG.resolveSibling(
                    PERFORMANCE_CONFIG.getFileName().toString()+".tmp");
            try {
                Files.createDirectories(PERFORMANCE_CONFIG.getParent());
                Files.writeString(temp,JSON.toJson(settings),StandardCharsets.UTF_8);
                try {
                    Files.move(temp,PERFORMANCE_CONFIG,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp,PERFORMANCE_CONFIG,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch(IOException ex) {
                throw new IllegalStateException(
                        "Cannot save RealScale performance settings",ex);
            } finally {
                try { Files.deleteIfExists(temp); } catch(IOException ignored) { }
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

    public static Screen createScreen(Screen parent) {
        Draft d=Draft.read();
        d.initializeProfiles();
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("RealScale Studio | Natural Automate & Performance"))
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Natural Automate | Unified World Engine"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Adaptive Morphology Strength (%)"))
                    .description(description("The single public RealScale engine composes all six fitted kernels over the smoothed Natural baseline with climate, regional and topographic suitability gates. The expanded Cave Exploration V2 ring-and-annex network is enabled in this world profile. 0 disables fitted morphology; 100 is the fitted default; up to 135 increases its bounded contribution."))
                    .binding(100, () -> d.naturalMorphologyPercent,
                            v -> d.naturalMorphologyPercent=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o)
                            .range(0,135).step(5)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Natural Automate Terrain Relief (%)"))
                    .description(description("80-125%; scales the composite height signal for NEW worlds only. Sea level remains Y=63."))
                    .binding(100, () -> d.naturalReliefPercent, v -> d.naturalReliefPercent=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o)
                            .range(80,125).step(1)).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Prefer Inland Spawn"))
                    .description(description("Uses a stronger inland continentalness target for the NEW world's first spawn."))
                    .binding(true, () -> d.naturalInlandSpawn, v -> d.naturalInlandSpawn=v)
                    .controller(BooleanControllerBuilder::create).build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Built-in Vanilla Structures"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Overworld Structures (next world)"))
                    .description(description("Vanilla=0, Light=2x, Active=3x, Grand=4x (default), Epic=6x. All density packs are included in the RealScale JAR. Save and restart before creating a new world; existing saves keep their own datapack selections."))
                    .binding(4, () -> d.structureDensity, v -> d.structureDensity=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(0,2,3,4,6)).build())
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Nether + End Structures (next world)"))
                    .description(description("Vanilla=0, Grand=2x (default), Epic=3x. Save and restart before creating a new world."))
                    .binding(2, () -> d.dimensionStructureDensity,
                             v -> d.dimensionStructureDensity=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(0,2,3)).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Enable Architecture Library (100 Structures)"))
                    .description(description("ON by default for NEW worlds. Selects the built-in 100-structure pack automatically in Create World. Turn off to keep the pack available but not selected. Save and fully restart Minecraft before creating a new world; existing saves keep their own datapack choices."))
                    .binding(Boolean.TRUE,
                        () -> Boolean.TRUE.equals(d.architectureStructuresEnabled),
                        v -> d.architectureStructuresEnabled=v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Enable Modern Cities (Experimental)"))
                    .description(description("OFF by default. Selects a separate high-rise city-district datapack for newly created worlds after saving and restarting. A city candidate occurs in compatible flat-land biomes only; no existing saves or the 100-building library are changed."))
                    .binding(Boolean.FALSE,
                        () -> Boolean.TRUE.equals(d.urbanDistrictsEnabled),
                        v -> d.urbanDistrictsEnabled=v)
                    .controller(BooleanControllerBuilder::create).build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Shared Climate & Export"))
                .option(Option.<Integer>createBuilder()
                    .name(Component.literal("Fixed 1:20 Latitude Temperature Strength"))
                    .description(description("One fixed WGS84 meridional model: equator Z=0 and approximately 500,098 blocks from equator to either pole. 1:20 applies ONLY to climate latitude; terrain coordinates and X/Z sampling remain 1 block = 1 metre. Set strength to 0 to disable the latitude contribution."))
                    .binding(58, () -> d.latitudeStrength, v -> d.latitudeStrength=v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(0,100).step(5)).build())
                .build())
            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Performance Studio | Configuration"))
                .option(Option.<String>createBuilder()
                    .name(Component.literal("Performance Profile"))
                    .description(description("Balanced preserves current cache sizes. Low Memory reduces bounded RealScale cache capacities to one quarter. On Forge 1.20.1, Smooth Render does not yet intercept chunk uploads; it only requests the optional DH cave-LOD adapter. Restart required."))
                    .binding("balanced", () -> d.performanceProfile,
                            v -> d.performanceProfile=v)
                    .controller(o -> CyclingListControllerBuilder.create(o).values(
                        "balanced","low_memory","smooth_render",
                        "low_memory_smooth_render")).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Live Performance Diagnostics"))
                    .description(description("Enables bounded client frame/queue/heap telemetry for Performance Studio. Measurements include frame pacing and JVM heap; they are not GPU-only timings or VRAM. Restart required."))
                    .binding(false, () -> d.performanceDiagnostics,
                            v -> d.performanceDiagnostics=v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Predictive Upload Pacing (Experimental)"))
                    .description(description("Reserved for the Forge upload interception port. No effect in this build; keep OFF."))
                    .binding(false, () -> d.predictiveUpload,
                            v -> d.predictiveUpload=v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Worldgen Density Diagnostics"))
                    .description(description("Enables low-allocation density-function timing. Diagnostic overhead is measurable; use only during controlled profiling. Restart required."))
                    .binding(false, () -> d.worldgenDiagnostics,
                            v -> d.worldgenDiagnostics=v)
                    .controller(BooleanControllerBuilder::create).build())
                .build())
            // Saves an immutable next-world configuration DRAFT only. Players
            // use matching Config Exporter to create the new world's datapack.
            .save(d::save)
            .build()
            .generateScreen(parent);
    }
}
