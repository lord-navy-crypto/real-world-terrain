# RealScale — Forge 1.20.1

独立 Forge 移植模块：Minecraft 1.20.1、Forge 47.4.28、Java 17、YACL 3.6.6。与 NeoForge 1.21.1 的 JAR 不可混装；建议使用独立游戏实例和新世界。

构建：在仓库根目录执行 `gradle -p forge clean build`。JAR 位于 `forge/build/libs/`。默认预设为 Natural Automate（`realscale:natural_automate_three_realms`）。

CI 独立检出 Forge 与共享建筑素材/脚本，检查成品资源、建筑 NBT、服务端实际模板读取及客户端界面。只有实际通过的检查才算验收。Java 包名暂保留历史名称，不代表依赖 NeoForge。NeoForge 的快速出生点、上传拦截、实验性 Mixin 和 Chunky 生命周期注入尚未移植。Smooth Render 暂无上传限流；DH/Oculus 及真实客户端世界仍需专项测试。
