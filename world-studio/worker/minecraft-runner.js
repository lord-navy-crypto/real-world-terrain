import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { once } from "node:events";
import { createInterface } from "node:readline";
import { randomBytes, randomUUID } from "node:crypto";
import net from "node:net";
import { fileURLToPath } from "node:url";
import { RconClient } from "./rcon-client.js";
import { normalizeExportRequest, expectedChunkCoordinates } from "./export-contract.js";
import { validateWorldSave } from "./save-validator.js";
import { createWorldArchive } from "./archive.js";

const MODULE_DIRECTORY = path.dirname(fileURLToPath(import.meta.url));
const REPOSITORY_ROOT = path.resolve(MODULE_DIRECTORY, "../..");
const DEFAULT_STARTUP_TIMEOUT_MS = 10 * 60 * 1000;
const DEFAULT_GENERATION_TIMEOUT_MS = 30 * 60 * 1000;
const DEFAULT_CHUNK_TIMEOUT_MS = 5 * 60 * 1000;
const LOG_LIMIT_BYTES = 32 * 1024 * 1024;

function abortError() {
  const error = new Error("World export was cancelled");
  error.name = "AbortError";
  return error;
}

function throwIfAborted(signal) {
  if (signal?.aborted) throw abortError();
}

function sleep(milliseconds, signal) {
  throwIfAborted(signal);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(done, milliseconds);
    function cleanup() {
      clearTimeout(timer);
      signal?.removeEventListener("abort", onAbort);
    }
    function done() {
      cleanup();
      resolve();
    }
    function onAbort() {
      cleanup();
      reject(abortError());
    }
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

function progress(callback, stage, percent, message, details = {}) {
  callback?.({ stage, percent, message, ...details });
}

function isTcpPortOpen(port) {
  if (!Number.isInteger(port) || port < 1 || port > 65535) return Promise.resolve(false);
  return new Promise(resolve => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    let settled = false;
    const finish = open => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(open);
    };
    socket.once("connect", () => finish(true));
    socket.once("error", () => finish(false));
    socket.setTimeout(500, () => finish(false));
  });
}

async function reserveLocalPorts(count) {
  const servers = [];
  const ports = [];
  try {
    for (let index = 0; index < count; index += 1) {
      const server = net.createServer();
      servers.push(server);
      await new Promise((resolve, reject) => {
        server.once("error", reject);
        server.listen(0, "127.0.0.1", resolve);
      });
      ports.push(server.address().port);
    }
    return ports;
  } finally {
    await Promise.all(servers.map(server => new Promise(resolve => {
      if (!server.listening) return resolve();
      server.close(() => resolve());
    })));
  }
}

async function snapshotFile(filePath) {
  try {
    const info = await fs.lstat(filePath);
    if (info.isSymbolicLink() || !info.isFile()) {
      throw new Error("Refusing to replace a non-regular server configuration file: " + filePath);
    }
    return { exists: true, bytes: await fs.readFile(filePath), mode: info.mode };
  } catch (error) {
    if (error.code === "ENOENT") return { exists: false };
    throw error;
  }
}

async function restoreFile(filePath, snapshot) {
  if (snapshot.exists) {
    await fs.writeFile(filePath, snapshot.bytes, { mode: snapshot.mode });
  } else {
    await fs.rm(filePath, { force: true });
  }
}

function buildServerProperties(request, { worldName, serverPort, rconPort, rconPassword }) {
  return [
    "accepts-transfers=false",
    "allow-flight=false",
    "broadcast-console-to-ops=false",
    "broadcast-rcon-to-ops=false",
    "difficulty=peaceful",
    "enable-command-block=false",
    "enable-query=false",
    "enable-rcon=true",
    "enable-status=false",
    "enforce-secure-profile=false",
    "force-gamemode=false",
    "gamemode=survival",
    "generate-structures=true",
    "hardcore=false",
    "level-name=" + worldName,
    "level-seed=" + request.seed.replaceAll("\\", "\\\\"),
    "level-type=" + request.presetId,
    "max-players=0",
    "max-tick-time=-1",
    "motd=RealScale isolated world export",
    "online-mode=false",
    "pvp=false",
    "rcon.ip=127.0.0.1",
    "rcon.password=" + rconPassword,
    "rcon.port=" + rconPort,
    "server-ip=127.0.0.1",
    "server-port=" + serverPort,
    "simulation-distance=3",
    "spawn-animals=false",
    "spawn-monsters=false",
    "spawn-npcs=false",
    "spawn-protection=0",
    "spawn-chunk-radius=0",
    "sync-chunk-writes=true",
    "view-distance=3",
    "white-list=false",
    ""
  ].join("\n");
}

