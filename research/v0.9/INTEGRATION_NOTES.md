# NeoForge 1.21.1 integration contract (not compiled against Minecraft)

Keep the scientific model and Minecraft loader separate. The v0.9 Java source is a pure-Java library that can be copied into a NeoForge Java source-set, but **is not a registered ChunkGenerator by itself**.

Mojang 1.21.1 `ChunkGenerator` required signatures (see https://mappings.dev/1.21.1/net/minecraft/world/level/chunk/ChunkGenerator.html):

- `protected MapCodec<? extends ChunkGenerator> codec()`
- `CompletableFuture<ChunkAccess> fillFromNoise(Blender,RandomState,StructureManager,ChunkAccess)`
- `int getBaseHeight(int x,int z,Heightmap.Types,LevelHeightAccessor,RandomState)`
- `NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor,RandomState)`
- `void buildSurface(WorldGenRegion,StructureManager,RandomState,ChunkAccess)`
- `void applyCarvers(WorldGenRegion,long,RandomState,BiomeManager,StructureManager,ChunkAccess,GenerationStep.Carving)`
- `int getSeaLevel()`, `int getMinY()`, `int getGenDepth()`, `void spawnOriginalMobs(WorldGenRegion)`, `void addDebugScreenInfo(List<String>,RandomState,BlockPos)`.

Exactly one `GeoSnapshotCache` should be owned by a configured generator instance. Select macro-region and seed explicitly. **Current macro-region independent boundaries are not safe to use for a seamless unlimited world**: either lock worldgen temporarily to a bounded experimental testing area or first implement cross-macro outlets and halo blending; do not hide this defect.

Within a supported macro-region, map 1x1 meter `VanillaColumnAdapter.column(snapshot, geology, x, z)` to existing `Blocks.STONE`, `Blocks.DIRT`, `Blocks.WATER`... BlockStates. For structure heightmap `Heightmap.Types.OCEAN_FLOOR_WG`, return `oceanFloorFirstFreeY`; for `WORLD_SURFACE_WG`, return `worldSurfaceFirstFreeY`. Populate actual `ChunkAccess` block states using the same column. **Biome selection must be implemented**: this pure-Java library does not choose biomes or caves.

Do not register any blocks/items. Add a generator CODEC and world preset ONLY after validation and smoke testing; keep v0.6 presets unchanged. 

Required GameTests: true 1.21.1 loader can decode/encode new world preset; 3+ seeds create actual chunks; `getBaseHeight` agrees with all placed columns; village/temple/pillager structures physically inspect foundation blocks; water at sea level behaves; 32km region edges match; every registered resource is from `minecraft:*` or the generator codec (not new blocks).
