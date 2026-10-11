# RealScale GeoCore v0.7 — Java 地貌计算核心（技术验证版）

**适用方向：Minecraft Java 1.21.1 / NeoForge 21.1.x / RealScale Terrain。**

当前交付的是 **Java 21、无第三方库的可执行地貌算法原型**；**不是**可直接放入 `run/mods` 的 NeoForge 模组。它不注册新的方块、物品、生物或群系，也**不会自动改变**已经安装的 RealScale v0.6 世界预设。原版地表方块和世界预设完全未修改。

## 已实际实现的算法

1. **严格单位**：世界采样坐标 `1 Minecraft block = 1 m`；`heightAt(x,z)` 返回米。计算水文采用 `32m × 32m` 栅格（加密到每米将显著增加成本）。
2. **多尺度地貌**：大陆宽缓起伏、域扭曲（domain warp）、主山脊、次山脊、山麓、小尺度地形噪声。`rawHeight` 连续且按种子确定。
3. **热侵蚀/坡面重分配**：局部陡坡输送等量高度材料到相邻低地（离散近似，不是完整的地质力学模拟）。
4. **Priority-Flood**：低洼填平、D8 单向流向树和降水加权汇水面积，内存中每个地形瓦片全覆盖；没有循环且每个网格都排水到该瓦片边界。
5. **河道切割**：汇流面积决定河道识别与切割深度；为宽度约 1–4 个粗网格的局部近似。
6. **简化沉积预算**：下游海岸低坡区域可沉积部分泥沙，余量从瓦片出口输出，侵蚀量 = 沉积量 + 出口量（使用 32m×32m 网格折算体积 m³，不是完整输沙方程）。
7. **跨瓦片高度采样**：瓦片包括 16 格水文栅格光环（512m），区界 128m 平滑混合，计算结果不依赖采样调用顺序。缓存可设置大小。
8. **输出检查**：数值 CSV、灰度 PGM、高度与水文报告 JSON；可直接执行本地测试。没有任何新的 Minecraft 方块 ID。

## 测试与构建

需要 `Java 21`，运行：

```bash
./build.sh
```

程序会依次编译源代码、执行 `GeoTests`，打包 `dist/RealScale_GeoCore_v0.7.jar`，然后对种子 `785312` 生成数值数据和报告。

自定义种子 / 中心位置，单位均为米：

```bash
java -Xmx1g -jar dist/RealScale_GeoCore_v0.7.jar 42 8192 4096 dist/seed42
```

主要文件：

- `src/main/java/org/realscale/geo/GeoTerrainEngine.java`：地形 + 水文核心。
- `src/main/java/org/realscale/geo/GeoCli.java`：测试输出工具。
- `src/test/java/org/realscale/geo/GeoTests.java`：可重复验证的无依赖测试。
- `dist/sample-785312/geo_qa_report.json`：数值测试证据。
- `dist/sample-785312/terrain_samples.csv`：二维高度和降水加权汇水量。

## 不应误解为已经解决的问题

- **尚未接入 NeoForge 的自定义 `ChunkGenerator`**，没有执行 Minecraft 的 `fillFromNoise`、`getBaseHeight`、`getBaseColumn` 或 GameTest。**不要将此 JAR 放到 mods 目录**。
- 目前采用瓦片内汇流至本瓦片边界，**跨多个瓦片的河网连续性尚未严格保证**。区界高度是连续的，但河流连接仍是下一阶段要解决的独立问题。
- 还没有真正的板块物理计算、冰川动力学或守恒流体求解，也不保证跨地形群系匹配、原版村庄基础落地或洞穴生成。
- 仅使用纯数值地形，不生成或替换任何方块。真正的方块材质沿用原版/现有 RealScale 配置，后续 Java 生成器必须调用原版地表规则。
- 本次地貌高度安全区间为 -28～1940m，和主世界维度 -64～2031 相容。**即使 1格=1米，当前高度上限也无法表现 8849 米的珠穆朗玛峰。**

## 与 Minecraft / NeoForge 正确集成的契约

1. 在 NeoForge 1.21.1 注册独立的 `ChunkGenerator` 类型（`MapCodec`），旧 RealScale v0.6 预设不改；新预设使用该生成器。
2. 同一实例的 `GeoTerrainEngine.heightAt(x,z)` 必须同时用于生成方块列、`getBaseHeight` 与 `getBaseColumn`，确保结构高度预测一致；这些 API 仍需实际实现和引擎测试。
3. 在 `minY=-64` 至 `maxY=2031` 的合法区间里，仅为 `y <= floor(heightAt(x,z))` 设置原版地形实体方块。海面是 `Y=63` 的原版水方块；不得创建任何新的方块或注册表条目。
4. 保留原版生物群系与地表规则、结构高度图、流体和洞穴语义；真实河道设计还要与 Minecraft 水平/纵向流体系统整合。
5. 将地貌缓存、远距离访问和异步区块线程隔离，且测试大量固定种子与结构位置。
6. 以后应扩展为跨瓦片的高一级流域连通图，而不是只依靠当前区界高度混合。

## 引用与算法来源（概念参考，未复制第三方代码）

- Barnes et al., 2014, *Priority-flood: An optimal depression-filling and watershed-labeling algorithm for digital elevation models*. DOI: `10.1016/j.cageo.2013.04.024`.
- TerraForged: `https://github.com/TerraForged/TerraForged`。
- Tectonic: `https://github.com/Apollounknowndev/tectonic`。
- FastNoiseLite: `https://github.com/Auburn/FastNoiseLite`。

本模块代码为本次会话独立编写，未复制第三方源代码。许可证、未来公开发布和任何第三方集成应遵循各项目的原始条款。
