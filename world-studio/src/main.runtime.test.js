import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";

const source = readFileSync(new URL("./main.js", import.meta.url), "utf8")
  .replace(/^import\s+.*?;\s*$/gm, "");

function createRuntime(seed = "runtime-smoke-seed") {
  const elements = new Map();
  const downloads = [];
  const blobs = [];
  let sampleCalls = 0;
  let renderedFrames = 0;
  let now = 0;

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

  const URLApi = {
    createObjectURL(blob) {
      blobs.push(blob);
      return "blob:world-studio-test";
    },
    revokeObjectURL() {}
  };

  const window = {
    requestAnimationFrame(callback) {
      callback();
      return 1;
    },
    setTimeout(callback) {
      callback();
      return 1;
    }
  };

  runInNewContext(source, {
    document,
    window,
    performance: { now: () => ++now },
    crypto: { getRandomValues: values => values.fill(7) },
    URL: URLApi,
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
