# RealScale Terrain

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21 的实验性真实尺度地形与生态系统模组。当前源码版本为 **2.0.0-preview.2-test.10**；这是实验开发版本，不是 Stable 发布版。

本仓库正在进行一次统一架构整合。目标是把地形、生态、维度、结构、可视化和性能系统放入一致的产品结构，而不是继续增加互相重复的预设和配置入口。

## 当前新世界菜单（2026-11-21）

当前 `normal` 与 `extended` 世界类型菜单均提供八个选项：

1. **Natural Automate Three Realms** — 六种拟合地貌的自适应区域组合，推荐实验模式。
2. **Natural Three Realms** — 自然地形基线。
3. **Alpine Three Realms** — 高山拟合地形（实验）。
4. **Canyon Three Realms** — 峡谷拟合地形（实验）。
5. **Glacial Three Realms** — 冰川拟合地形（实验）。
6. **Volcanic Three Realms** — 火山拟合地形（实验）。
7. **Karst Three Realms** — 喀斯特拟合地形（实验）。
8. **Dune Three Realms** — 沙丘拟合地形（实验）。

六种拟合模式仍属于实验配置，不能把部分模式的测试结果推广成全部模式已经完成真实地理拟合验证。

**USGS Big Sur / Big Sur v2 已按此前决定从普通新世界菜单中移除。** 实测地形资源和 `measured_three_realms` 等内部配置仍保留，用于兼容、数据导入和受控实验；不要将其误认为当前公开菜单选项。USGS 3DEP 数据导入是离线、可选流程，不代表整个世界都是实测地形。

## 核心系统

- **Terrain Engine：** Realism MAX、Terrain Archetype、DEM、山脉起伏、侵蚀、地层和河流地形。
- **Ecology Engine：** Natural Ecotones、Sky Ocean、Sky Islands、Cave Depth Habitats、海洋和洞穴生态、可选 Geological Wonders。
- **World Systems：** 扩展 Overworld 洞穴、Grand Nether / End、天空岛、结构密度包、100 结构建筑库和可选水上载具。
- **RealScale Studio：** 统一 Terrain Lab、Climate Inspector 与 Performance Studio；World Atlas 已从当前产品中移除。
- **Performance：** 世界生成采样、缓存诊断、客户端帧间隔和可选区块上传节流。
- **Compatibility：** 可选 Distant Horizons / Chunky 兼容钩子及外部客户端视觉工具。

完整 41 个世界预设的公开、隐藏和历史分类见 [Preset Catalog](docs/PRESET_CATALOG.json)。详细功能清单、状态分级、冲突判断和最终整合路线见 [Unified Feature Consolidation](docs/UNIFIED_FEATURE_CONSOLIDATION.md)。

## 当前可视化状态

这些功能已经存在，但尚未整合成统一工作台：

- **Terrain Lab：** 已将 Natural Automate 设为主线，支持自适应六种拟合地貌、地形起伏和 1:20 气候配置；旧版 Landscape / Alpine / Canyon 混合噪声入口保留为 Legacy Advanced。尚无实时地形预览。
- **World Atlas：** 已移除，不再扫描本地存档。
- **Geographic Climate Inspector：** 只读显示当前坐标的 1:20 地理纬度、虚拟太阳季节、日照和高度温度估算；不是实时天气模拟。
- **Performance Studio：** 已提供四种性能配置、缓存容量管理、FPS/帧间隔、区块上传队列和 JVM 堆内存遥测。

这些能力现在通过统一的 **RealScale Studio** 入口访问，但尚未成为一个完整的实时 3D 工作台。不能把尚未实现的实时预览或完整地形热力图描述成现有功能。

## Optional architecture library

主模组包含可选的 100 结构建筑库。新世界创建时可在 Data Packs 中启用；它不是强制加入所有世界的功能。结构密度包之间有互斥限制，创建世界前应只选择一套适用的 Overworld 密度配置。

## Distant Horizons / Chunky compatibility

当前 main 在主模组中嵌入可选的 DH–Chunky 兼容钩子，针对已测试的 Distant Horizons 3.3.3 + Chunky 1.4.23 组合。历史独立 `dhchunkyguard` 插件与当前嵌入版本不应同时安装；本分支没有独立的顶层 `dh-chunky-guard/` 子项目。

World Atlas 的存档扫描和地图读取代码已从当前分支删除；没有保留替代存档扫描器。

## Repository layout

- `neoforge/` — NeoForge 主模组源码、注册、世界生成资源和客户端界面。
- `scripts/` — Terrain Lab exporter、USGS 3DEP importer、结构模板生成和开发工具。
- `qa/` — 源码完整性、数据、Anvil、世界生成和性能回归检查。
- `docs/` — 当前架构、验证范围、来源记录和历史设计。
- `assets/architecture/` — 建筑模板源素材及许可记录。
- `archive/` — 历史数据包和旧版本材料。
- `research/` — 尚未完整集成的数值地质、水文和数据导入研究原型。
- `dist/v1.1/` — 历史 v1.1 测试候选 JAR，不是当前八模式源码构建。

## Build and validation

要求 Java 21、Minecraft 1.21.1、NeoForge 21.1.x 和 Python 3。

```sh
python3 qa/verify_repository.py
cd neoforge
gradle --no-daemon build
```

**当前验证状态：** 本分支已移除与 RealScale 无关的 LOTM 孤立测试。Natural Automate、Studio 和性能配置仍需通过本分支完整 CI 与实际客户端测试；目前不应将其标记为 Stable。

不要在已有世界上替换生成器配置。创建新世界测试前备份实例，并且不要同时安装多个 RealScale 主模组 JAR。

## Version and licensing

当前源码版本以 `neoforge/build.gradle` 和 `META-INF/neoforge.mods.toml` 为准；更新记录见 [CHANGELOG](neoforge/CHANGELOG.md)。历史恢复状态见 [VERSIONS](docs/VERSIONS.md)。

原始源码没有明确的开源许可授权，本次没有擅自补成 MIT。请阅读 [LICENSE](LICENSE) 和 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。
