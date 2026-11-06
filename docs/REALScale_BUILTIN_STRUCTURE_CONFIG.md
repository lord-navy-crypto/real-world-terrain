# RealScale — Built-in vanilla structure density and architecture activation

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

## In-game configuration

1. Install RealScale and YACL 3.8.1+ in the same client profile.
2. Start Minecraft and open **Mods > RealScale > Config**.
3. Open **Built-in Vanilla Structures**.
4. Choose the Overworld density (Vanilla 0x, 2x, 3x, 4x, or 6x).
5. Choose the Nether + End density (Vanilla 0x, 2x, or 3x).
6. Leave **Enable Architecture Library (100 Structures)** on to include the 100-building pack in newly created worlds. Turn it off if that world should not use the architecture library.
7. Save, fully exit Minecraft, restart, and then create a NEW world.

The architecture checkbox defaults to ON. The pack is still optional, not mandatory: it is selected as a default for new worlds, and players can deselect it in **Create World > Data Packs** before creating the world.

The settings are stored in:

`config/realscale-terrain-lab.json`

The registrar reads `structureDensity`, `dimensionStructureDensity`, and `architectureStructuresEnabled`. If the new architecture field is missing in an older config, it safely migrates to ON. A malformed config fails closed to vanilla structure densities and leaves the architecture pack disabled.

## What gets enabled

- The Overworld and Nether/End selectors choose the matching built-in vanilla structure-density packs.
- The architecture checkbox selects `realscale_structures_architecture_pilot`, containing all 100 licensed/original structure definitions, template pools, biome tags, structure sets, and NBT templates.
- The pack is discovered through NeoForge's `AddPackFindersEvent` as a built-in `SERVER_DATA` pack.
- The pack is marked non-mandatory. `PackSource.DEFAULT` means selected by default for a new world; it does not force-enable the pack in an existing save.

The NBT templates remain available in the mod namespace for explicit placement even when the optional worldgen pack is not selected. Natural structure generation requires the architecture pack to be enabled when the world is created.

## Important world-save behaviour

1. These are **next-world defaults**, not in-place worldgen changes.
2. Existing saves keep their recorded datapack selections.
3. Structure sets in the vanilla namespace can affect their target dimensions globally, not only RealScale biomes.
4. Do not enable competing Overworld density packs at the same time.
5. Back up a test world before changing its datapack selections; unexplored chunks can be affected.

## Developer validation

```sh
cd neoforge
gradle --no-daemon build
cd ..
python3 -m unittest discover -s qa -p test_builtin_structure_packs.py -v
python3 -m unittest discover -s qa -p test_architecture_pilot_pack.py -v
```

The tests verify that the checkbox, config migration, built-in pack registration, and packaged architecture resources stay connected. Dedicated-server acceptance is still required to prove natural generation in a playable world.
