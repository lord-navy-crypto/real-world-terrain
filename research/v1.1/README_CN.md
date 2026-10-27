# RealScale Terrain Archetype Fitting v0.2

本工具链负责把真实地形 DEM 转换为紧凑的地貌统计参数，再由新种子合成不同地形；它不把原始高度图当作无限世界地图。

## 默认模式与六种数据拟合模式

- **Natural Landscapes**：默认模式，不绑定某一个真实地点。
- **Alpine Mountains**：珠峰/喜马拉雅山脊。
- **Canyons & Valleys**：美国大峡谷的峡谷壁、台地和谷地尺度。
- **Glacial Valleys**：优胜美地冰川谷地的 U 形谷、谷宽与坡壁统计。
- **Volcanic Landscapes**：夏威夷盾状火山与火山口形态。
- **Karst Landscapes**：中国桂林喀斯特峰林、峰丛和洼地。
- **Sand Dunes**：纳米布沙海的沙丘主波长、脊线方向和不对称性。

后六种目标都要使用真实 DEM 拟合。默认数据基线为 Copernicus DEM GLO-90 公共 COG，垂直基准 EGM2008，水平坐标 WGS84，单位为米。GLO-90 名义分辨率约 90 米，是 DSM（数字表面模型），包含植被和人工地物影响，适合宏观地貌形态，不足以解析细小悬崖、喀斯特小洼地或单个沙丘波纹。美国境内的峡谷、冰川谷和火山目标可在核验实际产品元数据后换用 USGS 3DEP 更高分辨率裸地 DEM。

## 获取真实数据并生成拟合配置

安装依赖：

```bash
python3 -m pip install -r research/v1.1/requirements.txt
```

获取六个公开 GLO-90 栅格并生成拟合参数与新种子预览：

```bash
python3 research/v1.1/tools/build_terrain_profiles.py \
  --all \
  --seed 42 \
  --out-dir build/terrain-archetype-calibration
```

也可以只处理一个目标，例如：

```bash
python3 research/v1.1/tools/build_terrain_profiles.py \
  --target grand_canyon \
  --seed 42 \
  --out-dir build/terrain-archetype-calibration
```

输出包括：

- `profiles/*.json`：来源 URL、实际下载瓦片 SHA-256、裁切 DEM SHA-256、坐标系、网格间距、垂直基准和拟合参数。
- `previews/*.npz`：只根据拟合参数与新种子生成的合成高度场，不包含原始 DEM。
- `calibration_summary.json`：六个目标的拟合摘要和产物哈希。
- `inputs/`：本地下载的真实源瓦片与裁切栅格，不应提交到 Git 仓库。

目标区域和数据源记录在 `research/v1.1/data/terrain_dem_targets.json`。数据获取工具会校验瓦片、裁切范围和坐标系；如果下载源不可用或研究窗口超出瓦片，它会失败，而不是悄悄改用合成数据。

## 拟合方法

每个区域提取高程分位数、P05–P95 高差、坡度分布、多尺度起伏 RMS、方向半变异函数、主导空间波长与方向。六种生成核分别突出山脊、峡谷陡壁、U 形谷、盾状火山/火山口、喀斯特峰林/洼地和风向沙丘脊线。

这些是基于真实栅格统计的参数化地貌模型，不是神经网络，也不意味着模型推断出了真实地质成因。真实数据只用于拟合，不应直接复制成无限世界的高度图。

## 当前边界

- 该流水线会从公开数据源下载真实栅格；首次成功执行后，生成的拟合 JSON 才能被称为对应目标的真实数据拟合结果。
- 合成预览器已根据七类核生成可重复的新高度样本，但它仍是离线研究预览。
- 拟合参数尚未正式编译进 NeoForge DensityFunctions；不能声称游戏内六个地貌预设已经使用这些真实拟合系数。
- 当前 Overworld 的垂直高度预算无法在垂直 1 方块 = 1 米的条件下容纳珠峰绝对海拔；后续必须明确选择相对高程映射或垂直压缩。
- 不要提交原始 DEM 瓦片。保留官方来源、数据产品版本、垂直基准、许可要求和哈希即可。

## 权威数据来源

- Copernicus DEM GLO-90 公共数据说明与瓦片命名：<https://copernicus-dem-90m.s3.amazonaws.com/readme.html>
- Copernicus DEM 产品规格与垂直基准：<https://dataspace.copernicus.eu/sites/default/files/media/files/2024-06/geo1988-copernicusdem-spe-002_producthandbook_i5.0.pdf>
- Copernicus DEM 公共数据集与许可说明：<https://registry.opendata.aws/copernicus-dem/>
- USGS 3DEP 产品、分辨率和裸地 DEM 说明：<https://www.usgs.gov/3d-elevation-program/about-3dep-products-services>
- USGS 约 10 米 1/3 arc-second DEM 产品元数据：<https://data.usgs.gov/datacatalog/data/USGS%3A3a81321b-c153-416f-98b7-cc8e5f0e17c3>


## 已拟合的六个真实地貌区域（GLO-90）

以下统计来自已下载并裁切的真实 DEM；`P95–P05` 是研究窗口内高程第 95 与第 5 百分位之差。主导波长来自按地貌类别限制频带后的二维频谱峰值，用作宏观空间尺度参数，不等同于单个山峰、峡谷或沙丘的实际宽度。

| 预设 | 研究区域 | P95–P05 高差 | 拟合主导波长 | 归一化起伏系数 |
|---|---|---:|---:|---:|
| 高山 | 珠峰/喜马拉雅 | 3,863.39 m | 18.15 km | 0.9658 |
| 峡谷 | 美国大峡谷 | 1,454.54 m | 34.29 km | 0.3636 |
| 冰川山谷 | 优胜美地 | 2,854.78 m | 17.31 km | 0.7137 |
| 火山 | 夏威夷 | 2,942.26 m | 25.04 km | 0.7356 |
| 喀斯特 | 桂林 | 1,054.56 m | 7.94 km | 0.2636 |
| 沙丘 | 纳米布沙海 | 590.74 m | 4.78 km | 0.1477 |

归一化起伏系数按 `clip((P95-P05)/4000, 0.10, 1.00)` 计算，作为 Minecraft 密度信号的相对幅度权重；它**不是**把源 DEM 的绝对海拔直接映射到游戏世界。运行时内核将波长与主导方向用于高山脊线、峡谷走向、冰川谷宽、火山盾形尺度、喀斯特峰体/洼地尺度及沙丘脊线，世界种子仍会改变程序化细节。

六个源瓦片与六个裁切栅格的 SHA-256、源 URL、坐标间距和垂直基准都记录在 `research/v1.1/profiles/*.json` 及 `terrain-profile-provenance.json` 中。原始 DEM 瓦片和裁切高度网格不进入仓库；只提交紧凑拟合参数和来源证明。
