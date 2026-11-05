# Research prototypes (not yet integrated)

These folders contain original independent RealScale GeoCore research prototypes recovered from the development environment. They are included to preserve technical work, not to claim these algorithms are enabled in the v1.1 NeoForge JAR.

- **v0.7**: noise terrain, hills, drainage, continuity assertions (standalone Java).
- **v0.8**: geological stream-power, uplift and sediment numerical kernels (standalone Java).
- **v0.9**: regional watershed, connected internal tile drainage, sediment ledger and cache (standalone Java).
- **v1.0**: Java measured-DEM adapter and Python GIS import, source verification and tests. The original USGS response data is in [`../data/usgs-big-sur`](../data/usgs-big-sur). Synthetic GeoTIFF test fixtures are omitted from this source-only migration.

**Important:** v1.1 only integrates USGS density sampling, not all these research models. See each prototype's README.
