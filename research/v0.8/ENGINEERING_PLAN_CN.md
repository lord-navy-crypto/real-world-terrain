# RealScale Terrain — 地质驱动地貌引擎路线图（v0.8 research/prototype）

**状态：已研究与编写独立 SI 单位 Java 数值内核；尚未接入 NeoForge / Minecraft 区块生成器。** 这不是新的可安装 Minecraft 模组。原 v0.6 世界/Mod 不变。

## 硬约束和由它推导出的限制

- 每 1 个 Minecraft X/Z/Y 方块表示真实 1 米，绝不纵向压缩，也绝不增加新方块或物品。
- v0.6 / v0.7 海平面 Y=63，法定最高地表 Y=2031，最大海拔仅 **1968 米**，最低建筑高度 Y=-64，只能容纳约 **127 米**的海面以下空间。
- 要让 8,848 米高峰按原尺寸出现，必须达到至少 Y=8911，并重新设计可用的世界构建范围；这应是单独的实验维度，内存/性能成本显著，不要悄悄改变原有预设。
- 原版已有方块是渲染材料，不是地质过程的替代品；真实岩性作为内部参数，最终映射到 stone / granite / andesite / deepslate / tuff / calcite / sandstone / terracotta / gravel / clay / sand / basalt / water / snow 等原版方块。

## 地貌科学模块与落地路径

| 模块 | 物理/地质依据 | 在 Java 中计算 | Minecraft 表达 |
|---|---|---|---|
| 大陆、构造分区 | 大陆地壳与海盆、断裂带 | 米制构造分区、构造边界曲线、带状宽尺度隆起速率 U(x,z) | 大陆与海洋的初始标高 |
| 碰撞型山脉 | 板块挤压、褶皱和逆冲 | 距离板块碰撞带 + 多条平行脊线 + 非对称山麓 | 连绵的山脊与高原边缘 |
| 地堑断块 | 正断层、地垒地堑 | 断层位置、走向、两侧升降场和非对称侵蚀 | 陡峭山前断崖/宽谷盆地 |
| 岩性/地层 | 基岩与沉积层差异、差异侵蚀 | 地层接口函数 + rockResistance(x,y,z) + 断层位移 | 原版岩石的 3D 地层分布 |
| 河流 | 汇水、Priority-Flood、流功率侵蚀 | 基于降水和坡度的 Q [m³/year] / E [m/year]、泥沙 [m³] | 连通的河槽与原版 water/gravel/sand |
| 冰川 | 冰流沿谷地侵蚀、U 形谷等 | 冰川源区阈值 + 流线 + 侵蚀截面核 | 冰斗、宽底谷、冰碛坡和雪/冰 |
| 差异侵蚀 | 硬盖层保护软层 | 层位抗侵蚀系数与倾角影响 | 台地、桌山、阶梯悬崖 |
| 火山 | 盾状、复合火山的喷发形态 | 类型化火山体、喷发时序，侵蚀/火山口 | 原版 basalt/tuff/stone 的火山形态 |
| 喀斯特 | 可溶岩石与渗流 | 可溶岩分区、地下渗透路径、坍塌场 | 落水洞、溶洞、地下泉 |
| 降水与气候 | 地形抬升降水、背风雨影 | 风向+抬升率+大陆水汽输送、海拔温度 | 原版 biome、snow/grass/stone 分布 |
| 三角洲/冲积扇 | 河流输沙与低坡堆积 | 河口坡度、输沙预算，宽度随坡度变化 | 原版 sand/gravel/clay/dirt |

### 代表性方程（不是完整的地球物理求解）

构造—河流地形的演化：

    ∂h/∂t = U(x,z,t) − K(x,z) A(x,z)^m S(x,z)^n + D(x,z,t)

其中 h、U、K、汇水面积 A、坡度 S、沉积 D 的量纲需要严格一致。直接的 K 单位随指数变化；演示内核为了避免隐含单位错误，使用有量纲的参考流量 Qref、坡度 Sref：

    E [m/year] = Kref [m/year] * (Q/Qref)^m * (S/Sref)^n / resistance

    Qlocal [m³/year] = P [m/year] * cellArea [m²] * runoffCoefficient

汇流量按单方向汇流图进行聚合；已侵蚀岩石体积必须等于地表沉积体积 + 域边界输出体积（除非模型显式包括其他质量源汇）。

**注意：** 当前 GeoCore v0.7 的 `rain` 是无量纲的相对降水因子、汇流量是加权面积，而 `riverErosion()` 基于 `log(A)` 给定经验切割深度；它不是具备物理流量与地质时标的模型。本目录新 `GeoPhysicsKernels.java` 是 SI 单位示范核心（具备运行测试），并没有实现所有表中模块。

## 跨分块/跨流域的正确解决思路

现有 v0.7 `Priority-Flood` 是每 4,096 米的 tile 独立向边缘排水。128 米边缘高度混合只能使地表连续，不能令河流的上游汇水量穿过边界持续增加。

提出两级设计：

1. 先在区域级（如 32–64km）生成低分辨率的构造、岩性和地表底图，含 halo；构建区域出口与流域 ID，并储存边界交换的水量和泥沙通量。
2. 计算跨区域的河流骨架与出口连接，采用确定性区域 ID + seed 哈希，显式处理跨区域水量/方向，不能将每个 tile 边缘都当成海洋出口。
3. 之后在 32m / 8m 两级分辨率细化河床与坡面。每个 Minecraft 区块是 16m×16m，最终按 1m 横坐标计算每根方块柱的高度。
4. 支持无穷地图的 *完全全球排水一致性* 是困难的；需确定宏观流域尺度及稳定出口规则，并测试不同生成顺序的一致性。

