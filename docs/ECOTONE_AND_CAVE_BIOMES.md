# Own Biomes and Narrow Ecotone Design — experimental v0.1

## What has been committed
Four standalone JSON biome registrations:
- `realscale:woodland_meadow`
- `realscale:semi_arid_scrubland`
- `realscale:subalpine_woodland`
- `realscale:damp_limestone_caverns`

A deterministic, pure-Java `EcotoneMath` utility defines compatible biome pairings, checks protected waterways, limits transition widths to 16–64 blocks, and computes smooth boundary weights. **This code is not wired to the actual multi-noise biome source yet.**

## Critical worldgen caveat
Existing RealScale world presets all use `{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:overworld\"}` for the overworld. This source does not automatically select custom biomes merely because their registry JSON files exist. A new opt-in preset or a custom biome-source codec is required. Do NOT overwrite the validated default presets. The existing biome data files presently use empty decoration feature lists and spawn settings; these are registry/scaffold biomes rather than production-quality landscapes.

## Target selection architecture
1. Read native vanilla biome/climate samples at nearby quart-resolution positions; classify biome pair. Never blend solely on visible 16-block chunk edges.
2. Establish a stable signed-distance-to-boundary field from climate/multi-noise sampling, cache per region without mutation.
3. Choose a compatible ecotone biome ID only inside 16–64 block width, excluding river/ocean, abrupt cliffs and protected geographic features.
4. Smooth vegetation/substrate *within* and across boundaries using per-column environment samplers; do not interpolate terrain blocks or edit flow channels.
5. Separate cave selector: check whether position is below underground ceiling, local cave air openness, groundwater and host rock. Register underground biome only within cave-relevant depth windows, not on aboveground surface.
6. Provide independent cave-carving repairs by fixing the cave density function/carver and aquifer consistency; changing biome IDs alone does not repair broken cave geometry.

## Testing and rollout
- Verify all new biome JSONs decode in Minecraft 1.21.1 / NeoForge 21.1.x.
- Run an isolated synthetic algorithm test for symmetry, protected waterways, width clamping, slope rejection and deterministic weights.
- After implementing the opt-in biome source, verify `/locate biome realscale:woodland_meadow` and `/locate biome realscale:damp_limestone_caverns` against generated chunks.
- Test surface/underground 3D biome sampling, Y-boundary transitions, zero air-pocket false positives, and repeatability after restart.
- Confirm sunlight, mobs, vegetation features, spawning and precipitation in custom biomes; currently empty feature/spawn lists need intentional population.
- Compare chunk-generation times, cave continuity, water connectivity and vertical biome transitions against accepted hydrology build. The currently connected repo history does not include referenced commit 8962a71.
- Only merge once real dedicated server tests demonstrate no crashes or regressions.

## References
NeoForge 1.21.1 biome modifiers: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
Minecraft biome source serialization: https://minecraft.wiki/w/Custom_dimension
