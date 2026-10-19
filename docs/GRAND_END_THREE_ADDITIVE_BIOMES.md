# Grand End — Three additive biomes (Minecraft 1.21.1 / NeoForge)

**Status:** new experimental PR, stacked on unified world settings PR #59;
the canonical `main`/legacy Sacred, Measured and vanilla worlds are not
modified by the world-generator wrapper.

## Design

Inspired by the small-biome diversity of BetterEnd, WITHOUT bundling,
copying or depending on BetterEnd/BCLib assets or implementation:

| Biome ID | Localized name | In-world decoration |
| --- | --- | --- |
| `realscale:end_chorus_gardens` | Chorus Gardens / 紫颂花园 | Sparse living chorus flowers growing from End Stone |
| `realscale:end_crystal_barrens` | Crystal Barrens / 晶体荒原 | Small amethyst bases and naturally-supported amethyst clusters |
| `realscale:end_shattered_glades` | Shattered Glades / 破碎幽林 | Tiny purpur/end-brick fragments, occasional end rods |

All three use vanilla block palettes: **no new blocks, shader or library
required**. Features only add to natural, unobstructed End Stone surfaces,
are chunk-local, do not touch liquid/bedrock/end structures and are
registered via NeoForge `add_features` biome modifiers scoped to only
their own three custom biome IDs.

**True biomes, not just decorations:** the built-in biome registry contains
three complete custom data-driven biome JSON records with distinct effects;
the saved end `BiomeSource` identifies each region through F3 / `locate
biome`. Their origin is deterministic: 512x512 cell grids, average 1 of 5
eligible cells selected, radius 88-136 blocks, preserving the central
dragon/main island within 1024 blocks of world origin.

The additional `EndAccentBiomeSource` takes `source`, asks it FIRST for
the selected biome, and only replaces original vanilla End biome IDs
(`end_highlands`, `end_midlands`, `end_barrens`,
`small_end_islands`). ALL other biome IDs are returned unchanged,
including `betterend:* `, `byg:*`, imported `minecraft:forest` /
`minecraft:nether_wastes` biome islands, or any foreign namespace.
The original `realscale:grand_end` **noise settings and actual terrain
generator are not replaced**. End City/dragon main island terrain are
untouched by this feature (external mod compatibility needs live testing).

## Unified Settings

**Mods → RealScale TVA → Config → Unified World Creation**

- Base World: Sacred (default) / Measured / Terrain Lab
- Dimension Invasion: ALWAYS ON for new unified profiles (PR #59)
- **Grand End | Three Extra Biomes: ON by default**, OFF to troubleshoot
  mod compatibility. This optional setting does not disable invasion.
- Invasion showcase: optional, only for new test worlds.

Save the settings and export using the matched JAR and source's
`scripts/export_terrain_lab_datapack.py` (or the matching standalone
exporter). Enable the generated ZIP in **Create New World → Data Packs**,
then select the resulting profile:

- `realscale:unified_sacred_invasion_v1_end3`
- `realscale:unified_measured_invasion_v1_end3`

Showcase variants insert `_showcase_v1_end3` after `_invasion`.
These **new IDs** avoid replacing existing `..._invasion_v1` saved
profiles. Existing and historic worlds retain their previous End source,
so upgrading a JAR never automatically changes the old End biome map.

## Compatibility: important limitations

- BetterEnd's primary line is Fabric, which cannot be installed alongside
  a native NeoForge mod. An **unofficial BetterEnd NeoForge** port exists
  for Minecraft 1.21.1, but has its own dependencies, biome registries
  and world-generation system.
- The RealScale wrapper PASS-THROUGH protects *already supplied foreign
  biome IDs*, not arbitrary custom End generators that both mods may
  attempt to install in the same save. If another mod rewrites the End
  generator/biome source, a separate integration path may be required.
- A NeoForge `BiomeModifier` can inject placed features into a biome,
  but CANNOT by itself ensure a brand-new biome is selected by a
  generator. Hence the separate minimal `EndAccentBiomeSource`.
- No claim of universal BetterEnd, Nullscape, Enderscape, Terralith
  compatibility without exact-loader/version real-server tests.
- Do NOT make the optional End source wrapper global across other
  third-party End worlds, and do not overwrite `minecraft:the_end`
  or foreign custom generator resources in a datapack.

## Verification plan

1. Static verification for 3 registered biome IDs, JSON modifiers,
   chunk-local safety and foreign-biome no-replacement.
2. NeoForge Gradle Java 21 compile and normal repo QA.
3. Real dedicated NeoForge boots with newly exported Sacred/Measured
   three-biome profiles, and `execute in minecraft:the_end ... if biome`
   identifies all three IDs. Check biome source in persisted level.dat.
4. Real End land chunks in expendable world: actual decorations, End Cities,
   chorus plant growth, Dragon spawn and no fluid/cross-chunk damage.
5. Optional compatibility matrix with the **actual third-party NeoForge
   mod versions**, their dependencies, multiple preset orders, world
   reopening and non-vanilla biome-preservation snapshots.

Until 4 and 5 are complete, mark compatibility **designed for additive
behavior, not certified**.

## 2026-10-19 enhancement: exclusive own-End invasion at first spawn

The ALWAYS-ON unified cross-realm invasion continues in all six directions.
**Only the newly created OVERWORLD first spawn is special:**
within the existing small 192-block starting radius at world origin the
`CrossRealmBiomeSource` now reports exactly ONE of the three custom habitats,
`realscale:end_chorus_gardens`, `realscale:end_crystal_barrens` or
`realscale:end_shattered_glades`. A fixed climate-biome sample at the origin
selects it consistently for the entire horizontal area AND all Y values,
including high-altitude Measured terrain. This overwrites the previous
Nether-wastes/vanilla-End-highlands first-spawn choice. The optional
Invasion Showcase delegates these custom IDs unchanged.

The initial spawn event **still only runs when creating a NEW opt-in world**.
Its 9×9, illuminated arrival platform uses the selected biome's palette,
end-stone brick guardrails and four sea-lantern corner lights. It creates an
accessible, level, bounded survival arrival pad even at sea or on a cliff;
it does not build a large fortress or rewrite remote chunks.

Elsewhere, Nether/End/Overworld cross-realm patches continue using their
prior rare spatial placement. No new rules globally replace all Overworld
biomes, no forced seed, and no retroactive migration of an existing save.

### Visual biome pass

1. **Chorus Gardens:** living 2–4-block chorus shoots with real flowers;
   sparse purpur/end-brick ground mosaic.
2. **Crystal Barrens:** calcite/amethyst ground crusts, 1–3-block amethyst
   spires, supported upward clusters.
3. **Shattered Glades:** fragmented brick/purpur floor, irregular purpur
   remnants and occasional capped end rods.

Two bounded feature attempts per decorating chunk, limited to tiny 9×9
origin patches and natural unobstructed End Stone. No new block registration,
no copied BetterEnd assets, no destructive terrain shaping.

### Recommended acceptance

- CI: verify first-spawn biome is exactly one of the **three own IDs** at
  Y=64/100/160/320; reject vanilla `nether_wastes` and `end_highlands`.
- In a fresh Sacred and fresh Measured world, F3 confirms the same custom
  biome at initial spawn. Check the real 9×9 starter pad, railings and lights.
- Use `/locate biome realscale:end_chorus_gardens` and analogous commands in
  the End, then actually load those chunks and inspect the surface decoration.
- Ensure legacy saves, Grand End dragon island, End City generation and
  foreign-modded biome IDs are unchanged. Compatibility with the real
  NeoForge BetterEnd port remains provisional until an integrated test.
