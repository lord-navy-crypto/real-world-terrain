#!/usr/bin/env python3
"""Convert authentic OSM/GeoJSON vectors to a conservative MC 1.21.1 datapack.

REQUIRES authentic input; never invents buildings, bridges, widths or
water polygons. Center/geodesy and Y sampling intentionally match the
existing RealScale Chaotianmen DEM resource; no downloading in this module.

This is an offline *overlay*, to be applied only AFTER the separately
selected Special Chongqing world is generated and backed up. Minecraft
functions use /setblock and /fill. No direct save files modified.
"""
import argparse
import base64
import collections
import gzip
import hashlib
import json
import math
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DEM = (ROOT / "neoforge/src/main/resources/assets/realscale/terrain"
               / "chongqing_chaotianmen_dem_v1.properties")
RADIUS = 500
VERTICAL_OFFSET = 96
PACK_FORMAT = 48  # Minecraft 1.21.1
MAX_COMMANDS = 250000
COMMANDS_PER_FUNCTION = 3000
MAX_BLOCKS_PER_COMMAND = 2048  # conservative /fill cap (vanilla default is higher)
MAX_BLOCKS_PER_FUNCTION = 20000  # limit work in a single /function batch
VALID_BLOCKS = {
    "building_footprint": "minecraft:light_gray_concrete",
    "building_wall": "minecraft:light_gray_concrete",
    "building_roof": "minecraft:gray_concrete",
    "road": "minecraft:gray_concrete",
    "railway": "minecraft:polished_andesite",
    "water_reference": "minecraft:blue_concrete", # NOT flowing water!
}
LEVEL_HEIGHT_ESTIMATE = 3  # only for tagged levels; report as estimated
SUPPORTED = {"building", "highway", "railway", "water"}

def props(path: Path) -> dict:
    output = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or key in output:
            raise ValueError("Invalid/duplicate DEM metadata key")
        output[key] = value
    return output

class Dem:
    def __init__(self, file: Path):
        p = props(file)
        if p.get("format") != "CQDEM1":
            raise ValueError("Missing authentic CQDEM1 file")
        self.n = int(p["width"])
        self.step = int(p["spacing_m"])
        self.min_x = int(p["min_x"])
        self.min_z = int(p["min_z"])
        self.lat = float(p["center_lat"])
        self.lon = float(p["center_lon"])
        if self.n < 2 or self.n > 1025 or self.step <= 0:
            raise ValueError("Malformed DEM grid geometry")
        payload = gzip.decompress(base64.b64decode(
            p["heights_gzip_base64"], validate=True))
        if len(payload) != self.n * self.n * 2:
            raise ValueError("DEM size mismatch")
        self.sha256 = hashlib.sha256(payload).hexdigest()
        if self.sha256 != p["source_raw_sha256"]:
            raise ValueError("DEM checksum mismatch")
        self.data = struct.unpack(">" + str(self.n * self.n) + "h", payload)
        phi = math.radians(self.lat)
        # IMPORTANT: exactly the local WGS84 tangent-plane formula used
        # to source the original Mapzen DEM. An EPSG:32648 transform alone
        # is not identical to these existing saved-world coordinates.
        self.m_lat = (111132.92 - 559.82 * math.cos(2 * phi)
                      + 1.175 * math.cos(4 * phi))
        self.m_lon = (111412.84 * math.cos(phi)
                      - 93.5 * math.cos(3 * phi)
                      + 0.118 * math.cos(5 * phi))
        if min(self.m_lat, self.m_lon) <= 0:
            raise ValueError("Invalid local world projection")

    def coords(self, lon: float, lat: float) -> tuple[float, float]:
        if not all(map(math.isfinite, (lon, lat))):
            raise ValueError("Nonfinite geographic position")
        if not (100 < lon < 115 and 20 < lat < 40):
            raise ValueError("OSM geometry is not in Chongqing WGS84")
        return ((lon - self.lon) * self.m_lon,
                (self.lat - lat) * self.m_lat)

    def height(self, x: float, z: float) -> float:
        a = (x - self.min_x) / self.step
        b = (z - self.min_z) / self.step
        if a < 0 or b < 0 or a > self.n - 1 or b > self.n - 1:
            raise ValueError("No authentic DEM outside recorded coverage")
        ix = min(self.n - 2, math.floor(a))
        iz = min(self.n - 2, math.floor(b))
        u = a - ix
        v = b - iz
        i = iz * self.n + ix
        h00, h10 = self.data[i], self.data[i + 1]
        h01, h11 = self.data[i + self.n], self.data[i + self.n + 1]
        return (h00 + (h10 - h00) * u) * (1 - v) + (
                h01 + (h11 - h01) * u) * v

    def block_y(self, x: int, z: int) -> int:
        # Approximate top surface of the existing real density field.
        # Export is a *postgen* overlay; inspect a saved-world section
        # before using on terrain near cliffs/water or altered heightmaps.
        return math.floor(self.height(x, z) + VERTICAL_OFFSET) + 1


