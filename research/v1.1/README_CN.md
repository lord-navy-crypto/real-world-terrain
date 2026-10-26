# RealScale Terrain Archetype Fit v1.1

这是一个离线研究工具，用真实 DEM 拟合紧凑的地形形态参数。它不是 NeoForge 模组，也不会把原始 DEM 作为无限世界的地形贴图。

## 依赖

    python3 -m pip install -r research/v1.1/requirements.txt

## 用法

准备一个经过来源核验的 GeoTIFF，或使用现有 RGT1 压缩地形瓦片。输入区域应至少有 32 × 32 个有效样本，并覆盖至少约 1.5 km；建议区域更大，才能可靠估计多尺度起伏。

珠峰/喜马拉雅样本示例（请把路径、产品名称、URL、垂直基准替换为实际下载文件的元数据）：

    python3 research/v1.1/tools/fit_terrain_archetype.py       --dem /path/to/verified_everest_dem.tif       --source "Copernicus DEM GLO-30"       --source-url "https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM"       --vertical-datum "verify from the exact downloaded product metadata"       --family alpine       --out research/v1.1/profiles/everest_alpine_fit.json

大峡谷样本则选择实际下载的 USGS 3DEP 产品，并将 family 设置为 canyon。不要仅为了让命令运行就填写猜测的垂直基准；必须先核对具体数据文件的元数据。

## 输出内容

- 来源文件 SHA-256、来源名称/URL、输入格式、坐标系和估算原生网格间距。
- 高程分位数、P05–P95 relief、坡度分布、不同空间尺度的起伏 RMS、方向半变异函数。
- 程序化生成器可使用的多尺度幅度、粗糙度指数、各向异性、山脊/谷地统计参数。
- 显式标记不嵌入原始高程网格，并要求后续生成器明确选择垂直映射策略。

拟合工具不会下载数据、不会把源 DEM 写进输出 JSON，也不会自动把参数接入 NeoForge。合成测试数据仅用于验证算法，不得描述为真实珠峰/大峡谷数据。

## 数据候选

- Copernicus DEM GLO-30：全球约 30 m 网格，但属于 DSM，可能包含植被/建筑影响。https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM
- USGS 3DEP：美国区域裸地 DEM，分辨率和覆盖取决于具体产品/地点。https://www.usgs.gov/3d-elevation-program/about-3dep-products-services

注意：当前 Minecraft Overworld 高度范围不足以同时保留珠峰绝对海拔与垂直 1:1。拟合出地貌形态系数，并不等于已经解决游戏高度限制。
