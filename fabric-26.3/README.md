# RealScale — Fabric 26.3

Experimental migration workspace for Minecraft Java 26.3.

- Minecraft **26.3** / Java **25**
- Fabric Loader **0.20.1**
- Fabric API **0.167.0+26.3**
- Fabric Loom **1.19-SNAPSHOT**
- Gradle **9.8.2**
- Optional YACL **3.9.7+26.3-fabric**

Build: `gradle --no-daemon -p fabric-26.3 clean build`

This is not a release build. Its source baseline is copied from the validated Fabric 1.21.1 module. Required migration work:
- [ ] DensityFunction / DensitySampler split
- [ ] Feature registry and worldgen JSON migration
- [ ] BiomeSource / BiomeResolver migration
- [ ] Data-pack format 121.0 and generated pack metadata
- [ ] Client and dedicated-server startup
- [ ] Multi-seed new-world generation and terrain regression checks

Back up worlds before testing; do not use this experimental build for production saves.