def classify(tags: dict) -> str | None:
    if tags.get("building") and tags.get("building") != "no":
        return "building"
    if tags.get("highway"):
        return "highway"
    if tags.get("railway"):
        return "railway"
    if (tags.get("natural") == "water"
            or tags.get("waterway") == "riverbank"):
        return "water"
    return None

def stitch_members(members: list, role: str) -> list[list] | None:
    """Stitch authentic OSM relation way segments; never fake closure.

    All endpoints must have identical WGS84 coordinates after export.
    A broken multipolygon is rejected in full, not approximately closed.
    """
    parts = []
    for part in members:
        if part.get("type") != "way" or part.get("role") != role:
            continue
        geometry = part.get("geometry")
        if not isinstance(geometry, list) or len(geometry) < 2:
            return None
        line = [[v["lon"], v["lat"]] for v in geometry]
        parts.append(line)
    result = []
    while parts:
        chain = parts.pop(0)
        while chain[0] != chain[-1]:
            merged = False
            for i, candidate in enumerate(parts):
                if chain[-1] == candidate[0]:
                    chain += candidate[1:]
                elif chain[-1] == candidate[-1]:
                    chain += list(reversed(candidate[:-1]))
                elif chain[0] == candidate[-1]:
                    chain = candidate[:-1] + chain
                elif chain[0] == candidate[0]:
                    chain = list(reversed(candidate[1:])) + chain
                else:
                    continue
                parts.pop(i)
                merged = True
                break
            if not merged:
                return None
        if len(chain) < 4:
            return None
        result.append(chain)
    return result

def relation_multipolygon(element: dict) -> list | None:
    if element.get("tags", {}).get("type") != "multipolygon":
        return None
    members = element.get("members") or []
    outers = stitch_members(members, "outer")
    inners = stitch_members(members, "inner")
    if not outers or inners is None:
        return None
    polygons = [[outer] for outer in outers]
    for hole in inners:
        fitting = [i for i, outer in enumerate(outers)
                   if ring_contains(hole[0][0], hole[0][1], outer)]
        if len(fitting) != 1:
            return None  # ambiguous/intersecting or missing outer
        polygons[fitting[0]].append(hole)
    return polygons

