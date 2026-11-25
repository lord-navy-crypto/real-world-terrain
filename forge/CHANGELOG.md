# RealScale — Changelog / 更新日志

本文件记录 **RealScale 地形主模组**的版本变化。当前主线将可选的 Distant Horizons × Chunky 兼容钩子嵌入主模组 JAR；历史独立 `dhchunkyguard` 插件与嵌入版本不兼容，不应同时安装。实验构建不能直接视为正式稳定版。

## 2.0.0-preview.2-test.12 — 2026-11-21 — Final Natural Automate integration candidate (Experimental)

### Completed
- Natural Automate remains the only public RealScale world-generation profile in both standard and extended Create World menus.
- The Terrain Lab exporter now derives new worlds from `natural_automate_v2`, so YACL/CLI exports also include the expanded cave topology without rewriting the old registry entry.
- Enabled Cave Exploration V2 in Natural Automate's default final-density pipeline: deterministic multi-tier loop passages, satellite-room connections and annex chambers now share the unified Overworld density field with the existing cave network. The public world preset uses a new `natural_automate_v2` noise-settings registry key; the old `natural_automate` key remains unchanged so existing saves keep their original generator reference.
- Removed the Legacy Landscape / Alpine / Canyon selectors from the player-facing Terrain Lab screen. Their schemas, saved-draft migration and exporter support remain internal for compatibility.
- Updated the cave contracts, preset validator and Terrain Lab regression checks to prevent the six-kernel engine or expanded cave network from silently reverting.
- Repaired APES Gallery's RCON acceptance harness: it no longer assumes the Gradle parent process remains alive after Minecraft starts; it now tickets each satellite chunk, waits for loaded-state acknowledgements, flushes the world and stops the server through RCON.

### Validation scope
- Dedicated-server and Anvil checks must pass on this exact commit, including all 16 APES gallery biome palettes and the satellite FULL chunks.
- Natural Automate's expanded cave codec is checked by the unified test-JAR server boot and the cave topology parity tests.
- Legacy worldgen codecs still default `gallery_v2` to false when the field is absent, preserving older preset behavior.
- This is the final experimental integration candidate, not a Stable certification. Client FPS, GPU behavior, shaders and every third-party mod combination still require physical-client validation.

## 2.0.0-preview.2-test.11 — 2026-11-21 — Single public Natural Automate composite (Experimental)

### Changed
- The standard and extended Create World menus now expose only `natural_automate_three_realms`.
- Natural baseline and six standalone fitted world-preset resources remain packaged but hidden as compatibility/diagnostic references; the six fitted morphology kernels remain active inside Natural Automate.
- The public composite retains the natural height baseline, regional/climate/topography suitability, normalized six-kernel cross-fades, natural fallback, shared ecology chain, and Overworld/Nether/End configuration.
- Updated the preset catalog, validator, source tests and candidate-JAR workflow to enforce exactly one public mode.

### Validation status and limits
- Source changes are committed to the feature branch; the new one-mode CI and packaged-JAR checks must pass before the updated candidate is considered ready.
- This remains an experimental build. Hidden kernel references are retained, and client FPS improvement still requires physical-client A/B testing.

## 2.0.0-preview.2-test.10 — 2026-11-21 — Natural Automate & Studio consolidation (Experimental)

### Added
- Added **Natural Automate Three Realms** as the first and recommended experimental world profile. It composes the six existing fitted morphology kernels (Alpine, Glacial, Canyon, Volcanic, Karst and Dune) using a low-frequency Weirdness regional selector and a 16% smooth transition band. The local Erosion selector remains separate for morphology detail.
- Natural Automate keeps the natural terrain base, mountain-relief signal, Grand Overworld cave graph, Natural Ecotones, Sky Ocean and Cave Depth Habitats. Geological Wonders remain explicitly outside the default profile.
- Added a new Terrain Lab export path for Natural Automate. Custom profiles are content-addressed and create new world preset/noise/density IDs; they do not modify existing saves.
- Added one **RealScale Studio** pause-menu entry with routes to Terrain Lab, Performance Studio and Geographic Climate Inspector.
- Added **Performance Studio** live telemetry: Minecraft-reported FPS, opt-in frame interval percentiles, compile/upload queue state, free mesh buffers, JVM heap use and approximate Natural Automate cache-array memory per worker.
- Added a unified performance configuration file, `config/realscale-performance.json`, with Balanced, Low Memory, Smooth Render and combined profiles plus opt-in diagnostics.

### Changed
- Terrain Lab now defaults to Natural Automate. The older Landscape / Alpine / Canyon blended-noise families remain available as Legacy Advanced and are clearly distinguished from the six fitted archetype kernels.
- Natural Automate exposes composite morphology strength and terrain relief controls; the shared 1:20 climate strength applies to the exported profile.
- The Performance Core selectively integrates bounded cache capacities from PR #156 and additionally includes the three horizontal morphology caches plus volcanic and karst shape caches in its Low Memory profile and estimate.
- The current Create World menu contains eight public choices: Natural Automate, Natural baseline, and the six fitted archetypes.

