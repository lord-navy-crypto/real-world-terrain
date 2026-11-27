import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createServer } from "node:http";
import { createWorldExportServer } from "./server.js";

function createFakeManager() {
  const archive = {
    path: null,
    filename: "realscale-world-test.zip",
    bytes: 4,
    sha256: "b".repeat(64)
  };
  return {
    activeJobId: null,
    queue: [],
    create(input) {
      return {
        id: "12345678-1234-1234-1234-123456789012",
        status: "queued",
        request: input
      };
    },
    get(id) {
      return id === "12345678-1234-1234-1234-123456789012"
        ? { id, status: "running" }
        : null;
    },
    cancel(id) {
      return id === "12345678-1234-1234-1234-123456789012"
        ? { id, status: "cancelled" }
        : null;
    },
    list() {
      return [];
    },
    getDownload(id) {
      return id === "12345678-1234-1234-1234-123456789012" && archive.path
        ? archive
        : null;
    },
    setArchive(value) {
      archive.path = value;
    }
  };
}

async function withServer(manager, callback) {
  const server = createWorldExportServer({ manager });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  try {
    await callback("http://127.0.0.1:" + address.port);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
}

test("health, create, status, list, and cancellation routes work", async () => {
  const manager = createFakeManager();
  await withServer(manager, async base => {
    const health = await fetch(base + "/api/health");
    assert.equal(health.status, 200);
    assert.equal((await health.json()).worker, "realscale-world-export");

    const create = await fetch(base + "/api/world-exports", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ seed: "demo" })
    });
    assert.equal(create.status, 202);
    const id = (await create.json()).job.id;

    const status = await fetch(base + "/api/world-exports/" + id);
    assert.equal((await status.json()).job.status, "running");

    const list = await fetch(base + "/api/world-exports");
    assert.deepEqual((await list.json()).jobs, []);

    const cancel = await fetch(base + "/api/world-exports/" + id + "/cancel", { method: "POST" });
    assert.equal((await cancel.json()).job.status, "cancelled");
  });
});

test("rejects non-local origins, malformed JSON, and non-JSON bodies", async () => {
  const manager = createFakeManager();
  await withServer(manager, async base => {
    const origin = await fetch(base + "/api/health", { headers: { origin: "https://evil.example" } });
    assert.equal(origin.status, 403);

    const malformed = await fetch(base + "/api/world-exports", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{"
    });
    assert.equal(malformed.status, 400);

    const contentType = await fetch(base + "/api/world-exports", {
      method: "POST",
      headers: { "content-type": "text/plain" },
      body: "seed"
    });
    assert.equal(contentType.status, 415);
  });
});

test("streams only an existing regular archive and sets a checksum header", async t => {
  const manager = createFakeManager();
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), "realscale-api-test-"));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const archive = path.join(directory, "world.zip");
  await fs.writeFile(archive, Buffer.from([1, 2, 3, 4]));
  manager.setArchive(archive);

  await withServer(manager, async base => {
    const response = await fetch(base + "/api/world-exports/12345678-1234-1234-1234-123456789012/download");
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("content-type"), "application/zip");
    assert.equal(response.headers.get("x-realscale-sha256"), "b".repeat(64));
    assert.deepEqual([...new Uint8Array(await response.arrayBuffer())], [1, 2, 3, 4]);
  });
});

test("returns 404 for unknown jobs and 409 for unavailable downloads", async () => {
  const manager = createFakeManager();
  await withServer(manager, async base => {
    const unknown = await fetch(base + "/api/world-exports/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assert.equal(unknown.status, 404);
    const unavailable = await fetch(base + "/api/world-exports/12345678-1234-1234-1234-123456789012/download");
    assert.equal(unavailable.status, 409);
  });
});
