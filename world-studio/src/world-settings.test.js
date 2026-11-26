import test from "node:test";
import assert from "node:assert/strict";
import { buildWorldShareUrl, parseWorldSettings } from "./world-settings.js";

const DEFAULTS = {
  seed: "realscale-2026",
  size: 8192,
  relief: 100,
  view: "relief"
};

test("share settings restore a complete valid world configuration", () => {
  assert.deepEqual(
    parseWorldSettings("?seed=alpine-expedition&size=16384&relief=135&view=biomes", DEFAULTS),
    {
      seed: "alpine-expedition",
      size: 16384,
      relief: 135,
      view: "biomes"
    }
  );
});

test("share settings use safe defaults when parameters are missing or invalid", () => {
  assert.deepEqual(parseWorldSettings("?size=9999&relief=142&view=orbit&seed=", DEFAULTS), DEFAULTS);
  assert.deepEqual(parseWorldSettings("", DEFAULTS), DEFAULTS);
});

test("share settings reject overlong seeds and invalid numeric values independently", () => {
  const longSeed = "x".repeat(49);
  assert.deepEqual(
    parseWorldSettings("?seed=" + longSeed + "&size=4096&relief=65&view=relief", DEFAULTS),
    { seed: DEFAULTS.seed, size: 4096, relief: 65, view: "relief" }
  );
  assert.deepEqual(
    parseWorldSettings("?seed=valid&size=4096.5&relief=70&view=biomes", DEFAULTS),
    { seed: "valid", size: DEFAULTS.size, relief: 70, view: "biomes" }
  );
});

test("share URL preserves the page path, unrelated query parameters, and fragment", () => {
  const result = buildWorldShareUrl("https://example.test/studio?campaign=spring#map", {
    seed: "alpine-expedition",
    size: 16384,
    relief: 135,
    view: "biomes"
  });
  const url = new URL(result);

  assert.equal(url.origin, "https://example.test");
  assert.equal(url.pathname, "/studio");
  assert.equal(url.searchParams.get("campaign"), "spring");
  assert.equal(url.searchParams.get("seed"), "alpine-expedition");
  assert.equal(url.searchParams.get("size"), "16384");
  assert.equal(url.searchParams.get("relief"), "135");
  assert.equal(url.searchParams.get("view"), "biomes");
  assert.equal(url.hash, "#map");
});

test("share URL safely round-trips seeds containing spaces and reserved characters", () => {
  const seed = "river & ridge + 42";
  const url = buildWorldShareUrl("https://example.test/studio", {
    seed,
    size: 4096,
    relief: 85,
    view: "relief"
  });
  assert.equal(parseWorldSettings(url, DEFAULTS).seed, seed);
});

test("share URL rejects invalid settings rather than creating a misleading link", () => {
  assert.throws(
    () => buildWorldShareUrl("https://example.test/studio", {
      seed: "bad",
      size: 1234,
      relief: 100,
      view: "relief"
    }),
    /Invalid world settings/
  );
});


test("share settings accept and restore the interactive 3D view", () => {
  assert.deepEqual(
    parseWorldSettings("?seed=mountain-world&size=8192&relief=110&view=3d", DEFAULTS),
    {
      seed: "mountain-world",
      size: 8192,
      relief: 110,
      view: "3d"
    }
  );
  const url = buildWorldShareUrl("https://example.test/studio", {
    seed: "mountain-world",
    size: 8192,
    relief: 110,
    view: "3d"
  });
  assert.equal(new URL(url).searchParams.get("view"), "3d");
});
