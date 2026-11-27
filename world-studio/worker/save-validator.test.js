import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { gzipSync, deflateSync } from "node:zlib";
import { validateWorldSave } from "./save-validator.js";

function nbtString(value) {
  const bytes = Buffer.from(value, "utf8");
  const result = Buffer.alloc(2 + bytes.length);
  result.writeUInt16BE(bytes.length, 0);
  bytes.copy(result, 2);
  return result;
}

function namedTag(type, name, payload) {
  return Buffer.concat([Buffer.from([type]), nbtString(name), payload]);
}

function intPayload(value) {
  const result = Buffer.alloc(4);
  result.writeInt32BE(value);
  return result;
}

function compoundPayload(children) {
  return Buffer.concat([...children, Buffer.from([0])]);
}

function levelDat(includeWorldGen = true) {
  const children = [namedTag(3, "DataVersion", intPayload(3953))];
  if (includeWorldGen) children.push(namedTag(10, "WorldGenSettings", compoundPayload([])));
  const data = compoundPayload(children);
  return gzipSync(Buffer.concat([
    Buffer.from([10]),
    nbtString(""),
    namedTag(10, "Data", data),
    Buffer.from([0])
  ]));
}

function chunkNbt(x = 0, z = 0) {
  return Buffer.concat([
    Buffer.from([10]),
    nbtString(""),
    namedTag(3, "DataVersion", intPayload(3953)),
    namedTag(3, "xPos", intPayload(x)),
    namedTag(3, "zPos", intPayload(z)),
    Buffer.from([0])
  ]);
}

async function createWorld() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "realscale-save-test-"));
  await fs.mkdir(path.join(root, "region"));
  await fs.writeFile(path.join(root, "level.dat"), levelDat());
  const compressed = deflateSync(chunkNbt());
  const chunkLength = compressed.length + 1;
  const sectors = Math.ceil((chunkLength + 4) / 4096);
  const region = Buffer.alloc((2 + sectors) * 4096);
  region.writeUIntBE(2, 0, 3);
  region.writeUInt8(sectors, 3);
  region.writeUInt32BE(chunkLength, 2 * 4096);
  region.writeUInt8(2, 2 * 4096 + 4);
  compressed.copy(region, 2 * 4096 + 5);
  await fs.writeFile(path.join(root, "region", "r.0.0.mca"), region);
  return root;
}

test("validates a real gzip level.dat and the exact requested Anvil chunk", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  const result = await validateWorldSave(world, {
    seed: "test", widthChunks: 1, depthChunks: 1
  });
  assert.equal(result.valid, true);
  assert.equal(result.minecraftDataVersion, 3953);
  assert.equal(result.requestedChunks, 1);
  assert.equal(result.verifiedChunks, 1);
  assert.equal(result.regionFiles, 1);
});

test("rejects a missing requested chunk even when a region file exists", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  const header = Buffer.alloc(4);
  const handle = await fs.open(path.join(world, "region", "r.0.0.mca"), "r+");
  await handle.write(header, 0, 4, 0);
  await handle.close();
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /chunk is missing/
  );
});

test("rejects malformed level.dat and truncated region headers", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  await fs.writeFile(path.join(world, "level.dat"), "not gzip");
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /incorrect header check|unknown compression method|unexpected end/i
  );
  await fs.writeFile(path.join(world, "level.dat"), levelDat());
  await fs.writeFile(path.join(world, "region", "r.0.0.mca"), Buffer.alloc(32));
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /region header/
  );
});

test("rejects world directories containing symbolic links", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  const outside = path.join(path.dirname(world), "outside-test-file");
  await fs.writeFile(outside, "private");
  t.after(() => fs.rm(outside, { force: true }));
  await fs.symlink(outside, path.join(world, "outside-link"));
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /symbolic link/
  );
});

test("rejects invalid region sector offsets", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  const handle = await fs.open(path.join(world, "region", "r.0.0.mca"), "r+");
  const invalid = Buffer.from([0, 0, 2, 255]);
  await handle.write(invalid, 0, 4, 0);
  await handle.close();
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /outside its region file/
  );
});


test("rejects valid compressed chunk NBT stored in the wrong region slot", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  const compressed = deflateSync(chunkNbt(9, 9));
  const chunkLength = compressed.length + 1;
  const sectors = Math.ceil((chunkLength + 4) / 4096);
  const region = Buffer.alloc((2 + sectors) * 4096);
  region.writeUIntBE(2, 0, 3);
  region.writeUInt8(sectors, 3);
  region.writeUInt32BE(chunkLength, 2 * 4096);
  region.writeUInt8(2, 2 * 4096 + 4);
  compressed.copy(region, 2 * 4096 + 5);
  await fs.writeFile(path.join(world, "region", "r.0.0.mca"), region);
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /mismatched coordinates/
  );
});


test("rejects a level.dat without persisted world-generation settings", async t => {
  const world = await createWorld();
  t.after(() => fs.rm(world, { recursive: true, force: true }));
  await fs.writeFile(path.join(world, "level.dat"), levelDat(false));
  await assert.rejects(
    validateWorldSave(world, { seed: "test", widthChunks: 1, depthChunks: 1 }),
    /WorldGenSettings/
  );
});
