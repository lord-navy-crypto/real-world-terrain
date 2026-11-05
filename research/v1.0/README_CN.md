# RealScale GeoEngine v1.0 REAL DATA — 实测高程导入与生成器原型

**这不是 NeoForge mod JAR。** 它是 Java 21 + Python 的独立真实数据转换、分析和地形方块生成核心。不能直接放进 `run/mods`。

## 完成的具体工作（有真实数据）

- 从 **USGS 3DEP EPQS** 官方服务下载了加利福尼亚州 Big Sur 附近 (36.3°N, -121.7°W) 的 **12×12=144 个真实查询结果**，将所有原始 JSON 回包和 SHA-256、网格坐标打包归档。原始 EPQS 值属于**从 DEM 内插的服务高程**，不是 144 个实地测量点。原服务返回 `resolution=1`，但本项目这些查询点横向间距约 **54m × 67m**，绝不宣称整片区域已测得每米一个高程。
- 原始高程范围 **883.0009–1298.2545 米**，以当地服务垂直高程基准为准；单点高度对应的精确大地基准仍待核对。没有捏造 NOAA ETOPO 数据：其在线 CSV 端点在当前自动获取环境返回了 HTTP 403。
- 以 1m Minecraft 坐标、32m 地形计算网格、**原始垂直尺度 1:1** 生成可重现 `.rgt.gz` 数据块。
- 高程导入后（32m 网格内插）范围 **896.909–1263.205 米**。游戏固体地面使用 `Y=63+实测高程`，没有任何垂直缩放或静默裁切；超出 Y=-64..2031 时直接拒绝。
- 输出真正依据这份 DEM 数据计算的 **16×16 米 Minecraft 原版方块列 CSV**，高度预测和方块放置共用一套 Java 数值对象，差异 0 米。
- 计算原始区域的坡度分布与 32/64/128/256m 方向半变异函数（semivariogram），以后可用这些统计量校准全新世界的山系宽度、坡度和各向异性。**统计相似不等于重建真实板块运动过程。**
- 预留真实年降水量（可用 12 个月 WorldClim GeoTIFF 合成）及经过独立校准的岩性抗侵蚀系数输入。缺少这些输入时，模拟模型中的降水和阻抗是显式标注的默认值，不能称为实测。

## 如何运行已下载的 Big Sur 数据（Java 21）

```bash
java -jar dist/RealScale_RealData_GeoCore_v1.0.jar \
  data/USGS_3DEP_BigSur_20261011/BigSur_Observed_16x16_32m.rgt.gz \
  dist/my_big_sur_test measured
```

输出 `measured_chunk_16x16.csv` 和 `measured_model_report.json`。

## 导入你下载的正式 USGS 3DEP 或 NOAA ETOPO GeoTIFF

依赖 Python 3 + `numpy`、`rasterio`、`scipy`。建议使用专门的 Python venv：

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install numpy rasterio scipy
python tools/import_dem.py \
  --dem '/path/to/real_usgs_3dep.tif' \
  --source 'USGS 3DEP (fill exact product name)' \
  --source-url 'https://apps.nationalmap.gov/downloader/' \
  --vertical-datum NAVD88 \
  --center-lat 36.3 --center-lon -121.7 \
  --cells 128 --cell-m 64 \
  --out 'dist/real_tile.rgt.gz'
```

输入的 TIFF 必须覆盖所选区域，并带正确的 CRS 坐标系。对于本开发包已经包含的 **USGS EPQS 导出的 ESRI ASCII**，若没有 `.prj` 文件，增加 `--source-crs EPSG:4326`。

**垂直基准很重要：** USGS 的 NAVD88、NOAA ETOPO 的 EGM2008，以及实际平均海面不是任何位置都精确相同。本模块记录基准，可以通过 `--vertical-offset-m` 使用**有来源依据的**转换值；绝不自动瞎猜。地形的 1 方块 1 米保持不变。

## 实际数据追溯与科学定义

- USGS 3DEP / EPQS：<https://apps.nationalmap.gov/epqs/>（逐点内插高程；不能视为直接测量）
- USGS 3DEP 高分辨率数据：<https://www.usgs.gov/3d-elevation-program/about-3dep-products-services>
- NOAA ETOPO 2022：<https://www.ncei.noaa.gov/products/etopo-global-relief-model>
- NASA NASADEM：<https://data.nasa.gov/dataset/nasadem-merged-dem-global-1-arc-second-v001>
- WWF HydroSHEDS：<https://www.worldwildlife.org/our-work/science/hydrosheds/>
- WorldClim 2.1：<https://www.worldclim.org/data/worldclim21.html>

### 关键区别

1. **真实地点复刻：** 原始 DEM 决定大地形，厘米或米级有效细节取决于源数据分辨率。按每 1m 输出 Minecraft 方块，仅表示方块尺度，不表示源 DEM 已有 1m 的精度。
2. **真实数据学到的规律：** 坡度直方图、各向异性、粗糙度尺度、谷地密度等统计特征是区域经验参数；真实地壳构造、岩性及流域仍需要额外独立数据才能构成因果生成系统。
3. **No new blocks：** 输出只使用 `minecraft:` 开头的原版方块 ID；源码没有向 Minecraft 注册任何自定义方块、材质或生物群系。
4. **高度上限硬限制：** 当前游戏测试范围 Y=-64..2031、海面 Y=63，允许的最高海拔为 1968m。无法无损导入超过世界高度上限的真实珠峰。不要为“真实1:1”偷偷压扁高度。
5. **并非无限地形：** 这个版本是在固定局部区域复刻真实高程，超出数据范围会报错；无限世界需要分区缓存、真实/合成边界混合与流量出口协议。
6. **并非已接入游戏：** 目前生成器产出 CSV 和模拟水文，并未向 NeoForge 注册 `ChunkGenerator`；没有执行这个版本的 NeoForge GameTest。

## 自动验证

运行 `bash build.sh`：

- 8 个 Python 单元测试（含真实 USGS 文件比对、月降水合成输入、边界检查和数据来源验证）。
- 保留 GeoEngine v0.9 原有 **167,892** 条 Java 数值回归断言。
- 新增对真实 USGS 32m 重采样数据的 **1,030** 条 Java 单元断言，覆盖 256 列固体地面和建筑高度一致性、原版方块 ID、无额外侵蚀、水流守恒、严禁越界。
- 原始 USGS 服务 JSON **144/144** 对应 ESRI ASCII 数值，误差只有存储 6 位小数的舍入。

## 后续真正的 Minecraft 集成

必须为 NeoForge 1.21.1 创建经验证的 `ChunkGenerator` 注册和 `MapCodec`，让 `fillFromNoise`、`getBaseHeight`、`getBaseColumn` 共用同一个 `MeasuredGeoTile`。按 Minecraft 地表规则放置原版块、地下原版岩石、水体，再使用 3 个以上世界种子做真实区块 GameTest，并检查原版村庄与结构落地。现阶段**不要把本项目 JAR 当作可玩的 Mod 安装**。
