# RealScale — Forge 1.20.1

This is the independent Forge port of the RealScale terrain project.

- Minecraft: **1.20.1**
- Loader: **Minecraft Forge 47.4.28**
- Java: **17**
- Configuration UI: **YACL 3.6.6 for Forge 1.20.1**

The NeoForge 1.21.1 module remains unchanged in `../neoforge`. This module is being backported separately; it is not considered release-ready until the Forge build, dedicated-server startup, world creation, terrain-generation smoke tests, and client UI checks pass.

The port must preserve the shared RealScale terrain algorithms and data while adapting loader integration and Minecraft-version-specific worldgen/resource formats. Do not treat a successful Java compilation as proof that generated worlds are correct.

## Isolation and limitations

CI checks out only Forge, shared generator scripts, and architecture assets. It checks the packaged JAR, 1.20.1 NBT version and template paths, real server template lookup, and a software-rendered client UI smoke. These checks must pass before release. The Java package retains its historical name for source parity; no NeoForge runtime is required. The NeoForge-only mixins (spawn acceleration, upload interception, aquifer/beardifier experiments and Chunky lifecycle hooks) are not active in this port. Smooth Render does not provide upload pacing here. DH/Oculus compatibility and extended client gameplay require separate integration tests.
