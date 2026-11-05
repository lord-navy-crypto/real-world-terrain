# Biome expansion v0.2: registration vs actual world generation

Eight new candidate biomes added to the prior four:
- deep_fungal_caverns, dry_karst_caves, underground_springs, volcanic_caverns, glacial_subsurface
- forest_steppe, marshy_woodland, rocky_alpine_meadow

**All 12 are biome registry JSON scaffolds, not active natural worldgen biomes.** Current vanilla multi-noise overworld preset cannot automatically select them. Do not advertise them as enabled or server-tested. Empty decoration arrays mean they are not finished ecosystems.

## CaveHabitatMath
The new pure Java classifier implements preliminary subterranean depth, cave-air, moisture, geology, lava, spring and cold checks. It does not hook Minecraft biome-source sampling. Selectors should use stable coordinate-based environmental fields rather than checking already-placed blocks during biome selection, to avoid cyclic world generation dependencies.

## Critical cave generator repair scope
Minecraft noise caves are determined by the chunk generator noise router. Biome modifiers that add legacy carvers cannot repair noise caves. Cave geometry, aquifers and rivers must be tested in isolation. No density router, aquifer settings, surface rules, or default world preset were modified in this series.

## Next implementation
1. Provide custom BiomeSource MapCodec registered to the correct 1.21.1 registry and serialize a stable climate sampler.
2. Wrap native overworld biome selection without querying generated chunks; compute habitat compatibility and distance-to-climate-boundary from climate samples.
3. Dedicated opt-in preset to avoid changing existing world behavior.
4. Wire 3D cave designation from sampling inputs available during biome selection; not a post-generation block state scan.
5. Populate biome feature steps with robust vanilla references, creature spawning profiles and compatible surface rules.
6. Test registry decoding, integration, /locate biome, multi-seed edge widths, cave Y continuity, server chunk budget and save/restart persistence.

References:
https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
https://minecraft.wiki/w/Custom_biome
