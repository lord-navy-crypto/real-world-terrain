# RealScale two-mode playable candidate (Natural + USGS)

This release track intentionally prioritizes the two established world presets:

| Shown in the RealScale world-preset menu | Resource ID | Overworld noise settings |
| --- | --- | --- |
| Natural Landscapes | `realscale:natural_three_realms` | `realscale:tva_natural_smoothed` |
| USGS Big Sur V2 | `realscale:usgs_big_sur_v2` | `realscale:usgs_big_sur_v2` |

The six DEM-fitted archetypes (Alpine, Canyon, Glacial, Volcanic/Hawaii,
Karst, and Dune), smoothing probes and additional showcase choices remain
in the `experimental/terrain-modes-20261026` source branch, not the
two-mode playable JAR. No source feature is intentionally deleted from
that experimental branch.

## Safeguards

- The normal and extended Minecraft world-preset tags list only the two
  choices above. Minecraft's own base presets may still appear in the GUI.
- The release JAR excludes other `realscale` world-preset JSON files and the
  six new fitted archetypes' noise settings and density-function JSON.
- Legacy worldgen settings and codecs remain where needed to reduce the risk
  of breaking **existing** saved worlds. Some of these resources are present
  but are not presented as new-world choices.
- Do **not** use the two-mode release to open a world originally created with
  one of the six experimental fitted presets. Use the experimental branch
  build for those worlds, and back up all saves.
- The standalone server/Chunky pregenerator remains removed from the main
  mod; the offline read-only Atlas can still inspect regions.
- Existing shared terrain caches and Minecraft/NeoForge scheduling are kept.
  Excluding presets does not automatically reduce the height budget of the
  remaining modes; both retained modes need performance testing.

## Build and audit

From `neoforge` with Java 21 and a compatible Gradle environment:

```sh
gradle --no-daemon clean build verifyStablePlayablePresetSurface
```

The `check` task also depends on `verifyStablePlayablePresetSurface`.
The task scans the actual built JAR and fails if the RealScale playable
preset entries differ from exactly Natural + USGS Big Sur V2, if required
noise settings are missing, or if any new experimental fitted-archetype
noise/density resources leaked into the release JAR.

Test each preset in a **fresh** world, first without optional performance
or compatibility mods, then repeat in the original modpack to isolate
third-party effects. Record Java version, JVM memory, mod list, timing to
enter the world, timing to generate distant chunks, and `latest.log`.

**Status:** Two-mode release candidate. Never claim this branch is ready
until its own CI build/verification and in-game smoke tests pass.
