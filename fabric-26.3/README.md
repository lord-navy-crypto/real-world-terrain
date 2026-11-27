# RealScale — Fabric 26.3

Experimental migration workspace for Minecraft Java **26.3**. This is not a release build.

**The Fabric and NeoForge ports target the same Minecraft version, but their JARs are loader-specific and are not interchangeable.** Use this artifact only with Fabric.

## Toolchain

- Minecraft **26.3** / Java **25**
- Fabric Loader **0.20.1**
- Fabric API **0.167.0+26.3**
- Fabric Loom **1.19-SNAPSHOT**
- Gradle **9.8.2**
- Optional YACL **3.9.7+26.3-fabric**

Build: `gradle --no-daemon -p fabric-26.3 clean build`

## Migration status — 2026-11-27

### Completed and CI-verified on Fabric and NeoForge

- [x] `DensityFunction` / `DensitySampler` API migration.
- [x] `BiomeSource` / `BiomeResolver` API migration.
- [x] Feature registry and worldgen data migration:
  - 11 custom feature codecs and 70 custom feature definitions.
  - 38 vanilla feature definitions; 108 feature definitions total.
  - 122 placed-feature definitions and 120 biome modifiers.
  - 35 random-patch vegetation definitions migrated to `simple_block` features with count, offset, and predicate placement modifiers.
  - Removed 106 obsolete 1.21 `configured_feature` JSON resources.
- [x] All generated structure packs use data-pack format **121.0** metadata, including Architecture Pilot and Urban Districts.
- [x] Both loader modules pass **108 automated tests**, including world-generation resource-integrity checks.

### Still pending

- [ ] Client runtime smoke test and UI/render integration. The current Fabric client entrypoint is intentionally minimal; Studio screens, upload pacing, vehicle rendering, and 1.21.x mixins remain deferred until their 26.3 ports are validated.
- [ ] Dedicated-server startup with the RealScale default world preset and runtime registry/resource smoke checks.
- [ ] Multi-seed new-world generation and terrain regression checks.

Back up worlds before testing. Do not use this experimental build for production saves.
