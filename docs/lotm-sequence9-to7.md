# RealScale × Lord of the Mysteries: Sequence 9 → 7

此扩展仍是 **RealScale 内部功能**，不需要另装模组，也不另建 GitHub Project。当前为 PR #155，完成四条途径的序列9、8、7的可编译原型；未实现序列6以上。

## 1. 序列对应与能力

| 途径 | 序列9 | 序列8 | 序列7 | 游戏内增强 |
|---|---|---|---|---|
| 愚者 | 占卜家 | 小丑 | 魔术师 | 灵视与灵摆、协调性、短距幻步和障眼 |
| 隐者 | 窥秘人 | 格斗学者 | 巫师 | 非凡材料感知、基础身手、夜视及扩大观察 |
| 黑暗 | 不眠者 | 午夜诗人 | 梦魇 | 夜间视觉与精力、抚慰之歌、梦魇迟缓效果 |
| 死神 | 收尸人 | 掘墓人 | 通灵者 | 抗寒及亡灵感应、死亡之眼、灵性痕迹沟通 |

玩法**不等于**原著技能的完整实现；所有数值、效果持续时间、自然分布都是改编。

## 2. 晋升方式：常规魔药 / 完整特性

- **常规魔药**：每一级使用两份对应主材料、一份神秘学催化符和一只秘法瓶（序列9沿用原有配方）。
- **非凡特性**：遗迹缓存方块可掉落序列9、8、7中某一条途径的完整特性。该物品 **无合成配方**，可以直接右键使用，不需要另找魔药材料。
- 不能跨途径晋升，也不能从未晋升状态跳到序列8、7。此版到序列7封顶。
- 升序列8或7前，常规魔药默认需要至少70/100消化熟练度；直接使用未经调和的特性需要90/100，使用后灵性降到30并记录不稳定次数。此为游戏平衡，非原著固定数值。
- 序列5前不引入完整晋升仪式；不要将上述自创制作方法当作现实或原著配方。

### 全部新增的 8→7 主材料

| 途径 | 序列8主材料 | 序列7主材料 |
|---|---|---|
| 愚者 | 霍纳奇斯灰山羊角晶 + 人脸玫瑰 | 迷雾树人真根 + 邪纹黑豹精华 |
| 隐者 | 铁臂兽脊骨 + 四眼绿龟灵核 | 鹿头兽人之角 + 灰鸟祖母之眼 |
| 黑暗 | 红月咆哮者声核 + 摄魂风铃花 | 食梦黑鸦之心 + 噩梦之影凝核 |
| 死神 | 告死乌鸦之眼 + 墓园血玫瑰 | 幽灵怪猫之爪 + 灵界晶石 |

名称来自设定，部分材料在游戏里做成抽象灵核/精华，不使用真实生物组织制作教程。

## 3. 材料生态与尺度

这是 **Minecraft 数量与概率**，不是小说中的现实计量：

| 类型 | 默认数据 | 更改方法 |
|---|---|---|
| 星晶、受诅晶、冥灵晶矿 | 对应群系每区块2–3次矿脉尝试，每脉5方块 | 修改对应 `placed_feature` 的 `count`，`configured_feature` 的 `size` |
| 灵界晶矿 | 适配沼泽/黑森林/针叶林，区块2次尝试，深度-55至12 | `lotm_spirit_quartz_ore.json` 配置 |
| 午夜美人花 + 3种新花 | 基础稀有度约每合格区块1/34–1/42次花簇尝试 | 修改各自 `placed_feature` 的 `rarity_filter.chance` |
| 17类特殊生物变种 | 匹配自然群系后，原版生物生成时以约1/20至1/54概率变成特殊变种 | `variantRatePercent` 参数 |
| 古代遗迹匣 | 合格区块约每20次放置尝试入选1次，另按 `relicRarity=8` 二次筛选 | 调整 `placed_feature` 概率或通用配置 |
| 完整非凡特性 | 遗迹匣随机出一份；序列9/8/7权重约67%/25%/8% | 源码 `LotmRelicCacheBlock` 可后续开放自定义权重 |

这些是生成 **尝试**，不是每个区块必然有矿脉或建筑。结构空间、生态匹配和地形条件可能降低实际生成量；特殊生物首先要有原版生物实际生成。

## 4. 自定义配置

此版由 `RealScaleMod` 注册 Common 配置，游戏目录内在生成配置文件后可以调整 `lotm` 小节：

```toml
[lotm]
  enabled = true
  creatureVariants = true
  relicSites = true
  variantRatePercent = 100
  relicRarity = 8
  preparedPractice = 70
  characteristicPractice = 90
  spiritRecovery = 2
```

配置更改与生物生成规则按服务端实际加载配置为准；不要把客户端改过的配置视作服务端规则。

**世界生成开关的重要区别：** `enabled=false` 会停止游戏序列操作和遗迹放置逻辑，但是已经通过 datapack 注入的矿物和花朵配置不会自动从生成列表消失。彻底停用新增矿物/花朵请用 datapack 覆盖
`data/realscale/neoforge/biome_modifier/lotm_*.json`
为 `{"type":"neoforge:none"}`；已生成的区块不会被回滚。NeoForge 的这种覆盖机制有官方文档支持。

## 5. 游玩与测试指令

```mcfunction
/lotm guide
/lotm status
/lotm materials
/lotm config
/give @s realscale:lotm_spirit_focus
/give @s realscale:lotm_divination_pendulum
/give @s realscale:lotm_seer_s8_potion
/give @s realscale:lotm_seer_s8_characteristic
/give @s realscale:lotm_relic_cache
```

`/give` 需要测试世界相应权限，普通玩家也可通过 `/lotm` 查询。

**非凡特性测试**：先用任一序列9魔药进入途径，再使用聚焦器或灵摆提升熟练度；分别测试常规魔药和同途径同序列特性晋升，确保旧序列不丢失，且不能跨途径或跳级。

## 6. 原著与技术资料

- 官方设定集：https://h5.if.qidian.com/h5/workSet/main?albumId=131&bookId=1010868264&catelogId=69153
- 次级交叉资料：https://lordofthemysteries.fandom.com/zh/wiki/非凡途径
- NeoForge 1.21.1 生物群系修饰符：https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- NeoForge 1.21.1 配置：https://docs.neoforged.net/docs/1.21.1/misc/config/

原著：序列名称、特性可代替主要材料、直接使用特性的风险、扮演消化、序列5起的仪式。
改编：现实生态适配、Minecraft 方块和材料、具体概率、物品合成、消化门槛与施法效果。

## 7. 留待实际游玩验收

1. Minecraft 客户端：四条途径序列9→8→7的12级玩家状态及能力逐项验收。
2. 自然世界：实际找到四种群系资源、多个特殊生物，并在新区块中发现遗迹匣。
3. 游戏性能：测试区块生成、指令扫描、实体状态和现有 RealScale 结构/DH 兼容。
4. 角色存档：下线重进、死亡重生及原有世界存档续玩。
5. 视觉：目前为程序生成原创像素图和原版实体占位，尚非独立3D实体模型。