function waitForExit(child, timeoutMs) {
  if (child.exitCode !== null || child.signalCode !== null) {
    return Promise.resolve({ code: child.exitCode, signal: child.signalCode });
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("Minecraft server did not stop before the shutdown deadline"));
    }, timeoutMs);
    const onExit = (code, signal) => {
      cleanup();
      resolve({ code, signal });
    };
    function cleanup() {
      clearTimeout(timer);
      child.off("exit", onExit);
    }
    child.once("exit", onExit);
  });
}

function killProcessTree(child, signal = "SIGTERM") {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  try {
    if (process.platform !== "win32" && child.pid) process.kill(-child.pid, signal);
    else child.kill(signal);
  } catch (error) {
    if (error.code !== "ESRCH") throw error;
  }
}

async function waitForReady(child, lines, timeoutMs, signal) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => finish(new Error("Minecraft server startup timed out")), timeoutMs);
    const output = createInterface({ input: child.stdout });
    const errors = createInterface({ input: child.stderr });

    const onLine = line => {
      if (line.includes("Done (") && line.includes("For help")) finish(null);
    };
    const onErrorLine = () => {};
    const onError = error => finish(error);
    const onExit = code => finish(new Error(
      "Minecraft server exited before startup completed (code " + code + "):\n" + lines.slice(-30).join("\n")
    ));
    const onAbort = () => finish(abortError());

    function finish(error) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      output.close();
      errors.close();
      child.off("error", onError);
      child.off("exit", onExit);
      signal?.removeEventListener("abort", onAbort);
      if (error) reject(error);
      else resolve();
    }

    output.on("line", onLine);
    errors.on("line", onErrorLine);
    child.once("error", onError);
    child.once("exit", onExit);
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

async function connectRcon(port, password, signal) {
  const deadline = Date.now() + 60_000;
  let lastError;
  while (Date.now() < deadline) {
    throwIfAborted(signal);
    try {
      return await RconClient.connect({
        host: "127.0.0.1",
        port,
        password,
        timeoutMs: 5 * 60 * 1000
      });
    } catch (error) {
      lastError = error;
      await sleep(1000, signal);
    }
  }
  throw new Error("Unable to connect to the isolated Minecraft RCON endpoint: " + (lastError?.message ?? "timeout"));
}

async function awaitChunkLoaded(rcon, chunk, deadline, signal) {
  const blockX = chunk.x * 16 + 8;
  const blockZ = chunk.z * 16 + 8;
  const command = "execute in minecraft:overworld if loaded " + blockX + " 128 " + blockZ + " run seed";
  while (Date.now() < deadline) {
    throwIfAborted(signal);
    const reply = await rcon.command(command);
    if (reply.includes("Seed:")) return;
    await sleep(500, signal);
  }
  throw new Error("Chunk generation timed out at " + chunk.x + "," + chunk.z);
}

function safeJobId(value) {
  const id = typeof value === "string" ? value : randomUUID();
  if (!/^[a-zA-Z0-9-]{8,64}$/u.test(id)) throw new TypeError("Invalid world export job ID");
  return id;
}

function resolveRunDirectory(neoforgeDirectory) {
  // ModDevGradle's default server run uses neoforge/run as its game directory.
  // Keep server.properties, eula.txt, the world, and the lock in that exact directory.
  return path.join(neoforgeDirectory, "run");
}

