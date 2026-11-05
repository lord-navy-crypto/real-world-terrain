package org.realscale.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Built-in optional structure packs: six vanilla density presets plus a
 * mixed licensed/original RealScale architecture library.
 *
 * Density defaults and the 58-structure architecture pack are selected from
 * the Terrain Lab draft for NEW worlds. Packs are never forced, and saved worlds
 * retain their own selected datapack IDs. The architecture pack defaults on
 * but can be deselected with the in-game checkbox.
 *
 * Since minecraft:structure_set keys are global, these packs change all
 * matching dimensions of an opting-in world, not only RealScale biomes.
 */
public final class RealScaleStructurePacks {
    private static final String MODID = "realscale";
    private static final Path DRAFT = Path.of("config", "realscale-terrain-lab.json");

    record Selection(int overworld, int otherDimensions, boolean architectureEnabled) {
        Selection {
            if (!validOverworld(overworld) || !validDimensions(otherDimensions))
                throw new IllegalArgumentException("Unknown structure density");
        }
    }

    static boolean validOverworld(int density) {
        return density == 0 || density == 2 || density == 3
                || density == 4 || density == 6;
    }

    static boolean validDimensions(int density) {
        return density == 0 || density == 2 || density == 3;
    }

    static Selection readSelection() {
        if (!Files.isRegularFile(DRAFT))
            return new Selection(4, 2, true);
        try (var reader = Files.newBufferedReader(DRAFT, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int overworld = root.has("structureDensity")
                    ? root.get("structureDensity").getAsInt() : 4;
            int dimensions = root.has("dimensionStructureDensity")
                    ? root.get("dimensionStructureDensity").getAsInt() : 2;
            boolean architectureEnabled = !root.has("architectureStructuresEnabled")
                    || root.get("architectureStructuresEnabled").getAsBoolean();
            if (validOverworld(overworld) && validDimensions(dimensions))
                return new Selection(overworld, dimensions, architectureEnabled);
        } catch (IOException | RuntimeException e) {
            // A damaged config must never silently enable an unexpected
            // density. Keep the conservative vanilla placements in this case.
            System.err.println("[RealScale] Invalid structure density config; "
                    + "falling back to vanilla placements: " + e);
            return new Selection(0, 0, false);
        }
        System.err.println("[RealScale] Unsupported structure density value; "
                + "falling back to vanilla placements.");
        return new Selection(0, 0, false);
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;

        Selection current = readSelection();
        for (int ratio : new int[]{2, 3, 4, 6}) {
            registerOne(event, "overworld_" + ratio,
                    "RealScale | Overworld Structures " + ratio + "x",
                    current.overworld() == ratio);
        }
        for (int ratio : new int[]{2, 3}) {
            registerOne(event, "dimensions_" + ratio,
                    "RealScale | Nether + End Structures " + ratio + "x",
                    current.otherDimensions() == ratio);
        }
        // Terrain Lab controls the next-world default. The pack remains
        // optional (required=false); existing saves retain their own selection.
        registerOne(event, "architecture_pilot",
                "RealScale | Architecture Library (58 Structures)",
                current.architectureEnabled());
    }

    private static void registerOne(AddPackFindersEvent event, String id,
                                    String displayName, boolean selectedDefault) {
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(
                        MODID, "resourcepacks/realscale_structures_" + id),
                PackType.SERVER_DATA,
                Component.literal(displayName),
                selectedDefault ? PackSource.DEFAULT : PackSource.BUILT_IN,
                false, // Never mandatory: an existing world may omit this pack.
                Pack.Position.TOP);
    }

    private RealScaleStructurePacks() {}
}
