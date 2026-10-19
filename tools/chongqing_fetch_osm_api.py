#!/usr/bin/env python3
"""One-shot official OSM 0.6 map API fallback for the pinned Chaotianmen DEM.

For small bounded, educational edits/research only; prefer Overpass for
repeat/bulk extracts. The output preserves original OSM IDs, coordinates,
tag dictionaries, ODbL attribution and original response hash. No inferred
vertices, riverbanks, building positions or tower massing.

No in-game commands, save-file mutation, retries, or query loops.
"""
import argparse
import base64
import datetime
import gzip
import hashlib
import json
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

from chongqing_osm_to_minecraft import DEFAULT_DEM, Dem

API = "https://api.openstreetmap.org/api/0.6/map"
MAX_SOURCE_BYTES = 8 * 1024 * 1024


def bounds(dem: Dem, radius: int, buffer_m: int = 100):
    if not 32 <= radius <= 500 or not 0 <= buffer_m <= 200:
        raise ValueError("Only the explicitly bounded 500m pilot is supported")
    dx = (radius + buffer_m) / dem.m_lon
    dy = (radius + buffer_m) / dem.m_lat
    # OSM endpoint accepts west,south,east,north.
    bbox = [round(dem.lon-dx,7), round(dem.lat-dy,7),
            round(dem.lon+dx,7), round(dem.lat+dy,7)]
    return bbox, API + "?bbox=" + ",".join(str(x) for x in bbox)


def _tags(element):
    return {tag.get("k"): tag.get("v") for tag in element.findall("tag")}


def _supported(tags):
    return ((tags.get("building") not in (None, "no")) or
            "highway" in tags or "railway" in tags or
            tags.get("natural") == "water" or
            tags.get("waterway") == "riverbank")


def convert_xml(raw: bytes, url: str, bbox: list, timestamp: str) -> dict:
    if len(raw) > MAX_SOURCE_BYTES:
        raise ValueError("OSM XML source exceeds 8 MiB")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise ValueError("Invalid OSM XML response") from exc
    if root.tag != "osm" or root.get("version") != "0.6":
        raise ValueError("Not authentic OSM API v0.6 data")
    license_url = root.get("license") or ""
    if "odbl" not in license_url.lower():
        raise ValueError("Expected an ODbL-attributed OSM API response")

    nodes = {}
    for node in root.findall("node"):
        try:
            lon, lat = float(node.get("lon")), float(node.get("lat"))
            if not (100 < lon < 115 and 20 < lat < 40):
                raise ValueError("Out-of-region OSM node")
        except (TypeError, ValueError) as exc:
            raise ValueError("Malformed or misplaced OSM node") from exc
        nodes[node.get("id")] = (lon, lat)
    ways = {way.get("id"): way for way in root.findall("way")}

    def geometry(way):
        positions = [nodes.get(nd.get("ref")) for nd in way.findall("nd")]
        if len(positions) < 2 or any(p is None for p in positions):
            return None
        return [{"lon":p[0], "lat":p[1]} for p in positions]

    elements = []
    deferred = []
    for way in ways.values():
        tags = _tags(way)
        if not _supported(tags):
            continue
        coords = geometry(way)
        if coords is None:
            deferred.append("way/" + str(way.get("id")))
            continue
        elements.append({
            "type": "way", "id": int(way.get("id")),
            "version": int(way.get("version")),
            "tags": tags, "geometry": coords,
        })

    for rel in root.findall("relation"):
        tags = _tags(rel)
        if (tags.get("type") != "multipolygon" or
                not (_supported(tags) and
                     ("building" in tags or tags.get("natural") == "water" or
                      tags.get("waterway") == "riverbank"))):
            continue
        members = []
        for m in rel.findall("member"):
            if m.get("type") != "way":
                continue
            way = ways.get(m.get("ref"))
            coords = geometry(way) if way is not None else None
            members.append({
                "type": "way", "ref": int(m.get("ref")),
                "role": m.get("role"), "geometry": coords or [],
            })
        if not members or any(not m["geometry"] for m in members):
            deferred.append("relation/" + str(rel.get("id")))
            continue
        elements.append({
            "type": "relation", "id": int(rel.get("id")),
            "version": int(rel.get("version")), "tags": tags,
            "members": members,
        })

    if not elements:
        raise ValueError("No complete supported OSM geography")
    return {
        "version": 0.6,
        "generator": "RealScale faithful official OSM 0.6 geometry adapter",
        "realscale_source": {
            "url": url, "bbox_wsen": bbox, "date_utc": timestamp,
            "raw_xml_sha256": hashlib.sha256(raw).hexdigest(),
            "raw_xml_bytes": len(raw), "api_xml_version": "0.6",
            "license": "ODbL-1.0",
            "attribution": "© OpenStreetMap contributors",
            "total_nodes": len(nodes),
            "total_ways": len(ways),
            "total_relations": len(root.findall("relation")),
            "included_elements": len(elements),
            "rejected_incomplete_objects": deferred,
            "conversion": "Exact nodes/ways/relations; no invented geometry",
        },
        "elements": elements,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dem", type=Path, default=DEFAULT_DEM)
    parser.add_argument("--radius", type=int, default=500)
    parser.add_argument("--buffer-m", type=int, default=100)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    if not args.output.name.endswith(".json.gz.b64"):
        parser.error("Use versioned .json.gz.b64 output to record immutable source")
    dem = Dem(args.dem)
    bbox,url = bounds(dem,args.radius,args.buffer_m)
    if args.dry_run:
        print(json.dumps({"bbox_wsen":bbox,"url":url},indent=2))
        return 0
    if args.output.exists():
        parser.error("Immutable OSM snapshot exists; never silently overwrite")
    req = urllib.request.Request(
        url, headers={"User-Agent":
                      "RealScale-Chongqing-GIS/0.4 (educational OSM/ODbL source audit)",
                      "Accept": "application/xml"})
    with urllib.request.urlopen(req,timeout=75) as response:
        if response.status != 200:
            raise RuntimeError("OSM API returned HTTP "+str(response.status))
        raw = response.read(MAX_SOURCE_BYTES + 1)
    result = convert_xml(
        raw, url, bbox,
        datetime.datetime.now(datetime.timezone.utc).isoformat())
    binary = json.dumps(result,ensure_ascii=False,separators=(",",":")).encode()
    packed = base64.b64encode(gzip.compress(binary,mtime=0)).decode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(packed+"\n",encoding="ascii")
    print(json.dumps({"source":url,
                      "upstream_sha256":result["realscale_source"]["raw_xml_sha256"],
                      "archived_elements":len(result["elements"]),
                      "archive":str(args.output)},
                     ensure_ascii=False,indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