async function ensureProject(neoforgeDirectory) {
  const buildFile = path.join(neoforgeDirectory, "build.gradle");
  const info = await fs.stat(buildFile).catch(() => null);
  if (!info?.isFile()) {
    throw new Error("NeoForge project not found; set REALSCALE_NEOFORGE_DIR to the neoforge project directory");
  }
}

async function writeLockOwner(handle, owner) {
  const bytes = Buffer.from(JSON.stringify(owner), "utf8");
  await handle.truncate(0);
  let offset = 0;
  while (offset < bytes.length) {
    const result = await handle.write(bytes, offset, bytes.length - offset, offset);
    if (!result.bytesWritten) throw new Error("Unable to update NeoForge run-directory lock");
    offset += result.bytesWritten;
  }
  await handle.sync();
}

async function isProcessAlive(pid) {
  if (!Number.isSafeInteger(pid) || pid < 1) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error.code !== "ESRCH";
  }
}

async function acquireRunLock(lockPath, jobId) {
  const owner = {
    jobId,
    workerPid: process.pid,
    serverPid: null,
    createdAt: new Date().toISOString()
  };
  try {
    const handle = await fs.open(lockPath, "wx", 0o600);
    try {
      await writeLockOwner(handle, owner);
      return handle;
    } catch (error) {
      await handle.close().catch(() => {});
      await fs.rm(lockPath, { force: true }).catch(() => {});
      throw error;
    }
  } catch (error) {
    if (error.code !== "EEXIST") throw error;
  }

  const info = await fs.lstat(lockPath);
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error("The NeoForge run-directory lock is not a regular file");
  }
  let previous;
  try {
    previous = JSON.parse(await fs.readFile(lockPath, "utf8"));
  } catch {
    throw new Error("The NeoForge run-directory lock exists but its owner cannot be verified");
  }
  const active = await Promise.all([previous.workerPid, previous.serverPid].map(isProcessAlive));
  if (active.some(Boolean)) {
    throw new Error("The NeoForge server run directory is already in use by another export or development session");
  }

  await fs.rm(lockPath);
  const handle = await fs.open(lockPath, "wx", 0o600);
  try {
    await writeLockOwner(handle, owner);
    return handle;
  } catch (error) {
    await handle.close().catch(() => {});
    await fs.rm(lockPath, { force: true }).catch(() => {});
    throw error;
  }
}

