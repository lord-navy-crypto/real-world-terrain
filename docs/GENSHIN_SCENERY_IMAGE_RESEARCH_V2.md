# RealScale — Genshin scenery image study, second pass (2026-10-20)

**Purpose:** study visual composition, relief, geology and natural exploration
from existing Genshin Impact environment screenshots, then derive *original*
Minecraft 1.21.1 NeoForge rules. The linked images belong to their respective
owners; **do not bundle screenshots, textures, meshes or proprietary map data**.
A visual similarity is **not evidence that HoYoverse used the named real-world
place as an official reference**. RealScale's four playable presets remain four.

## Screenshot-by-screenshot morphology notes

Each URL is a reference for visual study, *not* an asset for redistribution.

| Scene (image link) | Observe at macro / middle / near scale | Geomorphic or ecological interpretation | RealScale engineering direction |
|---|---|---|---|
| [Liyue / Chenyu Vale](https://images.rpgsite.net/image/da49c9a1/133526/original/Genshin-Impact_20240119_10.jpg) | Layered blue-green ridges recede into valley fog; wide waters with small foreground plants | Moist mountain river valley, slope vegetation and orographic low-cloud distribution | Preserve DEM slopes; valley-floor fog as **optional client effect**, elevation/aspect-aware foliage; no worldgen fog blocks |
| [Liyue / terraced pools](https://upload-static.hoyoverse.com/hoyolab-wiki/2023/07/21/7998136/9e257c976cbd0981802630345b2c551d_6751099469004983469.png) | Contour-following, nested pale rims enclose connected shallow blue-green water; orange trees contrast | Real analogue: carbonate deposition (Huanglong) where water chemistry and flow allow; not generic sandy ponds | Plan flow-aware travertine rims, preserve upstream/outlet continuity and existing fluids. Present code is dry microform **only** |
| [Sumeru / giant rainforest canopy](https://progameguides.com/wp-content/uploads/2022/08/29-varuna-gatha-genshin-impact-3-featured.jpg?fit=1200%2C675) | A single colossal canopy dominates the scene; shadows, hanging roots and multiple understory levels | Vertical tropical forest structure; monumental fantasy prop is not a real tree species | Canopy height classes, buttress-root analogue, vines and multiple foliage strata; tree/structures in bounded chunks |
| [Sumeru / Hadramaveth dunes](https://cdn2.unrealengine.com/07-3840x2160-297cf5d1a2c5.jpg) | One dominant moving sand vortex, crescent dune slopes, sharp bedrock fins and sparse plants | Aeolian dunes plus differential rock erosion; giant vortex is fantasy | Separate dune-shape field, windward/leeward slopes, bedrock yardangs and dry ephemeral channels; no persistent artificial sandstorm |
| [Sumeru / desert caverns](https://live.staticflickr.com/65535/52607064131_82ae2f0bad_h.jpg) | Deep overhangs, huge chamber silhouette, warm sand above blue shadowed abyss | Wind-worn surface connected to caves; cave genesis may be different from surface erosion | Add geological roof-thickness and cave-overhang guardrails, not random floating slabs |
| [The Chasm / luminous cave ecology](https://cdn2.unrealengine.com/03-3840x2160-fe4debde8da7.jpg) | A small amount of strong cyan light guides sight lines in a dark expansive grotto | Fungal/subterranean niche + photometric contrast; central monumental tree is fantasy | Sparse cave-luminescence pockets only, damp rocky substrate, sightline targets, performance bounds |
| [Dragonspine / ice cavern](https://live.staticflickr.com/65535/50703862411_785818095d_k.jpg) | Large ice aperture, overhang, bridge and hanging formations; bright surface light vs cold shaded lower cavern | Alpine cold caves and frozen water/ice processes | Ice cave zone conditioned on snowline, altitude, exposure and water; cave bridges must avoid unsupported blocks |
| [Inazuma / Tsurumi coastline](https://jolstatic.fr/www/captures/4836/4/153554.jpg) | Repeated coastal cliffs disappear into layered blue-gray fog; sparse wind-exposed plants | Maritime fog, cliffs, salt spray and exposure | Island-scale coastal slope mask, windward stunted flora, optional climate-responsive fog |
| [Inazuma / Seirai storm](https://img.4gamers.com.tw/ckfinder-th/image2/auto/2021-08/genshin-impact-2-1-seirai-island-inazuma-210820-225355.jpg?versionId=r0f2zPZO59nbyYvJjGP3qP3bRTAWudpB) | Vortex centered above disrupted high-rock silhouettes; very high color contrast | The purple permanent storm is fictional; volcanic/coastal islands can still have exposed rock and dynamic weather | Preserve vanilla/season-mod weather ownership. Rock island morphology separate from optional event ambience |
| [Fontaine / underwater seabed](https://img.gamewith.net/img/original_13410b1a84341899df863552711eb196.jpg) | Platform-like rock ledges, horizontal depth layers, coral groups and soft light attenuation | Photonic depth decrease and hard-bottom niches; avoid colorful corals on every deep seabed | Existing marine zoning + hard/soft substrate and temperature/depth controls; underwater sightline corridors |
| [Fontaine / Remuria sea](https://images.rpgsite.net/image/da49c9a1/136864/original/Genshin-Impact-4-6_20240413_12.jpg) | Huge connected arches separate near and far underwater spaces; corals visually frame ruins | Monumental fantasy architecture; **natural** underwater arches may form through differential erosion | Larger natural submerged rock arch field separate from player-built/artificial structures; no direct architecture copies |
| [Natlan / canyon plateau](https://staticg.sportskeeda.com/editor/2024/08/1d1a7-17244923923489-1920.jpg) | Broad horizontally layered mesa escarpments, warm tree palette and deep canyon perspective | Mesas and escarpments; can resemble sandstone/continental sediments, but exact official real reference unconfirmed | True broad mesa density masks and strata following world Y, plus dry grassland ecology and rivers |
| [Natlan / high-ledge waterfalls](https://images.rpgsite.net/image/da49c9a1/140398/original/Genshin-Impact-5-0_20240816_12.jpg) | Tiered cliffs and thin waterfalls connect terraces at several levels | Structural benches, caprock scarps, rain/runoff | Slope-aware cascades in river-model *only*, not indiscriminate new fluid source placement |
| [Nod-Krai / northern shore](https://gamedaily.com/.image/MTU6MDAwMDAwMDAwMDE4OTUy/nod-krai-north-18952.jpg?profile=w2560) | Pale-blue coast, sparse glowing grasses and exposed rock | Coastal marine climate, exposed bedrock, wet heath; glows fictional | Boreal/wet coastal plant mosaics; no dependence on invented moon-energy systems |
| [Enkanomiya / subterranean void](https://wallpaper.forfun.com/fetch/95/95c03c08eb4b13a475ee61221dca5986.jpeg) | A cavern-wide void with large vertical gaps and rare silhouette anchors | Deliberate fantasy negative space; not normal Earth geology | Consider **opt-in non-DEM fantasy-only** showcase using large cave voids, not USGS measured world |

## Reality checks

- [Wulingyuan UNESCO](https://whc.unesco.org/en/list/640/): more than 3,000 sandstone pillars, many >200 m. The existing 4-8 block decorative pillars do **not** reconstruct Wulingyuan.
- [Huanglong UNESCO](https://whc.unesco.org/en/list/638): travertine deposition; shallow blue pools need genuine basin/flow logic, not a pale rock cap.
- [South China Karst UNESCO](https://whc.unesco.org/en/list/1248): chemically dissolved limestone differs from Wulingyuan sandstone joints.
- [China Danxia UNESCO](https://whc.unesco.org/en/list/1335): continental redbed strata differ from basalt and lava flows.
- [Deep-sea coral habitat NOAA](https://www.fisheries.noaa.gov/national/habitat-conservation/deep-sea-coral-habitat): coral and sponge distributions depend on substrate and water conditions.
- [Chenyu Vale, HoYoverse PlayStation dev discussion](https://blog.playstation.com/?p=388276): Global Far Fog uses terrain/height; Gradient Tint Rock is a renderer technique, not biome/stone replacement.
- [Natlan, HoYoverse PlayStation dev discussion](https://blog.playstation.com/?p=394972): canyons, cliffs, water, hot springs and volcanoes coexist. They are *separate processes*, not one random noise term.

## Highest impact lesson

A photograph should be decomposed into **four independently testable layers**:

1. **Macro silhouette (100–1000+ blocks):** valleys, escarpments, true sandstone towers, mesa top, basin and reef slope. Implement via noise/density and DEM-preserving feature masks, not small above-ground decorations.
2. **Meso structure (16–128 blocks):** rock bridges, ravines, river terraces, shallow travertine rims, canopies and submarine arches. Shape generation must preflight large regions, respect chunk scheduling, and keep fluids stable.
3. **Micro ecology (1–16 blocks):** understorey vegetation, moss, weathered ledges, coral/sponge substrate, layer-color palettes. Existing placed features operate primarily at this level.
4. **Atmosphere / readability:** valley vs peak fog, skylight and depth attenuation, distant tint, weather and flora color. Implement optionally on compatible client/rendering hooks, not by spawning hundreds of blocks or taking control from seasons mods.

## Development order / acceptance criteria

- **A — Landscape morphology library**: documented deterministic math for terrain silhouette classes (pillar density, mesa plateau, river terrace, canyon slope, reef shelf); exhaustive seeded tests and worldgen profile boundaries. Use RealScale overworld only, preserve existing four menus and USGS DEM.
- **B — Macro dryland + humid mountains**: Wulingyuan quartz sandstone pillar districts and separately Guilin carbonate tower-karst, each with distinct geology and river/cave relations. First inspect generated contour maps and height distributions, then integrate density settings.
- **C — Hydro-geomorphic systems**: Huanglong travertine and existing-channel cascades, never broken connected water or unwanted lake flooding.
- **D — Vertical ecologies**: Sumeru-like multistory rainforest *principles*, misty coastline exposure and fog, source-appropriate biological diversity.
- **E — Underwater multi-depth exploration**: shallow seagrass/rocky shelf, deep seamount/cold-water coral, large natural arches and abyssal floor. No universal decorative coral carpet.
- **F — Visual fidelity validation**: per-scene comparison at far/mid/near distances; test seed reproducibility, density/CPU, world height and rollback from saved chunks.

**Current state:** eight geographic landmark microforms and four scenic microforms exist in Draft PR #75. This is NOT the finished macro landscape system. No images are distributed with this repository file; links are visual references only.
