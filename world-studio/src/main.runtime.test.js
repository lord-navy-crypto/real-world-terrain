import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { buildWorldShareUrl, parseWorldSettings } from "./world-settings.js";

const source = readFileSync(new URL("./main.js", import.meta.url), "utf8")
  .replace(/^import\s+.*?;\s*$/gm, "");

function createRuntime(seed = "runtime-smoke-seed", { workerMode = "unsupported", locationSearch = "", clipboardAvailable = true } = {}) {
  const elements = new Map();
  const downloads = [];
  const blobs = [];
  const clipboardWrites = [];
  let sampleCalls = 0;
  let renderedFrames = 0;
  let now = 0;
  let workerInstance = null;

  class FakeWorker {
    constructor(url, options) {
      this.url = url;
      this.options = options;
      this.listeners = new Map();
      this.requests = [];
      this.terminated = false;
      workerInstance = this;
    }
    addEventListener(type, callback) {
      const list = this.listeners.get(type) || [];
      list.push(callback);
      this.listeners.set(type, list);
    }
    emit(type, event) {
      for (const callback of this.listeners.get(type) || []) callback(event);
    }
    makeMap(options) {
      const resolution = options.resolution || 384;
      const count = resolution * resolution;
      return {
        heights: new Float32Array(count).fill(180),
        temperatures: new Float32Array(count).fill(0.125),
        humidities: new Float32Array(count).fill(-0.25),
        size: options.size,
        step: options.size / (resolution - 1),
        min: 180,
        max: 180,
        landPercent: 100,
        seedText: options.seedText,
        reliefScale: options.reliefScale,
        resolution
      };
    }
    complete(requestId, options) {
      this.emit("message", { data: { type: "progress", requestId, progress: 100 } });
      this.emit("message", {
        data: { type: "complete", requestId, map: this.makeMap(options) }
      });
    }
    postMessage(message) {
      this.requests.push(message);
      if (message.type === "generate" && workerMode === "complete") {
        this.complete(message.requestId, message.options);
      } else if (message.type === "generate" && workerMode === "error") {
        this.emit("error", { message: "simulated worker failure" });
      }
    }
    terminate() {
      this.terminated = true;
    }
  }

  class TestURL extends URL {
    static createObjectURL(blob) {
      blobs.push(blob);
      return "blob:world-studio-test";
    }
    static revokeObjectURL() {}
  }

  function element(selector) {
    if (elements.has(selector)) return elements.get(selector);
    const listeners = new Map();
    const value = {
      selector,
      value: "",
      textContent: "",
      innerHTML: "",
      disabled: false,
      hidden: false,
      dataset: {},
      listeners,
      classList: {
        add() {},
        remove() {},
        toggle() { return false; }
      },
      addEventListener(type, callback) {
        const list = listeners.get(type) || [];
        list.push(callback);
        listeners.set(type, list);
      },
      dispatch(type, event = {}) {
        for (const callback of listeners.get(type) || []) callback(event);
      },
      focus() {},
      click() {
        this.dispatch("click");
      },
      getBoundingClientRect() {
        return { left: 0, top: 0, width: 384, height: 384 };
      }
    };
    elements.set(selector, value);
    return value;
  }

  const canvas = element("#terrain-canvas");
  const context = {
    createImageData(width, height) {
      return { data: new Uint8ClampedArray(width * height * 4) };
    },
    putImageData() { renderedFrames += 1; }
  };
  canvas.width = 384;
  canvas.height = 384;
  canvas.getContext = () => context;
  canvas.toDataURL = () => "data:image/png;base64,preview";
  element("#seed-input").value = seed;
  element("#size-select").value = "8192";
  element("#relief-slider").value = "100";
  element("#share-link-panel").hidden = true;

  const reliefButton = element(".view-relief");
  reliefButton.dataset.view = "relief";
  const biomeButton = element(".view-biomes");
  biomeButton.dataset.view = "biomes";

  const document = {
    querySelector: selector => element(selector),
    querySelectorAll: selector => selector === ".view-button"
      ? [reliefButton, biomeButton]
      : [],
    createElement(tag) {
      assert.equal(tag, "a");
      return {
        href: "",
        download: "",
        click() { downloads.push({ href: this.href, download: this.download }); }
      };
    }
  };

  const window = {
    location: {
      href: "https://example.test/studio" + locationSearch,
      search: locationSearch
    },
    requestAnimationFrame(callback) {
      callback();
      return 1;
    },
    setTimeout(callback) {
      callback();
      return 1;
    }
  };

  runInNewContext(source.replaceAll("import.meta.url", '"file:///world-studio/src/main.js"'), {
    document,
    window,
    performance: { now: () => ++now },
    crypto: { getRandomValues: values => values.fill(7) },
    URL: TestURL,
    Worker: workerMode === "unsupported" ? undefined : FakeWorker,
    navigator: {
      clipboard: clipboardAvailable
        ? { async writeText(value) { clipboardWrites.push(value); } }
        : undefined
    },
    buildWorldShareUrl,
    parseWorldSettings,
    Blob,
    clamp: (value, min, max) => Math.max(min, Math.min(max, value)),
    hashSeed: () => 123456,
    sampleTerrain(x, z) {
      sampleCalls += 1;
      return {
        elevation: 180 + (x + z) / 10000,
        temperature: 0.125,
        humidity: -0.25
      };
    }
  }, { filename: "world-studio-main.js" });

  return {
    elements,
    downloads,
    blobs,
    clipboardWrites,
    get worker() { return workerInstance; },
    reliefButton,
    biomeButton,
    get sampleCalls() { return sampleCalls; },
    get renderedFrames() { return renderedFrames; }
  };
}

