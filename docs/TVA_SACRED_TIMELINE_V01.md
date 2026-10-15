# TVA — Sacred Timeline Ultimate (experimental v0.1)

**Minecraft 1.21.1 / NeoForge.** This document describes the *actual combined
experiment*, not a shipping promise. All integration changes live on
`feature/tva-sacred-timeline-ultimate`; **main is unchanged**.

## One-click new-world preset

Select **`realscale:tva_sacred_timeline_three_realms`** when creating a new
world. Its worldgen stack is:

1. **Overworld:** original `realscale:realism_max` 2,096-block vertical
   noise setting (Y -64..2031), with the ocean-aware sky-archipelago biome
   source wrapping the 60-natural-ecotone biome source, wrapping Minecraft's
   multi-noise biome source. Forests and aquatic habitats receive 15 APES
   visual features with seeded 768/128-block habitat patches.
2. **Nether:** original Nether lower environment preserved beneath the
   `realscale:grand_nether` 512-block tall generator. PR #9's experimental
   connected main chambers, horizontal and vertical tunnels, and secondary
   satellite chambers are included; original lava and bedrock limits are
   explicitly tested.
3. **End:** 512-block tall `realscale:grand_end` noise, with the central
   End-dragon island intentionally protected. Outer archipelagos can extend
   much farther upward.
4. **Offshore sky:** 1–5 clustered floating islands per 512-block cell
   conditionally appear at high altitude over distant oceans. The sky-ocean
   wrapper delegates ordinary surface and underwater biomes unchanged
   below Y160; floating islands use a distinct biome and are optional
   via this preset.
5. **Performance:** PR #5's per-thread DEM column locality cache; no
   cross-worker mutable lookup map.

## Integrated research provenance

| Source | Status in TVA |
|---|---|
| PR #7 + PR #8 + PR #9 Grand Dimensions | 512-height Nether/End and Phase III satellite cave network integrated |
| PR #10 through PR #13 Overworld Ecology | All 60 ecological transition definitions, regional microhabitat resources and radial-aware classification integrated |
| PR #14 Grand Ecology APES | 15 surface/aquatic feature groups and coherent seed-dependent visual habitat patterns integrated |
| PR #6 Ocean + Sky | Ocean-biome-preserving high-sky source, floating-island structures and cave features integrated; *marine sediment modifier not enabled* |
| PR #5 Terrain Optimization | DEM cache and benchmarks integrated |
| Experimental latitude/valley/climate branch PR #2 | **Not merged.** This diverged prototype changes many core density functions and is incompatible with the current verified USGS/Realism MAX graph without detailed repair and independent world tests. Preserve separately for later port. |

## What is NOT yet proven

- The combined version must pass **all** its CI checks; static registrations
  and Java compilation alone do not prove a saved-world result.
- PR #14's 16-biome gallery has an existing test harness issue involving
  synchronous RCON and high-ceiling chunk load times. The TVA branch adds
  an extended timeout, but the real rerun determines whether it worked.
- Ocean/sky upstream marine integrity QA was not fully green; do not enable
  alternative seafloor carving, ocean sediment replacement or unsafe global
  marine decorators. Preserving vanilla ocean sources is a design constraint,
  **not proof that every ocean structure has been sampled in this merge**.
- True planet-wide climate bands, rain shadows, seasonal water/soil nutrient
  cycling, ecosystem population dynamics, detailed river and lake hydrology,
  and all optional fantasy presets are still separate R&D.
- Do **not** apply this world preset to an existing save or promise seamless
  conversion from older world presets. Back up worlds before testing.

## QA acceptance

Run `python3 scripts/check_tva_sacred_timeline.py`,
`python3 scripts/check_custom_biomes.py`,
`python3 scripts/check_grand_ecology.py`, and run the native NeoForge
build. The CI workflows additionally boot the ultimate preset on a
dedicated server and examine persisted Anvil chunks, plus verify Nether/End
and natural ecotone worlds. Do not merge the Draft PR until these tests
have conclusively passed and the marine-risk review is complete.

*TVA means one traceable experimental development branch, not erasing its
independent source branches or rewriting existing world saves.*

## v0.2 — restore the FOUR curated player-facing families

The original `main` branch exposed eight historical world presets and the
initial TVA merge accidentally re-expanded the world-creation menu to fourteen.
This violated the prior approved UI design.

Both `normal.json` and `extended.json` now expose exactly four new,
unambiguous TVA three-realm presets:

| User choice | Stable opt-in world preset | Overworld terrain |
|---|---|---|
| Natural | `tva_sacred_timeline_three_realms` | Realism MAX |
| Alpine | `tva_alpine_three_realms` | Himalayan |
| Canyon | `tva_canyon_three_realms` | Grand Canyon |
| Real-World | `tva_measured_three_realms` | USGS Big Sur v2 |

All four keep Grand Nether, Grand End, APES and the ocean-protecting
sky-archipelago/ecotone source. Historical world preset JSON IDs are **not
deleted or renamed**; they simply no longer occupy the world-creation menu.

The missing `RealDemSeamlessDensity` + `RealDemSeamlessMath` pair, 2,048
block exterior blend and USGS v2 noise/resource graph were ported from
the former v1.3 terrain branch as a separate worldgen codec, leaving the
old USGS profile available for previously created saves.

**Remaining difference:** the YACL Terrain Lab family-profile editor from the
diverged v1.3 prototype is not restored by this menu/terrain fix. Do not claim
the GUI/config-profile work is integrated until its dependency and client
runtime tests also pass.

### USGS v2 server registry dependency repair

Real worldgen CI revealed that v2's new `cave_density` graph references
`realscale:realism_max/cave_core` from the old v1.3 implementation. The
first port omitted that secondary density function, causing the entire
worldgen registry to fail during data loading. The original `cave_core`
resource has now been imported without changing existing Realism MAX
terrain presets, and the new TVA validation asserts its presence.

## TVA preview identity (no more v1.1 USGS artifact names)

TVA integrated builds use version `2.0.0-tva-preview.1` and output the
`RealScale-TVA-Ultimate-NeoForge-1.21.1` JAR family. Mod metadata now
accurately describes the four TVA world choices and 512-block Nether/End
rather than the original v1.1 USGS-only pilot. The original v1.1 immutable
release JAR, archives and historical worldgen IDs remain untouched.
