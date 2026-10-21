# Experimental Northern Coastal Fjords v4 (Draft PR #75)

**Status:** separate optional NeoForge 1.21.1 density codec and *unlisted* world preset. Do not distribute as a stable release yet.

The new `realscale:coastal_fjord_v4` codec consumes the immutable v3 height signal and uses site-stable absolute coordinates. At each cold, wet site it searches eight compass transects for an *already submerged* ocean endpoint (including a second seaward sample) and an elevated inland endpoint. Only qualifying transects are eroded into a sinuous U-shaped submerged channel. This addresses the core v3 limitation: the old inland gate prevented a trough from touching the sea.

- Standalone, production Java source: `CoastalFjordDensityV4.java`, including nested mathematical geometry.
- New density function graph and noise settings: `tva_cinematic_landforms_v4`.
- Opt-in world preset: `realscale:tva_northern_fjord_experiment_v4_three_realms`, **not** among the four player menu entries.
- v1/v2/v3 and integrated v1 sources/JSON remain unchanged. USGS measured mode is untouched.
- No direct fluid placement; aquifers and sea level Y=63 are inherited from v3.
- Cell-local 3072-block sites, with radius 1350 and maximum site jitter 96, give a guaranteed zero displacement before site boundaries.
- Carving never raises ground or seafloor, and no landlocked site qualifies merely because it is cold.
- Exact outlet and water accessibility are **not proved** by JDK checks: actual Minecraft aquifers, terrain density, cave intersections and chunk-to-chunk navigability need multi-seed 3D server inspection.

## Validate

Run `python3 -m unittest discover -s qa -p 'test_coastal_fjord_v4.py' -v` for the pure JDK math and data isolation checks, then `cd neoforge && gradle build` to prove the codec links against NeoForge. Launch a **new** disposable world with `level-type=realscale:tva_northern_fjord_experiment_v4_three_realms` to test real water connection. Do not use this preset in existing important saves.

The unchanged four player presets deliberately do not pick up v4 until real multi-seed coast geometry is approved. This experiment is not an image-based recreation of a fictional game region.

Scientific reference: US National Park Service, U-shaped valleys and fjords: https://www.nps.gov/articles/ushapedvalleysfjordshangingvalleys.htm
