import test from "node:test";
import assert from "node:assert/strict";
import { WorldExportJobManager } from "./job-manager.js";

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const success = id => ({
  archivePath: "/private/" + id + ".zip",
  archiveBytes: 1234,
  sha256: "a".repeat(64),
  validation: { valid: true, verifiedChunks: 64 }
});

test("runs jobs sequentially and publishes progress without leaking filesystem paths", async () => {
  const first = deferred();
  const calls = [];
  const manager = new WorldExportJobManager({
    runner: async (request, context) => {
      calls.push({ request, context });
      context.onProgress({ stage: "generating", percent: 42, message: "Generating chunks" });
      if (calls.length === 1) return first.promise;
      return success(context.jobId);
    }
  });
  const a = manager.create({ seed: "first" });
  const b = manager.create({ seed: "second" });
  assert.equal(manager.get(a.id).status, "running");
  assert.equal(manager.get(b.id).status, "queued");
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.get(a.id).progress, 42);
  assert.equal(calls.length, 1);
  first.resolve(success(a.id));
  await new Promise(resolve => setImmediate(resolve));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.get(a.id).status, "completed");
  assert.equal(manager.get(b.id).status, "completed");
  assert.equal(calls.length, 2);
  assert.equal(manager.getDownload(a.id).path, "/private/" + a.id + ".zip");
  assert.equal(manager.get(a.id).result.downloadUrl, "/api/world-exports/" + a.id + "/download");
  assert.equal("archivePath" in manager.get(a.id).result, false);
});

test("cancels a queued job without starting the runner", async () => {
  const first = deferred();
  let calls = 0;
  const manager = new WorldExportJobManager({
    runner: async (_request, context) => {
      calls += 1;
      return first.promise;
    }
  });
  const active = manager.create({ seed: "active" });
  const queued = manager.create({ seed: "queued" });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.cancel(queued.id).status, "cancelled");
  assert.equal(calls, 1);
  first.resolve(success(active.id));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.get(active.id).status, "completed");
  assert.equal(manager.get(queued.id).status, "cancelled");
  assert.equal(calls, 1);
});

test("aborts an active job and records a cancelled terminal state", async () => {
  let signal;
  const manager = new WorldExportJobManager({
    runner: (_request, context) => new Promise((_resolve, reject) => {
      signal = context.signal;
      signal.addEventListener("abort", () => {
        const error = new Error("cancelled");
        error.name = "AbortError";
        reject(error);
      }, { once: true });
    })
  });
  const job = manager.create({ seed: "cancel-me" });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.cancel(job.id).status, "cancelling");
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(signal.aborted, true);
  assert.equal(manager.get(job.id).status, "cancelled");
});

test("marks runner failures and enforces queue capacity", async () => {
  const first = deferred();
  const second = deferred();
  let calls = 0;
  const manager = new WorldExportJobManager({
    maxQueue: 1,
    runner: () => {
      calls += 1;
      return calls === 1 ? first.promise : second.promise;
    }
  });
  const active = manager.create({ seed: "one" });
  const waiting = manager.create({ seed: "two" });
  assert.throws(() => manager.create({ seed: "three" }), /queue is full/);
  first.reject(new Error("NeoForge failed to boot"));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.get(active.id).status, "failed");
  assert.match(manager.get(active.id).error, /failed to boot/);
  assert.equal(manager.get(waiting.id).status, "running");
  second.resolve(success(waiting.id));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(manager.get(waiting.id).status, "completed");
});

test("rejects invalid export requests before they enter the queue", () => {
  const manager = new WorldExportJobManager({ runner: async () => success("unused") });
  assert.throws(() => manager.create({ seed: "bad", preset: "untrusted" }), /Unsupported RealScale/);
  assert.equal(manager.list().length, 0);
});


test("worker shutdown cancels queued jobs and waits for the active server job", async () => {
  let signal;
  const manager = new WorldExportJobManager({
    runner: (_request, context) => new Promise((_resolve, reject) => {
      signal = context.signal;
      signal.addEventListener("abort", () => {
        const error = new Error("shutdown");
        error.name = "AbortError";
        reject(error);
      }, { once: true });
    })
  });
  const active = manager.create({ seed: "shutdown-active" });
  const queued = manager.create({ seed: "shutdown-queued" });
  await new Promise(resolve => setImmediate(resolve));
  const shutdown = manager.shutdown();
  assert.equal(manager.get(queued.id).status, "cancelled");
  await shutdown;
  assert.equal(signal.aborted, true);
  assert.equal(manager.get(active.id).status, "cancelled");
  assert.throws(() => manager.create({ seed: "after-shutdown" }), /worker is shutting down/);
});
