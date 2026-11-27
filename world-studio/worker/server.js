// Temporary validation-branch change: run the full World Studio 0.5.0 CI suite.
import http from "node:http";
import fs from "node:fs/promises";
import { createReadStream } from "node:fs";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { WorldExportJobManager } from "./job-manager.js";

const MAX_BODY_BYTES = 16 * 1024;
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1"]);
const LOCAL_ORIGIN_PORTS = new Set(["", "5173", "4173", "4174"]);

function sendJson(response, statusCode, value) {
  if (response.destroyed || response.writableEnded) return;
  if (response.headersSent) {
    response.destroy();
    return;
  }
  const body = Buffer.from(JSON.stringify(value));
  response.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "content-length": body.length,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  response.end(body);
}

function isLocalHostHeader(value) {
  if (typeof value !== "string") return false;
  const host = value.replace(/:\d+$/u, "").replace(/^\[/u, "").replace(/\]$/u, "").toLowerCase();
  return LOCAL_HOSTS.has(host);
}

function isAllowedOrigin(value) {
  if (!value) return true;
  try {
    const origin = new URL(value);
    return ["http:", "https:"].includes(origin.protocol)
      && LOCAL_HOSTS.has(origin.hostname.toLowerCase())
      && LOCAL_ORIGIN_PORTS.has(origin.port);
  } catch {
    return false;
  }
}

async function readJson(request) {
  const contentType = String(request.headers["content-type"] || "").split(";")[0].trim().toLowerCase();
  if (contentType !== "application/json") {
    const error = new Error("Content-Type must be application/json");
    error.statusCode = 415;
    throw error;
  }
  const contentLength = Number(request.headers["content-length"] || 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_BODY_BYTES) {
    const error = new Error("Request body exceeds 16 KiB");
    error.statusCode = 413;
    throw error;
  }
  const parts = [];
  let total = 0;
  for await (const part of request) {
    total += part.length;
    if (total > MAX_BODY_BYTES) {
      const error = new Error("Request body exceeds 16 KiB");
      error.statusCode = 413;
      throw error;
    }
    parts.push(part);
  }
  try {
    return JSON.parse(Buffer.concat(parts).toString("utf8"));
  } catch {
    const error = new Error("Request body must be valid JSON");
    error.statusCode = 400;
    throw error;
  }
}

function safeJobId(value) {
  return typeof value === "string" && /^[a-zA-Z0-9-]{8,64}$/u.test(value);
}

function createWorldExportServer({ manager = new WorldExportJobManager() } = {}) {
  const server = http.createServer((request, response) => {
    void handleRequest(request, response, manager).catch(error => {
      sendJson(response, error.statusCode || 500, {
        error: error.statusCode ? error.message : "World export API failed"
      });
    });
  });
  server.requestTimeout = 20_000;
  server.headersTimeout = 10_000;
  server.maxHeadersCount = 32;
  server.worldExportManager = manager;
  return server;
}

async function handleRequest(request, response, manager) {
  if (!isLocalHostHeader(request.headers.host)) {
    sendJson(response, 403, { error: "The world export worker accepts loopback requests only" });
    return;
  }
  if (!isAllowedOrigin(request.headers.origin)) {
    sendJson(response, 403, { error: "Cross-origin requests are not allowed" });
    return;
  }

  const url = new URL(request.url || "/", "http://127.0.0.1");
  if (request.method === "GET" && url.pathname === "/api/health") {
    sendJson(response, 200, {
      status: "ok",
      worker: "realscale-world-export",
      activeJobs: manager.activeJobId ? 1 : 0,
      queuedJobs: manager.queue.length
    });
    return;
  }

  if (request.method === "GET" && url.pathname === "/api/world-exports") {
    sendJson(response, 200, { jobs: manager.list() });
    return;
  }

  if (request.method === "POST" && url.pathname === "/api/world-exports") {
    const input = await readJson(request);
    try {
      const job = manager.create(input);
      sendJson(response, 202, { job });
    } catch (error) {
      const status = /queue is full/iu.test(error.message) ? 429 : 400;
      sendJson(response, status, { error: error.message });
    }
    return;
  }

  const route = url.pathname.match(/^\/api\/world-exports\/([^/]+)(?:\/(cancel|download))?$/u);
  if (!route || !safeJobId(route[1])) {
    sendJson(response, 404, { error: "World export endpoint not found" });
    return;
  }
  const id = route[1];
  const action = route[2] || "status";

  if (action === "status" && request.method === "GET") {
    const job = manager.get(id);
    if (!job) sendJson(response, 404, { error: "World export job not found" });
    else sendJson(response, 200, { job });
    return;
  }

  if (action === "cancel" && request.method === "POST") {
    const job = manager.cancel(id);
    if (!job) sendJson(response, 404, { error: "World export job not found" });
    else sendJson(response, 200, { job });
    return;
  }

  if (action === "download" && request.method === "GET") {
    const download = manager.getDownload(id);
    if (!download) {
      sendJson(response, 409, { error: "World export is not ready for download" });
      return;
    }
    const filePath = path.resolve(download.path);
    const info = await fs.lstat(filePath).catch(() => null);
    if (!info?.isFile() || info.isSymbolicLink()) {
      sendJson(response, 404, { error: "Validated world archive is no longer available" });
      return;
    }
    response.writeHead(200, {
      "content-type": "application/zip",
      "content-length": info.size,
      "content-disposition": 'attachment; filename="' + download.filename + '"',
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "x-realscale-sha256": download.sha256
    });
    await pipeline(createReadStream(filePath), response);
    return;
  }

  sendJson(response, 405, { error: "Method not allowed" });
}

const MODULE_PATH = fileURLToPath(import.meta.url);
if (process.argv[1] && path.resolve(process.argv[1]) === MODULE_PATH) {
  const host = process.env.REALSCALE_WORKER_HOST || "127.0.0.1";
  const port = Number(process.env.REALSCALE_WORKER_PORT || 4174);
  if (host !== "127.0.0.1" && host !== "localhost") {
    throw new Error("The local World Studio worker must bind to loopback; public binding is disabled");
  }
  const server = createWorldExportServer();
  server.listen(port, host, () => {
    process.stdout.write("RealScale World Export Worker listening on http://" + host + ":" + port + "\n");
  });
  const shutdown = () => {
    server.close();
    void server.worldExportManager.shutdown();
  };
  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);
}

export { createWorldExportServer, handleRequest };
