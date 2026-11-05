# RealScale Terrain — GeoEngine v0.9：区域级真实河网原型

**状态：Java 21 独立数值核心已编译、通过自动测试与区域压力测试。不是可直接放进 Minecraft mods 文件夹的 NeoForge 模组。**

## 工作成果

1. **32.8×32.8 km 区域级 D8 流域图**：区域内的 4,096m 地形瓦片不再分别“排水到自己的边缘”。跨内部 tile 的汇流量由整个区域统一计算，结果和生成区块先后顺序无关。
2. **Priority-Flood 和汇流守恒**：只将从宏区域边界能够抵达的海平面以下区域定义为海洋；被地形包围的下沉区视为内陆湖盆候选。输出每个网格的有向下游节点、流域出口、流量 [m³/年] 与侵蚀深度 [m]。
3. **岩性影响下的水力侵蚀**：基于简化流功率模型，利用参照流量和坡度形成无量纲比值，按地层阻抗改变侵蚀强度，强制数值稳定截断。
4. **沉积账本**：侵蚀体积 [m³] = 沉积体积 [m³] + 流域出口输沙 [m³]，数值校验在每次模拟后执行。
5. **真实 1m 方块高度与共享结构高度**：保留 v0.7 原有米制细节函数，将粗网格算出的水文改变量内插回每个 Minecraft 1m 方块列；地表、WORLD_SURFACE 与 OCEAN_FLOOR 高度来自同一模型。
6. **仅用已有 Minecraft 方块**：代码中的方块 ID 为 `minecraft:stone`、`minecraft:granite`、`minecraft:deepslate`、`minecraft:dirt`、`minecraft:grass_block`、`minecraft:snow_block`、`minecraft:gravel`、`minecraft:sand`、`minecraft:clay`、`minecraft:water` 等原版方块，无任何新方块注册。
7. **有界区域缓存**：按 seed、区域坐标、网格尺寸、算法版本和所有物理参数共同索引；测试了并发读取及 LRU 淘汰后重建。

## 关键单位及边界

- X/Z 及高度坐标：**1 方块=1 米**。无垂直压缩。
- 模型采样网格：64 米/格（仅用于流域计算），最终可以按 1 米采样方块高度。这 **不是** 64m DEM 变成了真实测量的 1m 数据。
- 默认海平面：**Y=63**；当前最低 Y=-64，最高可放置 Y=2031。海拔范围相对 sea level 63 为 -127m 至 +1968m；不能容纳现实海拔超过 1968m 的山峰，除非另行提升 dimension height。
- 区域独立边界：**当前解决的是一个 macro-region 内部的跨 tile 河流连续性；两个独立 macro-region 之间还没有统一的流量交换、分水岭以及端点匹配。** 不允许宣称“无限 Minecraft 地图全部河流连通”。
- `river`、`pond`、`ocean` 是示范算法分类；没有完整沉积物颗粒级、多相水动力学模型。**没有运行 Minecraft 1.21.1 客户端或 NeoForge GameTest 验证此 v0.9 代码**。

## 目录

- `src/main/java/org/realscale/geo/GeoTerrainEngine.java`：v0.7 原始地貌函数 + 原始高程查询。
- `GeoPhysicsKernels.java`：来自 v0.8 的 SI 单位地质演化内核。
- `RegionalWatershed.java`：v0.9 宏流域、海陆连通、D8 河流、简化侵蚀/沉积及水量守恒。
- `GeoMacroFactory.java`：按 seed + 位置生成区域高程、降水、岩性、隆起参数。
- `VanillaColumnAdapter.java`：在 1m 坐标上生成原版方块列与共享高度查询。
- `GeoSnapshotCache.java`：宏区域结果缓存与并发防重算。
- `GeoRegionCli.java`：实际输出一块 16×16 Minecraft 参考方块列 CSV、宏区域水文指标。
- `src/test/.../RegionalWatershedTests.java`：自动化回归测试。

## 如何运行（Java 21）

```bash
unzip RealScale_GeoEngine_v0.9_Source_And_Tests.zip
cd RealScale_GeoEngine_v0.9
bash build.sh
# 或自行指定地形种子、64m 网格数、输出目录、米制原点坐标
java -jar dist/RealScale_GeoCore_v0.9.jar 785312 512 dist/benchmark_32km -16384 -16384
```

运行输出文件：`dist/sample_785312/chunk_16x16_columns.csv`、`region_report.json`。

## 实测与性能

独立 Java 编译和测试通过 **167,892 条断言**（包括所有节点归属流域、流量守恒、泥沙守恒、地表世界高度、1m 列实际材料和结构高度一致、不同种子确定性、瓦片边界连续性、并发缓存等）。

32.8 公里 × 32.8 公里区域（512×512、水文分辨率 64m）：
- 跨 4096m 内部分块的 D8 路径：3441 条；河道节点：662。
- 每年区域净汇流：约 6.33×10^8 m³；入口/出口流量误差在浮点容差内。
- 侵蚀：5,009,550m³；沉积：2,744,407m³；出口输沙：2,265,143m³，账本守恒。
- 用 Java 21 在当前容器测得这一次 `build input + solve` 的墙钟时间约 0.61 秒。**不是 Minecraft 生成区块 FPS/TPS 基准，也不能直接外推所有电脑。**

## 真实 NeoForge 1.21.1 集成尚需做什么？

要安装进游戏，必须把 `RegionalWatershed.Result` 映射到真正的 `ChunkGenerator`、通过 `MapCodec` 注册、在 `fillFromNoise` 中按 1m 输出块体，并复用同一快照实现 `getBaseHeight` / `getBaseColumn`，然后处理原版群系、地表规则、结构、洞穴、地下含水层及跨宏区域端点。内存压力也必须实测：**不能在生成每一个 16×16 区块时求解 512×512 流域**。

官方接口参考： https://mappings.dev/1.21.1/net/minecraft/world/level/chunk/ChunkGenerator.html

参考地貌学：Barnes et al. 2014 Priority-Flood, https://doi.org/10.1016/j.cageo.2013.04.024

此代码没有复制第三方模组的实现，仅借鉴公开的算法原理。
