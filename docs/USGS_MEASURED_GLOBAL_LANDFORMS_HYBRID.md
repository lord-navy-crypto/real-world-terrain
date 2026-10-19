# RealScale USGS Measured + Global Landforms — integrated hybrid world

**New-world choice:** `realscale:tva_measured_global_landforms_three_realms`.

This is a fused world, **not** three separate exploration modes: Measured
landscape provides the ordinary ground, and one of **all 24 shapes** from
the previous v1/v2/v3 highland and global-atlas releases may appear in
a rare eligible mainland highland region.

## DEM coverage transparency

The currently bundled genuine USGS DEM is a **480 × 480 m Big Sur tile**.
The existing measured profile already interpolates that tile and blends into
procedural natural terrain in a 2048-block boundary. Global-scale DEM coverage
is **not yet available in this repository**. The word “measured” applies to
available DEM terrain only; elsewhere ordinary land remains the existing
RealScale fallback, not an invented worldwide USGS recording.

Priority and terrain selection:
- Ordinary columns: **bit-exact existing** `usgs_big_sur_v2/terrain_density`,
  including real samples, DEM boundary transition and procedural fallback.
- Rare highland candidates: 12,288-block deterministic sites, selected
  sparsely at 2 out of 5 coordinate-hash residues. Inside an eligible site,
  the core uses **procedural density + selected geological shape**, not DEM.
- Boundary columns: smooth mixing to the untouched measured baseline.
  Low coasts and ocean are protected by inland/height gating.
- An otherwise eligible region can be rejected by coastal and height gates;
  therefore this does not promise a guaranteed special shape in every cell.

The 24 sites cover Tibetan highlands/basins, Andes volcanic plains, Yungui
karst and intermontane basins, Sichuan/Chongqing ridges, Guiana tepui mesas,
tropical inselbergs, glacial esker/drumlin fields, star dunes, East African
rift, slot canyons and Icelandic caldera. The special shapes are procedural
**analogues**, not exact GIS reconstructions.

## Protecting old saves and other dimensions

The original `usgs_big_sur_v2` and standalone 4km/8km/12km atlas codecs
are never rewritten. Only a new `realscale:measured_rare_hybrid`
DensityFunction and `usgs_global_hybrid` noise graph are used.
The new world preset preserves v3 biodiversity, SkyOcean sky-islands,
protected triple End-biomeland spawn with the 9×9 pad, Grand Nether and End,
and the previous **range_min_exact** cave and aquifer scheme.
Old Sacred/Measured and real Chongqing OSM/DEM worlds remain untouched.

## Release criteria

- [x] Versioned hybrid density code and source graph committed.
- [x] New world selectable from Minecraft's world creation menu.
- [x] Dedicated Python source guards and bit-identical math tests authored.
- [x] Dedicated NeoForge boot/RCON/persisted generator test authored.
- [ ] Complete NeoForge build and CI green.
- [ ] Actual server boot with the fusion mode and saved End arrival pad.
- [ ] Multi-seed verified real Anvil columns both on original DEM and inside
      synthetic rare sites, checking caves, rivers, structures and climate.

**Draft, not ready for main.**
