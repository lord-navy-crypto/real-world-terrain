# Stable two-preset terrain build

This branch is the conservative playable candidate for RealScale. Its packaged JAR
exposes exactly these two custom world presets:

- `realscale:natural_three_realms` — RealScale Natural Terrain
- `realscale:usgs_big_sur_v2` — USGS Big Sur measured terrain

The Gradle `check` task verifies the packaged JAR contains exactly those two
`world_preset` entries and that both required noise settings are packaged.
The stable GitHub Actions workflow then boots a real NeoForge server for each
preset, requests a previously unexplored distant chunk, enforces the existing
180-second generation budget, and saves timing/log/Anvil evidence.

The experimental terrain catalog has been preserved on
`experimental/terrain-modes-20261026` and
`feature/terrain-archetype-fit-universal-layers-20261026`. The stable JAR filter
does not delete those source files or change the experimental branch.

A passing static build is not enough to declare either mode fast: compare the
actual server timing artifacts for Natural and USGS before claiming a winner.
