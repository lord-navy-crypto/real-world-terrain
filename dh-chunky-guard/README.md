# RealScale — Distant Horizons × Chunky compatibility module

This independent NeoForge addon lives **in the same RealScale repository** at
`dh-chunky-guard/`. It produces a **separate JAR**: do not bundle it in the
RealScale terrain mod or force players to install DH and Chunky.

### Version history

See [CHANGELOG.md](CHANGELOG.md) for v0.1 → v0.2 → v0.3 changes, verified behavior and current limitations.

### Supported test combination
- Minecraft 1.21.1 / Java 21 / NeoForge 21.1.x
- Distant Horizons 3.3.3
- Chunky 1.4.23
- RealScale optional

The addon guards one confirmed startup race: DH may ask
`ChunkyProvider.get()` before Chunky is registered. It retries DH's **native**
event binding at the next chunk event, without suppressing DH generation.
Read-only `/dhchunkyguard status` and `/dhchunkyguard advice` report the
lifecycle. Neither proves that Iris shaders or distant GPU LOD rendered.

### Build and install
```sh
cd dh-chunky-guard
gradle --no-daemon build
```
Install exactly **one** DH–Chunky Guard JAR in the `mods` folder alongside
the version-pinned DH and Chunky. No third-party JAR is bundled here. Keep an
old known-working build available for rollback when testing.

### Release status and ownership
Experimental: real server startup/handshake tests passed, but complete physical
client / Iris / Sodium verification for this release is not yet certified.
This repository's root LICENSE currently reserves source licensing decisions
to its rights holder; no blanket MIT grant is implied by adding this module.
