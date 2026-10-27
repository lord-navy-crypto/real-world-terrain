# RealScale seven-mode public experimental test (2026-10-27)

The Create World menu intentionally shows **seven** RealScale choices:

| Choice | World preset | Release status |
|---|---|---|
| Natural Landscapes | `realscale:natural_three_realms` | Original, preserved |
| Everest / Himalaya alpine | `realscale:alpine_three_realms` | Experimental DEM fit |
| Grand Canyon canyons | `realscale:canyon_three_realms` | Experimental DEM fit |
| Yosemite glacial valleys | `realscale:glacial_three_realms` | Experimental DEM fit |
| Hawaiʻi volcanic relief | `realscale:volcanic_three_realms` | Experimental DEM fit |
| Guilin karst | `realscale:karst_three_realms` | Experimental DEM fit |
| Namib dunes | `realscale:dune_three_realms` | Experimental DEM fit |

Natural uses the same **byte-identical world-preset and noise-settings JSON** as the isolated stable candidate. USGS Big Sur is **removed from both new-world menus** because it failed validation. Its historical data resources remain only for existing-world compatibility; the USGS mode is not an offered gameplay choice. The fitted code is present as extra registered behavior; on-device performance still requires testing.

The six fitted modes are **visible** in the standard and extended new-world menu, with clear Experimental / 实验玩法 labels in English and Chinese. Other legacy and opt-in world resources remain available for backwards compatibility but are not additional public menu choices.

## What passed and what did not

At fitted ancestor `f704ce4`, all six fitted servers booted and saved 25-chunk area data. Volcanic passed a strict 180s distant-chunk check. However the full seven-mode persisted heightmap comparator **FAILED** because the dune region did not change any of 6,400 sampled terrain columns compared to Natural; the fixed test location may not exercise its land-dependent fit. This branch is for **player testing, not production terrain-quality certification**.

Offline NeoForge/Chunky launcher and pre-generated chunk controller have been removed from the source history of this testing variant. World Atlas only reads already-saved chunk/heightmap data and does not generate chunks or launch another server. Runtime fixed USGS URL is not embedded. The JAR workflow rejects an unsafe launcher and validates visible preset entries.

## Install and playtest

Download the artifact `RealScale-7-Modes-Public-Experimental-Test-JAR` from the passing **Public seven-mode test JAR** GitHub Actions run, unzip, and copy the single installable JAR into the Minecraft 1.21.1 / NeoForge mods directory. Requires compatible dependencies already specified by the mod manifest and Java 21. Do not install alongside another RealScale JAR.

Use **new worlds only** for fitted modes, back up existing saves, and try more than one seed and known continental/land-heavy regions rather than assuming an ocean-area smoke proves the intended terrain profile. Record startup time, distant chunks, memory, TPS/FPS, and screenshots at X/Z coordinates.

Only USGS menu selection, its directly corresponding tests and JAR verification were changed for this seven-mode test. The separate two-mode release-candidate branch is unchanged.
