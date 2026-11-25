# RealScale — Fabric 1.21.1

Independent Fabric port of RealScale for Minecraft 1.21.1.

- Minecraft: **1.21.1**
- Fabric Loader: **0.16.14 or newer** (CI baseline: 0.19.5)
- Fabric API: **0.116.18 or newer**
- Java: **21**
- Optional configuration UI: **YACL 3.8.1+1.21.1-fabric**

YACL is compile-only for the mod and optional at runtime. Players without YACL can still load the core terrain generator; the Studio configuration entry is available when the supported YACL build is installed.

Build from the repository root:

```sh
gradle --no-daemon -p fabric-1.21.1 clean build
```

The distributable JAR is written to `fabric-1.21.1/build/libs/`. This is a separate 1.21.1 artifact and must not be installed in a 1.20.1 instance. The first port milestone validates compilation, packaged worldgen resources, Natural Automate dedicated-server startup, and client startup without YACL. Renderer stacks and real-world chunk-generation performance require further in-game testing.
