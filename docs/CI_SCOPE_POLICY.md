# CI scope and acceptance policy

## Focused development: Geometry Clipmap PR #164

This Draft PR is intentionally scoped to distant visible-surface rendering and Iris compatibility. Four expensive workflow families remain enabled automatically: source integrity / NeoForge build, Geometry Clipmap test JAR, real Iris client rendering, and the measured 180-second regression (the JFR harness was changed).

The 22 other workflows retain their test steps and existing manual/push triggers. Their jobs skip while PR #164 remains Draft; the condition is deliberately limited to this PR, not all Draft PRs. Moving the PR to ready for review re-enables the domain regression workflows. Fitted preset publication remains opt-in via its existing publication marker. GitHub may display skipped checks because PR path filters evaluate the cumulative diff; skipped jobs do not allocate the expensive runners.

## Path rules

Domain PR triggers target their source, resources and test harnesses instead of all of neoforge/**. Common world-generation resources and relevant shared harnesses still trigger their consumers. Main push coverage and workflow_dispatch are preserved. Workflows cancel superseded runs within the same ref.

## Release acceptance

Before merging or releasing, run the full applicable integration suite and inspect actual results; a skipped Draft job is not a pass. Keep the clipmap experiment Draft until real client evidence demonstrates distant depth-passing pixels during movement, multiple fresh centres, and the expected surface-only geometry. Validate the no-shader path and Apple Silicon separately; neither is proven by the Linux Iris test. Do not merge main automatically.

