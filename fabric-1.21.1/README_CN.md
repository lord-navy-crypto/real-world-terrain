# RealScale — Fabric 1.21.1

RealScale 面向 Minecraft 1.21.1 的独立 Fabric 移植版本。

- Minecraft：**1.21.1**
- Fabric Loader：**0.16.14 或更新版本**（CI 基准：0.19.5）
- Fabric API：**0.116.18 或更新版本**
- Java：**21**
- 可选配置界面：**YACL 3.8.1+1.21.1-fabric**

YACL 仅作为编译期依赖，运行时可选。没有安装 YACL 的玩家仍可使用核心地形生成；安装对应版本的 YACL 后才提供 Studio 配置入口。

在仓库根目录构建：

```sh
gradle --no-daemon -p fabric-1.21.1 clean build
```

生成的 JAR 位于 `fabric-1.21.1/build/libs/`。这是独立的 1.21.1 文件，不能安装到 1.20.1 实例。本轮首先验证编译、JAR 内世界生成资源、Natural Automate 专用服务器启动，以及不安装 YACL 时的客户端启动。Sodium/Iris/DH 渲染组合和真实区块生成性能仍需后续游戏内测试。
