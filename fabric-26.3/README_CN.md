# RealScale — Fabric 26.3

这是面向 Minecraft Java 26.3 的实验性迁移工作区，不是正式发布版本。

- Minecraft **26.3** / Java **25**
- Fabric Loader **0.20.1**
- Fabric API **0.167.0+26.3**
- Fabric Loom **1.19-SNAPSHOT**
- Gradle **9.8.2**
- 可选 YACL **3.9.7+26.3-fabric**

构建：`gradle --no-daemon -p fabric-26.3 clean build`

初始源代码来自已验证的 Fabric 1.21.1 模块。仍需完成：
- [ ] DensityFunction / DensitySampler 新架构
- [ ] Feature 注册与世界生成 JSON 迁移
- [ ] BiomeSource / BiomeResolver 迁移
- [ ] 数据包格式 121.0 与生成资源元数据
- [ ] 客户端和独立服务器启动
- [ ] 多种子新世界生成及地形回归测试

请先备份存档；不要把实验版本用于正式世界。
