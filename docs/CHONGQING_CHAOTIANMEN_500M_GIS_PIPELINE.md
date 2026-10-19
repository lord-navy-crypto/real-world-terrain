# Special Chongqing — Chaotianmen 500m real GIS city-block pipeline

This is a SECOND, separate stage after PR #54's **genuine DEM floating shell**.
It deliberately has **no fictional Chongqing buildings or river shapes**.
Data must come from a real, licensed GIS snapshot; the pipeline fails if a
usable source is absent or has no supported ways. Existing worldgen and
TVA Sacred/Measured/sky presets remain unchanged.

## Pilot scope and exact coordinates

- First stage: square **-500..+500 blocks** X/Z, about 1,000 m across,
  centered on the already committed DEM world origin.
- DEM world origin is WGS84 29.5667 N, 106.5874 E
  (approximately the Chaotianmen riverfront core).
- X increases **east**; Z increases **south**; source XY in WGS84 degrees
  converts to metres with the **SAME local WGS84 tangent-plane formula**
  originally used by PR #54's 129 x 129, 64m elevation grid.
- Minecraft ground surface is modelled by the DEM density function,
  conservative first-air Y=ceil(real DEM elevation +96) for overlays.
  The exact integer-elevation case matters: floor(Y)+1 would create a
  floating one-block offset. Actual saved WORLD_SURFACE must still be
  checked by the GUI before placing a block. Block placement
  is a post-generation approximation and should be visually measured in
  real saved Anvil worlds; exact cliff/road foundations will require
  future heightmap-based placement calibration.
- The special floating land is currently ~40 blocks thick: no giant
  subterranean caves, deep geologic simulation, or separate duplicated
  chunk generator.

## Source and licensing

Preferred vector dataset: **OpenStreetMap** snapshot retrieved via
Overpass QL, which provides measured contributor geometry and tags. OSM
data carry the Open Database License (ODbL). Retain attribution and
provenance; whether redistributing a derivative database triggers
share-alike duties depends on the eventual product, so review licensing
before distributing a public world.

Links:
- https://www.openstreetmap.org/copyright
- https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL
- https://overpass-turbo.eu/
- https://www.openstreetmap.org/#map=17/29.5667/106.5874

**Do not scrape Google Earth/Maps 3D tiles**, nor convert unauthorized
satellite or building meshes into mod assets. Visual references and
redistributable geometry are different rights.

## Step A. Acquire verified real GIS

With Python 3.10+ and internet access on your OWN device:

    python3 tools/chongqing_fetch_osm.py \
      --radius 500 \
      --dry-run \
      --output chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64

Review the exact query. Then run without --dry-run. It requests:
1. Closed building footprints (only supported OSM ways).
2. Roads/streets/paths, including their actual line coordinates.
3. Railways, genuine natural=water and waterway=riverbank polygon ways.

Query extends 100 metres outside the target area so features intersecting
its boundary can be clipped properly. Closed water/building multipolygon
relations are also queried; the importer only stitches segments where
the exported coordinates form valid, complete closed rings. Source saved as immutable raw
Overpass JSON plus a provenance JSON containing OSM timestamp and source
query SHA-256; NEVER silently replace a snapshot with new data.

Some public Overpass servers may be slow, unavailable, throttled or block
queries. If this happens **STOP** and use Overpass Turbo's official
export (GeoJSON) into a local file. We do not invent geography or
commit a pretend successful snapshot.

**Important limitation:** Complete OSM multipolygon relation way segments
are now stitched and their interior holes respected. Broken/unclosed
relations are rejected instead of being forced together. Real OSM coverage
can still omit waterways and buildings, so a successful conversion does
not imply the Yangtze/Jialing rivers or plaza have been fully surveyed.
**UPDATE 2026-10-19:** Authentic OSM contributor data now exists at
`chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64`.
This comes from the **official OSM API** `/api/0.6/map` (not Overpass):
preserved OSM IDs, tagged positions and provenance with upstream XML SHA,
licensed ODbL. It is gzip+base64 for compact GitHub archival and can be read
directly by the converter and audit tools. It is NOT a complete 3D survey.
See `docs/CHONGQING_REAL_OSM_SNAPSHOT_20261019.md`.

## Step A.5 — Audit the actual imported GIS before Minecraft placement

