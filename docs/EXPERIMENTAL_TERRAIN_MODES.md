# Experimental terrain modes

This branch preserves the full terrain-mode development catalog and is not the
conservative playable release target. Keep experimental work here while the
stable release branch exposes only Natural and USGS Big Sur V2.

Current experimental families include fitted alpine, canyon, glacial, volcanic,
karst, and dune terrain; smoothing probes; PR25 climate variants; and the other
world-preset/datapack experiments already present at this branch's snapshot.

Do not remove or rename experimental resources merely to simplify the stable
JAR. Evaluate each mode independently for server startup, distant-chunk
generation time, terrain continuity, biome behavior, and memory/CPU cost before
promoting it into the stable preset surface.
