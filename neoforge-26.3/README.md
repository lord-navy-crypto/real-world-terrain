# RealScale — NeoForge 26.3

Experimental migration workspace for Minecraft Java **26.3**. This is not a release build.

**Both RealScale ports target Minecraft 26.3, but their JARs are loader-specific and are not interchangeable.** Use the NeoForge artifact only with NeoForge.

## Toolchain

- Minecraft **26.3** / Java **25**
- NeoForge **26.3.0.118**
- ModDevGradle **2.0.148**
- Gradle **9.2.1**
- Client-side YACL **3.9.7+26.3-neoforge**

Build: `gradle --no-daemon -p neoforge-26.3 clean build`

## Migration status — 2026-11-27

### Completed and CI-verified

- [x] `DensityFunction` / `DensitySampler` API migration.
- [x] `BiomeSource` / `BiomeResolver` API migration.
- [x] Feature registry and 26.3 world-generation data migration:
  - 11 custom Feature codecs and 70 custom Feature definitions.
  - 38 vanilla Feature definitions; 108 definitions total.
  - 122 placed-feature definitions and 120 biome modifiers.
  - 35 random-patch vegetation definitions migrated to `simple_block` Features with count, offset, and predicate placement modifiers.
  - Removed 106 obsolete 1.21 `configured_feature` JSON resources.
- [x] Generated structure packs use data-pack format **121.0**, including Architecture Pilot and Urban Districts.
- [x] The NeoForge CI build and all **108 automated tests** pass, including world-generation resource-integrity checks.

### Still pending — do not treat the port as release-ready

- [ ] Dedicated-server startup with the RealScale default world preset and runtime registry/resource validation.
- [ ] Client runtime validation. The YACL Terrain Lab entrypoint is included, but Studio screens, upload pacing, vehicle rendering, and legacy mixins remain deferred or disabled until their 26.3 ports are validated.
- [ ] Multi-seed new-world generation and terrain regression checks.

Back up worlds before testing. Do not use this experimental build for production saves.
