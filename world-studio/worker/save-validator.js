import fs from "node:fs/promises";
import path from "node:path";
import { gunzipSync, inflateSync } from "node:zlib";
import { expectedChunkCoordinates, normalizeExportRequest } from "./export-contract.js";

const REGION_HEADER_BYTES = 8192;
const SECTOR_BYTES = 4096;
const MAX_NBT_BYTES = 16 * 1024 * 1024;

function readString(buffer, state) {
  if (state.offset + 2 > buffer.length) throw new Error("Truncated NBT string length");
  const length = buffer.readUInt16BE(state.offset);
  state.offset += 2;
  if (state.offset + length > buffer.length) throw new Error("Truncated NBT string");
  const value = buffer.toString("utf8", state.offset, state.offset + length);
  state.offset += length;
  return value;
}

function readPayload(buffer, state, type, depth = 0) {
  if (depth > 64) throw new Error("NBT nesting limit exceeded");
  const requireBytes = count => {
    if (count < 0 || state.offset + count > buffer.length) throw new Error("Truncated NBT payload");
  };
  switch (type) {
    case 1:
      requireBytes(1);
      return buffer.readInt8(state.offset++);
    case 2: {
      requireBytes(2);
      const value = buffer.readInt16BE(state.offset);
      state.offset += 2;
      return value;
    }
    case 3: {
      requireBytes(4);
      const value = buffer.readInt32BE(state.offset);
      state.offset += 4;
      return value;
    }
    case 4: {
      requireBytes(8);
      const value = buffer.readBigInt64BE(state.offset);
      state.offset += 8;
      return value;
    }
    case 5: {
      requireBytes(4);
      const value = buffer.readFloatBE(state.offset);
      state.offset += 4;
      return value;
    }
    case 6: {
      requireBytes(8);
      const value = buffer.readDoubleBE(state.offset);
      state.offset += 8;
      return value;
    }
    case 7: {
      requireBytes(4);
      const length = buffer.readInt32BE(state.offset);
      state.offset += 4;
      if (length < 0 || length > MAX_NBT_BYTES) throw new Error("Invalid NBT byte-array length");
      requireBytes(length);
      const value = buffer.subarray(state.offset, state.offset + length);
      state.offset += length;
      return value;
    }
    case 8:
      return readString(buffer, state);
    case 9: {
      requireBytes(5);
      const elementType = buffer.readUInt8(state.offset++);
      const length = buffer.readInt32BE(state.offset);
      state.offset += 4;
      if (length < 0 || length > 1_000_000 || (length > 0 && (elementType < 1 || elementType > 12))) {
        throw new Error("Invalid NBT list header");
      }
      const values = new Array(length);
      for (let index = 0; index < length; index += 1) {
        values[index] = readPayload(buffer, state, elementType, depth + 1);
      }
      return values;
    }
    case 10: {
      const compound = Object.create(null);
      let tagCount = 0;
      while (true) {
        requireBytes(1);
        const childType = buffer.readUInt8(state.offset++);
        if (childType === 0) break;
        if (childType < 1 || childType > 12) throw new Error("Unknown NBT tag type");
        const name = readString(buffer, state);
        compound[name] = readPayload(buffer, state, childType, depth + 1);
        tagCount += 1;
        if (tagCount > 100_000) throw new Error("NBT compound tag limit exceeded");
      }
      return compound;
    }
    case 11: {
      requireBytes(4);
      const length = buffer.readInt32BE(state.offset);
      state.offset += 4;
      if (length < 0 || length > 1_000_000) throw new Error("Invalid NBT int-array length");
      requireBytes(length * 4);
      const values = new Array(length);
      for (let index = 0; index < length; index += 1) values[index] = buffer.readInt32BE(state.offset + index * 4);
      state.offset += length * 4;
      return values;
    }
    case 12: {
      requireBytes(4);
      const length = buffer.readInt32BE(state.offset);
      state.offset += 4;
      if (length < 0 || length > 1_000_000) throw new Error("Invalid NBT long-array length");
      requireBytes(length * 8);
      const values = new Array(length);
      for (let index = 0; index < length; index += 1) values[index] = buffer.readBigInt64BE(state.offset + index * 8);
      state.offset += length * 8;
      return values;
    }
    default:
      throw new Error("Unsupported NBT tag type: " + type);
  }
}

function parseRootCompound(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 4 || buffer.length > MAX_NBT_BYTES) {
    throw new Error("NBT document has an invalid size");
  }
  const state = { offset: 0 };
  const rootType = buffer.readUInt8(state.offset++);
  if (rootType !== 10) throw new Error("NBT root tag must be a compound");
  const rootName = readString(buffer, state);
  const root = readPayload(buffer, state, 10);
  if (state.offset !== buffer.length) throw new Error("Unexpected trailing bytes after NBT root");
  return { rootName, root };
}

function decodeCompressedNbt(compression, bytes) {
  let decoded;
  if (compression === 1) decoded = gunzipSync(bytes, { maxOutputLength: MAX_NBT_BYTES });
  else if (compression === 2) decoded = inflateSync(bytes, { maxOutputLength: MAX_NBT_BYTES });
  else if (compression === 3) decoded = bytes;
  else throw new Error("Unsupported Anvil chunk compression type: " + compression);
  return parseRootCompound(decoded);
}

