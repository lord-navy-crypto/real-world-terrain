# RealScale Terrain

## 两模式干净稳定候选（2026-10-27）

当前可优先验证的稳定候选是 [release/stable-natural-usgs-clean-20261027](https://github.com/lord-navy-crypto/real-world-terrain/tree/release/stable-natural-usgs-clean-20261027)。该分支从品牌兼容基线 PR #96 分出，**不叠加六套 DEM 拟合地形的 PR #136**。它还不是最终 Stable 发布版。

- 正式可见：`realscale:natural_three_realms`（自然地貌）与 `realscale:usgs_big_sur`（原始 USGS Big Sur 实测小范围地形）。
- 暂不纳入正式新世界菜单：高山、峡谷、实测 V2 及其他旧实验入口。旧资源及存档 ID 保留用于兼容；不是删除已有世界。
- 六套新增的 DEM 拟合地形（冰川、火山、喀斯特、沙丘等）仍在 [experimental/terrain-modes-20261026](https://github.com/lord-navy-crypto/real-world-terrain/tree/experimental/terrain-modes-20261026) 和 PR #136 开发，不编入本候选的新增 `terrain_archetype` 引擎。

该候选的专属 CI 包含源码菜单和 DEM 核心连线检查、NeoForge JAR 构建、打包内容检查，以及自然模式和 USGS 模式各自的专用服务器远距区块 180 秒内完成并落盘测试。**只有 CI 真正通过、后续同机性能和客户端测试完成后，才能称为稳定发布。** 当前 README 的历史实验版说明不代表这条稳定候选分支菜单。

Minecraft 1.21.1 / NeoForge / Java 21 的真实尺度地形实验项目。仓库保留 **v1.1 历史测试候选版**：USGS Big Sur 高程覆盖层 + 七个程序化世界预设。地表单位为 1 方块 = 1 米；这不意味着原始观测分辨率为 1 米。

## 当前实验预览与兼容组件（2026-10-23）

最新实验性主模组采用 **RealScale 2.0.0-preview.2-test.6** 命名，下载 JAR 的前缀为 **RealScale-NeoForge-1.21.1**。游戏菜单提供 RealScale 自然地貌、高山、峡谷、实测地形、自然奇观及洞穴海洋生态六项命名。原有世界注册 ID 在技术层保留作**存档兼容别名**，创建新世界不再显示旧的电影梗名称。对已有世界，请先备份再测试新版本。

RealScale 与 Distant Horizons / Chunky 的独立兼容补丁统一在本仓库的 [`dh-chunky-guard/`](dh-chunky-guard/) 管理。它拥有**单独的 Java / Mixin / Gradle 和 JAR 构建**，不是 RealScale 主模组必需依赖；需要使用精确匹配的 DH 3.3.3、Chunky 1.4.23、NeoForge 1.21.1。不要把两款 RealScale 主模组 JAR 同时安装，也不要同时安装两个 Guard 版本。

详细说明请见 [品牌命名与旧世界迁移约定](docs/REAL_SCALE_BRANDING_COMPATIBILITY.md)。实验性优化尚未完成全部旧存档等价性及 Mac 光影实测，**不应以 Stable 名义发布**。

主模组各版本更新请参阅 [RealScale 更新日志](neoforge/CHANGELOG.md)。DH–Chunky 独立补丁的更新记录请参阅 [兼容补丁更新日志](dh-chunky-guard/CHANGELOG.md)。

## 当前交付与边界

- `neoforge/`：从已成功构建的原始快照恢复的完整可读 Java 模组源码、Gradle 配置、世界生成 JSON、DEM 与独立 Java 测试。
- `archive/v0.4–v0.6/`：分别保存可读的数据包历史快照；v0.4 另保留本机已有的旧 JAR。目录实际名为 `v0.4`、`v0.5`、`v0.6`。
- `data/usgs-big-sur/`：144 条 USGS EPQS 请求记录、12×12 高程网格与来源信息。
- `qa/`：历史 Minecraft NBT/Anvil 解析测试、真实服务器区块高程核验，以及迁移完整性检查。
- `dist/v1.1/`：从 GitHub Actions 恢复的可安装 NeoForge JAR。
- `evidence/`：带 SHA-256 校验的原始构建附件、构建附件及服务器报告。
- `docs/`：来源清单、版本恢复状态、许可说明和验证范围。
- `research/v0.7`～`research/v1.0`：原始 Java 数值地质、水文与 USGS 测量数据导入算法及其独立测试；尚未完整接入可玩版。

**v0.7–v1.0 的原始独立 GeoCore／地质／水文／实测数据导入源码已补充恢复到 [`research/`](research/)。** 这些是独立测试原型，**没有全部集成到 v1.1 可安装模组**。v1.1 的三个 Java 类主要负责 DEM 加载、密度函数和注册，并非完整自定义 `ChunkGenerator`。详见 [版本清单](docs/VERSIONS.md) 与 [原型来源校验](docs/RESEARCH_PROVENANCE.md)。

## 安装测试候选版

优先下载 [与原始交付字节一致的 v1.1 JAR](dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar)（SHA-256: `638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43`），或从 [dist/v1.1](dist/v1.1/) 浏览其他构建产物；将 JAR，放入 Minecraft 1.21.1、NeoForge 21.1.249、Java 21 实例的 `mods` 文件夹。关闭游戏后操作，先备份实例。只装一份 RealScale，创建新的实验世界，选择 USGS Big Sur 预设，建议种子 `785312`，观察世界原点附近。

DEM 覆盖约 480×480 米，32 米网格由约 54–67 米间距的 USGS 服务值重采样而来；内侧 96 米过渡带混合回 Realism MAX。真实降水、岩性、侵蚀与完整河网不在这个 JAR 中。客户端画面、村庄落地、长距离河流和性能仍待测试。

## 构建与验证

要求 Java 21、Python 3.9+、Gradle **8.14.3**（与原验证环境一致）。

```sh
python3 qa/verify_repository.py
cd neoforge
gradle --no-daemon build
gradle --no-daemon runServer
```

启动服务器前需自行阅读并接受 Minecraft EULA；测试应使用一次性新世界。原始 `neoforge/README_CN.md` 保留为历史文件，其中旧 SWARM-MOBS 本机启动路径不适用于此独立仓库，以上命令取代该路径。

CI 自动运行离线核验、Java DEM 测试和 NeoForge 构建，并保留新构建 JAR。服务器 CI 在一次性世界中启动真正的 NeoForge 并检查保存区块；完整服务器存档 ZIP 因上传审核容量限制未进入 GitHub；提供该文件路径作为 `qa/verify_repository.py` 的第一个参数可离线重验。历史来源与新 CI 结果分别记录，详见 [验证说明](docs/VALIDATION.md)。

## 来源与许可

[来源清单](docs/PROVENANCE.json)记录原始提交、压缩快照校验值、Actions run 和附件 ID。只迁移 RealScale 相关路径；没有导入 SWARM-MOBS 的怪物 AI、资源或提交历史。旧版本以 archive 保存。

原始源码没有明确的开源许可授权，本次没有擅自补成 MIT。请阅读 [LICENSE](LICENSE) 和 [第三方来源声明](THIRD_PARTY_NOTICES.md)。