## NeoForge 1.21.1 的接入方式（设计草案，尚未编译）

- 保留原 v0.6 七种 world presets 不变，新添 `realscale:geology_v08` 世界预设。
- 注册自定义 **ChunkGenerator 的 MapCodec**，这是生成器 *类型* 的注册，不是新方块注册。
- `GeoTerrainEngine` 和新的 `GeoPhysicsKernels` 生成可缓存的 `GeoRegionSnapshot`：

```
record GeoRegionSnapshot(
    HeightField height, RiverField river, GeologicStrata strata,
    ClimateField climate, RegionalOutletGraph outlets) { }
```

- 生成区块时，区块 x,z 在每米坐标取稳定的 `surfaceY(x,z)`；低于地表填入现有岩石方块，高于地表按 seaY=63 填原版 water 或 air；特别处理泥沙与冰川地貌。在洞穴生成时使用三维密度/地层，而不是任意覆盖现有方块。
- **务必将同一地形快照用于 `fillFromNoise`、`getBaseHeight`、`getBaseColumn`，并在 `buildSurface` / carvers / aquifers / heightmap 和结构前处理链中保持一致。** 不能只覆盖 fillFromNoise，否则 Minecraft 结构预测仍可能基于旧噪声地形，建筑悬空。
- 保持原版 biome / structure / placed features，或通过数据驱动重新分配 **已有群系**；BiomeModifier 不能替代地形生成器。
- 版本化 GeoRegion 缓存的关键包括 seed、算法版本、地区坐标、参数 hash；异步生成必须线程安全、结果与生成顺序无关。不要在运行时对已生成旧区块做破坏性回写。
- 最初可先接入地表高度 + 高度查询（不会有结构漂浮），随后分开增加河流流体、洞穴、矿物和岩性层，最后冰川/海岸/其他。

### 我们必须通过的自动验收

A. 多种种子测试高度 Y=-64..2031、无岩浆海、没有地表悬空方块大面积集聚。

B. 流域：每个有明确出口的流域都能汇入海洋或内流湖；湖不能被误当作“数据错误”；跨 tile 的流量守恒，在邻接边界上下游连续。

C. 结构：强制定位/生成村庄、前哨站等结构，读取完整结构实际组件及下方地面支撑方块；若未采样到组件则测试为“未覆盖”，不是通过。

D. 地质：同种子重复生成同高程/岩性/河道；跨边界同一点的结果相同；三维倾斜岩层跨边界连续；不同抗蚀岩层产生不同侵蚀程度。

E. 性能：测量生成 10km²、100km² 的墙钟时间、峰值内存、Java GC、缓存命中率和区块 tick TPS。不在每生成一个 16m 区块时重复处理整个 64km 区域。

F. 对照实测 DEM（USGS 3DEP 1m 局部；NASA SRTM 30m 全球较广区域）比较坡度分布、河网分支、曲率、地形起伏频谱，不只比较最大高度。**将 30m 数据插值到 1m 不会创造真实 1m 细节。**

## 真实数据接入（另一种模式）

- **Real-World DEM 模式**：从 USGS 3DEP 或 NASA SRTM 加载真实高程数据（GeoTIFF / COG），先转换至米制投影与统一高程基准；在地图指定基准点处映射 Minecraft 世界 X/Z 坐标。
- 3DEP 在有覆盖的位置提供 1m 裸地 DEM，NASA SRTM 约 30m 分辨率适合校准大山系形态。将 SRTM 插值至 1m 仅用于显示和过渡，不宣称新增真实高频测量。
- 1:1 坐标（X/Z）与高度（Y）保持米制；若真实最高峰超过 dimension maxY，必须拒绝载入或提示缺失，绝不悄悄压缩。
- USGS 的地质图/三维地层数据可作为岩性、基岩界面的外部参考；DEM 只能给地表高程，不能由高程反推出全部地下地质结构。

## 主要权威资料

- NPS：板块构造地貌 https://www.nps.gov/subjects/geology/tectonic-landforms.htm
- NPS：冰川谷 https://www.nps.gov/articles/ushapedvalleysfjordshangingvalleys.htm
- NPS：差异侵蚀、峡谷 https://www.nps.gov/articles/nps-geodiversity-atlas-canyonlands-national-park.htm
- NPS：喀斯特 https://www.nps.gov/subjects/caves/karst-landscapes.htm
- USGS：火山类型 https://pubs.usgs.gov/gip/volc/types.html
- USGS：3DEP 1m 裸地 DEM https://data.usgs.gov/datacatalog/data/USGS%3A77ae0551-c61e-4979-aedd-d797abdcde0e
- NASA：SRTM 30m https://www.earthdata.nasa.gov/centers/lp-daac
- NOAA：地形降水及雨影 https://forecast.weather.gov/glossary.php?word=rain+shadow
- Barnes et al. 2014，Priority-Flood DOI 10.1016/j.cageo.2013.04.024
- Braun & Willett 2013/2014，流功率地貌演化算法 https://www.sciencedirect.com/science/article/abs/pii/S0169555X12004618
- NPS：第三纪岩层差异侵蚀 https://www.nps.gov/places/differential-erosion.htm
- NeoForge 1.21.1：Biome Modifier https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- Minecraft 1.21.1：ChunkGenerator API https://mappings.dev/1.21.1/net/minecraft/world/level/chunk/ChunkGenerator.html
