# RealScale — NeoForge 1.20.1

这是 RealScale 面向早期 NeoForge 1.20.1 分支的独立版本。

- Minecraft：**1.20.1**
- NeoForge：**1.20.1-47.1.106**
- Java：**17**
- 构建工具：**ModDevGradle Legacy Forge 插件**
- 配置界面：**YACL 3.6.6（客户端依赖）**

NeoForge 的 1.20.1 分支沿用了 Forge 47.1.x 的旧 API 和 FML 元数据格式。本模块使用 NeoForged 发布的 1.20.1 构建目标，不是简单重命名普通 Forge JAR，也不影响现有 Forge 1.20.1 或现代 NeoForge 1.21.1 模块。

地形算法、世界生成数据、建筑模板和 Natural Automate 预设保持 1.20.1 主线内容；平台接入和构建工具链单独维护。

在仓库根目录构建：

```sh
gradle --no-daemon -p neoforge-1.20.1 clean build
```

生成的 JAR 位于 `neoforge-1.20.1/build/libs/`。CI 会检查 JAR 内容并启动 Natural Automate 专用服务器。客户端渲染和第三方模组兼容性仍需实际游戏测试。
