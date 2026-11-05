# RealScale Natural Ecotones — 1.21.1 integration playtest

## Actual implementation
The opt-in world preset `realscale:realism_max_ecotones` uses `realscale:natural_ecotones` as the Overworld BiomeSource. Its `source` remains the vanilla `minecraft:multi_noise` overworld sampler and its noise settings remain `realscale:realism_max`. It samples adjacent quart positions to identify a REAL underlying biome boundary, then substitutes the configured intermediate biome only within a narrow strip.

The preset is listed in the normal world-preset tag. Use Create New World and choose the RealScale Realism Max Ecotones preset (resource ID above), then explore **new** chunks.

No checkerboard grid; no queries of yet-unformed terrain blocks; no cave/river density router changes. This implementation never replaces a current river, frozen-river or ocean biome, or any biome at quart Y below 12 (Y < 48). Width is based on radial axis-aligned climate samples (4 block per quart), and actual shapes are approximations to the 16–48 block target widths.

## Testing
1. NeoForge 21.1.249, Minecraft Java 1.21.1, Java 21. Install a JAR built by the **latest green** PR CI artifact.
2. **Create a new test world**. In existing worlds new chunks are needed, and old worldgen source serialized into an existing save will remain unchanged.
3. Explore boundaries between forest and plains/meadow, forest and swamp, savanna and desert, taiga and mountains/snow, and riverbanks (river blocks themselves remain the source biome).
4. Check F3 biome names. Expected: `realscale:woodland_meadow`, `realscale:marshy_woodland`, `realscale:semi_arid_scrubland`, `realscale:subalpine_woodland`, `realscale:floodplain_margin` in eligible borders only.
5. Walk across a boundary and document sampled transition-strip width, whether coastline or river changed, abrupt color changes, tree density and missing vegetation.
6. Compare against **original** `realscale:realism_max` world using the same seed. It should preserve its prior climate-source core and terrain shape when no ecotone is selected.

## Current limitations
- This is climate-boundary based, not a true terrain-slope-driven ecological classifier. Quart sampling may yield irregular stepped strips.
- The forty biome IDs are data-driven prototypes with basic colors and currently sparse/empty spawn and decoration data. Natural selection does NOT magically fill them with full vanilla biome forests or wildlife.
- The separate six cave biomes remain unselected by this new *surface-only* BiomeSource.
- Existing stable presets remain unchanged. The new preset is opt-in until player screenshots/real-world generation confirm the visual quality and performance.

## Technical regression
PR CI: Gradle compile, static validation, existing USGS dedicated-server boot and new Realism Max Ecotone dedicated-server boot. The new job asserts persisted world preset in level.dat; this proves codec/registry decode and server-generated world startup but **does not** prove all 40 boundary types are found. Use visible screenshots + seed/coordinates for the visual acceptance.
