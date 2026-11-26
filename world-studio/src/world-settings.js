const ALLOWED_SIZES = new Set([4096, 8192, 16384, 32768, 65536]);
const ALLOWED_VIEWS = new Set(["relief", "biomes"]);
const DEFAULT_SETTINGS = Object.freeze({
  seed: "realscale",
  size: 8192,
  relief: 100,
  view: "relief"
});

function validSeed(value) {
  return typeof value === "string" && value.trim().length > 0 && value.trim().length <= 48;
}

function validRelief(value) {
  return Number.isInteger(value)
    && value >= 65
    && value <= 140
    && (value - 65) % 5 === 0;
}

function normalizeDefaults(defaults) {
  const value = defaults && typeof defaults === "object" ? defaults : {};
  return {
    seed: validSeed(value.seed) ? value.seed.trim() : DEFAULT_SETTINGS.seed,
    size: ALLOWED_SIZES.has(value.size) ? value.size : DEFAULT_SETTINGS.size,
    relief: validRelief(value.relief) ? value.relief : DEFAULT_SETTINGS.relief,
    view: ALLOWED_VIEWS.has(value.view) ? value.view : DEFAULT_SETTINGS.view
  };
}

function toSearchParams(search) {
  if (search instanceof URLSearchParams) return search;
  if (search instanceof URL) return search.searchParams;
  if (typeof search !== "string" || !search) return new URLSearchParams();

  if (/^[a-z][a-z\d+.-]*:\/\//i.test(search)) {
    try {
      return new URL(search).searchParams;
    } catch {
      return new URLSearchParams();
    }
  }
  return new URLSearchParams(search);
}

function validateWorldSettings(settings) {
  if (!settings || typeof settings !== "object"
    || !validSeed(settings.seed)
    || !ALLOWED_SIZES.has(settings.size)
    || !validRelief(settings.relief)
    || !ALLOWED_VIEWS.has(settings.view)) {
    throw new TypeError("Invalid world settings");
  }
  return {
    seed: settings.seed.trim(),
    size: settings.size,
    relief: settings.relief,
    view: settings.view
  };
}

export function parseWorldSettings(search, defaults = DEFAULT_SETTINGS) {
  const fallback = normalizeDefaults(defaults);
  const params = toSearchParams(search);

  const requestedSeed = params.get("seed");
  const requestedSize = Number(params.get("size"));
  const requestedRelief = Number(params.get("relief"));
  const requestedView = params.get("view");

  return {
    seed: validSeed(requestedSeed) ? requestedSeed.trim() : fallback.seed,
    size: ALLOWED_SIZES.has(requestedSize) ? requestedSize : fallback.size,
    relief: validRelief(requestedRelief) ? requestedRelief : fallback.relief,
    view: ALLOWED_VIEWS.has(requestedView) ? requestedView : fallback.view
  };
}

export function buildWorldShareUrl(href, settings) {
  const normalized = validateWorldSettings(settings);
  const url = new URL(href);
  url.searchParams.set("seed", normalized.seed);
  url.searchParams.set("size", String(normalized.size));
  url.searchParams.set("relief", String(normalized.relief));
  url.searchParams.set("view", normalized.view);
  return url.toString();
}
