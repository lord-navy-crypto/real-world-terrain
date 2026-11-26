# RealScale — NeoForge 1.20.1

Dedicated legacy NeoForge build for Minecraft 1.20.1.

- Minecraft: **1.20.1**
- NeoForge Legacy: **47.1.54–47.1.106 (tested endpoints; CI validates both)**
- Java: **17**
- Build: **ModDevGradle Legacy Forge plugin 2.0.148**
- Client configuration library: **YACL 3.6.5+ (CI tests 3.6.5 and 3.6.6)**

NeoForge's short-lived 1.20.1 line uses the legacy Forge 47.1.x API and FML metadata. This module targets the NeoForged Maven legacy artifact family `net.neoforged:forge:1.20.1-47.1.x`; CI validates the minimum tested version 47.1.54 and the latest 47.1.106 artifact. It is separate from modern NeoForge modules.

The RealScale terrain algorithms, world-generation resources, structure templates, and Natural Automate preset are retained. Loader integration and the build toolchain are isolated in this module.

Build from the repository root:

```sh
gradle --no-daemon -p neoforge-1.20.1 clean build
```

The distributable JAR is written to `neoforge-1.20.1/build/libs/`. CI checks the packaged resources and starts a dedicated server with the Natural Automate preset. Client rendering and third-party mod compatibility still require in-game validation.
