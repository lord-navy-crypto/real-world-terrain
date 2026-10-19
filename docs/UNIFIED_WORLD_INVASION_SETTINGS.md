# Unified World Settings — Invasions Always ON + Guaranteed Invasion Spawn

**Development status:** PR #59 experimental branch, new-world only. Minecraft
1.21.1, NeoForge 21.1.249, Java 21.

## Rule changes (2026-10-19)

- For every **new Unified World Creation** export, Dimension Invasion is
  **ALWAYS ON**. It is not an optional checkbox.
- The default base terrain for a new YACL draft is Sacred Timeline. Players
  can choose Sacred Timeline / Measured (read-only USGS) / Terrain Lab.
- The initial overworld spawn is intentionally located inside **one**
  genuine Nether or End biome ID. A 192-block radius around X/Z 0/0 has a
  guaranteed invasion biome source; other rare invasion sites remain sparse.
- Only the first creation of a matching opt-in world invokes the spawn
  initialization event; reloading an existing save does not reset its spawn.
- The world creates a minimal safe 3×3 blackstone/glowstone arrival pad
  based on the actual terrain height, including high-altitude Measured
  terrain. This is a deliberately **small visible change at X/Z 0/0**.
- The outer sky-island source delegates within this starting region, so it
  cannot accidentally override the guaranteed invasion biome at high Y.
- The initial spawn uses X=0, Z=0. Randomized arrival offsets remain within
  the special 192-block biome-radius under normal spawn-radius settings.
- No canonical Sacred/Measured world preset JSON files are overwritten;
  old worlds with other saved biome-source codecs retain their original
  biomes, and default vanilla worlds remain unaffected.

**Meaning of “always”:** every newly exported **unified** profile now
requires `dimensionalInvasion: true`. The *historical* canonical world
IDs and pre-feature draft imports remain available unchanged for existing
save compatibility; they are not retroactively converted. Selecting a
historical non-invasion world type from the old menus is NOT the same as
selecting a new unified profile.

## In-game settings

**Mods → RealScale TVA → Config → Unified World Creation**

- **Base World:** Sacred Timeline (default), Measured/USGS, or Terrain Lab.
- **Dimension Invasion:** Always enabled (no opt-out setting for new
  unified worlds).
- **Invasion Showcase near Origin:** Optional. ON demonstrates the six
  interrealm directions at X/Z 0,0 and 176,0, in each dimension. OFF keeps
  rare sites beyond the guaranteed starting region.

## Export and create a new world

1. Install matching RealScale JAR, Java 21, NeoForge 1.21.1 and
   YACL 3.8.1+ on client.
2. Save the unified config; this only writes the next-world JSON draft.
3. Exit Minecraft and export a new-world datapack:

```sh
python3 scripts/export_terrain_lab_datapack.py \
    --jar "/path/to/RealScale-TVA-Ultimate-NeoForge-1.21.1.jar" \
    --draft "/path/to/minecraft/config/realscale-terrain-lab.json" \
    --output "/path/to/Unified-Sacred-Invasion.zip"
```

4. Enable that ZIP under **Create New World → Data Packs**.
   Select the newly registered world type:
   `realscale:unified_sacred_invasion_v1`, or
   `realscale:unified_measured_invasion_v1`. Showcase has corresponding
   `_invasion_showcase_v1` suffix.
5. Optional `-Language.zip` is an independent human-readable resource pack.
   Keep the data pack with the world's save.

The standalone exporter uses the matching JAR and cannot edit a running
world, a previous save, or the canonical base preset registry. It rejects
explicit new unified drafts with `dimensionalInvasion: false`. For backward
compatibility, *old pre-unification drafts* without any `worldSource` field
continue their old opt-out export semantics. You may delete/replace only
test worlds, and should back up any important saves.

## Player test: verify spawn's real biome

In a newly created unified world, stand at the original spawn near X/Z=0
and use F3 to inspect the Biome ID. It should be
`minecraft:nether_wastes` **or** `minecraft:end_highlands`:

```mcfunction
/execute in minecraft:overworld positioned 0 100 0 if biome ~ ~ ~ minecraft:nether_wastes run say INVASION_NETHER_PASS
/execute in minecraft:overworld positioned 0 100 0 if biome ~ ~ ~ minecraft:end_highlands run say INVASION_END_PASS
```

The two examples are an OR condition; either result is a valid spawn
invasion. Height Y=100 is only an example: use the actual spawn Y in F3
for high mountain worlds.

**Release gates still required:** Java compile, source/UI verification,
actual Sacred+Measured first-world spawn biome and block pad persisted in
Anvil, real client F3, no old-save changes, no surprise sky-island changes.
Natural Smoothing seed 2 structures-on timeout remains a separate known
issue. Do not call the guarantee fully game-verified until the live checks pass.
