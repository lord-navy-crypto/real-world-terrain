# RealScale Terrain

An experimental Minecraft 1.21.1 / NeoForge terrain project. The recovered v1.1 candidate overlays a small USGS-derived Big Sur DEM onto seven existing procedural presets. Java 21 and Gradle 8.14.3 are required.

Readable mod sources live in `neoforge/`; historical v0.4–v0.6 datapacks in `archive/`; raw USGS records in `data/`; original successful build artifact and saved-world reports in `evidence/`; installable candidate JAR in `dist/v1.1/`. The original exact delivered v1.1 JAR is [`RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar`](dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar) (SHA-256 `638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43`).

Run `python3 qa/verify_repository.py`, then `cd neoforge && gradle --no-daemon build`. Install the JAR into a fresh Minecraft 1.21.1 instance with NeoForge 21.1.249. Create a new experimental world with the USGS Big Sur preset, seed 785312, and inspect near the origin. Do not use existing saves or duplicate RealScale mods.

The DEM covers approximately 480×480m. Its 32m grid is derived from EPQS service samples spaced approximately 54–67m apart. One block per meter is interpolation, not one-meter survey data. A 96m interior blend transitions back to procedural terrain.

**The original standalone v0.7–v1.0 GeoCore/geology/hydrology and measured-DEM Java/Python source files are now recovered under [`research/`](research/).** These research prototypes are not fully integrated into the installable v1.1 candidate. See `docs/VERSIONS.md` and `docs/VALIDATION.md` for exact recovery and verification status, including differences between the historical report and replay of the final saved world.

No open-source license was present in the recovered project. See `LICENSE` and `THIRD_PARTY_NOTICES.md`; this migration does not invent a permission grant. Source commit and artifact provenance is preserved in `docs/PROVENANCE.json`.

The full original server ZIP is available as a separate local deliverable, not stored in GitHub because its upload exceeded automatic approval review capacity. To replay it, run `python3 qa/verify_repository.py /path/to/RealScale-v1.1-original-server-qa.zip`. Without this argument CI explicitly skips historical-world replay; the new server workflow generates and checks its own world.