def osm_features(data: dict) -> list[dict]:
    """Accept Overpass out geom OR GeoJSON, reject empty/invalid sources."""
    if data.get("type") == "FeatureCollection":
        incoming = data.get("features", [])
        out = []
        for f in incoming:
            geometry = f.get("geometry") or {}
            kind = geometry.get("type")
            coords = geometry.get("coordinates")
            if kind not in ("LineString", "Polygon", "MultiPolygon"):
                continue
            tags = f.get("properties") or {}
            # Overpass Turbo marks GeoJSON that has missing nodes / broken
            # geometries as @tainted. Do NOT rasterize it as verified GIS.
            if not isinstance(tags, dict) or tags.get("@tainted"):
                continue
            feature_id = str(f.get("id", tags.get("@id", "unknown")))
            if not isinstance(coords, list):
                continue
            out.append(dict(id=feature_id, tags=tags, kind=kind,
                            coordinates=coords))
        if not out:
            raise ValueError("No supported real GeoJSON features")
        return sorted(out, key=lambda x: x["id"])
    if "elements" in data:
        incoming = data["elements"]
        if not isinstance(incoming, list):
            raise ValueError("Malformed Overpass elements")
        out = []
        for f in incoming:
            tags = f.get("tags") or {}
            if f.get("type") == "relation" and classify(tags):
                polygons = relation_multipolygon(f)
                if polygons:
                    out.append(dict(id="relation/" + str(f["id"]),
                                    tags=tags, kind="MultiPolygon",
                                    coordinates=polygons))
                # Invalid OSM relations are skipped, not autocorrected.
                continue
            if f.get("type") != "way" or not classify(tags):
                continue
            raw = f.get("geometry")
            if not isinstance(raw, list) or len(raw) < 2:
                continue
            coords = [[p["lon"], p["lat"]] for p in raw]
            is_ring = (coords[0] == coords[-1] and len(coords) >= 4)
            category = classify(tags)
            # Closed ways are NOT necessarily filled areas: a loop of highway
            # or railway is still a centreline unless explicitly area=yes.
            is_area = (is_ring and tags.get("area") != "no" and
                       (category in ("building", "water") or
                        tags.get("area") == "yes"))
            out.append(dict(id="way/" + str(f["id"]), tags=tags,
                            kind="Polygon" if is_area else "LineString",
                            coordinates=[coords] if is_area else coords))
        if not out:
            raise ValueError("Overpass data has no complete, supported ways")
        return sorted(out, key=lambda x: x["id"])
    raise ValueError("Expected Overpass out geom or GeoJSON FeatureCollection")

def ring_contains(x: float, z: float, ring: list) -> bool:
    hit = False
    for a, b in zip(ring, ring[1:]):
        x1, z1 = a
        x2, z2 = b
        if ((z1 > z) != (z2 > z)
                and x < (x2 - x1) * (z - z1) / (z2 - z1) + x1):
            hit = not hit
    return hit

def polygon_cells(rings: list, radius: int) -> set[tuple[int, int]]:
    if not rings or len(rings[0]) < 4 or rings[0][0] != rings[0][-1]:
        return set()
    outer = rings[0]
    xs = [v[0] for v in outer]
    zs = [v[1] for v in outer]
    min_x = max(-radius, math.floor(min(xs)))
    max_x = min(radius, math.ceil(max(xs)))
    min_z = max(-radius, math.floor(min(zs)))
    max_z = min(radius, math.ceil(max(zs)))
    if (max_x - min_x + 1) * (max_z - min_z + 1) > 1050000:
        raise ValueError("Unbounded polygon raster range")
    result = set()
    for z in range(min_z, max_z + 1):
        for x in range(min_x, max_x + 1):
            if ring_contains(x + 0.5, z + 0.5, outer) and not any(
                    ring_contains(x + 0.5, z + 0.5, hole)
                    for hole in rings[1:]):
                result.add((x, z))
    return result

def linestring_cells(points: list, radius: int) -> set[tuple[int, int]]:
    pixels = set()
    for a, b in zip(points, points[1:]):
        x0, z0 = a
        x1, z1 = b
        # Interpolate at <=0.5-block spacing, for curved map features.
        distance = math.hypot(x1 - x0, z1 - z0)
        if distance > 100000:
            raise ValueError("Unbounded segment")
        divisions = max(1, math.ceil(distance * 2))
        for i in range(divisions + 1):
            t = i / divisions
            x = round(x0 + (x1 - x0) * t)
            z = round(z0 + (z1 - z0) * t)
            if -radius <= x <= radius and -radius <= z <= radius:
                pixels.add((x, z))
    return pixels

def meters_tag(tags: dict, key: str) -> float | None:
    value = tags.get(key)
    if not isinstance(value, str):
        return None
    value = value.strip().lower().replace(" ", "")
    if value.endswith("m"):
        value = value[:-1]
    if not re.fullmatch(r"(?:\d+(?:\.\d+)?)", value):
        return None
    amount = float(value)
    return amount if 0 < amount <= 500 else None

