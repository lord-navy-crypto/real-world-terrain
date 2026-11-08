# Performance Phase 20 — Stable underground visibility sampling

## Change

The optional Distant Horizons cave-visibility classifier now uses two sampling rates:

- TRANSITION, invalid displacement, or meaningful movement: 10 ticks (0.5 seconds at 20 TPS).
- Stable OPEN_SURFACE or SEALED_CAVE with low displacement: 20 ticks (1 second).

Previously, the Distant Horizons path always ran the five-column sky probes, heightmap boundary checks, and possible bounded exterior sightlines every 10 ticks, including while the player stood still in a confidently classified area.

## Safety properties

- The first changed or ambiguous observation still moves immediately to TRANSITION.
- A surface-to-cave transition disables surface cave-LOD culling on its first observed cave sample; three consistent samples are still required before entering SEALED_CAVE.
- Unknown chunks, invalid movement values, ravines, portals and teleports remain fail-open.
- No chunks are force-loaded.
- The diagnostic-only profile keeps its existing cadence.
- The feature remains opt-in; the default client performs no visibility probes.

## Expected impact

Stable scenes perform half as many visibility samples, reducing repeated sky, heightmap and possible line-of-sight queries by up to 50% while stationary. This is a reduction in classifier work, not a measured whole-game FPS claim. Vanilla section meshes and chunk rendering are unchanged.

## Validation

JUnit tests cover both modes, transition responsiveness, movement-triggered fast sampling, and invalid displacement fallback. The NeoForge build and server acceptance workflow must pass before this phase is considered validated.
