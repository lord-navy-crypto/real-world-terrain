# 验证记录与限制

## 原始构建证据

- NeoForge 构建：原仓库 Actions run `38115577108` 成功；下载附件 ID `11693133054`，校验 ZIP SHA-256 与 GitHub artifact digest 一致。内部 JAR 为本仓库 `dist/v1.1/` 中的原始字节。
- 真实 NeoForge 服务器：run `38115588244` 成功；附件 ID `11693367960`，校验 digest 一致。原始报告：121 chunks / 192 sampled columns / median 0.464m / p95 0.949m / 0 lava。
- USGS 输入：run `38114075983` 的附件 ID `11693160633`，digest 一致；144 条服务响应记录，而非 144 条独立实地勘测。
- 原始源码 TAR.XZ SHA-256：`c025c04397a54aecc17eb0ae89682bd2afe225acad7518a1ded2483d0a4e34f9`。已解码为 `neoforge/`，不要求使用 Base64 才能构建。

以上链接与精确提交见 `PROVENANCE.json`。旧仓库链接仅用于溯源。

## 本次离线重验（2026-10-11）

执行 `python3 qa/verify_repository.py`，结果通过：

- 历史 NBT、调色板、Anvil、数据包与服务器配置单元测试：5/5。
- Java 21 编译并执行原 `RealDemTileSmoke`：14,641 个 4 米网格采样检查通过，原始 DEM SHA-256 与边界检查通过。
- 原构建 JAR 具有八个世界预设，全部 resources 字节与恢复源码一致。
- 保存的真实服务器存档重验：**836 个区块 / 1,040 列 / median 0.478m / p95 0.945m / 0 lava**。结果见 `evidence/v1.1/replayed-world-report.json`。

附件存档与原报告的计数不相同：下载的四个 region 文件解码得到 836 个区块，而随附件保存的报告为 121 个区块。原报告与最终附件代表的扫描集合不同，原因尚未由运行过程复现确认；不能声称两份报告完全相同。保留两者，并对实际附件重新扫描。新检查要求采样不少于原报告且 median、p95 均小于 1m。

重验使用原始解析器与原始高程扫描代码；属于恢复验证，不是独立重写的测量程序。采样只在原点附近 [-60,60]×[-60,60] 且有保存高度图的列，并非整个世界验收。表面含积雪、植被与结构；这些误差是相对输入插值模型的误差，不能当作 USGS 测量精度。

## 本次从可读源码重新构建

Java 21 + Gradle 8.14.3 执行 `gradle --no-daemon build`，退出码 0，BUILD SUCCESSFUL。重新构建 JAR 的三个 Java class 与原成功构建附件中的 class **逐字节一致**，JAR 条目集合也一致。归档时间等元数据可能不同，不要求整个 JAR 的 SHA-256 一致。构建日志见 `evidence/v1.1/local-build.log`。

## 新仓库 CI

完整历史服务器 ZIP 因自动审批审核输入预算限制未上传，单独交付本地文件。可执行 `python3 qa/verify_repository.py /path/to/RealScale-v1.1-original-server-qa.zip` 重验。未提供附件时会明确跳过历史存档扫描。

`ci.yml` 在新仓库源码上执行源码/构建附件核验与 Gradle 8.14.3 / Java 21 NeoForge 构建；`server.yml` 启动一次性新世界并检查实际保存区块。请以当前提交的 Actions 结果为准。本文件不预先宣称尚未结束的 CI 已通过。

## 未验证或未恢复

- Minecraft 客户端画面、村庄贴地、长距离河网、帧率及长期性能仍未验收。
- v0.7–v1.0 独立引擎源码、其原测试和真实数据转换工具未恢复，无法复验对话中 32,777 / 167,892 / 1,030 等断言数字。
- 数据导入的原始重采样工具未恢复。原始 EPQS 输入和最终 RGT 栅格均已保留，但不能声称本仓库已能端到端重新生成相同 RGT。
- v1.1 是 DEM 密度覆盖层，不包含完整地质、水文、侵蚀或真实气候模拟。
