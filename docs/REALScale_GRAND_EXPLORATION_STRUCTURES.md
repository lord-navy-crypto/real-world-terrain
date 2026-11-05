# RealScale TVA — Grand Exploration Structures (1.21.1 / NeoForge)

## The problem

RealScale's mountain ranges, wide continents, deep oceans, huge cave
systems and expanded dimensions leave vanilla exploration feeling sparse.
The existing project already includes **opt-in 2x and 3x Overworld random-spread
structure candidate packs**. This phase **adds 4x and 6x**, plus separate
**2x/3x Nether + End** packs. The default mod JAR and existing worlds do not
automatically change.

## Available world datapacks

| Pack | Sets | Intended use |
| --- | ---: | --- |
| RealScale-Vanilla-Structures-2x-EXPERIMENTAL.zip | 12 Overworld | Light |
| RealScale-Vanilla-Structures-3x-EXPERIMENTAL.zip | 12 Overworld | Active |
| RealScale-Vanilla-Structures-4x-EXPERIMENTAL.zip | 12 Overworld | **Recommended** |
| RealScale-Vanilla-Structures-6x-EXPERIMENTAL.zip | 12 Overworld | Epic / busy; test performance |
| RealScale-Nether-End-Structures-2x-EXPERIMENTAL.zip | Nether complexes, End cities | Expanded realms |
| RealScale-Nether-End-Structures-3x-EXPERIMENTAL.zip | Nether complexes, End cities | Very large realms |

Exactly **one** Overworld pack per world; optionally add **one** Nether+End
pack. Those dimensions use different structure-set filenames, so the two
selected packs can coexist. Do not combine 2x, 3x, 4x or 6x Overworld packs
with each other, or stack Nether/End 2x and 3x packs.

The 12 Overworld sets are: villages, ancient cities, desert pyramids, igloos,
jungle temples, ocean monuments, ocean ruins, shipwrecks, swamp huts, trail
ruins, trial chambers, woodland mansions. Relative weights, seeds/salts,
placement types, triangular distribution, and separation remain unchanged.
Nether complexes preserve **fortress:bastion = 2:3** and End cities preserve
triangular spread.

Strongholds remain the vanilla ring-based 128-set distribution; they are NOT
random-spread. Mineshafts and buried treasure use special frequency logic;
outposts have a village exclusion-zone and frequency rule; ruined portals
are shared across Overworld and Nether. They are not touched in this phase
to avoid silently altering progression or multiple dimensions. Village
frequency increases can indirectly influence the *existing outpost
exclusion-zone*, so measure outpost density as well before promotion.

## How the multiplier works

Only random-spread **spacing** is reduced while retaining vanilla
**separation**, `salt`, `structures`, and any original `spread_type`.
A placement grid's candidate count is roughly proportional to
`1 / spacing^2`, not actual built structure density. For example:

| structure set | vanilla spacing | 2x | 3x | 4x | 6x |
| --- | ---: | ---: | ---: | ---: | ---: |
| villages | 34 | 24 | 20 | 17 | 14 |
| shipwrecks | 24 | 17 | 14 | 12 | 10 |
| ancient cities | 24 | 17 | 14 | 12 | 10 |
| woodland mansions | 80 | 57 | 46 | 40 | 33 |
| ocean ruins | 20 | 14 | 12 | 10 | 10 |

The 6x ocean-ruins option is limited to ~4x candidate count by the original
8-chunk minimum separation; actual generated structure count depends on
biome, terrain, suitable placement and collisions. In mountainous RealScale
biomes, the gain may be much lower than the nominal candidate multiplier.

## Installing for a *new* world

Generate the ZIPs:

```sh
python3 scripts/make_structure_density_packs.py --outdir build/structure-packs
```

In the Minecraft Create World screen: Data Packs > Open Pack Folder, copy
the selected ZIP into that folder, enable it and create your RealScale world.
Or for a *new disposable dedicated-server world*, put the chosen ZIP(s) in
the world's `datapacks/` before terrain generation, then enable them.

The overrides are stored at
`data/minecraft/worldgen/structure_set/<vanilla-name>.json`. They apply to
that entire world, including vanilla and mixed mod biomes within it;
**they cannot be scoped to only RealScale terrain just by naming the pack
realscale**. Do not embed them unconditionally in the mod JAR.

For old saves, back up your world. Changing structure_set registry data after
world creation can yield mixed old/new distribution and unexpected travel
results. Prefer a fresh save.

## QA / release gates

1. Check ZIP integrity; 1.21.1 datapack format 48; exact original fields.
2. Verify world loads with only the selected world pack(s).
3. Fixed-seed headless/server 2x/3x/4x/6x runs over the *same area*, recording
   successful starts (not just candidate chunks) by each structure key,
   the number of generated chunks, and near-spawn distances.
4. Verify native structures and seafloor remain untouched, that structures
   avoid severe terrain failures, and village density doesn't wipe outposts
   through vanilla exclusion.
5. Profile newly generated chunks/sec, p95 worldgen latency, peak memory
   and GC vs no extra pack. Do not claim 4x/6x actual buildings until measured.
6. Keep this feature on a separate branch until the full TVA test matrix passes.

Authoritative guidance: NeoForge 1.21.1 data pack rules:
https://docs.neoforged.net/docs/1.21.1/resources/

Vanilla 1.21.1 structure sets:
https://mcasset.cloud/1.21.1/data/minecraft/worldgen/structure_set/
