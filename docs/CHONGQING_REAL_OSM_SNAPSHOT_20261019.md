# Authentic OSM dataset — Chaotianmen 500 m pilot (2026-10-19)

## Evidence and immutable provenance

**This dataset was successfully fetched from the real OpenStreetMap
production API, not drawn by AI or invented from satellite photos.**

- Geographic origin: **29.5667° N, 106.5874° E** as pinned in the original
  CQDEM1 dataset. World X east, Z south, 1 horizontal metre = 1 block.
- Official download endpoint: `https://api.openstreetmap.org/api/0.6/map`
  with the exact source bounding box:
  `106.58115,29.56125,106.59365,29.57215` (W,S,E,N).
- Source result: HTTP 200, genuine OSM 0.6 XML.
- Raw OSM XML length: **1,391,754 bytes**.
- Original XML SHA-256:
  `7a2e4d777de05efe5fc46b889fca3d86ff47981d6d9bab2cf940188790022d31`.
- License/credit: **© OpenStreetMap contributors, ODbL 1.0**.
- Archived derived source file:
  `chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64`.
  It contains exact node-derived coordinates, original OSM way/relation IDs,
  tags and source metadata, encoded with deterministic gzip+base64. No
  invented building positions or hand-drawn river outlines.
- This is an OSM map-API **spatial extract**, not a time-travel archival API:
  retrieving the same URL on a later day may produce new bytes and a
  different checksum. Do not silently overwrite the pinned dataset.

See <https://wiki.openstreetmap.org/wiki/API_v0.6> for public OSM
API semantics and <https://www.openstreetmap.org/copyright> for ODbL.

## Source and GIS audit results

| Measure | Recorded output |
| --- | ---: |
| Original source ways parsed | 386 |
| Source elements with supported map tags archived | 334 |
| Building-tagged elements in archived source | 79 |
| Road-tagged elements in archived source | 220 |
| Railway-tagged elements in archived source | 33 |
| Water-category geometries in archived source | 2 |
| Source features whose projected BBOX intersects ±500 m | 205 |
| Nearby building features | 43 |
| Nearby road features | 137 |
| Nearby railway features | 23 |
| Nearby water features | 2 |
| Building features requiring explicit vertical placement | 7 |
| Multilevel transport elements deferred during conversion | 71 |

Counts are OSM **elements**, not independent towers, lanes, river reaches
or cadastral properties. Real-world OSM accuracy and coverage remain uneven.

Authentic SVG preview:
`docs/assets/chaotianmen_500m_authentic_osm_20261019.svg`
(produced offline directly from this archived vector dataset, without
Google imagery or synthetic test shapes).

## Named actual OSM building footprint candidates

| Landmark candidate | Native OSM way ID | Important source tags |
| --- | --- | --- |
| 水晶连廊 — The Crystal | `way/661717188` | `height=276.5`, `min_height=250`, `layer=1` |
| T2 塔楼 | `way/661717190` | `height=250` |
| T3S 塔楼 | `way/661717191` | `height=250` |
| T3N 塔楼 | `way/748910916` | `height=350` |
| T4N 塔楼 | `way/748910917` | `height=350` |
| T4S 塔楼 | `way/748910918` | `height=250` |
| T5 塔楼 | `way/748910919` | `height=250` |
| T6 塔楼 | `way/748910921` | `height=250` |
| 朝天门广场 | `way/230583762` | `building=yes`, `layer=-1` |
| 重庆来福士广场 | `way/1307164454` | `building=commercial`, `layer=-1` |

An OSM tag can identify a useful true source polygon without proving
its surveyed altitude or full architecture. **No separately named T1 tower
footprint was verified in this particular source extract.** Do not
invent it or treat T2/T3N/etc. as duplicates to make the count eight.

**Non-negotiable 3D safeguard:** The Crystal's minimum 250-m height and
nonzero layer prevent this pipeline from generating it as a solid
276-m-high building from terrain level. The two named underground-layer
plaza objects are similarly **deferred** until a real structural datum
is obtained. This is intentional, not an export bug.

Note: Yangtze/Jialing banks need field checks. Some OSM street names
contain `长江`, which is not evidence of river polygon geometry.
River landmark matching in the GIS audit is restricted to **water
geometries**, not similarly named roads.

## First real full-area export result

Workflow: `.github/workflows/chongqing-real-gis.yml`.
First fully successful real dataset conversion was on
<https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39224355947>.

| Measured metric | Result |
| --- | ---: |
| Actual geographic features producing blocks | 161 |
| Bounded run-length placement rows | 32,403 |
| Planned UI block writes (including schematic tower mass) | 4,033,166 |
| Optional legacy function commands (not player workflow) | 44,606 |
| Ground-unsupported multilevel transport | 71 |
| Building geometries held for 3D survey | 7 |

The **native UI hard cap was raised from 2,000,000 to 5,000,000 writes**
only after observing this real-source result. Blocks are executed directly
by the NeoForge integrated server, explicitly from a GUI Start/Pause
control, throttled to <=256 writes per tick when time remains.
Minecraft command strings are **not** the player interaction protocol.

The workflow publishes a downloadable `real-chaotianmen-500m-gis-ui-plan`
artifact containing: `plan.svg`, `audit.json`, `manifest.json`,
and the native `chongqing-gis-plan.json`.

## Remaining acceptance gates — do not call this completed city

1. A test client must load a disposable **new Special Chongqing world**,
   show G-key GUI, load valid source, reject foreign TVA worlds and verify
   backup acknowledgment/pause behavior. No existing world modifications.
2. Run a bounded stage against real saved blocks, inspect world Y,
   shoreline, structures, stalled tick time and re-entry after backup.
3. Resolve river water levels, bridge decks, tunnels, podiums and The
   Crystal elevation, without inventing missing geometry.
4. Source the missing eighth tower footprint separately or validate it
   is actually represented under another source tag/part.
5. Confirm ODbL redistribution obligations and run final exact-head
   worldgen/multiseed/client-FPS QA.

**Release state: Draft. No merge to main. No claim of actual live 3D
reconstruction or verified in-game placement yet.**
