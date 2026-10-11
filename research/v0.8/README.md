# RealScale Geology v0.8 — SI physics prototypes and research

**IMPORTANT: This is not a Minecraft / NeoForge mod.** Do not place any artifact of this repository inside `run/mods`.

Contains:
- `ENGINEERING_PLAN_CN.md`: sources, physical assumptions, geologic landforms, architecture and NeoForge 1.21.1 integration phases.
- `src/org/realscale/geo/GeoPhysicsKernels.java`: pure Java 21 demonstration of orogenic uplift, SI precipitation runoff, Priority-Flood drainage, stream power erosion with lithologic resistance, and sediment volume accounting.
- `src/org/realscale/geo/GeoPhysicsTests.java`: reproducible assertions for determinism, closed water and sediment budgets, no erosion without runoff, valid heights.

Commands:

```bash
javac -d dist src/org/realscale/geo/*.java
java -cp dist org.realscale.geo.GeoPhysicsTests
```

The included numerical coefficients are **illustrative**, not field-calibrated; no claim of geological predictive accuracy is made. Outlet drainage is **regional**, not globally stitched. This package doesn't change v0.6 or v0.7 Minecraft world files or registries and does not add any blocks.
