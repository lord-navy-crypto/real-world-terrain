# Deep Ocean Sky Blossom Isles, caves and marine habitats (experimental)

## Scope
**Opt-in** world preset `realscale:realism_max_ecotones_skylands` nests the existing `realscale:natural_ecotones` source inside `realscale:sky_ocean`. The normal Realism Max, normal Natural Ecotones, and USGS Big Sur presets remain unchanged.

### Sky Blossom Isles
- Each 512×512-block-centered cell has **at most one candidate**, with a deterministic jittered center (within ±128 blocks of the lattice node). The origin cell's candidate is **X32, Z63** for repeatable verification.
- An island is eligible only when its center is vanilla **deep ocean** and 64 sampled ocean points across 4 rings reaching **384 blocks** in 16 compass directions are all ocean biomes. This is a conservative sampled distance proxy; it is **not** an exact continent-distance computation.
- An eligible site receives a small 10–14-block-radius floating island at approximately Y 183–203. Island mass is genuine generated blocks (stone/end stone base, dirt, grass/moss top) with flowers and a glowing amethyst/sealight core.
- The sky biome `realscale:sky_blossom_isles` exists only in a volume around a confirmed eligible floating island. Ocean, river, seabed and cave biomes are never overwritten by the sky source at normal elevations.
- Full-island preflight avoids intersecting existing blocks or water. No fluids, ocean topography, or terrain noise are changed.
- Important limit: the candidate lattice is *coordinate*-deterministic, while ocean eligibility depends on the world seed's biomes; your seed influences which islands actually spawn.

### Caves
- Lush caves: low-frequency naturally supported moss carpets on stone/deepslate/tuff floors.
- Dripstone caves: small supported pointed-dripstone clusters only where existing dripstone substrate is present.
- These use the same optional habitat patch feature implementation. They do not widen tunnels, remove blocks, overwrite ores or cause water/lava replacement.

### Marine
- Ocean, lukewarm and some deep-water biomes: extra sparse seabed seagrass tuft patches.
- Cold and deep-ocean biome seabeds: thin tuff/andesite scatter, replacing a small amount of safe, ordinary sediment only.
- Features check for submerged origin and preserve the entire water column and ocean biomes. Existing vanilla coral reefs and shipwreck features remain.

## Playtesting
1. Install a **successful GitHub Actions JAR** for this branch in Minecraft Java 1.21.1 / NeoForge 21.1.249, using Java 21.
2. Create a new world and select `realscale:realism_max_ecotones_skylands`. Existing saved worlds will retain their serialized biome source.
3. Travel to large, open deep oceans, hundreds of blocks away from nearby coastal land. Islands are rare by design, roughly one *candidate* per 512-block grid cell, and most candidates near land are rejected. Search several large ocean regions; enable creative flight for visual testing.
4. When above sea level near Y190, use F3 to verify `realscale:sky_blossom_isles`. Check that a solid floating island exists beneath that biome, and that the ocean at Y60 retains its normal ocean biome.
5. Inspect lush/dripstone cave floors and seagrass/stone seabed patches in newly generated chunks.
6. Record world seed, X/Y/Z, F3 biome, screenshot, `latest.log`, and mods list if a generation anomaly or crash occurs.

## QA and caveats
- Static JSON check, Java signed-coordinate geometry test, NeoForge build, ordinary USGS regression server boot, plus a new dedicated test of the skylands preset's registry/world save.
- The ordinary preset-boot test **cannot guarantee seeing an island** near spawn. A separate dedicated `skylands_test_deep_ocean` fixture verifies a solid island and the SKY biome in actual generated Anvil files.
- The forced-deep-ocean test passed on commit `bab2b5f`, locating the SKY biome in four generated chunks and the real center's geological column at X32, Z63: end stone and stone beneath dirt/moss, purple amethyst at Y184 and sea lantern at Y185. This is **not** proof of island rarity or distance-to-land accuracy in naturally generated oceans.
- Updated QA also checks the island's vertically continuous solid center, detached underside and saved biome section; keep the PR experimental until the final CI pass and visual field testing.
- Cave/underwater decorative density is experimental. Ocean-island sampling adds up to 65 extra underlying biome reads per candidate's eligibility check, bounded by per-worker caching.
- Feature generation is intentionally restrained; do not claim improved performance or flawless marine ecology before screenshots and profiler evidence.
