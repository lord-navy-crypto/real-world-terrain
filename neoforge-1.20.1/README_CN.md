# RealScale — NeoForge 1.20.1

这是面向 Minecraft 1.20.1 的独立旧版 NeoForge 构建。

- Minecraft：**1.20.1**
- NeoForge Legacy：**47.1.54–47.1.106（已测试端点；CI 验证两个端点）**
- Java：**17**
- 构建工具：**ModDevGradle Legacy Forge 插件 2.0.148**
- 客户端配置库：**YACL 3.6.5+（CI 测试 3.6.5 和 3.6.6）**

NeoForge 早期的 1.20.1 分支沿用了 Forge 47.1.x 的旧 API 和 FML 元数据。本模块针对 NeoForged Maven 的旧版 `net.neoforged:forge:1.20.1-47.1.x` 工件系列构建；CI 会分别测试已测试的最低版本 47.1.54 和最高版本 47.1.106。它与现代 NeoForge 模块分开维护。

RealScale 的地形算法、世界生成资源、建筑模板和 Natural Automate 预设均予以保留；平台接入与构建工具链独立维护。

在仓库根目录构建：

```sh
gradle --no-daemon -p neoforge-1.20.1 clean build
```

生成的 JAR 位于 `neoforge-1.20.1/build/libs/`。CI 会检查成品资源并启动 Natural Automate 专用服务器。客户端渲染和第三方模组兼容性仍需实际游戏验证。
