package org.realscale.neoforge;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.biome.Biome;

/** Import the 1.20.1 placement rules without a Forge registry or dependency. */
final class FabricBiomeModifiers {
    private static List<String> values(JsonElement value) {
        if (value.isJsonPrimitive()) return List.of(value.getAsString());
        List<String> out = new ArrayList<>();
        for (JsonElement e : value.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }
    static void register() {
        var mod = FabricLoader.getInstance().getModContainer("realscale").orElseThrow();
        var folder = mod.findPath("realscale/biome_modifiers").orElseThrow();
        int count = 0;
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    JsonObject rule = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!rule.get("type").getAsString().endsWith(":add_features"))
                        throw new IllegalStateException("Unsupported biome modifier " + path);
                    List<String> selectors = values(rule.get("biomes"));
                    List<String> features = values(rule.get("features"));
                    var step = GenerationStep.Decoration.valueOf(rule.get("step").getAsString().toUpperCase(Locale.ROOT));
                    for (String feature : features) {
                        if (feature.startsWith("#")) throw new IllegalStateException("Unsupported feature tag " + feature);
                        BiomeModifications.addFeature(context -> {
                            for (String selector : selectors) {
                                if (selector.startsWith("#")) {
                                    if (context.hasTag(TagKey.create(Registries.BIOME, new ResourceLocation(selector.substring(1))))) return true;
                                } else if (context.getBiomeKey().location().equals(new ResourceLocation(selector))) return true;
                            }
                            return false;
                        }, step, ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(feature)));
                    }
                    count++;
                }
            }
        } catch (IOException e) { throw new IllegalStateException("Failed to read RealScale biome rules", e); }
        System.out.println("REALSCALE_FABRIC_BIOME_RULES " + count);
    }
}
