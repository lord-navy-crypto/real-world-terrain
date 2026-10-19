# Ocean Ecology Renewal — Zonation v1, integrated Measured world

**Same world option:** `realscale:tva_measured_global_landforms_three_realms`.
Extends the unmerged USGS + 24 rare geological landforms + NOAA-inspired
eight bathymetry shapes work, **without adding another world mode**.

## Why cold sea is beautiful

The world ocean is not just shallow tropical reef versus empty grey deep sea.

- NOAA Fisheries [Deep-Sea Coral Habitat](https://www.fisheries.noaa.gov/national/habitat-conservation/deep-sea-coral-habitat):
  branching soft corals and sponges create colorful, structure-rich groves
  hundreds to thousands of feet down, no photosynthetic symbiotic algae needed.
- NOAA [Kelp Forests](https://oceanservice.noaa.gov/facts/kelp.html):
  cool nutrient-rich, shallow sunlit waters support towering kelp with fish
  and invertebrate communities.
- NOAA [Light Zones](https://oceanservice.noaa.gov/facts/light_travel.html):
  euphotic 0–200m, twilight 200–1000m, aphotic beyond 1000m on Earth.
- NOAA [Mesophotic and Deep Benthic](https://www.fisheries.noaa.gov/southeast/habitat-conservation/mesophotic-and-deep-benthic-communities-restoration):
  deep rocky habitats include cold-water corals, sea fans, sponges, anemones,
  sea stars and fish.

**Real-world metres do NOT correspond one-to-one to Minecraft blocks**
in this build: its sea level stays Y=63 with 2096-height underground/world
geometry. Model light-zone thresholds as internal gameplay-relative depths.
Do not claim true temperature gradients, high hydrostatic pressure or species
distribution at Earth precision.

## What this v1 adds physically

One registered feature `realscale:marine_ecology_layers` is appended once
at `vegetal_decoration` to nine vanilla ocean biome types. The feature
immediately rejects every generator except **exactly**
`realscale:usgs_global_hybrid_marine`, thus a global biome modifier cannot
pollute vanilla, Sacred, legacy Measured or another mod.

At two attempted patches per chunk, place a handful of natural-block visual
analogues only after testing:
- actual ocean biome at the requested column (never ocean name implies water)
- WATER source block immediately above natural solid seabed
- column depth from original sea surface, with depth at least 4 blocks
- rocky versus sediment substrate, so a coral garden is not painted across mud
- seed-aware smooth **96-block** grove concentration rather than white-noise dots
- own origin chunk, all kelp stem voxels prechecked as water before placement

| Habitat zone | Preferred Minecraft analogues |
| --- | --- |
| Warm, sunlit rock | Colored vanilla living coral + fan blocks |
| Warm shallow soft bed | Seagrass meadows |
| Temperate/cold shallow rocky bed | 3–7-high kelp stands |
| Temperate shallow sediment | Seagrass meadows |
| Cold rocky intermediate depth | Coral fans and wet sponge gardens |
| Deeper rocky bed, including cold & polar water | Colorful coral-fan gardens, sponges |
| Sub-ice cold shallow bed | Sparse coral and sponge analogues |
| Deep silty bed | Intentionally sparse, preserving bare sediment |

**Important science/asset limitation:** Vanilla tropical coral blocks are
a **visual proxy** for colorful cold-water invertebrates. They are not
biologically authentic, nor are wet sponge blocks actual benthic sponge
species. Live tropical coral species should NOT be mistaken for natural
polar coral species. Future custom cold coral/sponges/models should use
species-specific assets and properly gated registries.

This patch introduces **no new mobs, fish species, whale models,
glowing plankton, currents, food webs, salinity simulation, hydrothermal
vents or coral species taxonomy**. Those are further phases.

## World integrity and release proof

- USGS Big Sur measured baseline unchanged; marine density noise remains.
- Ancient/modern shipwrecks, structures, vanilla corals and kelp are not
  deleted or overwritten. Writes only replace source WATER over natural
  seabeds and never extend across generation-chunk boundaries.
- Sacred world, existing Measured world/DEM and world choice counts unchanged.
- [x] MarineFeature+Math coded, vanilla-only data pack placement created.
- [x] Python static and pure Java photic/thermal/seed seam tests authored.
- [ ] Full NeoForge build and actual server world creation pass.
- [ ] Actual saved Anvil proof of cold ocean kelp/sponges/coral blocks,
      across at least two naturally oceanic seeds.
- [ ] Performance budget and Minecraft FeatureSorter DAG acceptance proven.
- [ ] Original ocean QA, structures and spawn regression tests all pass.

Keep PR **Draft**, do not merge to main until the proof gates complete.
