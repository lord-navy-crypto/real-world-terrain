# RealScale Terrain 版本恢复清单（2026-10-11）

| 版本 | 已上传到本仓库的源文件 | 实际集成状态 |
| --- | --- | --- |
| v0.4 AUDIT | `archive/v0.4/` 数据包与旧 JAR | 历史实验版 |
| v0.5 GEOREAL | `archive/v0.5/` 数据包 | 历史实验版 |
| v0.6 REALISM MAX | `archive/v0.6/` 数据包；七种预设也保留在 v1.1 | 历史实验版 |
| v0.7 GeoCore | `research/v0.7/`：原始 Java 源码、独立测试与使用说明 | 仅研究原型，未完全集成 |
| v0.8 Geology | `research/v0.8/`：地质隆起、侵蚀、沉积 Java 数值内核和测试 | 仅研究原型，未完全集成 |
| v0.9 GeoEngine | `research/v0.9/`：区域流域、水文、缓存、Java 测试 | 仅研究原型，未完全集成 |
| v1.0 Real Data | `research/v1.0/`：实测高程导入 Java + Python GIS 脚本 + 测试；`data/usgs-big-sur/` 存官方采样记录 | 实测导入原型，与 v1.1 密度函数不是同一套完整管线 |
| **v1.1 Final Battle** | **`neoforge/` 完整 NeoForge 源码；`dist/v1.1/` JAR；`qa/` 与 `evidence/` 验收资料** | **最新可安装的测试候选版** |

v1.1 用户下载的原始交付 JAR 已额外上传到 `dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar`。其 SHA-256：`638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43`。

**重要限制：** 研究代码的恢复不表示其模型已在 NeoForge 客户端运行。v1.1 主要实现 USGS DEM 高程密度函数和预设注册，现实河网/侵蚀/岩性/雨影的完整算法仍需未来集成和实际 GameTest。原始来源和测试细节详见 [RESEARCH_PROVENANCE.md](RESEARCH_PROVENANCE.md)。