The source-only audit can now read the checked-in authentic OSM API archive,
regular Overpass JSON, or Overpass Turbo GeoJSON. This needs no Minecraft
client, external Python packages or further network requests:

    python3 tools/chongqing_gis_audit.py \\
      --input chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64 \\
      --radius 500 \\
      --output build/chaotianmen-gis-audit

The output directory must be new/empty. Outputs:

- `plan.svg`: north-up **1m-to-1-block** local X/Z vector map from the
  supplied source only; blue=water polygons, grey=building footprints,
  orange=roads, purple=rail. Hover over paths in an SVG-capable browser
  to see their source identifiers/names. It does not use Google basemap
  imagery or infer any missing street/building outlines.
- `audit.json`: source SHA-256, GIS feature IDs, projected bounding boxes,
  coverage counts, missing building-height tags, vertical transport that
  still needs a real surveyed Y coordinate, and candidate IDs matching
  named points of interest. All named matches are **candidate OSM tags**,
  NOT proof that the real structure is accurately modelled.
- Missing name matches (e.g., 来福士 / 朝天门广场) are recorded as
  **missing in supplied source tags**. They are NOT claims that the real
  landmark does not exist. Some landmarks may fall outside the pilot.

For field validation, compare the SVG geometry with a licensed real-world
reference and record manually measured coordinates, height datums and
observed visibility; **do not scrape or redistribute copyrighted 3D maps**.
Until source acquisition succeeds, no audit PNG/SVG generated from a synthetic
unit-test fixture is evidence of Chongqing geometry.

## Step B. Convert to vanilla Minecraft datapack

After obtaining a REAL Overpass JSON/GeoJSON file:

    python3 tools/chongqing_osm_to_minecraft.py \
      --input chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64 \
      --radius 500 \
      --output build/chaotianmen-gis-datapack \
      --attribution "© OpenStreetMap contributors, ODbL; exported YYYY-MM-DD"

The output is a vanilla **Minecraft 1.21.1 datapack**, pack_format=48.
It does NOT modify any world save itself. The datapack contains up to
3,000 commands per function and a provenance-rich manifest listing
each function in execution order, checksums, tagged/estimated heights,
bounds and geometry counts.

All geometry is projected at **1 horizontal metre = 1 MC block**.
- Buildings: valid footprints drawn at the actual DEM-relative surface.
  Only explicit OSM height tags can be used as reported metres. Tagged
  building:levels are optionally converted to 3m per level, flagged
  **estimated** in the manifest. Buildings lacking both tags remain
  footprint-only — not fictional towers.
- Roads: exact centerlines from OSM. When tagged width in metres exists,
  rasterize approximate pavement breadth. If width is missing, keep
  just a 1-block centerline rather than invent 4/6-lane road widths.
- Railways: one-block alignment guide, not a fully accurate multi-level
  monorail, tunnel or bridge reconstruction. Ground-level geometry only:
  tagged bridges/tunnels and nonzero layer features are **deferred** when
  their actual vertical placement is not verified, rather than falsely
  flattened onto the terrain.
- Water: only REAL *closed water polygons* become blue **reference blocks**,
  never arbitrary flowing Minecraft water that could flood the whole
  floating island. Do not mistake the blue reference color for finished
  Yangtze/Jialing channel hydrodynamics.
- Closed highway/railway OSM ways remain **lines** rather than filling
  their interiors, unless explicitly `area=yes` and valid area geometry.
- The exporter run-compresses DEM-aligned horizontal rows into `fill` where
  every cell shares the same **real source feature / height / material / Y**.
  Every command touches at most 2,048 blocks, and every staged function
  at most **20,000 block writes**. `manifest.json` records
  `stage_block_writes` and `staged_block_writes` for load budgeting.
  This is a performance safety improvement, **not new geographic data**.
- Incomplete multipolygon relations, exact structural elevations, bridge
  clearance, tunnel geometry, vegetation and real 3D facades remain
  explicitly unknown. We do NOT auto-invent Raffles City or Hongyadong.

## Step C (experimental) — Native in-game UI, NO chat commands

The exporter also creates `chongqing-gis-plan.json`: a JSON file made
of immutable, DEM-aligned run-length block columns, separate from vanilla
function files. It contains **no Minecraft commands**. The preferred
interactive workflow now is:

1. Obtain/verify REAL OSM source; run GIS audit and export; examine source
   SHA, landmarks and `manifest.json`. No fabricated geometry is acceptable.
