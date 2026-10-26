# RealScale Terrain Archetype Fit v1.1

这是一个离线研究工具：从真实 DEM 拟合紧凑的地形形态参数，再用这些参数和新种子合成新的地形样本。它不会把原始 DEM 当作无限世界的高度图，也不会自动修改 NeoForge 运行时。

## 安装

    python3 -m pip install -r research/v1.1/requirements.txt

## 用法

输入应是经过来源核验的 GeoTIFF，或现有的 RGT1 压缩地形瓦片。区域至少需要 32 × 32 个有效样本，且每个方向至少约 1.5 km；更大的区域通常更适合估计多尺度地貌特征。

珠峰/喜马拉雅示例。请把路径、产品名称、URL 和垂直基准替换为实际下载文件的元数据；不要猜测垂直基准。

```bash
python3 research/v1.1/tools/fit_terrain_archetype.py \
  --dem /path/to/verified_everest_dem.tif \
  --source "Copernicus DEM GLO-30" \
  --source-url "https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM" \
  --vertical-datum "verify from the exact downloaded product metadata" \
  --family alpine \
  --out research/v1.1/profiles/everest_alpine_fit.json \
  --preview-out research/v1.1/previews/everest_alpine_seed42.npz \
  --seed 42
```

大峡谷样本应使用实际下载的 USGS 3DEP 文件，并把 family 设为 canyon。先核对数据产品元数据，再运行拟合。

## 输出

- 来源名称、来源 URL、输入格式、坐标系、网格间距、垂直基准和输入文件 SHA-256。
- 高程分位数、P05–P95 relief、坡度分布、多尺度起伏 RMS、方向半变异函数。
- 可供后续生成器使用的多尺度幅度、粗糙度指数、各向异性、山脊/谷地统计参数。
- 可选的 NPZ 合成预览。它只使用拟合系数和新种子，不再读取原始 DEM 网格。

## 重要边界

拟合 JSON 不包含原始高程网格；合成预览也是由参数生成的新样本。当前的谱噪声预览器只是研究原型，尚未接入 NeoForge DensityFunctions，不能称为已完成的游戏内生成模型。

当前仓库尚未包含经过核验的珠峰或大峡谷 DEM，也没有对应的真实拟合配置。合成测试数据仅用于测试，不得描述为真实地点的测量结果。

Copernicus DEM GLO-30 是数字表面模型（DSM），可能包含植被和建筑影响；USGS 3DEP 的分辨率与垂直基准取决于具体产品。参考：
- Copernicus DEM: https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM
- USGS 3DEP: https://www.usgs.gov/3d-elevation-program/about-3dep-products-services

Minecraft 当前 Overworld 的高度范围不足以同时保留珠峰绝对海拔和垂直 1:1。拟合出地貌形态系数并不等于已经解决这个高度限制。