async function assertNoSymlinks(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    const info = await fs.lstat(fullPath);
    if (info.isSymbolicLink()) throw new Error("World save contains a symbolic link: " + entry.name);
    if (info.isDirectory()) await assertNoSymlinks(fullPath);
    else if (!info.isFile()) throw new Error("World save contains a non-regular file: " + entry.name);
  }
}

function regionCoordinates(chunkX, chunkZ) {
  const regionX = Math.floor(chunkX / 32);
  const regionZ = Math.floor(chunkZ / 32);
  const localX = chunkX - regionX * 32;
  const localZ = chunkZ - regionZ * 32;
  return {
    regionX,
    regionZ,
    headerIndex: (localX + localZ * 32) * 4
  };
}

async function inspectRegionChunk(regionPath, header, headerIndex) {
  const offset = header.readUIntBE(headerIndex, 3);
  const sectorCount = header.readUInt8(headerIndex + 3);
  if (offset < 2 || sectorCount < 1) return false;

  const info = await fs.stat(regionPath);
  const start = offset * SECTOR_BYTES;
  const allocationEnd = start + sectorCount * SECTOR_BYTES;
  if (allocationEnd > info.size || start + 5 > info.size) {
    throw new Error("Anvil chunk sector points outside its region file");
  }

  const handle = await fs.open(regionPath, "r");
  try {
    const chunkHeader = Buffer.alloc(5);
    const { bytesRead } = await handle.read(chunkHeader, 0, 5, start);
    if (bytesRead !== 5) throw new Error("Truncated Anvil chunk header");
    const length = chunkHeader.readUInt32BE(0);
    const compressionFlag = chunkHeader.readUInt8(4);
    const external = (compressionFlag & 0x80) !== 0;
    const compression = compressionFlag & 0x7f;
    if (length < 2 || length > sectorCount * SECTOR_BYTES - 4) {
      throw new Error("Anvil chunk length exceeds its allocated sectors");
    }
    if (![1, 2, 3].includes(compression)) {
      throw new Error("Unsupported Anvil chunk compression type: " + compression);
    }
    const compressed = Buffer.alloc(length - 1);
    const result = await handle.read(compressed, 0, compressed.length, start + 5);
    if (result.bytesRead !== compressed.length) throw new Error("Truncated Anvil chunk payload");
    if (external) throw new Error("External Anvil chunk streams are not supported by this exporter");
    const decoded = decodeCompressedNbt(compression, compressed);
    if (!decoded.root || typeof decoded.root !== "object") throw new Error("Chunk NBT root is invalid");
    return true;
  } finally {
    await handle.close();
  }
}

export async function validateWorldSave(worldDirectory, requestInput) {
  const request = normalizeExportRequest(requestInput);
  const worldDir = path.resolve(worldDirectory);
  const rootInfo = await fs.lstat(worldDir);
  if (!rootInfo.isDirectory() || rootInfo.isSymbolicLink()) {
    throw new Error("World save path must be a real directory");
  }
  await assertNoSymlinks(worldDir);

  const levelPath = path.join(worldDir, "level.dat");
  const levelBytes = await fs.readFile(levelPath);
  const levelNbt = gunzipSync(levelBytes, { maxOutputLength: MAX_NBT_BYTES });
  const level = parseRootCompound(levelNbt);
  const data = level.root?.Data;
  if (!data || typeof data !== "object" || !Number.isInteger(data.DataVersion)) {
    throw new Error("level.dat is missing the Minecraft DataVersion compound");
  }

  const regionDir = path.join(worldDir, "region");
  const regionInfo = await fs.lstat(regionDir);
  if (!regionInfo.isDirectory() || regionInfo.isSymbolicLink()) {
    throw new Error("World save is missing a valid region directory");
  }

  const headerCache = new Map();
  const verifiedChunks = [];
  for (const chunk of expectedChunkCoordinates(request)) {
    const region = regionCoordinates(chunk.x, chunk.z);
    const regionName = "r." + region.regionX + "." + region.regionZ + ".mca";
    const regionPath = path.join(regionDir, regionName);
    let header = headerCache.get(regionPath);
    if (!header) {
      const handle = await fs.open(regionPath, "r");
      try {
        header = Buffer.alloc(REGION_HEADER_BYTES);
        const read = await handle.read(header, 0, REGION_HEADER_BYTES, 0);
        if (read.bytesRead !== REGION_HEADER_BYTES) throw new Error("Truncated Anvil region header: " + regionName);
      } finally {
        await handle.close();
      }
      headerCache.set(regionPath, header);
    }
    if (!await inspectRegionChunk(regionPath, header, region.headerIndex)) {
      throw new Error("Expected generated chunk is missing: " + chunk.x + "," + chunk.z);
    }
    verifiedChunks.push(chunk);
  }

  const regionFiles = await fs.readdir(regionDir);
  const regionCount = regionFiles.filter(name => /^r\.-?\d+\.-?\d+\.mca$/u.test(name)).length;
  if (regionCount < 1) throw new Error("No Anvil region files were saved");

  return Object.freeze({
    valid: true,
    minecraftDataVersion: data.DataVersion,
    requestedChunks: request.chunkCount,
    verifiedChunks: verifiedChunks.length,
    regionFiles: regionCount,
    worldBytes: await directorySize(worldDir)
  });
}

async function directorySize(directory) {
  let total = 0;
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    const info = await fs.lstat(fullPath);
    if (info.isFile()) total += info.size;
    else if (info.isDirectory()) total += await directorySize(fullPath);
  }
  return total;
}

export { parseRootCompound };
