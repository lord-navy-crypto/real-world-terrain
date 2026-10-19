# Ocean Renewal Phase I — Integrated Measured + Global Landforms

**Same Minecraft world as the existing USGS/global-landforms experiment.**
The player selects `realscale:tva_measured_global_landforms_three_realms`.
No new redundant ocean world type is added to the creation menu.
On this unmerged Draft feature branch, that preset now resolves
`realscale:usgs_global_hybrid_marine`, a versioned sea-floor graph.

## Verified scientific references

- [NOAA — ocean floor features](https://www.noaa.gov/education/resource-collections/ocean-coasts/ocean-floor-features):
  abyssal plains, mid-ocean ridges, ocean trenches, submarine volcanoes and vents.
- [NOAA — global mid-ocean ridge](https://origin.oceanexplorer.noaa.gov/ocean-fact/mid-ocean-ridge/):
  65,000-km-long ridge system, tectonic spreading centers and axial valleys.
- [NOAA — seamounts and flat-topped guyots](https://oceanexplorer.noaa.gov/ocean-fact/seamounts/):
  cone-shaped underwater volcanoes and eroded plateau summits.
- [USGS — submarine canyon bathymetry](https://www.usgs.gov/publications/geomorphic-characterization-four-shelf-sourced-submarine-canyons-along-us-mid-atlantic):
  shelf-breaking channels, sidewall gullies and erosion.
- [NOAA — coral fringing/barrier/atoll morphology](https://oceanservice.noaa.gov/facts/threecorals.html):
  biological reef types, not interchangeable with physical seabed noise.
- [NOAA — vents versus cold seeps](https://oceanexplorer.noaa.gov/ocean-fact/seeps-vents/):
  separate chemosynthetic habitats with different geological causes.
- [USGS — seafloor mapping](https://www.usgs.gov/science/science-explorer/ocean/mapping-the-seafloor):
  bathymetry, rock-versus-mud substrate, canyon networks and sediments.

## Implemented physical sea-floor shapes — 8 types

All are height-density **analogs** at up to 8,192-block-wide cells; they
are NOT imported global sea-floor measurements.

| Kind | Mathematical terrain geometry |
| --- | --- |
| Abyssal plain | Shallow flattening toward a smooth deep basin height |
| Mid-ocean ridge | Broad underwater rise with sinuous axial notch |
| Hadal trench | Narrow, strongly recessed seafloor and one adjacent rise |
| Seamount chain | Separate raised submarine volcanic-cone footprints |
| Flat-topped guyot | Broad top and sloping underwater volcanic margins |
| Submarine canyon | Narrow, deeply incised winding underwater river-like valley |
| Abyssal hills | Small-scale rolling/corrugated seafloor |
| Transform fracture zone | Paired offset oceanic fault scarp ridges and troughs |

Values are bounded in horizontal height-signal units rather than literal
Earth metres. Minecraft's vertical scale and `noise.height=2096` cannot
directly represent Earth's Challenger Deep at real 10.9-km depth.
A ridge here is an isolated **morphological analogue**, not a globally
connected tectonic plate-boundary graph.

## Non-negotiable terrain and ecology protections

1. `usgs_big_sur_v2`, Sacred and other legacy settings are unmodified.
2. Baseline source is **exact existing Measured + 24-landform hybrid**.
   Only truly offshore, submerged procedural columns receive ocean effects.
3. Any measured Big Sur DEM column is protected **100%**, and in its 2048m
   transition the ocean effect fades by the DEM source's own exact weight.
4. Ocean-floor rises are capped at a safe depth (~38 blocks below sea level).
   Depression features may be deeper. No new water blocks or dry islands.
5. Noise graph retains sea level 63, all vanilla ocean/river/beach biome
   decisions, aquifer noise, surface palette, SkyOcean islands, End spawn,
   Grand Nether/End and all earlier cavern logic.
6. No new global seagrass/stone biome modifiers; historical preserved reef,
   kelp, ruin, shipwreck and ocean monument sources stay in place.

## Next ocean renewal phases (NOT represented as finished)

- Use real open-ocean bathymetry grids (GEBCO/SRTM15+), coordinate reprojection
  and source tracking in places with coverage, as already done for limited
  Big Sur land DEM. **These data are not bundled yet**.
- True shelf/slope/rise geomorphology, sedimentary submarine fans, continental
  margin canyons with connected drainage.
- Context-aware reefs: warm shallow-water fringing/barrier/atoll habitat,
  lagoon water, coastal mangroves, sea-grass beds and cold kelp forests.
- Hydrothermal vents *only* near appropriate volcano/ridge sites; cold seeps
  over appropriate sedimentary basins — not random ocean-wide spam.
- Biome-aware marine fauna, schools, plankton/coral models and deep
  aphotic-zone ambient effects, with honest 1.21.1 biome and entity constraints.
- Actual saved Anvil multiseed marine depth profiles, continuous underwater
  terrain, biome-water-column consistency, structures and performance.

## Proof gates

- [x] Versioned marine density and physically distinct forms authored.
- [x] Exact DEM protection and safe underwater uplift cap.
- [x] Old world choices unchanged; new integrated world updated in Draft.
- [x] Static worldgen/router, pure Java seafloor geometry QA authored.
- [ ] NeoForge build and all source integrity checks pass.
- [ ] Dedicated real Measured+24+Marine server boot/save+End-safe spawn passes.
- [ ] Ocean depth QA from real saved marine-mode Anvil chunks and multiseed.
- [ ] Habitat and ecological deepwater biome/features introduced and verified.

Keep this PR Draft until the later proof gates are complete. Existing generated
old-mode worlds must never be silently rewritten by merging new codecs into
the old `usgs_big_sur_v2` settings.
