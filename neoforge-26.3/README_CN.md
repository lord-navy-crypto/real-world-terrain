# RealScale — NeoForge 26.3

这是面向 Minecraft Java 26.3 的实验性迁移工作区，不是正式发布版本。

- Minecraft **26.3** / NeoForge **26.3.0.118**
- Java **25**
- ModDevGradle **2.0.148**
- Gradle **9.2.1**
- 可选 YACL **3.9.7+26.3-neoforge**

构建：`gradle --no-daemon -p neoforge-26.3 clean build`

初始源代码来自当前 NeoForge 实现。仍需完成：
- [ ] DensityFunction / DensitySampler 新架构
- [ ] Feature 注册与世界生成 JSON 迁移
- [ ] BiomeSource / BiomeResolver 迁移
- [ ] 数据包格式 121.0 与生成资源元数据
- [ ] 客户端和独立服务器启动
- [ ] 多种子新世界生成及地形回归测试

请先备份存档；不要把实验版本用于正式世界。
