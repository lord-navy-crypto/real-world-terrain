# RealScale — Changelog / 更新日志

本文件记录 **RealScale 地形主模组**的版本变化。Distant Horizons × Chunky Guard 是单独安装的兼容补丁，版本说明见 `dh-chunky-guard/CHANGELOG.md`。实验构建不能直接视为正式稳定版。

## 2.0.0-preview.2-test.7 — 2026-10-23 — Experimental

### 新增 / Added
- 新建世界菜单提供六个 **RealScale 自有品牌预设**：自然地貌、高山地貌、峡谷与河谷、USGS 实测地形、自然奇观与跨维度生态、洞穴与海洋生态（实验）。
- 为每个新预设增加与对应历史配置相同的世界生成 JSON 入口，并增加验证新旧生成配置一致的自动测试。
- 旧存档可能引用的历史内部世界预设资源仍保留，但不会再作为新的菜单选项显示。

### 变更 / Changed
- 模组显示名称：`RealScale — Terrain, Caves & Oceans (Experimental)`。
- JAR 文件名：`RealScale-NeoForge-1.21.1-2.0.0-preview.2-test.7.jar`。
- 更新中英文世界创建界面、项目说明、Terrain Lab 描述和构建检查。

### 修复 / Fixed
- 同步更新洞穴、海洋、自然生态和世界菜单测试中受到预设改名影响的断言。
- 修复 Terrain Lab 重命名后遗留的变量引用问题，保持原有维度配置保护。

### 性能与兼容 / Performance & Compatibility
- **Test.6 主要是品牌命名、预设别名及相关测试修复；相较 Test.5 不宣称新的 FPS 或地形生成速度提升。**
- 保留实验性的 Beardifier 高度提前返回，默认开启；可使用 `-Drealscale.experimental.beardifier.ycull=false` 关闭。该实验尚不保证所有存档逐方块一致。
- Aquifer 只读诊断默认开启（`-Drealscale.experimental.aquifer.audit=false` 可关闭），不等于加速算法。
- 客户端实验性 GPU 区块上传节流默认**关闭**（仅显式启用 `-Drealscale.experimental.client.uploadbudget=true` 时开启）。
- DH–Chunky Guard 不是此 JAR 的内置部分；如需 DH + Chunky 兼容补丁，应单独安装版本匹配的独立 JAR。

### 测试与限制 / Validation & Limitations
- [CI 源码完整性、单元测试和 NeoForge 主模组构建成功](https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39913274639)（提交 `7bea910603b76beabf3dcc0d806b762be2ea920a`）。
- 此版本仍位于[实验 PR #96](https://github.com/lord-navy-crypto/real-world-terrain/pull/96)，不代表已合并主分支、全量旧存档通过或 Mac / Iris / Sodium / 特定光影全部认证。
- 不要在同一 `mods` 目录同时安装不同版本的 RealScale 主模组；测试前备份存档。

## 2.0.0-preview.2-test.5 — 2026-10-22 — Experimental

### 地形与生态 / Terrain & Ecology
- 增强洞穴生态：新增地下群系选择与装饰逻辑，包含八种洞穴群系，包括荧光苔藓溶洞与化石石灰岩洞穴。
- 增强海洋栖息地的方块装饰，如冷水礁、海草/海藻草甸和暖水珊瑚环境；**不是**海洋动物或流体物理重写。
- 新洞穴与海洋生态实验主要通过单独的可选预设体验，不能宣称四个基础预设均改为新的洞穴群系生成模式。

### 性能与诊断 / Performance & Diagnostics
- 新增 Beardifier 原版结构影响高度范围提前返回实验；特定测试下有生成耗时收益，但不等于 FPS 提升，也不保证与原版逐方块一致。
- 增加 Aquifer 只读统计及客户端相关性能诊断，保留显式开关。
- 新增或加强 RealScale、Distant Horizons、Chunky 兼容回归测试与 LOD 持久化检查；兼容补丁仍是独立 JAR。

## 2.0.0-preview.1 — Earlier preview baseline

- 已包含自然、高山、峡谷、USGS Big Sur 实测地形和原有跨维度、自然过渡与生态地貌系统。
- 用于与后续实验版进行对照与必要的回退；不意味着 Preview.1 获得全面 Stable 认证。

## 安装要求 / Installation

- Minecraft **1.21.1**、NeoForge **21.1.x**、Java **21**；客户端配置界面需要 YACL **3.8.1+**。
- 保留历史内部 ID 是为了保护已有存档；新版本仍需真实世界和多模组联合验证。
- 稳定发布前还需关注区块内容一致性、RealScale 高地形高度、长距离区块、DH 远景及 Iris/Sodium/光影客户端实际呈现。

### English release summary — 2.0.0-preview.2-test.7

- **Added:** Six RealScale-branded Create World presets, matching historical worldgen configurations without deleting legacy saved-world identifiers.
- **Changed:** Mod title, artifact filename, and English/Chinese player-facing preset labels.
- **Fixed:** Renaming-related regression tests and a Terrain Lab variable reference.
- **Performance:** No new speedup claimed over Test.5; experimental Beardifier height culling remains enabled by default, while the optional vanilla client GPU-upload override remains disabled by default.
- **Compatibility:** The DH–Chunky Guard is separate; shaderpack and GPU LOD support still require physical client validation.
- **Status:** Experimental release candidate; main NeoForge build CI passed, broader saved-world/client tests are not a blanket certification.