def rings_projected(dem: Dem, coordinates: list) -> list:
    rings = []
    for ring in coordinates:
        if not isinstance(ring, list) or len(ring) < 4:
            return []
        transformed = [dem.coords(float(p[0]), float(p[1])) for p in ring]
        if transformed[0] != transformed[-1]:
            return []  # no fabricated closure on malformed OSM polygon
        rings.append(transformed)
    return rings

def project(dem: Dem, geom: dict) -> list:
    kind = geom["kind"]
    coords = geom["coordinates"]
    if kind == "LineString":
        return [[dem.coords(float(p[0]), float(p[1])) for p in coords]]
    if kind == "Polygon":
        return [rings_projected(dem, coords)]
    if kind == "MultiPolygon":
        return [rings_projected(dem, polygon) for polygon in coords]
    return []

def prepare(dem: Dem, features: list, radius: int) -> tuple[dict, dict]:
    if not 32 <= radius <= 512:
        raise ValueError("Pilot radius must be between 32 and 512 metres")
    # One block per OSM metric coordinate, 1:1 XY horizontal scale.
    # Order is deterministic and ensures roads/buildings topologically
    # overlay water reference; no texture/height attributed when unknown.
    priority = {"water": 0, "highway": 1, "railway": 2, "building": 3}
    categorized = [(classify(f["tags"]), f) for f in features]
    categorized = sorted(
        ((k, f) for k, f in categorized if k in priority),
        key=lambda x: (priority[x[0]], x[1]["id"]))
    layers = {}
    stats = collections.Counter()
    samples = set()
    for kind, f in categorized:
        geom = project(dem, f)
        tags = f["tags"]
        if kind in ("highway","railway") and (
                tags.get("bridge") not in (None,"no") or
                tags.get("tunnel") not in (None,"no") or
                str(tags.get("layer","0")) not in ("0","0.0")):
            # Chongqing's road decks, tunnel mouths and sky rail cannot
            # be flattened onto ground. Only a survey-/source-backed
            # 3D alignment is allowed in a later version.
            stats["multilevel_lines_deferred_no_3d_y"] += 1
            continue
        if kind == "water" and f["kind"] == "LineString":
            stats["linear_water_skipped_unknown_width"] += 1
            continue
        if kind == "building" and f["kind"] == "LineString":
            stats["building_nonpolygon_skipped"] += 1
            continue
        polygons = []
        if f["kind"] in ("Polygon", "MultiPolygon"):
            for poly in geom:
                if poly and poly[0]:
                    polygons.append(poly)
        pixels = set()
        for rings in polygons:
            pixels.update(polygon_cells(rings, radius))
        if not pixels and f["kind"] == "LineString" and geom:
            pixels.update(linestring_cells(geom[0], radius))
        if not pixels:
            continue
        # Only stable REAL shape coordinates are used. If a road width is
        # missing, render its observed centerline ONE block wide.
        # That is a schematic guide, NOT a claim of real pavement width.
        if kind == "highway" and f["kind"] == "LineString":
            w = meters_tag(tags, "width")
            if w is not None and w <= 20:
                base = list(pixels)
                r = math.ceil(w / 2)
                for x,z in base:
                    for dz in range(-r,r+1):
                        for dx in range(-r,r+1):
                            if dx*dx+dz*dz <= (w/2)**2 and abs(x+dx)<=radius and abs(z+dz)<=radius:
                                pixels.add((x+dx,z+dz))
            else:
                stats["roads_missing_or_unsupported_width"] += 1

        explicit_height = meters_tag(tags, "height")
        levels = meters_tag(tags, "building:levels")
        building_height = (math.ceil(explicit_height) if explicit_height is not None
                           else (math.ceil(levels * LEVEL_HEIGHT_ESTIMATE)
                                 if levels is not None else None))
        if kind == "building" and explicit_height is None and levels is not None:
            stats["buildings_estimated_height_from_levels"] += 1
        if kind == "building" and building_height is None:
            stats["buildings_footprint_only"] += 1
        color = {"water": "water_reference", "highway": "road",
                 "railway": "railway", "building": "building_footprint"}[kind]
        for x, z in sorted(pixels):
            if not (-radius <= x <= radius and -radius <= z <= radius):
                continue
            y = dem.block_y(x, z)
            if kind == "building" and building_height is not None:
                if y + building_height >= 955:  # special preset Y max 959
                    stats["building_too_tall_skipped"] += 1
                    continue
                # Stone/roof palette is an interpretation, not source imagery.
                layers[(x,z)] = (color, y, building_height, f["id"])
            else:
                layers[(x,z)] = (color, y, None, f["id"])
            samples.add(f["id"])
        stats[kind + "_features_processed"] += 1
    if not layers:
        raise ValueError("No real geometries overlap the Chaotianmen pilot")
    return layers, dict(stats) | {"geometries_with_blocks": len(samples)}

