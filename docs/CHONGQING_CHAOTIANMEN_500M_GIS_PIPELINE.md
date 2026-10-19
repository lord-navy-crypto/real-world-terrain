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
      --output chongqing/data/chaotianmen_500m_2026.osm.json

Review the exact query. Then run without --dry-run. It requests:
1. Closed building footprints (only supported OSM ways).
2. Roads/streets/paths, including their actual line coordinates.
3. Railways, genuine natural=water and waterway=riverbank polygon ways.

Query extends 100 metres outside the target area so features intersecting
its boundary can be clipped properly. Source saved as immutable raw
Overpass JSON plus a provenance JSON containing OSM timestamp and source
query SHA-256; NEVER silently replace a snapshot with new data.

Some public Overpass servers may be slow, unavailable, throttled or block
queries. If this happens **STOP** and use Overpass Turbo's official
export (GeoJSON) into a local file. We do not invent geography or
commit a pretend successful snapshot.

**Not yet supported:** OSM multipolygon relations assembled from split
ways. This limitation matters a lot for the Yangtze and Jialing rivers,
complex station buildings, and plaza areas; those are deliberately not
called "fully rebuilt" here. Split-way geometry needs a future relation
assembler with topology tests or a sourced valid GeoJSON MultiPolygon.
No vector snapshot is bundled in this PR until such a real export is
successfully fetched and verified.

## Step B. Convert to vanilla Minecraft datapack

After obtaining a REAL Overpass JSON/GeoJSON file:

    python3 tools/chongqing_osm_to_minecraft.py \
      --input chongqing/data/chaotianmen_500m_2026.osm.json \
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
  monorail, tunnel or bridge reconstruction.
- Water: only REAL *closed water polygons* become blue **reference blocks**,
  never arbitrary flowing Minecraft water that could flood the whole
  floating island. Do not mistake the blue reference color for finished
  Yangtze/Jialing channel hydrodynamics.
- Complex building relations and exact structure elevations, bridge
  clearance, tunnel geometry, vegetation and true 3D facades remain
  explicitly unknown. We do NOT auto-invent Raffles City or Hongyadong.

## Step C. Preview safely and stage placement

1. Create a **NEW** Special Chongqing world, use Chunky (optional) to
   pregenerate the 1 km pilot. Do not load this datapack into existing
   TVA Sacred or Measured worlds.
2. **Back up your new world save**, and shut down other servers writing it.
3. Copy the exported datapack into that world's datapacks folder, then
   reload datapacks. Check the manifest and command counts first.
4. In an authorized creative/administrative session, execute each line
   under manifest.execute_in_order. Split batches deliberately to prevent
   hundreds of thousands of block placement commands in a single tick.
   Gradually compare rendered Minecraft geometry against the original
   georeferenced OSM and DEM (corner and riverfront alignment). This is
   still a laboratory-grade overlay, not public finished city content.
5. Record side-by-side checks at Chaotianmen Square, riverfront,
   surrounding roads and Raffles City building locations. Reject visible
   street/ground level mismatches instead of forcing random foundations.

The importer NEVER downloads Chunky, starts background servers, writes
world Anvil .mca or modifies saved level.dat.

## Acceptance status

The program and synthetic fixture tests are implemented; fixture geometry
is **only synthetic for QA**. OSM acquisition can be constrained by remote
API rate limits/outages, and the complete real city data is not yet
integrated. The source DEM in PR #54 and its server/worldgen tests are
genuine, but the structure overlay, saved-block Y alignment, river polygons
and 3D landmark models still need real dataset and live Minecraft QA.

Release: Keep Draft; do not merge until *actual* OSM provenance,
multi-geometry parity, river relations, field height checks, the
full worldgen matrix, client FPS and world-save re-entry pass.
