# RealScale Terrain

An experimental Minecraft 1.21.1 / NeoForge terrain project. The recovered v1.1 candidate overlays a small USGS-derived Big Sur DEM onto seven existing procedural presets. Java 21 and Gradle 8.14.3 are required.

Readable mod sources live in `neoforge/`; historical v0.4–v0.6 datapacks in `archive/`; raw USGS records in `data/`; original successful build artifact and saved-world reports in `evidence/`; installable candidate JAR in `dist/v1.1/`. The original exact delivered v1.1 JAR is [`RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar`](dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar) (SHA-256 `638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43`).

Run `python3 qa/verify_repository.py`, then `cd neoforge && gradle --no-daemon build`. Install the JAR into a fresh Minecraft 1.21.1 instance with NeoForge 21.1.249. Create a new experimental world with the USGS Big Sur preset, seed 785312, and inspect near the origin. Do not use existing saves or duplicate RealScale mods.

The DEM covers approximately 480×480m. Its 32m grid is derived from EPQS service samples spaced approximately 54–67m apart. One block per meter is interpolation, not one-meter survey data. A 96m interior blend transitions back to procedural terrain.

**The original standalone v0.7–v1.0 GeoCore/geology/hydrology and measured-DEM Java/Python source files are now recovered under [`research/`](research/).** These research prototypes are not fully integrated into the installable v1.1 candidate. See `docs/VERSIONS.md` and `docs/VALIDATION.md` for exact recovery and verification status, including differences between the historical report and replay of the final saved world.

No open-source license was present in the recovered project. See `LICENSE` and `THIRD_PARTY_NOTICES.md`; this migration does not invent a permission grant. Source commit and artifact provenance is preserved in `docs/PROVENANCE.json`.

The full original server ZIP is available as a separate local deliverable, not stored in GitHub because its upload exceeded automatic approval review capacity. To replay it, run `python3 qa/verify_repository.py /path/to/RealScale-v1.1-original-server-qa.zip`. Without this argument CI explicitly skips historical-world replay; the new server workflow generates and checks its own world.


> **v1.3 all-presets repair is an experimental branch, not an installable final release.** Seven procedural height functions now interpolate the original cached coarse field to eliminate 4×4 sampling terraces; all eight receive bounded 3D cave density. The previous v1.2 per-block cache strategy caused spawn-time performance concerns. **Current v1.1 remains the verified public build.** See [v1.3 design and QA](docs/V1.3_ALL_PRESETS_REPAIR.md).

## v1.3.5 curated world types (experimental branch only)

The normal Create World selector now offers **five** distinctive modes: Realism MAX, GeoReal Multi-scale, Himalayan, Grand Canyon and measured USGS Big Sur. **Continental, Realistic Mountains and Ultimate** are hidden from the normal menu but remain registered, with unchanged terrain density/noise JSON and world preset IDs. Hold **Alt** for the extended menu showing all eight, or use the old `level-type=realscale:<id>` on a dedicated server.

This intentionally consolidates only the **player-facing choices**; no historical terrain algorithm is silently remapped and no previously saved worlds are migrated. Back up existing saves and create fresh test worlds. Also fixes the 16-voxel cross-chunk cave unit-test fixture; all rapid QA tests run before the expensive NeoForge server checks. Client screenshots, cave entrances, structure foundations and multi-seed usability remain incomplete.
