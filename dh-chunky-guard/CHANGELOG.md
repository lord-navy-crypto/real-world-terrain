# DH–Chunky Guard — Changelog / 更新日志

本文件记录独立 NeoForge 兼容补丁的变更。RealScale 主模组的版本说明位于 `neoforge/CHANGELOG.md`。兼容补丁独立编译、独立安装，不会取代 RealScale 主模组。

## 0.3.0-experimental — 2026-10-23

### 新增 / Added
- 新增 `/dhchunkyguard status`：查询 Chunky 初始化探测、延迟、恢复信号、DH 原生监听器绑定数量及平均/最大探测耗时。
- 新增 `/dhchunkyguard advice`：只读诊断提示，区分监听器未绑定与远景生成/渲染问题。
- 使用启动期间的轻量内存计数器；不增加循环轮询线程、区块上传 Hook、存档写入或光影干预。
- 实际 Minecraft RCON 测试验证两条命令可用。

### 验证 / Verification
- 两组 RealScale 地形预设的真实 NeoForge 服务器测试观察到 Chunky 初始化恢复、DH 原生监听器绑定及远处完整区块加载。
- 构建与 25 项检查：[GitHub Actions](https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39905141570)。
- 真实多预设服务器联合测试：[GitHub Actions](https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39905141492)。
- `GPU_LOD=unverified` 为有意保留的状态：服务器测试无法证明 Iris/Sodium 或光影包在 Mac 上正确绘制远景。

## 0.2.0-experimental — 2026-10-23

### 改进 / Improved
- 缓存 `ChunkyProvider.get()` 反射方法，减少初始化等待阶段的重复 Java 方法查找。
- 增加 `REALSCALE_DH_CHUNKY_GUARD_BOUND` 标记，只在 DH 原生绑定流程返回并确认监听器已绑定后发出。
- 联合 QA 检查绑定、DH 生成队列和真实远处区块；不新增第二套 DH/Chunky 任务调度器。

## 0.1.0-experimental — 2026-10-22

### 修复 / Fixed
- 修复启动时 DH 早于 Chunky 注册 Provider 而触发的 `IllegalStateException: Chunky is not loaded.`。
- 只在这一已确认的初始化未就绪情形中暂缓第一次原生事件绑定，并允许后续区块事件重试；其他异常继续暴露，避免掩盖未知故障。
- 不禁用 DH 的 LOD 渲染，不重写 RealScale 高度、世界生成、区块或存档。
- v0.1 曾在真实 Mac 客户端观察到完整远山 LOD；这一结果**不自动等于 v0.3 的 Iris 光影已通过测试**。

## 安装要求、已知限制 / Requirements & Limitations

- 测试版本组合：Minecraft **1.21.1**、NeoForge **21.1.x**、Java **21**、Distant Horizons **3.3.3**、Chunky **1.4.23**。
- `0.1`、`0.2`、`0.3` **只可同时安装一个**；升级前备份可用版本。
- 这是独立可选小模组，不打包第三方 DH、Chunky、Iris、Sodium JAR，不改变其渲染流水线。
- 服务器绑定成功 ≠ 远景数据已完全持久化 ≠ GPU/光影实际显示正确。
- 仓库源码位于 [`dh-chunky-guard/`](https://github.com/lord-navy-crypto/real-world-terrain/tree/main/dh-chunky-guard)，发布许可仍需依项目权利人确认。

### English release notes — 0.3.0-experimental

- Added read-only in-game `/dhchunkyguard status` and `/dhchunkyguard advice` commands and lightweight initialization counters.
- Preserved the v0.2 native DH listener-bind verification and cached Chunky provider method lookup.
- Retained the narrowly targeted v0.1 early-startup race fix. Does not modify worldgen, the DH scheduler, chunk saves, GPU renderer or shaders.
- Dedicated-server and RCON checks passed. **Client-rendered LOD and Iris shader compatibility are not certified.**
