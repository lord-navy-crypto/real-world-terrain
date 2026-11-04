# RealScale integrated Distant Horizons × Chunky guard

## Integration status

The former `dh-chunky-guard` standalone NeoForge project is now part of the
RealScale source set. Its classes and Mixin configuration are packaged in the
single RealScale JAR. There is no second compatibility JAR to install.

## Scope

- Minecraft 1.21.1, Java 21, NeoForge 21.1.x
- Distant Horizons 3.3.3
- Chunky 1.4.23
- RealScale remains usable when either optional mod is absent.

The embedded Mixin activates only when both exact tested versions are present.
If either mod is missing, or a different version is installed, the Mixin plugin
skips the guard and RealScale continues without this compatibility patch.
Distant Horizons and Chunky are not bundled or made required dependencies.

## What it fixes

The known failure occurs when DH attempts its first Chunky event-listener bind
before Chunky's provider has registered. In that narrow state,
`ChunkyProvider.get()` throws `IllegalStateException: Chunky is not loaded.`.

The guard checks DH's existing `listenerBound` flag first. Before the first
successful bind, it probes the Chunky provider. It cancels only the premature
first-time setup call, without changing DH's flag, so DH can retry on the next
real chunk event. It handles only the confirmed `IllegalStateException`;
unexpected errors remain visible. Once DH's native listener is bound, the
normal chunk-event path returns after a field read.

The read-only commands remain available:

- `/dhchunkyguard status`
- `/dhchunkyguard advice`

They report provider probes, deferred attempts and confirmed native listener
binding. They do not control Chunky or DH schedulers.

## Explicit non-goals and verification limits

This integration does not alter RealScale terrain generation, the 2096-block
Overworld height, saved chunks, DH's LOD generator/scheduler, Iris, Sodium, or
shaderpack rendering. A successful native listener bind is not proof that
GPU-rendered distant LOD is visible.

**GPU LOD, Iris, Sodium and shaderpack compatibility remain unverified for this
integrated build.** They require physical-client A/B testing; headless server
CI cannot certify them.

## Build and validation

The integrated compatibility tests are in `qa/test_dh_chunky_*.py`. The
`dh-chunky-integrated` workflow builds the main `neoforge/` project and
checks that the compatibility classes and optional Mixin configuration are
inside the RealScale JAR, with no third-party DH/Chunky binaries bundled.
