# RealScale northern geomorphology v3 (October 20, 2026)

**Development status:** implementation and release-gate candidate in Draft PR #75.
This is an Earth-geology-inspired original landscape generator, **not** a copy
of Nod-Krai / Snezhnaya's map, art, textures, architecture, or imaginary powers.

## Actual terrain changes

The existing cinematic macro-density terrain family expands **eight -> ten**
original regional archetypes through a new independent
`realscale:cinematic_landforms_v3` density codec, Java math class and
`realscale:tva_cinematic_landforms_v3` noise settings.

1. Sandstone pillar clusters (weathered Wulingyuan-style silhouette)
2. Cone/tower karst districts
3. Caprock mesas
4. Incised canyon districts
5. Inland caldera ring
6. Broad, cold glacial U-shaped trough
7. Star dune districts
8. Double tepui tablelands
9. **Glacial cirque basin:** inward-curving mountain amphitheater with an
   eroded depressed floor, high headwall and lower open outlet. The saddle
   is *open*, not a sealed perfect-crater lake or floating mountain bowl.
10. **Lateral and terminal moraine ridges:** curved near-parallel lateral
    till embankments and a terminal arcuate ridge. Lower corridor interior
    contrasts with raised depositional sediments. **No automatic water, ice
    or gravel mass insertion**, since all features are height-density only.

Cirque and moraine families appear at cold/moist highland sites based on
site-stable temperature/vegetation fields. Cold/dry sites preserve caldera
variants; former eight relief equations are unchanged from v2 for the same
site/hash/position.

V3 works only for the *newly created player-facing Natural Exploration
preset*, not the measured USGS mode; it uses an independent noise setting,
and the old v1/v2 registries and saved-world settings stay available.
All 4 player-facing preset IDs, Sky Ocean, cross-realm invasion, cave and
marine ecology pipeline remain unchanged.

## Engineering / safety

- Stable 4096-block absolute site grid; site radius <1400 blocks; envelope
  smoothly approaches exactly zero at boundary.
- No block/fluids or neighboring chunk writes from the density functions.
- No new bodies of water, flooded land, arbitrary per-block ice, or modified
  ocean bathymetry. Inland continentalness and pre-existing elevation gates
  preserve lowlands, coastlines, and measured terrain.
- The lower outlet of the cirque is topographically open but no glacier or
  actual flowing meltwater is simulated.
- Moraines are landform *height silhouettes*. Per-lithology soil materials,
  till provenance, glacial transport and true snow climatology still require
  later biome/surface work.
- **A fjord is NOT implemented.** True fjords need glacier-carved below-sea-
  level valleys with verified continuous marine access; these mainland
  upland-only variants deliberately leave coasts unmodified.

## Automated validation

- Standalone Java 21 `CinematicLandformMathV3Smoke` checks that each of ten
  families is climate-accessible, geological samples are bounded and finite,
  cirque headwall/floor/outlet and moraine sides/corridor have correct relative
  heights, and v2 height output is **exactly** preserved for previous 8 forms.
- `qa/test_cinematic_macro_landforms.py` compares full v2/v3 noise router
  content, permitting only three changed density references. Also verifies
  the standalone Java output and ten generated PNG math diagnostics.
- New `CinematicLandformPreviewV3` generates ten deterministic PNG
  overhead relative-height views from actual production math.
- GitHub Actions `cinematic-macro-world.yml` boots a new v3 NeoForge 1.21.1
  server world, tests serialized world-profile ID in `level.dat`, checks
  nonempty Anvil chunks, and uploads logs plus v1/v2/v3 diagnostic PNGs.
- Full actual-game 3D visual approval, waterfall/river hydrology,
  seed-to-seed regional diversity, and long-route snowline continuity are
  required separately from these checks before any non-Draft release.

## Geography source links

- National Park Service glacial valleys / hanging valleys / fjords:
  https://www.nps.gov/articles/ushapedvalleysfjordshangingvalleys.htm
- USGS: glacial landforms and till deposits:
  https://pubs.usgs.gov/of/1993/0292c/report.pdf
- UNESCO: Wulingyuan: https://whc.unesco.org/en/list/640
- UNESCO: South China Karst: https://whc.unesco.org/en/list/1248

This original implementation borrows scenic design principles from games but
no copyrighted textures, geometry, screenshots or fictional map data.
