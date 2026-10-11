# Recovered research sources and provenance (2026-10-11)

These are **original local development artifacts** recovered from the RealScale project build environment, not code reconstructed by guessing from the conversation. Source and tests were extracted from the following locally verified archives. Binary build outputs and Python/Java caches were intentionally omitted from the readable `research/` folders.

| Version | Original archived package | Original package SHA-256 |
| --- | --- | --- |
| v0.7 | `RealScale_GeoEngine_v0.7_Java_Source_And_Tests.zip` | `153ebd8704a0bffd70bffc8854baf5150c27b61c81345e94196385dbccdf5284` |
| v0.8 | `RealScale_Geology_v0.8_Research_And_Java_Kernels.zip` | `111336d1bafe381a0ec14d1ccdb6c643eab3472e846c86bba5b04b72036aaa54` |
| v0.9 | `RealScale_GeoEngine_v0.9_Source_And_Tests.zip` | `8b9af7174e601a443b5387ba56a8ce6d41cd116ce03419bffb7d07d5143575ec` |
| v1.0 | `RealScale_GeoEngine_v1.0_USGS_REAL_DATA_Source_And_Java.zip` | `732b425960478f93dc394f3dd96745c2117706b58e8aa51acc4e90a98e2d83e0` |

Source restoration **does not mean tests were rerun on this new repository**. Historical independent tests were completed in the development environment; GitHub Actions in this repository cover the v1.1 mod separately.

Original raw USGS 3DEP EPQS sample records and provenance are stored in `data/usgs-big-sur/`. Source heights are interpolated from an observed service sampling grid; one Minecraft block equals one model meter, not one meter of observational resolution.

**The delivered v1.1 JAR** uploaded into `dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar` has SHA-256 `638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43`. It is the original locally shared file, while the other JAR file in `dist/v1.1/` is an independently compiled candidate whose binary byte content may differ due to build packaging.