test("browser app boots and completes its first preview without missing state errors", () => {
  const runtime = createRuntime();
  assert.match(runtime.elements.get("#generation-status").textContent, /Preview generated/);
  assert.equal(runtime.sampleCalls, 384 * 384);
  assert.equal(runtime.renderedFrames, 1);
  assert.equal(runtime.elements.get("#download-preview").disabled, false);
  assert.equal(runtime.elements.get("#download-csv").disabled, false);
  assert.equal(runtime.elements.get("#map-loading").hidden, true);
});

test("switching relief and biome views reuses sampled terrain instead of regenerating it", () => {
  const runtime = createRuntime();
  const samplesBefore = runtime.sampleCalls;
  const rendersBefore = runtime.renderedFrames;

  runtime.biomeButton.dispatch("click");

  assert.equal(runtime.sampleCalls, samplesBefore);
  assert.equal(runtime.renderedFrames, rendersBefore + 1);
  assert.equal(runtime.elements.get("#biome-legend").hidden, false);
  assert.equal(runtime.elements.get("#relief-legend").hidden, true);
  assert.equal(runtime.elements.get("#generation-status").textContent, "Map view updated");
});

test("PNG export downloads the rendered preview with a safe filename", () => {
  const runtime = createRuntime("sample seed");
  runtime.elements.get("#download-preview").click();

  assert.deepEqual(runtime.downloads[0], {
    href: "data:image/png;base64,preview",
    download: "realscale-preview-sample-seed.png"
  });
});

test("CSV export includes all preview samples and protects formula-like seed text", async () => {
  const runtime = createRuntime("=1+1");
  runtime.elements.get("#download-csv").click();

  assert.equal(runtime.blobs.length, 1);
  assert.equal(runtime.downloads.length, 1);
  assert.equal(runtime.downloads[0].download, "realscale-elevation-1-1-8192m-preview.csv");

  const csv = await runtime.blobs[0].text();
  const rows = csv.replace(/^\uFEFF/, "").split("\r\n");
  assert.equal(rows.length, 384 * 384 + 1);
  assert.equal(
    rows[0],
    '"sample_x_blocks","sample_z_blocks","estimated_elevation_m","temperature_index","humidity_index","seed","footprint_blocks"'
  );
  assert.ok(rows[1].includes("\"'=1+1\""));
  assert.ok(rows[1].includes('"-4096","-4096"'));
  assert.ok(rows[1].endsWith('"8192"'));
});


