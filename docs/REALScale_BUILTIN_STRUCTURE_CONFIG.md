# RealScale TVA — Built-in vanilla structure density + configuration

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

## In-game Config entry (client)

1. Install **RealScale TVA** and **YACL v3 3.8.1+** in the same client profile.
2. Start Minecraft. Open **Mods > RealScale TVA > Config**.
3. Select **Built-in Vanilla Structures**.
4. Choose **Overworld Structures**: Vanilla (0x), 2x, 3x, **4x default**, 6x.
5. Choose **Nether + End Structures**: Vanilla (0x), **2x default**, 3x.
6. **Save**, fully close Minecraft and restart **before creating a new world**.

The Config menu is backed by the existing registered NeoForge
`IConfigScreenFactory` extension. The selected values are saved together
with the existing Terrain Lab draft:

`config/realscale-terrain-lab.json`

Values read by the pack registrar: `structureDensity` and
`dimensionStructureDensity`. **No exporter or external ZIP is needed for
structure density.** Existing Lab terrain-tuning changes still require their
previous dedicated next-world exporter; this update does NOT claim to apply
new density functions from the menu directly.

The mod JAR now contains all six complete built-in datapacks. The
`AddPackFindersEvent` registers them in the normal Create World > Data Packs
screen, and chooses the Config-selected Overworld and Nether/End packs as
**default enabled** for a NEW world. They are **not forced active**; the
player can deselect or replace them before world creation. If you wish to
avoid density modifications entirely, set both selectors to 0, Save,
restart, and verify the Create World Data Packs list.

A dedicated server can set the same values in
`config/realscale-terrain-lab.json` before starting a **new** world (YACL
itself is client-only), or choose the built-in pack selection at world
creation through its datapack settings.

## Critical safety / registry semantics

1. Datapack choices become part of the world save. The Mod menu is a
   **next-world default**, not a command that mutates already generated
   chunks, and selecting another value is not an in-place migration plan.
2. The selected structure_set overrides use the vanilla `minecraft:`
   namespace within their own embedded pack roots. They apply to those
   **entire dimensions**, not only RealScale biome areas. Do not stack the
   standalone density ZIPs with these built-in packs, and never enable
   competing Overworld 2x/3x/4x/6x presets in the same world.
3. The 12 overworld structure sets, and Nether complexes/End cities,
   retain vanilla weights, salts, separation and spread type. Candidate
   frequency is higher but successful structure counts are not guaranteed.
4. Strongholds, buried treasure, mineshafts, outposts and ruined portals
   continue using their original placement rules; changing village density
   may still affect vanilla outpost exclusions.
5. Changing a pack selection for an existing save may affect unexplored
   chunks and structure locating behaviour; back up the world before
   experimenting. Enable new densities when creating a new test world.

## Developer validation

```sh
cd neoforge
gradle --no-daemon build
cd ..
python3 -m unittest discover -s qa -p test_builtin_structure_packs.py -v
```

The CI test checks the Gradle-produced JAR contains all six embedded packs,
that their structure_set JSONs match independent 1.21.1 reference
generators, and that default pack selection reads the same config fields
shown by the YACL menu.

**Limitations:** Java compile + resource/JSON tests do not alone prove the
Minecraft Mods screen renders correctly or that all structures can actually
generate on every RealScale biome. Verify the GUI, restart behaviour,
datapack auto-selection, new-world server load and actual structure starts
in a playable client/server before marking this feature stable.

NeoForge 1.21.1 API references:
- `IConfigScreenFactory` — Mod list config entry.
- `AddPackFindersEvent.addPackFinders` — built-in SERVER_DATA packs.
