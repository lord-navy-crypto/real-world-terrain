import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { deflateRawSync } from "node:zlib";

const MAX_ARCHIVE_BYTES = 1_500_000_000;
const MAX_FILE_BYTES = 256 * 1024 * 1024;
const MAX_ENTRIES = 65_535;
const CRC_TABLE = new Uint32Array(256);

for (let index = 0; index < 256; index += 1) {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) {
    value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
  }
  CRC_TABLE[index] = value >>> 0;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date = new Date()) {
  const year = Math.max(1980, Math.min(2107, date.getUTCFullYear()));
  const time = (date.getUTCHours() << 11) | (date.getUTCMinutes() << 5) | Math.floor(date.getUTCSeconds() / 2);
  const day = (year - 1980) << 9 | ((date.getUTCMonth() + 1) << 5) | date.getUTCDate();
  return { time, day };
}

function localHeader(entry) {
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50, 0);
  header.writeUInt16LE(20, 4);
  header.writeUInt16LE(0x0800, 6);
  header.writeUInt16LE(entry.method, 8);
  header.writeUInt16LE(entry.time, 10);
  header.writeUInt16LE(entry.day, 12);
  header.writeUInt32LE(entry.crc, 14);
  header.writeUInt32LE(entry.compressedSize, 18);
  header.writeUInt32LE(entry.size, 22);
  header.writeUInt16LE(entry.nameBytes.length, 26);
  header.writeUInt16LE(0, 28);
  return header;
}

function centralHeader(entry) {
  const header = Buffer.alloc(46);
  header.writeUInt32LE(0x02014b50, 0);
  header.writeUInt16LE(0x0314, 4);
  header.writeUInt16LE(20, 6);
  header.writeUInt16LE(0x0800, 8);
  header.writeUInt16LE(entry.method, 10);
  header.writeUInt16LE(entry.time, 12);
  header.writeUInt16LE(entry.day, 14);
  header.writeUInt32LE(entry.crc, 16);
  header.writeUInt32LE(entry.compressedSize, 20);
  header.writeUInt32LE(entry.size, 24);
  header.writeUInt16LE(entry.nameBytes.length, 28);
  header.writeUInt16LE(0, 30);
  header.writeUInt16LE(0, 32);
  header.writeUInt16LE(0, 34);
  header.writeUInt16LE(0, 36);
  header.writeUInt32LE(0x81a40000, 38);
  header.writeUInt32LE(entry.localOffset, 42);
  return header;
}

async function collectFiles(root, directory = root) {
  const result = [];
  const entries = await fs.readdir(directory, { withFileTypes: true });
  entries.sort((a, b) => a.name.localeCompare(b.name, "en"));
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    const info = await fs.lstat(fullPath);
    if (info.isSymbolicLink()) throw new Error("Refusing to package a symbolic link");
    if (info.isDirectory()) {
      result.push(...await collectFiles(root, fullPath));
    } else if (info.isFile()) {
      const relative = path.relative(root, fullPath).split(path.sep).join("/");
      if (!relative || relative === "realscale-export.json" || relative.startsWith("/") || relative.split("/").some(part => part === ".." || part === "")) {
        throw new Error("Unsafe or reserved world archive path");
      }
      if (info.size > MAX_FILE_BYTES) throw new Error("World file exceeds the 256 MiB packaging limit: " + relative);
      result.push({ fullPath, relative, size: info.size });
    } else {
      throw new Error("Refusing to package a non-regular world file");
    }
  }
  return result;
}

function validateArchiveName(name) {
  if (typeof name !== "string" || !/^[a-z0-9][a-z0-9_-]{0,63}$/u.test(name)) {
    throw new TypeError("World archive name must be a safe lowercase identifier");
  }
}

async function writeAll(handle, buffer) {
  let offset = 0;
  while (offset < buffer.length) {
    const result = await handle.write(buffer, offset, buffer.length - offset);
    if (!result.bytesWritten) throw new Error("Unable to finish writing world archive");
    offset += result.bytesWritten;
  }
}

export async function createWorldArchive(worldDirectory, outputPath, { archiveName, manifest }) {
  validateArchiveName(archiveName);
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new TypeError("A world export manifest is required");
  }

  const worldDir = path.resolve(worldDirectory);
  const output = path.resolve(outputPath);
  const rootInfo = await fs.lstat(worldDir);
  if (!rootInfo.isDirectory() || rootInfo.isSymbolicLink()) throw new Error("World directory is not a real directory");

  const files = await collectFiles(worldDir);
  const manifestBytes = Buffer.from(JSON.stringify(manifest, null, 2) + "\n", "utf8");
  files.push({ fullPath: null, relative: "realscale-export.json", size: manifestBytes.length, bytes: manifestBytes });
  files.sort((a, b) => a.relative.localeCompare(b.relative, "en"));
  if (files.length > MAX_ENTRIES) throw new Error("World archive contains too many files");

  const { time, day } = dosDateTime();
  const localParts = [];
  const entries = [];
  let localOffset = 0;
  let archivePayloadBytes = 0;

  for (const file of files) {
    const raw = file.bytes ?? await fs.readFile(file.fullPath);
    if (raw.length !== file.size) throw new Error("World file changed while packaging: " + file.relative);
    const stored = /\.(?:mca|mcc|dat|gz|zip)$/iu.test(file.relative);
    const compressed = stored ? raw : deflateRawSync(raw, { level: 6 });
    const name = archiveName + "/" + file.relative;
    const nameBytes = Buffer.from(name, "utf8");
    if (nameBytes.length > 65_535 || raw.length > 0xffffffff || compressed.length > 0xffffffff) {
      throw new Error("World file exceeds ZIP32 limits: " + file.relative);
    }
    const entry = {
      nameBytes,
      method: stored ? 0 : 8,
      time,
      day,
      crc: crc32(raw),
      size: raw.length,
      compressedSize: compressed.length,
      localOffset
    };
    const header = localHeader(entry);
    localParts.push(header, nameBytes, compressed);
    localOffset += header.length + nameBytes.length + compressed.length;
    archivePayloadBytes += compressed.length;
    entries.push(entry);
    if (localOffset > 0xffffffff || archivePayloadBytes > MAX_ARCHIVE_BYTES) {
      throw new Error("World archive exceeds the configured 1.5 GB limit");
    }
  }

  const centralOffset = localOffset;
  const centralParts = [];
  let centralSize = 0;
  for (const entry of entries) {
    const header = centralHeader(entry);
    centralParts.push(header, entry.nameBytes);
    centralSize += header.length + entry.nameBytes.length;
  }
  if (centralOffset + centralSize > 0xffffffff) throw new Error("World archive exceeds ZIP32 offset limits");

  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(centralOffset, 16);
  end.writeUInt16LE(0, 20);

  await fs.mkdir(path.dirname(output), { recursive: true });
  const handle = await fs.open(output, "wx");
  const hash = createHash("sha256");
  let written = 0;
  try {
    for (const part of [...localParts, ...centralParts, end]) {
      await writeAll(handle, part);
      hash.update(part);
      written += part.length;
    }
    await handle.sync();
  } catch (error) {
    await handle.close();
    await fs.rm(output, { force: true });
    throw error;
  }
  await handle.close();

  return Object.freeze({
    archivePath: output,
    archiveBytes: written,
    sha256: hash.digest("hex"),
    fileCount: entries.length
  });
}
