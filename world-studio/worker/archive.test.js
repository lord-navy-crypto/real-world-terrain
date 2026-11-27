import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { inflateRawSync } from "node:zlib";
import { createWorldArchive } from "./archive.js";

function readZip(buffer) {
  let end = -1;
  for (let index = buffer.length - 22; index >= Math.max(0, buffer.length - 65_557); index -= 1) {
    if (buffer.readUInt32LE(index) === 0x06054b50) {
      end = index;
      break;
    }
  }
  assert.ok(end >= 0, "ZIP end record exists");
  const count = buffer.readUInt16LE(end + 10);
  const centralOffset = buffer.readUInt32LE(end + 16);
  const files = new Map();
  let cursor = centralOffset;
  for (let index = 0; index < count; index += 1) {
    assert.equal(buffer.readUInt32LE(cursor), 0x02014b50);
    const method = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const size = buffer.readUInt32LE(cursor + 24);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const localOffset = buffer.readUInt32LE(cursor + 42);
    const name = buffer.toString("utf8", cursor + 46, cursor + 46 + nameLength);
    const localNameLength = buffer.readUInt16LE(localOffset + 26);
    const localExtraLength = buffer.readUInt16LE(localOffset + 28);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = buffer.subarray(dataStart, dataStart + compressedSize);
    const content = method === 0 ? compressed : inflateRawSync(compressed);
    assert.equal(content.length, size);
    files.set(name, content);
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

test("packages a world folder into a readable ZIP with a manifest and checksum", async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "realscale-archive-test-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const world = path.join(root, "world");
  await fs.mkdir(path.join(world, "region"), { recursive: true });
  await fs.writeFile(path.join(world, "level.dat"), Buffer.from([1, 2, 3, 4]));
  await fs.writeFile(path.join(world, "region", "r.0.0.mca"), Buffer.alloc(8192, 7));
  const output = path.join(root, "exports", "world.zip");
  const result = await createWorldArchive(world, output, {
    archiveName: "realscale-world",
    manifest: { seed: "test", chunkCount: 1, vanillaCompatible: false }
  });
  const archive = readZip(await fs.readFile(output));
  assert.equal(archive.get("realscale-world/level.dat").toString("hex"), "01020304");
  assert.equal(archive.get("realscale-world/region/r.0.0.mca").length, 8192);
  const manifest = JSON.parse(archive.get("realscale-world/realscale-export.json").toString("utf8"));
  assert.equal(manifest.seed, "test");
  assert.equal(manifest.vanillaCompatible, false);
  assert.equal(result.fileCount, 3);
  assert.equal(result.sha256.length, 64);
  assert.equal(result.archiveBytes, (await fs.stat(output)).size);
});

test("refuses to overwrite an existing archive", async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "realscale-archive-test-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const world = path.join(root, "world");
  await fs.mkdir(world);
  const output = path.join(root, "world.zip");
  await fs.writeFile(output, "existing");
  await assert.rejects(
    createWorldArchive(world, output, { archiveName: "safe-world", manifest: {} }),
    /EEXIST/
  );
});

test("rejects unsafe archive names and symbolic links", async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "realscale-archive-test-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const world = path.join(root, "world");
  await fs.mkdir(world);
  const outside = path.join(root, "outside");
  await fs.writeFile(outside, "not part of world");
  await fs.symlink(outside, path.join(world, "linked-file"));
  await assert.rejects(
    createWorldArchive(world, path.join(root, "world.zip"), {
      archiveName: "../escape",
      manifest: {}
    }),
    /safe lowercase identifier/
  );
  await assert.rejects(
    createWorldArchive(world, path.join(root, "world.zip"), {
      archiveName: "safe-world",
      manifest: {}
    }),
    /symbolic link/
  );
});
