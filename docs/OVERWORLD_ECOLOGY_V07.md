# RealScale Overworld Ecology v0.7 — Radial Ecotones and Water-Safe Coastal Debris

**Platform:** Minecraft 1.21.1 / NeoForge. Branch `feature/overworld-ecology-v07-radial-boundaries`, based on the fully tested v0.6 experimental branch (60 surface ecotones and 18 additional original-biome groundcover motifs).

## Radial, more terrain-coherent ecology transitions

Before v0.7 the `NaturalEcotoneBiomeSource` examined only four cardinal rays from each quart biome position. This meant a naturally diagonal climate interface could be missed, even when a neighboring original biome was just 5–11 blocks diagonally from the sample. It also preferred the shortest number of quart *steps* rather than physical 2D distance if diagonal sampling were subsequently added.

This phase adds **eight deterministic ray directions** (4 cardinal + 4 diagonal) and compares candidates using **squared Euclidean distance** in block coordinates. A 2-quart diagonal ray is sqrt(2) times longer than a 2-quart cardinal ray; the search accounts for this. A candidate can only be selected within **half of its declared ecotone width**. No new biome generation noise, chunk water routing or terrain density is introduced.

Existing rule priority on a given biome pair is preserved by the immutable preindexed `RULE_BY_BIOME` map from v0.6; ties across rays use fixed cardinal-first ordering. Each ray **stops on the first different original vanilla biome**, rather than skipping incompatible third-biome bands. This preserves small coastline and multi-biome boundaries. The search breaks when further samples cannot beat the current nearest interface, and stops diagonals beyond the global maximum physical radius. There is no random number generator or dependence on chunk load order.

Radial sampling can increase climate-source probes versus four-direction sampling; a **worldgen performance comparison on multiple seeds is a release gate**. The existing bounded thread-local cache and direct biome-pair lookup are preserved.

### Invariants

1. Vanilla ocean, deep ocean, river and frozen river biome cells are never replaced by the selector.
2. `quartY < 12` remains a strict protection against classifying the deepest climate sample spaces as land-surface ecotones. This is **not** a complete underground cave classifier.
3. The `realscale:realism_max_ecotones` opt-in preset still wraps the untouched base vanilla `minecraft:multi_noise` biome source. Default vanilla climate/noise settings are unchanged.
4. Neither world height, sea level, aquifers, cave carvers, structures, biome spawn categories, Nether nor End has been modified.
5. The 60 registered biome variants continue to use full 1.21.1 vanilla feature stages and mobs. No custom biome count change in this release.

## Vanilla coast natural structures — dry driftwood

Adds `realscale:coastal_driftwood`, a registered, **non-carving** custom Feature using only vanilla oak/spruce logs. Rarely added through a NeoForge biome modifier to `minecraft:beach`, `minecraft:stony_shore` and `minecraft:snowy_beach`; **never** targets original river or ocean biomes.

Wood is 2–4 blocks long, randomly horizontal X/Z oriented, and every single segment must be supported by dry full blocks (sand, red sand, gravel or stone). Every segment and the block above must be air and its fluid state empty. **The full log is prevalidated before any block is written**, preventing partial structures. A naturally flooded shore is simply skipped.

The configured feature, placed feature and biome modifier are checked for consistent 1.21.1 registry paths, heightmap and biome filter. Like other native-biome decoration additions, this feature affects those vanilla biomes in any world where the experimental mod JAR is installed; the ecotone source remains opt-in.

## Test/rollout gates

- Pure Java `HabitatMathSmoke` checks cardinal-vs-diagonal radial distance, band inclusions/exclusions, negative quadrant symmetry and invalid inputs.
- `scripts/check_custom_biomes.py` checks the new feature registry and the Java biome selector's radial/index/protection invariants.
- GitHub Actions must complete NeoForge build, baseline dedicated-server saved-world verification and ecotone-preset actual Anvil biome QA.
- Manual remaining tests: shore driftwood actual occurrence; screenshot/visual QA at diagonal biome edges; compare generated river/ocean surface and multi-seed throughput against v0.6. No current test proves visual terrain realism or a multi-seed speedup.

This is an isolated **Draft PR**, not a merge into stable main or a migration for existing worlds.
