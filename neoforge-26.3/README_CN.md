# RealScale — NeoForge 26.3

这是面向 Minecraft Java **26.3** 的实验性迁移工作区，不是正式发布版本。

**Fabric 和 NeoForge 两个版本都以 Minecraft 26.3 为目标，但 JAR 分别适用于对应加载器，不能互换。** NeoForge 环境请使用 NeoForge 构建产物。

## 工具链

- Minecraft **26.3** / Java **25**
- NeoForge **26.3.0.118**
- ModDevGradle **2.0.148**
- Gradle **9.2.1**
- 客户端 YACL **3.9.7+26.3-neoforge**

构建：`gradle --no-daemon -p neoforge-26.3 clean build`

## 迁移状态 — 2026-11-27

### 已完成，并通过 CI 验证

- [x] `DensityFunction` / `DensitySampler` 新接口迁移。
- [x] `BiomeSource` / `BiomeResolver` 新接口迁移。
- [x] Feature 注册与 26.3 世界生成数据迁移：
  - 11 种自定义 Feature codec、70 个自定义 Feature 定义。
  - 38 个原版 Feature 定义，总计 108 个。
  - 122 个 Placed Feature 定义，以及 120 个 biome modifier。
  - 35 个 random-patch 植被配置已迁移为 `simple_block` Feature，并使用 count、offset 和 predicate 放置修饰器。
  - 删除 106 个过时的 1.21 `configured_feature` JSON 资源。
- [x] 所有生成结构资源包均使用 **121.0** 数据包元数据，包括 Architecture Pilot 和 Urban Districts。
- [x] NeoForge CI 构建通过，**108 项自动化测试**通过，包含世界生成资源完整性测试。

### 尚未完成 — 目前不能视为正式发布版本

- [ ] 使用 RealScale 默认世界预设启动独立服务器，并完成运行时注册表与资源验收。
- [ ] 客户端实际启动验收。YACL Terrain Lab 入口已纳入构建，但 Studio 界面、上传节流、载具渲染和旧版 mixin 仍暂缓或关闭，等待完成 26.3 适配与验证。
- [ ] 多种子新世界生成和地形回归测试。

测试前请备份存档。不要将此实验版本用于正式世界。
