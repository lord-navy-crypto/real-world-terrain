import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { buildServerProperties, resolveRunDirectory } from "./minecraft-runner.js";
import { normalizeExportRequest } from "./export-contract.js";

test("server properties pin the real generator, seed, loopback ports, and isolated world folder", () => {
  const request = normalizeExportRequest({
    seed: "river-valley-42",
    preset: "alpine",
    widthChunks: 4,
    depthChunks: 6
  });
  const properties = buildServerProperties(request, {
    worldName: "realscale-export-12345678",
    serverPort: 25565,
    rconPort: 25575,
    rconPassword: "random-secret"
  });
  assert.match(properties, /level-seed=river-valley-42/);
  assert.match(properties, /level-type=realscale:alpine_three_realms/);
  assert.match(properties, /level-name=realscale-export-12345678/);
  assert.match(properties, /server-ip=127\.0\.0\.1/);
  assert.match(properties, /rcon\.ip=127\.0\.0\.1/);
  assert.match(properties, /enable-rcon=true/);
  assert.match(properties, /online-mode=false/);
  assert.match(properties, /rcon\.password=random-secret/);
  assert.match(properties, /spawn-chunk-radius=0/);
});

test("server properties never interpolate unvalidated newlines from a seed", () => {
  assert.throws(() => normalizeExportRequest({ seed: "first\nlevel-type=minecraft:normal" }), /Seed/);
});

test("uses the actual ModDevGradle runServer game directory", () => {
  const neoforgeDirectory = path.resolve("/workspace/project/neoforge");
  assert.equal(resolveRunDirectory(neoforgeDirectory), path.join(neoforgeDirectory, "run"));
  assert.notEqual(resolveRunDirectory(neoforgeDirectory), path.join(neoforgeDirectory, "run", "server"));
});
