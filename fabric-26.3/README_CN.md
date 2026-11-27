# RealScale — Fabric 26.3

这是面向 Minecraft Java **26.3** 的实验性迁移工作区，不是正式发布版本。

**Fabric 和 NeoForge 两个版本都以 Minecraft 26.3 为目标，但 JAR 分别适用于对应加载器，不能互换。** Fabric 环境请使用 Fabric 构建产物。

## 工具链

- Minecraft **26.3** / Java **25**
- Fabric Loader **0.20.1**
- Fabric API **0.167.0+26.3**
- Fabric Loom **1.19-SNAPSHOT**
- Gradle **9.8.2**
- 可选 YACL **3.9.7+26.3-fabric**

构建：`gradle --no-daemon -p fabric-26.3 clean build`

## 迁移状态 — 2026-11-27

### 已完成，并通过 Fabric 与 NeoForge CI 验证

- [x] `DensityFunction` / `DensitySampler` 新接口迁移。
- [x] `BiomeSource` / `BiomeResolver` 新接口迁移。
- [x] Feature 注册与 26.3 世界生成数据迁移：
  - 11 种自定义 Feature codec，70 个自定义 Feature 定义。
  - 38 个原版 Feature 定义，总计 108 个。
  - 122 个 Placed Feature 定义，以及 120 个 biome modifier。
  - 35 个 random-patch 植被配置已拆分为 `simple_block` Feature，以及 count、offset、predicate 放置修饰器。
  - 删除 106 个过时的 1.21 `configured_feature` JSON 资源。
- [x] 所有生成结构资源包均使用 **121.0** 数据包元数据，包括 Architecture Pilot 和 Urban Districts。
- [x] 两个加载器模块均通过 **108 项自动化测试**，包括世界生成资源完整性测试。

### 尚未完成

- [ ] 客户端实际启动测试与 UI/渲染迁移。当前 Fabric 客户端入口保持最小实现；Studio 界面、上传节流、载具渲染和 1.21.x mixin 仍待完成 26.3 适配与验收。
- [ ] 独立服务器启动、RealScale 默认世界预设加载，以及运行时注册表/资源验收。
- [ ] 多种子新世界生成和地形回归测试。

测试前请备份存档。不要将此实验版本用于正式世界。