test("browser uses a module worker for normal terrain sampling", () => {
  const runtime = createRuntime("worker-seed", { workerMode: "complete" });

  assert.ok(runtime.worker);
  assert.equal(runtime.worker.options.type, "module");
  assert.equal(runtime.worker.requests[0].type, "generate");
  assert.equal(runtime.worker.requests[0].options.seedText, "worker-seed");
  assert.equal(runtime.sampleCalls, 0);
  assert.match(runtime.elements.get("#generation-status").textContent, /Preview generated/);
});

test("late worker results from an older seed cannot replace the current map", () => {
  const runtime = createRuntime("old-seed", { workerMode: "hold" });
  const oldRequest = runtime.worker.requests[0];
  runtime.worker.complete(oldRequest.requestId, oldRequest.options);

  runtime.elements.get("#seed-input").value = "new-seed";
  runtime.elements.get("#generate-button").click();
  const newRequest = runtime.worker.requests.at(-1);
  runtime.worker.complete(newRequest.requestId, newRequest.options);
  runtime.worker.emit("message", {
    data: {
      type: "complete",
      requestId: oldRequest.requestId,
      map: runtime.worker.makeMap(oldRequest.options)
    }
  });

  assert.equal(runtime.elements.get("#map-title").textContent, "World new-seed");
});

test("worker runtime failure falls back to frame-batched main-thread sampling", () => {
  const runtime = createRuntime("fallback-seed", { workerMode: "error" });

  assert.equal(runtime.worker.terminated, true);
  assert.equal(runtime.sampleCalls, 384 * 384);
  assert.match(runtime.elements.get("#generation-status").textContent, /Preview generated/);
});

test("user can cancel a worker generation and restore the controls", () => {
  const runtime = createRuntime("cancel-seed", { workerMode: "hold" });

  runtime.elements.get("#cancel-generation").click();

  assert.ok(runtime.worker.requests.some(message => message.type === "cancel"));
  assert.equal(runtime.elements.get("#cancel-generation").disabled, true);
  assert.equal(runtime.elements.get("#generate-button").disabled, false);
  assert.equal(runtime.elements.get("#generation-status").textContent, "Generation cancelled");
});


test("shared URL settings are restored before the first worker generation", () => {
  const runtime = createRuntime("default-seed", {
    workerMode: "complete",
    locationSearch: "?seed=river%20valley&size=16384&relief=125&view=biomes"
  });
  const options = runtime.worker.requests[0].options;

  assert.equal(options.seedText, "river valley");
  assert.equal(options.size, 16384);
  assert.equal(options.reliefScale, 1.25);
  assert.equal(runtime.elements.get("#biome-legend").hidden, false);
  assert.equal(runtime.elements.get("#relief-legend").hidden, true);
});

test("share settings button copies a reproducible URL with the current preview controls", async () => {
  const runtime = createRuntime("river & ridge", {
    workerMode: "complete",
    locationSearch: "?campaign=spring"
  });
  runtime.elements.get("#size-select").value = "32768";
  runtime.elements.get("#relief-slider").value = "115";
  runtime.biomeButton.dispatch("click");
  runtime.elements.get("#share-world").click();
  await Promise.resolve();

  assert.equal(runtime.clipboardWrites.length, 1);
  const url = new URL(runtime.clipboardWrites[0]);
  assert.equal(url.searchParams.get("campaign"), "spring");
  assert.equal(url.searchParams.get("seed"), "river & ridge");
  assert.equal(url.searchParams.get("size"), "32768");
  assert.equal(url.searchParams.get("relief"), "115");
  assert.equal(url.searchParams.get("view"), "biomes");
  assert.equal(runtime.elements.get("#share-link-input").value, url.toString());
  assert.equal(runtime.elements.get("#share-link-panel").hidden, false);
});

test("share link remains visible and selectable when clipboard access is unavailable", async () => {
  const runtime = createRuntime("manual-copy", {
    workerMode: "complete",
    clipboardAvailable: false
  });
  runtime.elements.get("#share-world").click();
  await Promise.resolve();

  assert.equal(runtime.clipboardWrites.length, 0);
  assert.ok(runtime.elements.get("#share-link-input").value.startsWith("https://example.test/studio?"));
  assert.equal(runtime.elements.get("#share-link-panel").hidden, false);
  assert.match(runtime.elements.get("#generation-status").textContent, /copy the selected URL/i);
});
