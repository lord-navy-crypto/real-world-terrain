# RealScale Terrain

Minecraft 1.21.1 / NeoForge / Java 21 的真实尺度地形实验项目。当前可安装成果为 **v1.1 测试候选版**：USGS Big Sur 高程覆盖层 + 七个程序化世界预设。地表单位为 1 方块 = 1 米；这不意味着原始观测分辨率为 1 米。

## 当前交付与边界

- `neoforge/`：从已成功构建的原始快照恢复的完整可读 Java 模组源码、Gradle 配置、世界生成 JSON、DEM 与独立 Java 测试。
- `archive/v0.4–v0.6/`：分别保存可读的数据包历史快照；v0.4 另保留本机已有的旧 JAR。目录实际名为 `v0.4`、`v0.5`、`v0.6`。
- `data/usgs-big-sur/`：144 条 USGS EPQS 请求记录、12×12 高程网格与来源信息。
- `qa/`：历史 Minecraft NBT/Anvil 解析测试、真实服务器区块高程核验，以及迁移完整性检查。
- `dist/v1.1/`：从 GitHub Actions 恢复的可安装 NeoForge JAR。
- `evidence/`：带 SHA-256 校验的原始构建附件、真实服务器日志、存档区块及报告。
- `docs/`：来源清单、版本恢复状态、许可说明和验证范围。

**v0.7–v1.0 独立 GeoCore/地质/水文引擎源码尚未恢复。** 原对话中的附件不在本机或原仓库可读文件中；不能据对话重写一份原型并称为原始成果。详见 [版本清单](docs/VERSIONS.md)。v1.1 的三个 Java 类负责 DEM 加载、密度函数和注册，未集成 v0.9 流域引擎，也不是完整的自定义 ChunkGenerator。

## 安装测试候选版

从 [dist/v1.1](dist/v1.1/) 下载 JAR，放入 Minecraft 1.21.1、NeoForge 21.1.249、Java 21 实例的 `mods` 文件夹。关闭游戏后操作，先备份实例。只装一份 RealScale，创建新的实验世界，选择 USGS Big Sur 预设，建议种子 `785312`，观察世界原点附近。

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

CI 自动运行离线核验、Java DEM 测试和 NeoForge 构建，并保留新构建 JAR。服务器 CI 在一次性世界中启动真正的 NeoForge 并检查保存区块；历史服务器附件也可离线重验。历史来源与新 CI 结果分别记录，详见 [验证说明](docs/VALIDATION.md)。

## 来源与许可

[来源清单](docs/PROVENANCE.json)记录原始提交、压缩快照校验值、Actions run 和附件 ID。只迁移 RealScale 相关路径；没有导入 SWARM-MOBS 的怪物 AI、资源或提交历史。旧版本以 archive 保存。

原始源码没有明确的开源许可授权，本次没有擅自补成 MIT。请阅读 [LICENSE](LICENSE) 和 [第三方来源声明](THIRD_PARTY_NOTICES.md)。
