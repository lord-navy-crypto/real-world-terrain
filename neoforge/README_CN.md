# RealScale 1.21.1 Final Battle — 游戏集成候选版（真实 USGS 地形）

- **NeoForge 1.21.1 Java Mod**，不再是以前的 lowcodefml JAR。
- 保留 v0.6 的七种原有预设；新增 `realscale:usgs_big_sur`。
- 对约 480×480 米区域使用 USGS 3DEP EPQS Big Sur 样本派生的 32 米栅格；内侧 96 米以 smoothstep 混合回 v0.6 世界。
- 原数据提供约 54-67 米水平间距信息；不是每米实际勘测，1m 方块来自插值。
- 垂直 1 方块=1 米，没有将 1263m 压成 200m。世界 sea level=Y63。
- 没有新方块、物品、群系、结构，全部使用已有材质。
- 用同一密度函数 (`usgs_big_sur/terrain_density`) 作为 final_density 和 preliminary_density 基础，旨在避免结构高度不一致；尚需实机验证建筑。
- 此版故意没有宣称河流、侵蚀、真实气候已经集成。它是 DEM 实际落地集成版。
- 只能用于新建实验世界，不要加载 v0.6 旧存档，不要与 v0.6 JAR 同时安装。
- DEM 区域按世界原点居中（[-240,240] m），超出范围使用原始程序化 Realism MAX。突变坡度风险需要视觉和实际结构测试。
- 如果构建和游戏测试尚未通过，请勿当作稳定发布版。

## 游戏启动

```
cd ~/Desktop/MinecraftDev/SWARM-MOBS && gradle runClient
```

使用游戏创建世界菜单选择 USGS Big Sur Real DEM Pilot；地形插值覆盖原点周围约半公里。真实地理位置在美国 Big Sur，但世界坐标不是地理经纬度。
