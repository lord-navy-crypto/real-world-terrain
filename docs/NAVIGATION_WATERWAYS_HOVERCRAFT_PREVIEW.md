# RealScale TVA Navigable Waterways — experimental preview

## Why

Players reported that using soil/gravel obstacles and conservative bank
containment to preserve water levels can leave mountain rivers too shallow or
disconnected for travel. The reviewed old `RiverChannelFeature` did **not**
place dirt as an artificial dam. It put gravel at the bottom of two-layer stream
columns and rejected some water placements when bank protection failed.
Remaining soil/stone obstacles could originate from the base terrain.

## Changes on this independent branch

1. River-biome surface columns in RealScale-generated worlds now **prefer**
   three water blocks above a single gravel bed when their natural-ground
   foundation is safe. Open riverbanks are permitted to spill through vanilla
   fluid simulation rather than being shut off by the older bank-safety test.
2. Default oceans, sea level 63, aquifers, existing noise settings, Nether, End
   and non-RealScale worlds remain untouched.
3. A new hovercraft gameplay entity/item can be crafted from **one boat plus
   four leather**. It retains familiar boat movement, with a bounded water
   buoyancy assist when submerged and a small lift on wet low banks.
4. This first physics prototype uses the vanilla oak boat renderer; the new
   item uses the vanilla boat inventory sprite.

## Recipe

```text
[ ] [Leather] [ ]
[Leather] [Boat] [Leather]
[ ] [Leather] [ ]
```

## Important limits and QA

- Three-block water columns do **not** automatically guarantee an uninterrupted
  or wide shipping channel in every biome. Sheer cliffs, narrow rivers,
  inconsistent base terrain and chunk boundaries still need in-game testing.
- Hovercraft cannot traverse sealed stone/dirt dams or climb arbitrarily high
  waterfalls. The buoyancy control needs server-client and mount/dismount testing.
- Natural floodwater may increase fluid updates and server tick costs. Benchmark
  chunk-generation time, fluid update/tick cost and saved region water spread.
- The change intentionally affects **newly generated chunks**. Never test by
  replacing a mod in an irreplaceable old world; mixed old/new chunks can show
  transitions. Use a fresh world and a copied save.
- Existing river guards remain as fallback when a column is unsuitable for
  navigation-first carving. They are not waterway infrastructure.
- Static tests, Java/Gradle build and actual server worldgen checks are gates,
  not proof of real-time boat navigability.
- This branch is experimental and must not be merged until the smoke gates,
  real server checks, river/water continuity tests and gameplay tests pass.
