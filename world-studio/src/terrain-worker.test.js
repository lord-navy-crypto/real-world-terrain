import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { generateTerrainGrid } from "./terrain-worker-core.js";

const workerSource = readFileSync(new URL("./terrain-worker.js", import.meta.url), "utf8")
  .replace(/^import\s+.*?;\s*$/gm, "");

function createWorkerHarness({ cancelOnFirstProgress = false } = {}) {
  const messages = [];
  let messageHandler;
  const self = {
    addEventListener(type, handler) {
      assert.equal(type, "message");
      messageHandler = handler;
    },
    postMessage(message, transfer = []) {
      messages.push({ message, transfer });
      if (cancelOnFirstProgress && message.type === "progress") {
        cancelOnFirstProgress = false;
        messageHandler({ data: { type: "cancel", requestId: message.requestId } });
      }
    }
  };

  runInNewContext(workerSource, {
    self,
    generateTerrainGrid,
    setTimeout
  }, { filename: "terrain-worker.js" });

  return {
    messages,
    dispatch(data) {
      assert.ok(messageHandler, "worker message listener should be registered");
      return messageHandler({ data });
    }
  };
}

const options = {
  size: 4096,
  seed: 123456,
  seedText: "worker-protocol",
  reliefScale: 1,
  resolution: 16
};

test("worker emits progress and transfers all three completed terrain arrays", async () => {
  const worker = createWorkerHarness();
  await worker.dispatch({ type: "generate", requestId: "job-1", options });

  const progress = worker.messages
    .filter(entry => entry.message.type === "progress")
    .map(entry => entry.message.progress);
  const complete = worker.messages.find(entry => entry.message.type === "complete");

  assert.deepEqual(progress, [50, 100]);
  assert.ok(complete);
  assert.equal(complete.message.requestId, "job-1");
  assert.equal(complete.message.map.heights.length, 256);
  assert.equal(complete.message.map.temperatures.length, 256);
  assert.equal(complete.message.map.humidities.length, 256);
  assert.equal(complete.transfer.length, 3);
  assert.equal(complete.transfer[0], complete.message.map.heights.buffer);
  assert.equal(complete.transfer[1], complete.message.map.temperatures.buffer);
  assert.equal(complete.transfer[2], complete.message.map.humidities.buffer);
});

test("worker cancellation stops the active request without publishing partial arrays", async () => {
  const worker = createWorkerHarness({ cancelOnFirstProgress: true });
  await worker.dispatch({ type: "generate", requestId: "job-cancel", options });

  const types = worker.messages.map(entry => entry.message.type);
  assert.ok(types.includes("progress"));
  assert.ok(types.includes("cancelled"));
  assert.equal(types.includes("complete"), false);
});

test("worker rejects unsupported messages with a structured error", async () => {
  const worker = createWorkerHarness();
  await worker.dispatch({ type: "unknown", requestId: "job-invalid" });

  assert.equal(worker.messages[0].message.type, "error");
  assert.equal(worker.messages[0].message.requestId, "job-invalid");
  assert.equal(worker.messages[0].message.message, "Unsupported terrain worker message");
});

test("worker converts invalid generation options into an error response", async () => {
  const worker = createWorkerHarness();
  await worker.dispatch({
    type: "generate",
    requestId: "job-bad-options",
    options: { ...options, size: 4095 }
  });

  assert.equal(worker.messages[0].message.type, "error");
  assert.equal(worker.messages[0].message.requestId, "job-bad-options");
  assert.equal(worker.messages[0].message.message, "Unsupported preview footprint");
});
