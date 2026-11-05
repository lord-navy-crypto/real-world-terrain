# RealScale Architecture Library — Expansion 2

Twenty original designs are added in this batch:

- Stone mason workshop
- Canal lock
- Monastery chapel
- Apiary garden
- Snowbound storehouse
- Desert cistern
- Tropical boardwalk house
- Railway signal box
- Quarry shed
- Charcoal kiln
- Vineyard estate
- Sheepfold ranch
- Charcoal burners camp
- Fishing village compound
- Hilltop manor
- Frontier fort
- Railway depot
- Orchard cider estate
- Riverside sawmill
- Marsh reed hamlet

The batch adds ten new multi-building compounds, bringing the full catalog to fifteen compounds. The complete architecture library has 58 templates: 55 original designs and three individually licensed Apache-2.0 imports.

All templates are generated from source-controlled block-layout rules. NBT templates live in the mod's base data namespace; biome tags and worldgen registration remain in the explicitly optional architecture data pack.

The dedicated-server smoke test checks whether the optional pack is already enabled or needs enabling, then places each template sequentially in a bounded 64×64 chunk-ticket window and releases that ticket before moving to the next template.

The second expansion remains a development candidate until the full static build and dedicated-server placement checks pass.
