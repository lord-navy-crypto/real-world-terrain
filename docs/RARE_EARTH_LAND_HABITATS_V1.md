# Rare Earth terrestrial signatures (first runnable iteration)

This branch adds `realscale:rare_earth_habitat` to **all thirteen rare Earth
biomes**, shared across existing RealScale overworld presets. The existing
`rare_earth_surface` remains responsible for ground palettes; this second
feature places limited above-ground scenery.

## Habitat groups

| Rare Earth biome | Current vanilla-block signature |
| --- | --- |
| socotra_dragonblood_woodland | stylized acacia-log umbrella trees, sparse grass |
| socotra_endemic_scrub | small flowering-azalea-leaf trees and xeric shrubs |
| montane_mist_forest | compact spruce silhouettes, moss carpets, ferns, azalea |
| sundarbans_tidal_mangrove | sparse mangrove-log/leaves canopy and ground flora |
| congo_peat_swamp_forest | compact deciduous trees, moss and fern floor |
| permafrost_fen | low moss carpets and sparse grass |
| namib_fog_desert | red sandstone gravel outcrops, rare dry scrub |
| serpentine_barrens | tuff stony outcrops and rare dry scrub |
| limestone_island_scrub | calcite outcrops and dry scrub |
| white_dune_lagoon | calcite rock pockets, rare dry scrub (no lagoon excavation) |
| steppe_saline_lake | sparse calcite deposits (no lakes or flowing water) |
| geothermal_sulfur_basin | yellow terracotta mineral stacks (no steam/water chemistry) |
| acidic_geothermal_mudflat | pale gray terracotta mineral stacks (no acid simulation) |

Every attempt is limited to the originating chunk. Trees preflight all log and
leaf positions and require natural soil. Flora uses vanilla survival checks.
Rock clusters only write into air above natural support, so no structures,
water, underground geology or ore layers are intentionally modified.
Feature runs only in RealScale generator overworld settings; no per-tick
season engine is created and existing seasons mods remain authoritative.

## Scientific interpretation and caveats

These are **stylized proxies**, not reproductions of endemic plants. IUCN
ecosystem classification and conservation risk are different datasets; this
version does **not** label each Minecraft region 'endangered'. No real
species abundance, georeferenced ranges, salinity, fog collection, tides,
hydrology, mangrove root networks or tree-genetic fidelity is implemented.

References:
- IUCN Global Ecosystem Typology:
  https://iucn.org/resources/conservation-tool/iucn-global-ecosystem-typology
- UNESCO Namib Sand Sea:
  https://whc.unesco.org/en/list/1430/

## Verification gate

Review NeoForge source build, datapack boot, dedicated-server saved chunks,
multi-seed distribution, cave protection and structure overlap before merge.
Static JSON wiring tests cannot prove visual quality. Keep PR Draft until
real server terrain can be inspected.
