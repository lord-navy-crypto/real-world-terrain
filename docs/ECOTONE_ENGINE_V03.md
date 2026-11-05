# Ecotone engine v0.3 — priority ten specifications

This document is an implementation specification, not a claim that boundary-aware biomes now spawn naturally. Catalog contains 40 pairs; 10 have registered biome IDs and a JVM pairing rule.

| Biome | Adjacent macrohabitats | Suggested transition blocks | Width | Atmosphere / visual direction |
|---|---|---|---|---|
| woodland_meadow | forest + meadow | grass, oak, poppy, fern | 32 | sunlit opening woodland |
| forest_steppe | forest + grassland | coarse dirt, grass, shrubs | 40 | breezy sparse dry woodland |
| marshy_woodland | forest + swamp | mud, moss, oak and fern | 24 | cool, shaded damp |
| semi_arid_scrubland | desert + grassland | sand, coarse dirt, dead bush | 48 | dry open scrub |
| subalpine_woodland | taiga + alpine | spruce, stone, thin meadow | 32 | sparse high treeline |
| rocky_alpine_meadow | meadow + alpine | stone, gravel, grass | 24 | exposed alpine slope |
| snowy_treeline | taiga + snow | snow, spruce, stone | 24 | diminishing snow / dwarf trees |
| floodplain_margin | river + plains | gravel, mud, clay, grass | 16 | high-bank wet fertile fringe |
| birch_conifer_transition | birch forest + taiga | birch, spruce, podzol | 32 | cool mixed forest |
| wet_meadow | plains + swamp | mud, grass, wet grasses | 24 | saturated soft grassland |

## BiomeSource integration contract (not implemented)
- Sample the underlying climate-source biome at quart-resolution in X, Y, Z, never by querying already-generated chunks.
- Locate stable climate class interfaces using neighboring climate samples, not abrupt chunk boundaries.
- Select intermediate IDs only when adjacent macrohabitats have a registered compatible pair and signed distance is inside the configured band.
- Biome transition ID should occupy no more than 16–64 blocks across normal surface borders; close to cliffs width shrinks or vanishes.
- Preserve all vanilla river/ocean biome selections; any floodplain margin belongs exclusively on dry bank columns that are not in protected channel masks.
- The JVM rule returning FLOODPLAIN_MARGIN for RIVER+PLAINS identifies a candidate only. It DOES NOT authorize editing rivers.
- Surface materials/vegetation blend with smooth weights; no river carving, no new sea level or independent terrain height changes.
- Cave biomes require a separate 3D sampler and must never be inserted by 2D surface adjacency.
- Current `biome_lab` checkerboard is a registry smoke-test, not natural biome integration.

## Test requirements
- Unit tests: 10 pairings reversible; unknown pair NONE; river+meadow NONE; protected-channel weight zero.
- Registry decode tests on dedicated NeoForge 1.21.1 server for all new custom biomes.
- Golden-seed tests: width distribution, transition coverage vs protected stream area and chunk boundary seams.
- Performance budget: confirm chunkgen CPU and memory, especially nearest-boundary climate sampling.
- Multi-seed screenshot comparisons: visually coherent vegetation and no band-shaped unrealistic artifacts.
- Water tests: unchanged wet-column distribution and connection graph after ecotone changes.

## Future phase
Add wet-to-dry cave margins, root-mound and nurse-log subcommunities, valley-specific riverbank transitions, coast-aware brackish gradients and slope-aware woodland spacing.
