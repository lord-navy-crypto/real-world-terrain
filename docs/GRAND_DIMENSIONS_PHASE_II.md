# Grand Dimensions — Phase II cavern continuity and worldgen regression

**Status:** Experimental follow-up to `feature/grand-nether-end-dimensions`. This branch is based on the still-draft PR #7. It does **not** modify `main` or existing world presets.

## Improvements implemented

### Grand Nether tunnel geometry
- Preserve the original two-hall-per-256×256-cell topology, chamber elevations near Y205/Y348, approximately 22-block-wide horizontal corridors and 20-block-wide interlayer corridors.
- Give each passage a *small deterministic lateral curve*, keyed to its two hall endpoints. Endpoints are placed in canonical order so both neighboring cells calculate exactly the same centerline.
- Preserve the hall/corridor cores: horizontal curves are bounded to ±4 blocks; diagonal-shaft offsets are halved to tolerate the additional small seeded displacement.
- Replace integer-rounded noise warp with continuous fractional coordinates. This removes the source of stair-stepped sampling when the displacement crosses half-block thresholds.
- Keep all extra excavation inside Y144–440. Nether lava, Y0–143 and the roof Y441–511 remain delegated to the previous generator.
- No world-seed-dependent *hall-center positions* have been introduced. The warp already depends on seeded world noise; the geometric topology still uses deterministic coordinate hashes. **Do not claim the lattice is fully seed-randomized.**

### Stronger proof from real saved blocks
The dedicated NeoForge server job loads additional chunk columns covering eight intervals of the known horizontal passage and checks nine actual block samples along both the horizontal and interlayer paths. The verifier asserts:
- All hall cores and sampled connector interiors are air in saved Anvil data.
- Nether lava exists at sampled old-layer Y31 coordinates and there is terrain above Y128.
- The Nether bedrock roof persists at Y511.
- Both distinct raised End island elevation tiers (Y160–224 and Y320–384) have positive end-stone sample counts in distant End chunks.
- The vanilla dragon-arena center remains air at Y160 and end stone at Y60.
- A standalone Java regression checks negative-to-positive X boundaries and fractional-coordinate cave continuity.

**Interpretation:** Nine checkpoint samples are *stronger evidence*, but are **not** a global proof of path-connectedness for every voxel of every corridor. The End test confirms generated stone at both sampled altitude tiers, not entire continent topology.

## Testing / release gates
1. Run `GrandNetherCaveNetworkSmoke`, NeoForge compile and the `grand-dimensions.yml` dedicated server workflow; verify the new per-tier metrics in the Anvil report.
2. Generate large, consecutive explored areas using different world seeds and compare air connectivity, memory use and chunk-generation times to the Phase I-B branch.
3. Playtest both positive and negative boundaries, roof and portal travel, Nether Fortress/Bastion interiors, dragon respawn, End gateways, End cities and ships.
4. Never change existing save height in place: create disposable new worlds with `realscale:realism_max_three_realms`. Keep this feature opt-in and do not merge until the tests above are satisfied.

## Compatibility
Minecraft 1.21.1 / Java 21 / NeoForge 21.1.x. Current realm keys stay `minecraft:the_nether` and `minecraft:the_end`; Overworld Realism MAX is unchanged.
