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
  approx Y=floor(real DEM elevation +96)+1 for overlays. Block placement
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
3. Place the generated `chongqing-gis-plan.json` file at
   `config/realscale/chaotianmen-gis-plan.json` in the Minecraft game
   directory. This is a data import, not a command script. No arbitrary
   file locations or executable commands are read by the GUI.
4. In **integrated singleplayer Creative** with the mod installed, press
   **G** to open `Special Chongqing | GIS Placement Lab`:
   - **Load & verify source plan**: validates coordinate origin, source
     checksum format, exact DEM SHA, real DEM Y for each planned block,
     allowed materials, source geometry bounds, sorted/nonoverlapping rows,
     <=5 million pilot writes and row-count integrity; does NOT place blocks.
      The current real OSM plan totals 4,033,166 writes.
   - **I backed up this NEW world**: deliberate user confirmation. This is
     an acknowledgment, not a technical check that a backup exists.
   - **Start / Pause 256 blocks per tick**: after explicit confirmation,
     uses the integrated server tick event to place at most 256 blocks/tick
     when that tick has spare time. No Minecraft `/function`, chat or
     console commands are involved. Click again to PAUSE.
     **Closing the GUI or pressing ESC also PAUSES placement immediately**.
     Progress is retained while this integrated server instance lives;
     restarting the game is NOT yet a persistent resumable installer.
   - **Close / Pause**: stops further automatic changes immediately and
     requires a newly acknowledged backup before another GUI run.
5. The native executor *refuses* dedicated/multiplayer sessions, player
   non-Creative mode, every other dimension, and every noise generator
   except `realscale:special_chongqing_sky`. It refuses unloaded chunks
   and protected collisions; it never downloads data at game startup.
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