### Removed
- Removed World Atlas completely: Atlas screens, local save-directory scanning, Anvil heightmap readers, localization keys and Atlas-specific tests. No replacement save scanner is retained.
- Removed the unrelated orphan LOTM test and QA test from the RealScale branch; LOTM remains a separate workstream.

### Validation status and limits
- The catalog validator checks all 41 world-preset resources, both eight-entry menu tags, public biome-source chains and worldgen resource counts.
- Natural Automate is experimental and still requires multi-seed saved-Anvil validation. No live terrain preview is claimed.
- Performance telemetry is observational. Smooth Render time-slices FIFO upload completion; it does not reduce mesh compilation or GPU draw calls. No FPS improvement is claimed until physical-client A/B testing.
- Full GitHub Actions and in-game validation are pending for this branch. This is not a Stable release.

## 2.0.0-preview.2-test.9 — 2026-11-21 — Previous experimental baseline (superseded)

### Current source state
- The current Create World tags expose seven profiles: Natural plus Alpine, Canyon, Glacial, Volcanic, Karst and Dune.
- The six fitted profiles remain experimental. The USGS Big Sur modes remain hidden from the ordinary new-world menu by the earlier product decision; measured resources are retained for compatibility and controlled testing.
- Terrain Lab still edits three families (Landscape, Alpine and Canyon). It is not yet unified with all seven public profiles and does not provide a live terrain preview.
- World Atlas remains a read-only saved-Anvil coverage/heightmap inspector. Geographic Climate Inspector remains a read-only 1:20 geographic/solar report.
- No new general FPS or world-generation speedup is claimed by this source-version bump.

### Known integration blockers
- The current source tree contains an unrelated LOTM test referencing a missing `LotmBalance` class on main. Isolate the independent LOTM module/test and rerun full CI before promoting this source baseline.
- Unified profile catalog, shared ecology-layer controls and Visualization Studio remain planned consolidation work, not completed features.

## Seven-mode public test build — 2026-10-27
**Artifact basis:** `2.0.0-preview.2-test.6` (the JAR's internal version was not bumped for this menu-only test build).

### Changed
- The normal and extended Create World menus now expose **seven choices**: Natural Landscapes plus six fitted experimental presets (Alpine, Canyon, Glacial, Volcanic, Karst, Dune).
- Removed USGS Big Sur from both selectable menus at the user's request. Legacy USGS resources remain in the JAR for saved-world compatibility; USGS is not offered as a new-world mode.
- The six fitted presets are visibly marked Experimental / 实验玩法 in English and Chinese.
- Natural Landscapes' original preset and noise-settings resources remain byte-identical to the separate stable candidate baseline.

### Validation
- Source-integrity and packaged-JAR menu checks passed; downloadable artifact: [RealScale-7-Modes-Public-Experimental-Test-JAR](https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/40446967997).
- Real NeoForge smoke jobs for Natural, Volcanic and Dune were cancelled in this run, so this build is **not** certified by a completed in-game worldgen smoke test.
- A previous fitted-mode comparison found no measurable Dune-vs-Natural height difference across its sampled 6,400 columns. Treat all six fits as player-test experiments, not validated geographic matches.
- The JAR's internal version remains `2.0.0-preview.2-test.6`; this changelog does not imply a formal stable release.

## 2.0.0-preview.2-test.6 — 2026-10-23 — Experimental

### 新增 / Added
- 新建世界菜单提供六个 **RealScale 自有品牌预设**：自然地貌、高山地貌、峡谷与河谷、USGS 实测地形、自然奇观与跨维度生态、洞穴与海洋生态（实验）。
- 为每个新预设增加与对应历史配置相同的世界生成 JSON 入口，并增加验证新旧生成配置一致的自动测试。
- 旧存档可能引用的历史内部世界预设资源仍保留，但不会再作为新的菜单选项显示。

### 变更 / Changed
- 模组显示名称：`RealScale — Terrain, Caves & Oceans (Experimental)`。
- JAR 文件名：`RealScale-NeoForge-1.21.1-2.0.0-preview.2-test.6.jar`。
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

### English release summary — 2.0.0-preview.2-test.6

- **Added:** Six RealScale-branded Create World presets, matching historical worldgen configurations without deleting legacy saved-world identifiers.
- **Changed:** Mod title, artifact filename, and English/Chinese player-facing preset labels.
- **Fixed:** Renaming-related regression tests and a Terrain Lab variable reference.
- **Performance:** No new speedup claimed over Test.5; experimental Beardifier height culling remains enabled by default, while the optional vanilla client GPU-upload override remains disabled by default.
- **Compatibility:** The DH–Chunky Guard is separate; shaderpack and GPU LOD support still require physical client validation.
- **Status:** Experimental release candidate; main NeoForge build CI passed, broader saved-world/client tests are not a blanket certification.