def export_datapack(layers: dict, stats: dict, source: Path,
                    dem: Dem, destination: Path, radius: int,
                    attribution: str) -> dict:
    if not attribution.strip():
        raise ValueError("Real OSM attribution must be provided")
    if destination.exists() and any(destination.iterdir()):
        raise ValueError("Output directory must be empty; never overwrite a save")
    functions = destination / "data" / "realscale_chongqing" / "function"
    functions.mkdir(parents=True, exist_ok=True)
    (destination / "pack.mcmeta").write_text(json.dumps({
        "pack":{"pack_format":PACK_FORMAT,
                "description":"RealScale Chaotianmen real-GIS overlay, staged execution"}},
        ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    # Row-run compression makes large REAL water polygons and roads practical.
    # Never merge across different Y, features, materials or tagged heights;
    # y follows the authentic DEM rather than an invented level surface.
    # Keep each fill <= 2048 blocks, and each function <= 20000 writes so
    # no single function can attempt millions of placements in one tick.
    ordered = sorted(layers.items(), key=lambda e: (e[0][1], e[0][0]))
    output = []  # list of (Minecraft command, affected block count)
    native_rows = []  # safe, bounded, command-free native in-game UI plan
    used = collections.Counter()
    i = 0
    while i < len(ordered):
        (x, z), (type_name, y, height, feature_id) = ordered[i]
        j = i + 1
        while (j < len(ordered) and ordered[j][0] == (ordered[j-1][0][0] + 1, z)
               and ordered[j][1] == (type_name, y, height, feature_id)):
            j += 1
        # Limit width by the *vertical* /fill volume for known-height
        # buildings. Preserve the original exact footprint per block.
        max_width = max(1, MAX_BLOCKS_PER_COMMAND // max(1, height or 1))
        for start in range(i, j, max_width):
            stop = min(j, start + max_width)
            x0 = ordered[start][0][0]
            x1 = ordered[stop - 1][0][0]
            width = x1 - x0 + 1
            native_rows.append({
                "x0": x0, "x1": x1, "z": z, "y": y,
                "kind": type_name, "height": height if height is not None else 0
            })
            block = VALID_BLOCKS[type_name]
            if width == 1:
                output.append((f"setblock {x0} {y} {z} {block} replace", 1))
            else:
                output.append((f"fill {x0} {y} {z} {x1} {y} {z} {block} replace",
                               width))
            used[type_name] += width
            if height is not None:
                # Solid schematic massing; NOT a researched facade/roof.
                output.append((
                    f"fill {x0} {y+1} {z} {x1} {y+height} {z} "
                    f"{VALID_BLOCKS['building_wall']} replace",
                    width * height))
                used["building_mass_columns"] += width
        i = j
    if len(output) > MAX_COMMANDS or len(native_rows) > MAX_COMMANDS:
        raise ValueError("Too many commands/rows for conservative staging cap")
    chunks = []
    current, volume = [], 0
    volumes = []
    for command, blocks in output:
        if current and (len(current) >= COMMANDS_PER_FUNCTION or
                        volume + blocks > MAX_BLOCKS_PER_FUNCTION):
            chunks.append(current)
            volumes.append(volume)
            current, volume = [], 0
        if blocks > MAX_BLOCKS_PER_FUNCTION:
            raise ValueError("A single placement exceeds staged block cap")
        current.append(command)
        volume += blocks
    if current:
        chunks.append(current)
        volumes.append(volume)
    for batch, commands in enumerate(chunks):
        (functions / f"tile_{batch:04d}.mcfunction").write_text(
            "\n".join(commands)+"\n",encoding="utf-8")
    manifest = {
        "format":"REALSCALE_CHAOTIANMEN_GIS_OVERLAY_V1",
        "center_lat":dem.lat,"center_lon":dem.lon,
        "radius_m":radius,"meters_per_block_horizontal":1,
        "dem_source_raw_sha256":dem.sha256,
        "vector_source_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
        "vector_attribution":attribution,
        "features_summary":stats,
        "block_counts":dict(used),"staged_commands":len(output),
        "staged_block_writes":sum(volumes),
        "max_stage_block_writes":MAX_BLOCKS_PER_FUNCTION,
        "stage_block_writes":volumes,
        "execution_method":"DEM-aligned horizontal run compression with 2048-block fill and 20000-write stage caps",
        "stages":len(chunks),
        "execute_in_order":[
            f"/function realscale_chongqing:tile_{i:04d}"
            for i in range(len(chunks))],
        "safety_note":"Back up the generated Special Chongqing world; run only after chunk pregeneration. NEVER run on your primary TVA world. Water uses blue-concrete reference, NOT navigable fluid; unknown building heights are footprints only.",
    }
    (destination/"manifest.json").write_text(json.dumps(
        manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    # An opt-in native UI can read this plan directly in a singleplayer
    # Special Chongqing world. No chat /function or console commands.
    # Each row is a deterministic, DEM-aligned horizontal segment.
    # This file is not itself an installer and never edits a save.
    native = {
        "format": "REALSCALE_CHAOTIANMEN_PLACEMENT_V1",
        "center_lat": dem.lat, "center_lon": dem.lon, "radius_m": radius,
        "dem_source_raw_sha256": dem.sha256,
        "vector_source_sha256": manifest["vector_source_sha256"],
        "vector_attribution": attribution,
        "source_unverified": True,
        "placement_requires": "singleplayer creative NEW realscale:special_chongqing_sky world, saved-world backup, player confirmation",
        "row_count": len(native_rows),
        "planned_block_writes": sum(
            (r["x1"] - r["x0"] + 1) * (r["height"] + 1) for r in native_rows),
        "rows": native_rows,
    }
    (destination/"chongqing-gis-plan.json").write_text(
        json.dumps(native,ensure_ascii=False,separators=(",",":"))+"\n",
        encoding="utf-8")
    manifest["native_ui_plan"] = "chongqing-gis-plan.json"
    # Keep on-disk manifest in sync with the new UI export.
    (destination/"manifest.json").write_text(json.dumps(
        manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return manifest

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input",required=True,type=Path,
                   help="Authentic Overpass out geom JSON or GeoJSON")
    p.add_argument("--dem",default=DEFAULT_DEM,type=Path)
    p.add_argument("--radius",type=int,default=RADIUS)
    p.add_argument("--output",required=True,type=Path,
                   help="NEW EMPTY datapack output folder; not the saves dir")
    p.add_argument("--attribution",required=True,
                   help="OSM author/license/source URL/date for metadata")
    args=p.parse_args(argv)
    dem=Dem(args.dem)
    data=json.loads(args.input.read_text(encoding="utf-8"))
    features=osm_features(data)
    blocks,stats=prepare(dem,features,args.radius)
    manifest=export_datapack(blocks,stats,args.input,dem,
                             args.output,args.radius,args.attribution)
    print(json.dumps({key:manifest[key] for key in (
        "staged_commands","stages","block_counts","features_summary",
        "vector_source_sha256")},indent=2))
    print("Map interpretation is NOT a validated full Chongqing city; read manifest.json.")
    return 0

if __name__=="__main__":
    raise SystemExit(main())
