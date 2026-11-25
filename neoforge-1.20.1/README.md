# RealScale — NeoForge 1.20.1

This is the dedicated legacy NeoForge 1.20.1 build of RealScale.

- Minecraft: **1.20.1**
- NeoForge legacy line: **1.20.1-47.1.106**
- Java: **17**
- Build tool: **ModDevGradle Legacy Forge plugin**
- Configuration UI: **YACL 3.6.6 (client-side)**

NeoForge's short-lived 1.20.1 line retained the Forge 47.1.x API surface and legacy FML metadata. This module deliberately targets the NeoForged 1.20.1 artifact, rather than renaming or repackaging the regular Forge release. It is separate from the modern NeoForge 1.21.1 module.

The terrain algorithms, world-generation data, structure templates, and Natural Automate preset are carried over from the 1.20.1 source line. Loader integration and the build toolchain are isolated in this module.

Build from the repository root:

```sh
gradle --no-daemon -p neoforge-1.20.1 clean build
```

The distributable JAR is written to `neoforge-1.20.1/build/libs/`. CI checks the JAR contents and starts a dedicated server with the Natural Automate preset. Client rendering and third-party compatibility still require in-game testing.