2. Create a **NEW** `Special Chongqing Sky City` world (not TVA Sacred,
   Measured, any other family, and not an existing valued survival world).
   Let its pilot chunks load. **Create an external copy/backup of the save**
   before ever applying the overlay.
3. The authentic 500m OSM V2 plan is now **BUILT INTO THE MOD JAR** at
   `assets/realscale/terrain/chaotianmen_authentic_osm_ui_plan_v2.json.gz.b64`.
   Players need NO external download, terminal, Python, datapack or file copy.
   Press G and choose **Load & verify source plan** to read the real pinned
   OSM plan, SHA-verified, entirely offline.
   GIS researchers may optionally supply a newer audited JSON file at
   `config/realscale/chaotianmen-gis-plan.json`; invalid overrides fail
   closed, with no silent fall back to the built-in map.
4. In **integrated singleplayer Creative** with the mod installed, press
   **G** to open `Special Chongqing | GIS Placement Lab`:
   - **Load & verify source plan**: validates coordinate origin, source
     checksum format, exact DEM SHA, real DEM Y for each planned block,
     allowed materials, source geometry bounds, sorted/nonoverlapping rows,
     the V2 shell/roof policy, <=5 million pilot writes and row-count
     integrity; does NOT place blocks. The true-OSM native V2 shell plan
     requires **494,988 writes** versus the old solid V1's 4,033,166.
   - **Survey nearby terrain (NO writes)**: the server checks up to 64
     ALREADY LOADED mapped columns close to the player, comparing the actual
     saved-world surface with DEM plan Y. It neither changes blocks nor
     loads chunks. At least one valid nearby sample and zero mismatches
     are needed before Start. Every column is checked again during placement.
     This local preview does not guarantee unsurveyed columns are valid.
   - **I backed up this NEW world**: deliberate user confirmation. This is
     an acknowledgment, not a technical check that a backup exists.
   - **Start / Pause 256 blocks per tick**: after explicit confirmation,
     uses the integrated server tick event to place at most 256 blocks/tick
     when that tick has spare time. No Minecraft `/function`, chat or
     console commands are involved. Click again to PAUSE.
     **Closing the GUI or pressing ESC also PAUSES placement immediately**.
     Progress persists in memory only for the running server session.
     Before the FIRST block write the server atomically creates
     `realscale-chaotianmen-gis-lock.json` inside this save folder, recording
     exact plan/source SHA-256 and preventing a repeat run after quitting,
     crashing or restarting. This is NOT persistent resume. Restore an
     untouched backed-up world for a new attempt; never delete the marker
     to force replay.
   - **Close / Pause**: stops further automatic changes immediately and
     requires a newly acknowledged backup before another GUI run.
5. The native executor *refuses* dedicated/multiplayer sessions, player
   non-Creative mode, every other dimension, and every noise generator
   except `realscale:special_chongqing_sky`. It refuses unloaded chunks
   and protected collisions. The integrated server also verifies
   exact terrain height against the ACTUAL WORLD_SURFACE and requires
   real bare soil/stone beneath any projected air block. It never
   downloads data at game startup.
   A partially placed plan **cannot be automatically rolled back**:
   restore the external backed-up new world if necessary.

**Experimental warning:** This is a minimal native pilot interaction layer,
NOT yet a validated full-city or distributed multiplayer installer. Plans
are only source-faithful when genuine, complete and accurate GIS is provided.
Test an actual client in a disposable world and inspect each small batch
before increasing performance budgets. The standalone datapack remains an
optional backward-compatible laboratory exporter; it must not become the
required user interface.

## Research-only legacy output (NOT the player interface)

The exporter also retains a backwards-compatible vanilla datapack for
integration comparisons, but in normal gameplay **players never run chat
commands, terminal commands or functions to trigger buildings**.
Use the integrated **G-key** GUI and its Start/Pause button. The
command-based export is strictly optional research material, not a
requirement of the mod or city experience.

The importer NEVER downloads Chunky, starts background servers, writes
world Anvil .mca or modifies saved level.dat.

## Acceptance status (real OSM checkpoint)

- Authentic official OSM geometry snapshot archived with source URL,
  upstream raw response SHA-256, ODbL attribution and preserved OSM ids.
- The real 500m map preview is committed as
  `docs/assets/chaotianmen_500m_authentic_osm_20261019.svg`.
  205 source features overlap the pilot bounding box by their bounding
  rectangles, not all are complete/accurate 3D surveys.
