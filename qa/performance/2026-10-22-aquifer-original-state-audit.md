# Native Minecraft Aquifer side-effect-safe hotspot investigation

Branch: experiment/vanilla-aquifer-readonly-audit. Main unchanged. This is deliberately **not a cache** or algorithm replacement.

Minecraft 1.21.1's original NoiseBasedAquifer returns a nullable BlockState from computeSubstance(context,density) and maintains shouldScheduleFluidUpdate() state for later fluid scheduling. Intercepting only returned water/air/stone would lose state; **do not cache this function's return in isolation**. Official Aquifer API: https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/minecraft/world/level/levelgen/aquifer

This opt-in Mixin runs AFTER the unmodified vanilla method and reads one in 256 returns, including caller-supplied density sign, returned-null/air/fluid/other, and read-only shouldScheduleFluidUpdate flag. Observing them causes no cancelled computation or changed return value. It is instrumentation rather than a performance optimization; results include time sampling caveats. JVM flag `-Drealscale.experimental.aquifer.audit=true` (OFF by default).

Two original 2096-high NeoForge presets, fresh far-away waypoints, vanilla structure generation, seeds 2 and 42. Probe and no-probe worlds are separate; saved Anvil blocks, fluid blocks, biomes, heightmaps and structures compared, with differences explicitly reported. Monitor is not a benchmark candidate because instrumentation adds overhead. Do not interpret the sample ratio as total CPU time, nor optimize Aquifer before demonstrating an invariant that preserves ALL fluid update side effects.
