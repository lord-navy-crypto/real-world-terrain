# RealScale — NeoForge 26.3

Experimental migration workspace for Minecraft Java 26.3.

- Minecraft **26.3** / NeoForge **26.3.0.118**
- Java **25**
- ModDevGradle **2.0.148**
- Gradle **9.2.1**
- Optional YACL **3.9.7+26.3-neoforge**

Build: `gradle --no-daemon -p neoforge-26.3 clean build`

This is not a release build. Its source baseline is copied from the current NeoForge implementation. Required migration work:
- [ ] DensityFunction / DensitySampler split
- [ ] Feature registry and worldgen JSON migration
- [ ] BiomeSource / BiomeResolver migration
- [ ] Data-pack format 121.0 and generated pack metadata
- [ ] Client and dedicated-server startup
- [ ] Multi-seed new-world generation and terrain regression checks

Back up worlds before testing; do not use this experimental build for production saves.
