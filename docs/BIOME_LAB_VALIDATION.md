# Biome Lab: experimental integration and remaining blockers

The opt-in preset `realscale:biome_lab` uses the vanilla checkerboard biome source to force selection of all twelve custom biome registry entries. It copies the Realism Max dimension setup but **does not** replace its default preset.

**Purpose:** verify that the twelve biome IDs decode, can be selected, and are visible in newly generated chunks. Checkerboard borders are intentionally artificial; this is NOT the ecotone engine and DOES NOT restrict cave biomes to underground. Avoid playing real survival worlds with this diagnostic preset.

Static CI runs `python3 scripts/check_custom_biomes.py` for registry references and stable default preset. It does not verify Minecraft's registry codecs. The regular Gradle build does not exercise data-driven registry loading or worldgen.

## Hard blockers to completion
- Implement and register stable BiomeSource MapCodec that supports vanilla-compatible biomes plus narrow signed-distance ecotones.
- Derive 3D cave selection from climate, depth and geology sampling without reading chunk blocks during biome source computation.
- Finish biome feature/spawn lists, substrate placement and cave-specific decorations.
- Repair cave density/aquifer geometry independently from biome selection, using the validated newer river codebase.
- Perform actual NeoForge dedicated server tests and measure chunk times and water continuity.
- Current connected main's head `edd7598` is not the referenced accepted water-fix commit `8962a71`. Do not merge water or cave fixes without reconciling history.

## Acceptance commands
1. Create *new* test world with world preset `realscale:biome_lab`.
2. Run `/locate biome realscale:woodland_meadow`; test each of the 12 biome IDs.
3. Inspect Y-level biome selection and note diagnostic checkerboard remains 2D.
4. Capture startup logs for unknown registry keys, feature-cycle errors and invalid biome JSON.
5. Run multi-seed samples for caves, rivers, chunk timing, structures, snow and terrain integrity.
