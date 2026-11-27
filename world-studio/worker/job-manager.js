import { randomUUID } from "node:crypto";
import { normalizeExportRequest } from "./export-contract.js";
import { runMinecraftExport } from "./minecraft-runner.js";

function publicJob(job) {
  return Object.freeze({
    id: job.id,
    status: job.status,
    stage: job.stage,
    progress: job.progress,
    message: job.message,
    request: job.request,
    createdAt: job.createdAt,
    updatedAt: job.updatedAt,
    result: job.result ? {
      archiveBytes: job.result.archiveBytes,
      sha256: job.result.sha256,
      validation: job.result.validation,
      downloadUrl: "/api/world-exports/" + job.id + "/download"
    } : null,
    error: job.error
  });
}

export class WorldExportJobManager {
  constructor({ runner = runMinecraftExport, maxQueue = 3 } = {}) {
    if (typeof runner !== "function") throw new TypeError("World export runner must be a function");
    if (!Number.isInteger(maxQueue) || maxQueue < 0 || maxQueue > 32) {
      throw new TypeError("maxQueue must be an integer from 0 to 32");
    }
    this.runner = runner;
    this.maxQueue = maxQueue;
    this.jobs = new Map();
    this.queue = [];
    this.activeJobId = null;
    this.idleWaiters = [];
    this.shuttingDown = false;
  }

  create(input) {
    if (this.shuttingDown) throw new Error("World export worker is shutting down");
    const normalized = normalizeExportRequest(input);
    const waiting = this.queue.length + (this.activeJobId ? 1 : 0);
    if (waiting >= this.maxQueue + 1) {
      throw new Error("World export queue is full; try again when a job finishes");
    }

    const id = randomUUID();
    const now = new Date().toISOString();
    const job = {
      id,
      status: "queued",
      stage: "queued",
      progress: 0,
      message: "Waiting for the isolated NeoForge worker",
      request: {
        seed: normalized.seed,
        preset: normalized.presetKey,
        centerX: normalized.centerX,
        centerZ: normalized.centerZ,
        widthChunks: normalized.widthChunks,
        depthChunks: normalized.depthChunks,
        chunkCount: normalized.chunkCount
      },
      createdAt: now,
      updatedAt: now,
      result: null,
      error: null,
      controller: null
    };
    this.jobs.set(id, job);
    this.queue.push(id);
    this.pump();
    return publicJob(job);
  }

  get(id) {
    const job = this.jobs.get(id);
    return job ? publicJob(job) : null;
  }

  getDownload(id) {
    const job = this.jobs.get(id);
    if (!job || job.status !== "completed" || !job.result?.archivePath) return null;
    return {
      path: job.result.archivePath,
      filename: "realscale-world-" + id.replace(/-/gu, "").slice(0, 12) + ".zip",
      bytes: job.result.archiveBytes,
      sha256: job.result.sha256
    };
  }

  cancel(id) {
    const job = this.jobs.get(id);
    if (!job) return null;
    if (job.status === "queued") {
      this.queue = this.queue.filter(queuedId => queuedId !== id);
      job.status = "cancelled";
      job.stage = "cancelled";
      job.message = "World export cancelled before the server started";
      job.updatedAt = new Date().toISOString();
      return publicJob(job);
    }
    if (job.status === "running") {
      job.status = "cancelling";
      job.stage = "cancelling";
      job.message = "Cancellation requested; safely stopping the Minecraft server";
      job.updatedAt = new Date().toISOString();
      job.controller?.abort();
    }
    return publicJob(job);
  }

  resolveIdleWaiters() {
    if (this.activeJobId || this.queue.length) return;
    for (const resolve of this.idleWaiters.splice(0)) resolve();
  }

  shutdown() {
    this.shuttingDown = true;
    const idle = new Promise(resolve => this.idleWaiters.push(resolve));
    for (const id of this.queue.splice(0)) {
      const job = this.jobs.get(id);
      if (!job || job.status !== "queued") continue;
      job.status = "cancelled";
      job.stage = "cancelled";
      job.message = "Worker shutting down; queued export cancelled";
      job.updatedAt = new Date().toISOString();
    }
    if (this.activeJobId) this.jobs.get(this.activeJobId)?.controller?.abort();
    this.resolveIdleWaiters();
    return idle;
  }

  list() {
    return [...this.jobs.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(publicJob);
  }

  pump() {
    if (this.activeJobId) return;
    const id = this.queue.shift();
    if (!id) return;
    const job = this.jobs.get(id);
    if (!job || job.status !== "queued") {
      queueMicrotask(() => this.pump());
      return;
    }

    this.activeJobId = id;
    job.status = "running";
    job.stage = "starting";
    job.message = "Starting isolated NeoForge server";
    job.updatedAt = new Date().toISOString();
    job.controller = new AbortController();

    Promise.resolve()
      .then(() => {
        if (job.controller.signal.aborted) {
          const error = new Error("World export was cancelled");
          error.name = "AbortError";
          throw error;
        }
        return this.runner(job.request, {
          jobId: id,
          signal: job.controller.signal,
          onProgress: update => {
            if (job.status !== "running" || !update || typeof update !== "object") return;
            if (Number.isFinite(update.percent)) {
              job.progress = Math.max(job.progress, Math.min(99, Math.max(0, update.percent)));
            }
            if (typeof update.stage === "string") job.stage = update.stage;
            if (typeof update.message === "string") job.message = update.message.slice(0, 240);
            job.updatedAt = new Date().toISOString();
          }
        });
      })
      .then(result => {
        if (job.controller.signal.aborted) {
          job.status = "cancelled";
          job.stage = "cancelled";
          job.message = "World export cancelled";
        } else {
          job.status = "completed";
          job.stage = "completed";
          job.progress = 100;
          job.message = "World save validated and ready to download";
          job.result = result;
        }
      })
      .catch(error => {
        if (job.controller.signal.aborted || error?.name === "AbortError") {
          job.status = "cancelled";
          job.stage = "cancelled";
          job.message = "World export cancelled and the server was stopped";
        } else {
          job.status = "failed";
          job.stage = "failed";
          job.message = "World export failed";
          job.error = String(error?.message || "Unknown world export error").slice(0, 1200);
        }
      })
      .finally(() => {
        job.updatedAt = new Date().toISOString();
        job.controller = null;
        this.activeJobId = null;
        this.pump();
        this.resolveIdleWaiters();
      });
  }
}

export function createWorldExportJobManager(options) {
  return new WorldExportJobManager(options);
}
