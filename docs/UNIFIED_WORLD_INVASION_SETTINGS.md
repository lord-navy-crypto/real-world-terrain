# Unified World Settings — Sacred / Measured / Dimension Invasion (2026-10-19)

Status: **EXPERIMENTAL branch, new-world only.** Not an in-place conversion for
old saves; opt-in worldgen must remain deterministic and stored with each world.

## The settings are ONE next-world draft, not a global worldgen mutation

Minecraft client: **Mods → RealScale TVA → Config → Unified World Creation**.

| Setting | Choice | Default |
| --- | --- | --- |
| Base World (next new world) | Terrain Lab / Sacred / Measured | Terrain Lab |
| Dimension Invasion | ON / OFF | OFF |
| Invasion Showcase near Origin (test-only) | ON / OFF | OFF |

- **Sacred:** keeps the exact canonical three-realm Sacred Timeline terrain,
  including its original caves, ocean, skylands, ecotones, structures, and
  Grand Nether/End generator settings.
- **Measured:** keeps original USGS/DEM height and density settings without
  any editable relief conversion. Its base terrain remains read-only.
- **Terrain Lab:** retains its existing three editable family settings, but
  can now add invasion to the *exported world preset*. A distinct world ID
  avoids accidentally turning existing lab worlds into invasion worlds.

ON wraps the original biome source with `realscale:cross_realm_invasion` in
all three dimensions. In the overworld the wrapper is INSIDE the existing
`sky_ocean` wrapper to retain sky archipelagos and coastline semantics. The
feature/biome modifiers are already bundled with RealScale and gated to only
operate on explicitly cross-realm worlds.

Showcase ON adds a testing-only `cross_realm_showcase` outer wrapper with
demonstration biome regions near X/Z **0,0** and **176,0** in each dimension.
It is NOT suitable for normal rarity/performance tests; turn it OFF for real
gameplay. The transferred terrain accents are small experimental prototypes,
not an entire Nether/End landscape replicated into the target dimension.

## How to activate it (new world)

1. Install matching Minecraft 1.21.1, NeoForge 21.1.249 and RealScale TVA JAR.
   Install YACL 3.8.1+ on the client for its configuration screen.
2. In the RealScale config's **Unified World Creation** tab, choose Sacred or
   Measured, set **Dimension Invasion = ON**, optionally Showcase = ON,
   and **Save**. Saving only writes `config/realscale-terrain-lab.json`;
   it does not modify a running or existing world.
3. Exit Minecraft. Using Python 3.9+, the matching mod JAR and repo scripts,
   run (replace paths with yours):

   ```sh
   python3 scripts/export_terrain_lab_datapack.py \
      --jar "/path/to/RealScale-TVA-Ultimate-NeoForge-1.21.1.jar" \
      --draft "/path/to/minecraft/config/realscale-terrain-lab.json" \
      --output "/path/to/Unified-Invasion-World.zip"
   ```

   The exporter is standalone, does not need Gradle, and *never* silently
   replaces a previous ZIP. Run a new output filename on each export.
4. In **Create New World → Data Packs**, enable the exported ZIP. Select the
   newly available `realscale:unified_sacred_invasion_v1` or
   `realscale:unified_measured_invasion_v1` **World Type**. With Showcase ON,
   select the matching `..._invasion_showcase_v1` World Type instead.
5. Optional `-Language.zip` is a resource pack that supplies the human-readable
   world type label. Keep the exported data pack with that world save.
6. Always test first in a fresh world, never overwrite, force-convert, or
   rename an existing Sacred/Measured save. A world's saved generator codec
   controls its new chunks regardless of later client config changes.

## In-game verification

Use F3 Biome ID, or execute exact biome checks (with cheats enabled)
in a SHOWCASE world after the biome source has loaded:

```mcfunction
/execute in minecraft:overworld if biome 0 80 0 minecraft:nether_wastes run say INVASION_OVERWORLD_PASS
/execute in minecraft:the_nether if biome 0 80 0 minecraft:forest run say INVASION_NETHER_PASS
/execute in minecraft:the_end if biome 0 80 0 minecraft:forest run say INVASION_END_PASS
```

The above are *expected* by the showcase algorithm; actual live server
evidence has NOT yet been collected for all six directions. If a command
fails, verify world type, dimension/Y, registry source and log results rather
than teleporting randomly. The ordinary rare mode is intentionally sparse
and global `/locate biome` is not guaranteed to find a narrow patch.

## Safety and verification scope

- No original `tva_sacred_timeline_three_realms.json` or
  `tva_measured_three_realms.json` is modified.
- Existing YACL drafts missing new fields default to invasion OFF and legacy
  `terrain_lab` export behavior, for backward compatibility.
- New source validation checks exact three-dimension terrain settings and
  original biome sources under the composed wrappers.
- Datapack exporter bundles one additive World Type ID without editing the
  stock preset tags.
- The older `scripts/terrain_lab.py --from-yacl` legacy workflow only handles
  terrain tuning. Use `scripts/export_terrain_lab_datapack.py` for unified
  invasion toggles.
- Full client GUI confirmation, six-direction persisted biome tests,
  surface-patch visibility and performance regression are release gates,
  not yet proven by source/static tests.

**Known independent regression:** Natural Smoothing seed 2 with structures
enabled can time out on strict distant chunk loading. This unified settings
work does not claim to fix that worldgen performance issue.
