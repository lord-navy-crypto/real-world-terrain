# 版本恢复清单（2026-10-11）

| 版本 | 实际恢复内容 | 状态 |
| --- | --- | --- |
| v0.4 AUDIT | 可读 datapack、历史解析/服务器 QA、本机旧 lowcode JAR | 历史实验版本；不是 Java GeoCore |
| v0.5 GEOREAL | 可读 datapack、历史服务器 QA | 历史实验版本 |
| v0.6 REALISM MAX | 可读 datapack、历史服务器 QA；七个预设也存在于 v1.1 | 历史实验版本 |
| v0.7 GeoCore | 未找到原始附件或源码 | 缺失，不能验证 |
| v0.8 Geology | 未找到 Research_And_Java_Kernels 压缩包 | 缺失；对话描述为独立研究原型 |
| v0.9 GeoEngine | 未找到 Source_And_Tests 压缩包 | 缺失；对话描述为独立引擎，未集成 Minecraft |
| v1.0 Real Data | 恢复 USGS 官方输入、v1.1 使用的派生栅格；未恢复独立 Java/Python 引擎包 | 部分恢复 |
| v1.1 Final Battle | 完整 NeoForge 快照、独立 DEM 测试、已构建 JAR、真实服务器存档/日志/报告 | 测试候选版 |

需补充原附件：v0.7 开发包、`RealScale_Geology_v0.8_Research_And_Java_Kernels.zip`、`RealScale_GeoEngine_v0.9_Source_And_Tests.zip`、`RealScale_GeoEngine_v1.0_USGS_REAL_DATA_Source_And_Java.zip`。v1.1 对话中的完整源码附加包和 Mac 安装脚本也未恢复；当前源码来自经过 SHA-256 检查的 GitHub 构建快照。

未来补回时应保留原文件校验值，解压为可读源码，并重新运行各自测试；不得把缺失版本的对话测试数字标记为本仓库通过。
