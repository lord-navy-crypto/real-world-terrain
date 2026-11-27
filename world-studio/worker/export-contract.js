export const MINECRAFT_VERSION = "1.21.1";
export const NEOFORGE_VERSION = "21.1.249";
export const REALSCALE_VERSION = "2.0.0-preview.2-test.12";
export const MAX_FORCED_CHUNKS = 256;
export const MAX_WORLD_COORDINATE = 29_999_984;

export const WORLD_PRESETS = Object.freeze({
  "natural-automate": "realscale:natural_automate_three_realms",
  natural: "realscale:natural_three_realms",
  alpine: "realscale:alpine_three_realms",
  canyon: "realscale:canyon_three_realms",
  glacial: "realscale:glacial_three_realms",
  volcanic: "realscale:volcanic_three_realms",
  karst: "realscale:karst_three_realms",
  dune: "realscale:dune_three_realms"
});

function integerInRange(value, min, max, label) {
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    throw new TypeError(label + " must be an integer from " + min + " to " + max);
  }
  return value;
}

function normalizeSeed(value) {
  if (typeof value !== "string" && typeof value !== "number" && typeof value !== "bigint") {
    throw new TypeError("Seed must be text or an integer");
  }
  const seed = String(value).trim();
  if (!seed || Buffer.byteLength(seed, "utf8") > 64 || /[\u0000-\u001f\u007f]/u.test(seed)) {
    throw new TypeError("Seed must contain 1 to 64 UTF-8 bytes and no control characters");
  }
  return seed;
}

export function normalizeExportRequest(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("Export request must be a JSON object");
  }

  const presetKey = typeof input.preset === "string" ? input.preset : "natural-automate";
  const presetId = WORLD_PRESETS[presetKey];
  if (!presetId) {
    throw new TypeError("Unsupported RealScale world preset");
  }

  const seed = normalizeSeed(input.seed);
  const widthChunks = integerInRange(input.widthChunks ?? 8, 1, 16, "widthChunks");
  const depthChunks = integerInRange(input.depthChunks ?? 8, 1, 16, "depthChunks");
  const chunkCount = widthChunks * depthChunks;
  if (chunkCount > MAX_FORCED_CHUNKS) {
    throw new RangeError("Export area exceeds the 256-chunk MVP budget");
  }

  const centerX = integerInRange(input.centerX ?? 0, -MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE, "centerX");
  const centerZ = integerInRange(input.centerZ ?? 0, -MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE, "centerZ");
  const centerChunkX = Math.floor(centerX / 16);
  const centerChunkZ = Math.floor(centerZ / 16);
  const minChunkX = Math.floor(centerChunkX - (widthChunks - 1) / 2);
  const minChunkZ = Math.floor(centerChunkZ - (depthChunks - 1) / 2);
  const maxChunkX = minChunkX + widthChunks - 1;
  const maxChunkZ = minChunkZ + depthChunks - 1;

  const minBlockX = minChunkX * 16;
  const minBlockZ = minChunkZ * 16;
  const maxBlockX = (maxChunkX + 1) * 16 - 1;
  const maxBlockZ = (maxChunkZ + 1) * 16 - 1;

  if (Math.abs(minBlockX) > 30_000_000 || Math.abs(maxBlockX) > 30_000_000
    || Math.abs(minBlockZ) > 30_000_000 || Math.abs(maxBlockZ) > 30_000_000) {
    throw new RangeError("Export area crosses the Minecraft world border");
  }

  return Object.freeze({
    seed,
    presetKey,
    presetId,
    minecraftVersion: MINECRAFT_VERSION,
    neoforgeVersion: NEOFORGE_VERSION,
    realscaleVersion: REALSCALE_VERSION,
    centerX,
    centerZ,
    widthChunks,
    depthChunks,
    chunkCount,
    bounds: Object.freeze({
      minChunkX,
      maxChunkX,
      minChunkZ,
      maxChunkZ,
      minBlockX,
      maxBlockX,
      minBlockZ,
      maxBlockZ
    })
  });
}

export function expectedChunkCoordinates(request) {
  const normalized = normalizeExportRequest(request);
  const chunks = [];
  for (let z = normalized.bounds.minChunkZ; z <= normalized.bounds.maxChunkZ; z += 1) {
    for (let x = normalized.bounds.minChunkX; x <= normalized.bounds.maxChunkX; x += 1) {
      chunks.push({ x, z });
    }
  }
  return chunks;
}