- The real 500m GIS-only CI job completed successfully. Output:
  32,403 plan rows, 44,606 legacy commands, and 4,033,166 native
  block writes. 71 unsupported multilevel road/rail features and
  seven vertically ambiguous building features remain deferred.
- The Crystal is specifically **NOT** flattened into a ground tower.
  Real upper/lower construction datums, more complete river banks,
  facades and bridge levels still need separately verified sources.
- NeoForge GUI compilation has passed, but player interaction,
  long-duration real placement, exact live terrain Y, world reload,
  client FPS and complete TVA gates are NOT yet fully verified.

Release: Keep Draft. Do not merge without live Minecraft save QA,
source licensing review and 3D landmark / river bank acceptance tests.

## Phase 6 — in-game built-in OSM V2 hollow architecture

- Source geometry comes from the SAME real pinned OSM 500m archive;
  shell generation adds no invented structure footprints. `The Crystal`
  is still **DEFERRED** until correctly registered 3D support Y is verified.
- Vertical tower massing uses neutral schematic blocks only: cells along
  a source building's footprint edge become **perimeter walls + roof caps**,
  interior cells become **foundation + roof cap with empty interior air**.
  This is an illustrative shell, NOT a surveyed facade, floor layout,
  glass texture, architectural roof model or decorated interior.
- Native export format `REALSCALE_CHAOTIANMEN_PLACEMENT_V2` includes
  `shell=none|facade|roof`. Old V1 plans are rejected rather than mixed.
- GIS CI reports **34,063 rows and 494,988 planned direct GUI writes**,
  compared with 4,033,166 writes for the old solid mass.
- Bundled compressed-source plan is SHA-pinned to uncompressed JSON
  `04a380b144d3f002c0157d841edefb68d5a5bfacf6ca07149c43e8f03a858980`;
  build CI checks exact byte equality with a fresh offline conversion
  from the real ODbL OSM snapshot.

**Open:** full game-client G-key survey + actual save block-placement test,
OSM facade heights and eighth tower, 3D skybridge, riverbank heights,
client FPS and world re-entry. Draft only; never merge to main prematurely.

## Phase 7 — proximity-aware, chunk-safe GUI construction

The older GIS executor advanced only from the first row, immediately
stopping whenever that far-away `(X,Z)` belonged to an unloaded chunk.
The official authentic 500m map contains many distant roads and towers:
this made the built-in G-key UI difficult to use with ordinary Minecraft
render distances. The new design fixes the issue without generating or
force-loading additional chunks.

- New deterministic `ChongqingGisChunkQueue` divides the unchanged
  V2 OSM row runs at **actual 16×16 Minecraft chunk boundaries**.
  Negative X/Z coordinates use floor division, not truncated division.
  All rows remain attributed to the exact existing OSM geometry; each
  planned block-write operation belongs to **one and only one** chunk.
- The integrated-server executor automatically chooses **already loaded**
  unfinished GIS work within five chunks of the player, favoring the
  nearest and keeping a cached chunk cursor to minimize per-tick overhead.
  The same **256 operations/tick** cap, collision gate, actual
  `WORLD_SURFACE` check, backup acknowledgment and persistent
  non-replay save lock are retained.
- The player can stay in the same location while nearby buildings and
  roads are filled; **unloaded distant chunks remain untouched**.
  Once no loaded nearby work remains, construction PAUSES, gives
  coordinates of the **nearest still-unfinished GIS chunk** and asks the
  player to fly/walk there, re-run a **NO-WRITE terrain survey** and
  manually press Start again. No automatic teleport and no chat commands.
- The G-key vector minimap now shows an **actual player-position marker**
  when the player is inside the ±500m source square. Building/road/rail/
  water layer filters continue to use the pinned real OSM data only.
- New `ChongqingGisChunkQueueTest` consumes the entire 494,988-operation
  real V2 source plan through correct chunk cursors, enforcing exact
  totals, first/last write, negative coordinates and hollow tower roof
  skipping, **without claiming a real Minecraft saved-block write**.
- Actual Chongqing Anvil CI already confirmed **130/130 exact overlay Y
  matches on seed 2** and **130/130 on seed 42**, both with 1,570 actual
  generated chunks inspected. That tests real terrain alignment but
  is NOT yet a built-structure acceptance test.

**Release blockers still stand:** graphical game-client GUI clicks, real
NeoForge block placement and reopening a backed-up save, robust FPS
measurements, ODbL legal handling, The Crystal's measured 3D deck,
rivers and complex over/underpasses. **Only the isolated Draft PR changes.**

