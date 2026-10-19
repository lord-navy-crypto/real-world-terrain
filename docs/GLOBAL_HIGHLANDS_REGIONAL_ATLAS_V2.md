# Global Highlands Expedition v2 — 16 regional geomorphologies

**Experimental, additive.** v1 preserved: `realscale:regional_landform`, its
4,096-block region classification and all original noise settings remain
unchanged for existing saves. Only a newly selected world uses the
`realscale:regional_landform_diverse_v2` codec, new `tva_regional_atlas_v2`
noise graph, and the **8,192-block macroregion**.

## Eight additional geological analogues

1. **Tibetan U-shaped glacial trough** — broad low interior and higher flanks
2. **Tibetan closed inland basin** — depressed interior, enclosing high rim
3. **Peru/Bolivia Altiplano salt-pan floor** — smooth low-relief interior
4. **Andean bofedal bench** — wetland-compatible low meadow terraces
5. **Ecuadorian volcanic chain** — several separate cones and shallow crater tops
6. **Yunnan–Guizhou fenglin tower karst** — separated narrow limestone peaks
7. **Chongqing Wulong tiankeng field** — clustered closed limestone depressions
8. **Sichuan Basin escarpment** — gradual multilevel hills rising from basin edge

Alongside v1's eight shapes, total **16 distinct morphology IDs**. All are
world-seeded procedural analogues, not real georeferenced altitude grids.
No true saline water, hydrological closed-basin lake, cave-based collapse
mechanics or glacial ice sheets are implied by the terrain height function.

## Safety and generation

- Site ID, center, radius and subtype are fixed in absolute world coordinates.
- The 8km ring radius is 2,450–3,100 blocks and cannot overlap a neighboring
  8km site; all effects vanish smoothly at the external border.
- New morphology changes only the horizontal terrain-density signal inside
  inland highland gates. Natural sea level, ocean water, original cave network,
  temperature distribution and End/Nether densities keep their old values.
- V2 source uses a thread-local per-site classification cache (world seed and
  codec instance isolated) so adjacent block samples cannot disagree.
- Sacred and Measured profiles, original standalone regional atlas v1, and
  existing 1:1 Chongqing geographic DEM/GIS conversion stay unchanged.
- The new GUI preset is
  `realscale:tva_regional_landform_expedition_v2_three_realms`,
  preserving v3 biodiversity, offshore SkyOcean islands, safe End-invasion
  spawn and Grand Nether/End.

## Supporting science

- [Tibetan endorheic basin DEM study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10999788/)
- [Tibetan permafrost and complex intermontane geomorphology](https://www.frontiersin.org/journals/environmental-science/articles/10.3389/fenvs.2022.836085/full)
- [UNESCO South China Karst: Wulong tiankeng, Shilin pinnacles and cone karst](https://whc.unesco.org/en/list/1248)
- [Peruvian IGN high-Andean bofedal and pajonal vegetation zones](https://app4.ign.gob.pe/capitulos/cuatro/regiones.php/capitulos/tres/capitulos/imgx/menuprincipal/c7oaz.svg)

## Proof gates

- [x] Separate new codec, 16-shape mathematical morphology.
- [x] New 8km site geometry with 3 new density graph resources and one world
      creation preset, with persistent codec IDs.
- [x] Pure Java macroregion smoke and Python legacy/settings/menu tests authored.
- [ ] CI tests pass; NeoForge compilation and dedicated server boot completed.
- [ ] Actual Anvil site-center height/biome/vegetation comparisons across
      seeds. No production-readiness claims until complete.

This work is stacked on the earlier **FeatureSorter vegetation crash fix**
and regional atlas v1. Keep **Draft**, do not merge to main.
