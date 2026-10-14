# RealScale Grand Dimensions — Phase III satellite gallery network

Status: **opt-in experimental**. Based on the fully CI-passing Phase II branch; does not change stable `main`. Preset remains `realscale:realism_max_three_realms`. Minecraft Java 1.21.1, Java 21, NeoForge 21.1.x.

## New spatial structure

Each existing 256×256 macrocell contains **two existing giant halls** near Y205/Y348 plus **two small satellite chambers per hall** (four additional chambers per macrocell). Side chambers are ellipsoids with horizontal semiaxes **14–21 blocks** and vertical semiaxes **11–16 blocks**. Each connects directly to its same-layer main hall via a roughly **14-block-diameter** curving gallery.

The side chamber locations use the macrocell X/Z/layer hash to choose opposite diagonals of the main hall, with XY offsets constrained to ±50–56 blocks. This gives each cell a compact hub-and-spoke chamber system *in addition to* the original four-direction giant tunnel network. Gallery shapes are evaluated as continuous density, rather than placing block shells afterward.

### Continuity guarantees and performance

- Satellite rooms and galleries stay at least eight raw blocks inside their owning macrocell; the existing seeded noise warp of at most five horizontal blocks is not enough to pull them across a neighboring cell edge.
- Elevations respect existing network excavation bounds **Y144–440**; the original Nether lava and roof remain unchanged by these extras.
- Secondary galleries are only evaluated when the sampled position lies inside a conservative **±80 X/Z and ±30 Y** envelope around the parent hall. Cache remains thread-local, deterministic and independent of chunk generation order.
- Existing central halls and inter-macrocell paths were left intact. The geometric satellite positions themselves are **coordinate-hashed, not world-seed randomized**; the enclosing terrain and cave-wall noise warp remain seed dependent.
- This is not yet a realistic geological material simulation, low-Nether cave entrance system, or proof that vanilla structures always place intact.

## Checks

- Pure Java smoke test checks satellite core voids, axial distance from chunk-cell boundaries, upper/lower Nether protections, and multi-step branch continuity for multiple positive and negative macrocells.
- Dedicated NeoForge server force-loads the four satellite chamber centers and their four connector midpoints in cell (0,0), saves actual Anvil chunks and verifies each contains air.
- Existing tests continue checking 512-block Nether and End, both raised End island strata, the unmodified dragon center, the lava ocean and extended bedrock roof.

### Manual future gates

Long-distance flood-fill traversal on saved worlds, multiple seeds, fortress/bastion interiors, dragon gateways and cities, stable frame times, and multi-threaded generation under load. Use a **new disposable test world**: height settings and cave terrain are not safe for established saves.
