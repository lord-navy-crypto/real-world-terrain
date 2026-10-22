## Independent JFR baseline for 3 vanilla hotspots

An entirely unmodified Perlin/Aquifer/Beardifier server should be separately profiled outside timed A/B/A. `classify_vanilla_jfr_hotspots.py` partitions actual Java thread leaf methods into mutually exclusive Perlin/ImprovedNoise, Beardifier, Aquifer, other worldgen and other code. It also lists potentially overlapping inclusive stack-presence counts and 25 top leaves. Never sum inclusive sample shares as CPU percentages; all categories measure sampled CPU stacks rather than exact elapsed time.

Collect both Sacred seed 42 and Measured seed 2 with real JFR during three strict 180-second distant completion sites, normal structures, unchanged 2096 height, and save original .jfr plus parsed reports as artifacts. This does not optimize Aquifer or mutate fluid state.
