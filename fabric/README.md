# RealScale — Fabric 1.20.1 experimental port

Minecraft 1.20.1, Java 17, Fabric Loader 0.16.14, Fabric API 0.92.6+1.20.1. Optional client UI: YACL 3.6.6+1.20.1-fabric.

This module is independent of the Forge and NeoForge source sets. It retains the Forge port's 1.20.1 world-generation data and deterministic legacy architecture template generators, with Fabric registry, biome modification, resource pack, client lifecycle and rendering adapters. Build with `gradle -p fabric clean build`; use the remapped JAR, not the development JAR. New worlds only; no existing cross-version save compatibility is promised.

This is an initial port, not a stable release. CI must establish compilation, registry load, actual server/world generation, template availability, and client compatibility. The fast-spawn and upload-interception mixins from NeoForge are not present; DH/Chunky and shader compatibility are not verified. YACL is optional for gameplay but required for the Studio screens. 