## Phase 8 — real OSM + LICENSED Copernicus satellite terrain material recognition

User-observed bug: the old standalone city generated grass over essentially
every DEM cell, even on real urban hardscapes, and so looked like a
Minecraft dirt/grass island rather than Chongqing. The real cause is that
the pinned OSM V2 object archive originally filtered out nearly all
surface, land-use, squares and parks.

### Implemented georeferenced source layers

1. The official open OSM 0.6 API is used to fetch separate source-attributed
   real OSM **surface and land-use polygons**, not just lines/buildings.
   `tools/chongqing_osm_surface.py` preserves the raw API response
   SHA-256, correct WGS84 coordinates, ODbL provenance and each
   individual way ID. Exact pavement tags override inferred
   commercial/residential land-use; no undeclared feature is given a fake
   source. Official data verified in the first run: **27,537 1m
   ground locations** tagged or inferred (8,364 grass; 5,113 concrete;
   837 asphalt; 13,223 urban-use low-confidence candidates). A minimum
   of 974,464 cells had no qualifying direct OSM surface polygon.
2. `tools/chongqing_fetch_copernicus.py` queries the PUBLIC
   Microsoft Planetary Computer STAC catalog for an authentic **Copernicus
   Sentinel-2 L2A scene**, reads truly georeferenced green, red, NIR and
   scene-classification GeoTIFF bands, and records the exact source
   scene ID/date/cloud score and open Sentinel use terms. This does
   not use Google Earth, restricted imagery or satellite screen grabs.
   Data and attribution are reproducible at original 10m resolution.
3. `tools/chongqing_copernicus_landcover.py` calculates NDVI and
   checks Sentinel scene classes: healthy green vegetation becomes
   a candidate grass-covered location; confidently detected nonvegetated
   ground receives a **low-confidence generic urban/bare ground
   material**, NOT a claim that it is specifically an asphalt road.
   Cloud, shadow, snow, river/water, uncertain and invalid pixels
   stay unclassified instead of being colored as invented land cover.
   The first public scene classified **300,251** 1m display positions
   from true **10m image measurements** (250,130 low-confidence bare/
   urban and 50,121 vegetation), with many remaining unknown.
   The 1m display is upsampling, NOT 1m imagery detail.
4. In the independent vanilla `region/*.mca` export, color/material
   selection respects a **provenance-based precedence**:
   *actual pinned OSM V2 building/road/rail/reference blocks*
   above *explicit/land-use OSM ground* above *cloud-screened
   open Sentinel-2 broad spectral land cover* above
   *unverified default neutral terrain*.
   Terrain Y still comes from the same checksum-pinned actual DEM,
   not from satellite pixel color.
5. The experimental **standalone 1.21.1 world ZIP is rebuilt and
   real-Java-server-reloaded in CI**. Unit and actual-Anvil NBT tests
   verify source-ground material precedence, license and hashes,
   incomplete cloud masks, all 494,988 GIS writes and the extended
   world height. Original RealScale Java mod and TVA world presets
   remain unaffected.

### What this does NOT solve yet

- Sentinel-2 10m spectral pixels cannot resolve individual sidewalks,
  doors, building walls, paved tile patterns or accurate height.
- OSM `landuse=commercial` alone does NOT reveal real pavement material:
  these blocks are visibly annotated `LOW CONFIDENCE` in the audit.
- The rivers still need accurately sourced and vertically aligned
  water surfaces; their satellite detection alone does not supply a safe
  river surface Y. We must not pour vanilla water at guessed heights.
- True Chongqing building detail, Raffles City's elevated Crystal,
  overpasses, underpasses and tower facades require more open 3D
  geometry, building parts, DSM/LiDAR or independently verified modeling.
  Overture Maps (ODbL) provides building footprints, height and parts
  where available, but quality must be audited before import.
- High-resolution (sub-1m) licensed orthophotography would greatly
  improve pavement recognition. No such image has yet been obtained
  and validated for use and redistribution in this build.

**Never claim this is an exact 1:1 visual reconstruction:** coordinate
scale is 1m per block, but the source resolution and mapping completeness
are independent. Public attribution for the satellite layer:
"Contains modified Copernicus Sentinel data (2025)." Public OSM
attribution: "© OpenStreetMap contributors, ODbL-1.0."