export async function runMinecraftExport(input, {
  jobId,
  signal,
  onProgress,
  repositoryRoot = REPOSITORY_ROOT,
  gradleCommand = process.env.REALSCALE_GRADLE_BIN || "gradle",
  exportRoot = process.env.REALSCALE_EXPORT_ROOT || path.join(MODULE_DIRECTORY, "../exports"),
  startupTimeoutMs = Number(process.env.REALSCALE_SERVER_STARTUP_TIMEOUT_MS) || DEFAULT_STARTUP_TIMEOUT_MS,
  generationTimeoutMs = Number(process.env.REALSCALE_GENERATION_TIMEOUT_MS) || DEFAULT_GENERATION_TIMEOUT_MS,
  chunkTimeoutMs = Number(process.env.REALSCALE_CHUNK_TIMEOUT_MS) || DEFAULT_CHUNK_TIMEOUT_MS
} = {}) {
  const request = normalizeExportRequest(input);
  const id = safeJobId(jobId);
  const neoforgeDirectory = path.resolve(process.env.REALSCALE_NEOFORGE_DIR || path.join(repositoryRoot, "neoforge"));
  await ensureProject(neoforgeDirectory);
  throwIfAborted(signal);

  const runDirectory = resolveRunDirectory(neoforgeDirectory);
  await fs.mkdir(runDirectory, { recursive: true });
  const lockPath = path.join(runDirectory, ".realscale-world-export.lock");
  const lock = await acquireRunLock(lockPath, id);

  const jobDirectory = path.resolve(exportRoot, id);
  const logPath = path.join(jobDirectory, "minecraft-server.log");
  let logStream;
  const worldName = "realscale-export-" + id.replace(/-/gu, "").slice(0, 16).toLowerCase();
  const worldDirectory = path.join(runDirectory, worldName);
  const propertiesPath = path.join(runDirectory, "server.properties");
  const eulaPath = path.join(runDirectory, "eula.txt");
  let propertiesSnapshot;
  let eulaSnapshot;
  let child;
  let rcon;
  let serverStopped = false;
  let logBytes = 0;
  const logTail = [];

  const appendLog = line => {
    logTail.push(line);
    if (logTail.length > 500) logTail.shift();
    const bytes = Buffer.byteLength(line + "\n");
    if (logBytes < LOG_LIMIT_BYTES) {
      logStream?.write(line + "\n");
      logBytes += bytes;
    }
  };

  try {
    await fs.mkdir(jobDirectory, { recursive: true });
    logStream = createWriteStream(logPath, { flags: "wx", mode: 0o600 });
    await Promise.race([
      once(logStream, "open"),
      once(logStream, "error").then(([error]) => Promise.reject(error))
    ]);
    propertiesSnapshot = await snapshotFile(propertiesPath);
    eulaSnapshot = await snapshotFile(eulaPath);
    const previousProperties = propertiesSnapshot.exists
      ? propertiesSnapshot.bytes.toString("utf8")
      : "";
    const previousPortMatch = previousProperties.match(/^server-port\s*=\s*(\d+)\s*$/mu);
    const previousServerPort = previousPortMatch ? Number(previousPortMatch[1]) : 25565;
    if (await isTcpPortOpen(previousServerPort)) {
      throw new Error("A Minecraft server is already listening on the NeoForge run directory's game port; stop it before exporting");
    }
    if (await fs.lstat(worldDirectory).catch(() => null)) {
      throw new Error("The generated world directory already exists; refusing to overwrite it");
    }

    const [serverPort, rconPort] = await reserveLocalPorts(2);
    const rconPassword = randomBytes(32).toString("base64url");
    const properties = buildServerProperties(request, { worldName, serverPort, rconPort, rconPassword });
    await fs.writeFile(propertiesPath, properties, { mode: 0o600 });
    await fs.writeFile(eulaPath, "eula=true\n", { mode: 0o600 });
    progress(onProgress, "starting", 3, "Starting isolated NeoForge 1.21.1 server", { jobId: id });
    child = spawn(gradleCommand, ["--no-daemon", "runServer", "--stacktrace"], {
      cwd: neoforgeDirectory,
      env: { ...process.env, REALSCALE_WORLD_EXPORT_JOB: id },
      stdio: ["ignore", "pipe", "pipe"],
      detached: process.platform !== "win32"
    });
    await writeLockOwner(lock, {
      jobId: id,
      workerPid: process.pid,
      serverPid: child.pid ?? null,
      createdAt: new Date().toISOString()
    });

    const readyPromise = waitForReady(child, logTail, startupTimeoutMs, signal);
    child.stdout.on("data", chunk => {
      const text = chunk.toString("utf8");
      for (const line of text.split(/\r?\n/u)) if (line) appendLog(line);
    });
    child.stderr.on("data", chunk => {
      const text = chunk.toString("utf8");
      for (const line of text.split(/\r?\n/u)) if (line) appendLog("[stderr] " + line);
    });
    await readyPromise;
    progress(onProgress, "connecting", 8, "NeoForge server is ready; connecting over local RCON");

    rcon = await connectRcon(rconPort, rconPassword, signal);
    const bounds = request.bounds;
    const forceload = "execute in minecraft:overworld run forceload add "
      + bounds.minBlockX + " " + bounds.minBlockZ + " "
      + bounds.maxBlockX + " " + bounds.maxBlockZ;
    const forceloadReply = await rcon.command(forceload);
    if (/unknown command|failed|no chunks|incorrect argument/iu.test(forceloadReply)) {
      throw new Error("Minecraft rejected the bounded forceload request: " + forceloadReply);
    }

    const chunks = expectedChunkCoordinates(request);
    const generationDeadline = Date.now() + Math.min(generationTimeoutMs, chunkTimeoutMs * chunks.length);
    progress(onProgress, "generating", 10, "Generating " + chunks.length + " real Minecraft chunks", {
      completedChunks: 0,
      totalChunks: chunks.length
    });
    for (let index = 0; index < chunks.length; index += 1) {
      throwIfAborted(signal);
      const deadline = Math.min(generationDeadline, Date.now() + chunkTimeoutMs);
      await awaitChunkLoaded(rcon, chunks[index], deadline, signal);
      const completed = index + 1;
      progress(onProgress, "generating", 10 + Math.round(75 * completed / chunks.length),
        "Generated " + completed + " of " + chunks.length + " chunks", {
          completedChunks: completed,
          totalChunks: chunks.length,
          currentChunk: chunks[index]
        });
    }

    progress(onProgress, "saving", 88, "Flushing generated Anvil chunks to disk");
    await rcon.command("execute in minecraft:overworld run forceload remove "
      + bounds.minBlockX + " " + bounds.minBlockZ + " "
      + bounds.maxBlockX + " " + bounds.maxBlockZ);
    await rcon.command("save-all flush");
    await rcon.command("stop");
    rcon.close();
    rcon = null;
    const exit = await waitForExit(child, 180_000);
    serverStopped = true;
    if (exit.code !== 0) {
      throw new Error("NeoForge server exited with code " + exit.code + ": " + logTail.slice(-30).join("\n"));
    }

    progress(onProgress, "validating", 92, "Checking level.dat and every requested Anvil chunk");
    let validation = await validateWorldSave(worldDirectory, {
      ...request,
      preset: request.presetKey
    });

    progress(onProgress, "reopening", 94, "Restarting NeoForge to verify the saved world reopens");
    const reopenLines = [];
    const appendReopenLog = line => {
      reopenLines.push(line);
      if (reopenLines.length > 500) reopenLines.shift();
      appendLog(line);
    };
    const [reopenServerPort, reopenRconPort] = await reserveLocalPorts(2);
    const reopenRconPassword = randomBytes(32).toString("base64url");
    await fs.writeFile(propertiesPath, buildServerProperties(request, {
      worldName,
      serverPort: reopenServerPort,
      rconPort: reopenRconPort,
      rconPassword: reopenRconPassword
    }), { mode: 0o600 });

    serverStopped = false;
    child = spawn(gradleCommand, ["--no-daemon", "runServer", "--stacktrace"], {
      cwd: neoforgeDirectory,
      env: { ...process.env, REALSCALE_WORLD_EXPORT_JOB: id + "-reopen" },
      stdio: ["ignore", "pipe", "pipe"],
      detached: process.platform !== "win32"
    });
    await writeLockOwner(lock, {
      jobId: id,
      workerPid: process.pid,
      serverPid: child.pid ?? null,
      createdAt: new Date().toISOString()
    });

    const reopenReadyPromise = waitForReady(child, reopenLines, startupTimeoutMs, signal);
    child.stdout.on("data", chunk => {
      const text = chunk.toString("utf8");
      for (const line of text.split(/\\r?\\n/u)) if (line) appendReopenLog(line);
    });
    child.stderr.on("data", chunk => {
      const text = chunk.toString("utf8");
      for (const line of text.split(/\\r?\\n/u)) if (line) appendReopenLog("[stderr] " + line);
    });
    await reopenReadyPromise;
    rcon = await connectRcon(reopenRconPort, reopenRconPassword, signal);
    const reopenedSeedReply = await rcon.command("seed");
    if (!/Seed:/iu.test(reopenedSeedReply)) {
      throw new Error("Reopened Minecraft world did not respond to the seed query");
    }
    await rcon.command("save-all flush");
    await rcon.command("stop");
    rcon.close();
    rcon = null;
    const reopenExit = await waitForExit(child, 180_000);
    serverStopped = true;
    if (reopenExit.code !== 0) {
      throw new Error("NeoForge failed to stop after the clean world-reopen check (code " + reopenExit.code + ")");
    }
    if (!reopenLines.some(line => line.includes('Preparing level "' + worldName + '"'))) {
      throw new Error("The second NeoForge boot did not report loading the exported world folder");
    }
    if (reopenLines.some(line => line.includes("No existing world data, creating new world"))) {
      throw new Error("The second NeoForge boot created a new world instead of reopening the saved world");
    }

    validation = await validateWorldSave(worldDirectory, {
      ...request,
      preset: request.presetKey
    });
    progress(onProgress, "validating", 95, "Reopened world saved again and passed Anvil/NBT validation");
    const manifest = {
      schemaVersion: 1,
      exportType: "realscale-modded-minecraft-save",
      createdAt: new Date().toISOString(),
      seed: request.seed,
      worldPreset: request.presetId,
      presetKey: request.presetKey,
      minecraftVersion: request.minecraftVersion,
      neoforgeVersion: request.neoforgeVersion,
      realscaleVersion: request.realscaleVersion,
      area: {
        widthChunks: request.widthChunks,
        depthChunks: request.depthChunks,
        chunkCount: request.chunkCount,
        centerX: request.centerX,
        centerZ: request.centerZ,
        bounds: request.bounds
      },
      validation,
      requirements: {
        loader: "NeoForge",
        minecraftVersion: request.minecraftVersion,
        neoforgeVersion: request.neoforgeVersion,
        modId: "realscale",
        realscaleVersion: request.realscaleVersion,
        clientDependencies: [{
          modId: "yet_another_config_lib_v3",
          version: "3.8.1+1.21.1"
        }]
      },
      reopenVerified: true,\n      vanillaCompatible: false,
      note: "This is a real generated Minecraft save. It requires the matching RealScale NeoForge mod; vanilla compatibility has not been validated."
    };

    progress(onProgress, "packaging", 97, "Packaging the validated world save");
    const archiveName = "realscale-world-" + id.replace(/-/gu, "").slice(0, 12).toLowerCase();
    const archivePath = path.join(jobDirectory, archiveName + ".zip");
    const archive = await createWorldArchive(worldDirectory, archivePath, {
      archiveName,
      manifest
    });
    await fs.rm(worldDirectory, { recursive: true, force: true });
    progress(onProgress, "completed", 100, "World save validated and packaged", {
      verifiedChunks: validation.verifiedChunks,
      archiveBytes: archive.archiveBytes
    });
    return Object.freeze({
      jobId: id,
      archivePath: archive.archivePath,
      archiveBytes: archive.archiveBytes,
      sha256: archive.sha256,
      validation,
      manifest,
      logPath
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error(error.message + (logTail.length ? "\n\nLast server log lines:\n" + logTail.slice(-25).join("\n") : ""), { cause: error });
  } finally {
    if (rcon) {
      try {
        await rcon.command("stop");
      } catch {
        // The process shutdown path below remains authoritative.
      }
      rcon.close();
    }
    if (child?.pid && !serverStopped && child.exitCode === null && child.signalCode === null) {
      try {
        await waitForExit(child, 30_000);
      } catch {
        killProcessTree(child, "SIGTERM");
        try {
          await waitForExit(child, 15_000);
        } catch {
          killProcessTree(child, "SIGKILL");
          await waitForExit(child, 5_000).catch(() => {});
        }
      }
    }
    if (propertiesSnapshot) await restoreFile(propertiesPath, propertiesSnapshot).catch(() => {});
    if (eulaSnapshot) await restoreFile(eulaPath, eulaSnapshot).catch(() => {});
    if (child && (child.exitCode !== null || child.signalCode !== null)) {
      await fs.rm(worldDirectory, { recursive: true, force: true }).catch(() => {});
    }
    if (logStream && !logStream.closed) {
      logStream.end();
      await once(logStream, "close").catch(() => {});
    }
    await lock.close().catch(() => {});
    await fs.rm(lockPath, { force: true }).catch(() => {});
  }
}

export { buildServerProperties, resolveRunDirectory };
