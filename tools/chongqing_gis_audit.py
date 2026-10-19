#!/usr/bin/env python3
"""Offline audit of USER-PROVIDED Chaotianmen OSM GIS: SVG + JSON, no world edits.

Uses the SAME WGS84 coordinates as the real CQDEM1 terrain pilot. This
only inspects what the provided source actually contains: it does not
assert the dataset is authentic, complete, or a faithful 3D reconstruction.
"""
import argparse
import collections
import hashlib
import json
import math
import xml.etree.ElementTree as ET
from pathlib import Path

from chongqing_osm_to_minecraft import DEFAULT_DEM, Dem, classify, osm_features, project, load_gis_source

SVG_NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG_NS)
COLORS = {
    "water": ("#b1ddeb", "#368bb1"),
    "building": ("#667783", "#394950"),
    "highway": ("none", "#de8d32"),
    "railway": ("none", "#8b62b1"),
}
LANDMARKS = {
    "Raffles City / 重庆来福士": ("来福士", "raffles city"),
    "The Crystal / 水晶连廊": ("水晶连廊", "the crystal"),
    "T2 tower / T2塔楼": ("t2塔楼",),
    "T3N tower / T3N塔楼": ("t3n塔楼",),
    "T3S tower / T3S塔楼": ("t3s塔楼",),
    "T4N tower / T4N塔楼": ("t4n塔楼",),
    "T4S tower / T4S塔楼": ("t4s塔楼",),
    "T5 tower / T5塔楼": ("t5塔楼",),
    "T6 tower / T6塔楼": ("t6塔楼",),
    "Chaotianmen / 朝天门": ("朝天门", "chaotianmen"),
    "Chaotianmen Square / 朝天门广场": ("朝天门广场", "chaotianmen square"),
    "Qiansimen Bridge / 千厮门大桥": ("千厮门", "qiansimen"),
    "Yangtze River / 长江": ("长江", "yangtze"),
    "Jialing River / 嘉陵江": ("嘉陵江", "jialing"),
    "Hongyadong / 洪崖洞": ("洪崖洞", "hongyadong"),
}

def svg_elem(parent, name, attributes=None, text=None):
    element = ET.SubElement(parent, "{" + SVG_NS + "}" + name, attributes or {})
    if text is not None:
        element.text = str(text)
    return element

def flatten_paths(feature, geometries):
    """List paths in X east / Z south metres; polygons retain hole rings."""
    if feature["kind"] == "LineString":
        return [(geometries[0], False)] if geometries else []
    return [(ring, True) for poly in geometries for ring in poly]

def points_bbox(paths):
    points = [p for vertices,_ in paths for p in vertices]
    if not points:
        return None
    if not all(math.isfinite(x) and math.isfinite(z) for x,z in points):
        raise ValueError("Non-finite projected OSM coordinates")
    return [min(x for x,z in points),min(z for x,z in points),
            max(x for x,z in points),max(z for x,z in points)]

def inspect(data, dem, radius, source_digest):
    if not 32 <= radius <= 512:
        raise ValueError("Audit radius outside supported pilot")
    features = osm_features(data)
    if len(features) > 50000:
        raise ValueError("More than 50,000 source geometries: narrow the query")
    counts = collections.Counter()
    landmarks = {name: [] for name in LANDMARKS}
    entries = []
    for f in features:
        kind = classify(f["tags"])
        if kind not in COLORS:
            continue
        counts["input_" + kind] += 1
        try:
            geoms = project(dem,f)
            paths = flatten_paths(f,geoms)
            if sum(len(points) for points,_ in paths) > 100000:
                counts["features_skipped_large"] += 1
                continue
            bbox = points_bbox(paths)
        except (ValueError,TypeError,IndexError,KeyError,OverflowError):
            counts["features_skipped_invalid"] += 1
            continue
        if not bbox:
            counts["features_skipped_empty"] += 1
            continue
        if bbox[2] < -radius or bbox[0] > radius or bbox[3] < -radius or bbox[1] > radius:
            counts["features_outside_pilot_bbox"] += 1
            continue
        counts["nearby_" + kind] += 1
        tags = f["tags"]
        name = str(tags.get("name") or tags.get("name:zh") or tags.get("name:en") or "")
        names = " ".join(str(tags.get(k) or "") for k in
                         ("name","name:zh","name:en","alt_name","official_name")).lower()
        for label, keywords in LANDMARKS.items():
            if any(key in names for key in keywords):
                landmarks[label].append(f["id"])
        has_height = bool(str(tags.get("height","")).strip())
        has_levels = bool(str(tags.get("building:levels","")).strip())
        layered = (tags.get("bridge") not in (None,"no") or
                   tags.get("tunnel") not in (None,"no") or
                   str(tags.get("layer","0")) not in ("0","0.0"))
        if kind == "building":
            if layered or tags.get("min_height") or tags.get("building:min_level"):
                counts["buildings_require_vertical_survey"] += 1
            if has_height:
                counts["building_has_height_tag"] += 1
            elif has_levels:
                counts["building_has_levels_only"] += 1
            else:
                counts["building_missing_height"] += 1
        if kind in ("highway","railway") and layered:
            counts["transport_requires_vertical_survey"] += 1
        if kind == "water":
            if f["kind"] == "LineString":
                counts["water_only_line_no_bank_polygon"] += 1
            else:
                counts["water_polygon_candidates"] += 1
        entries.append({"id":f["id"],"kind":kind,"name":name,
                        "geometry_kind":f["kind"],
                        "bbox_xz_m":[round(v,2) for v in bbox],
                        "height_tag":tags.get("height"),
                        "levels_tag":tags.get("building:levels"),
                        "min_height_tag":tags.get("min_height"),
                        "layer_tag":tags.get("layer"),
                        "bridge_or_tunnel_or_layer":bool(layered),
                        "_paths":paths})
    report = {
        "format": "CHAOTIANMEN_OSM_GIS_AUDIT_V1",
        "source_sha256": source_digest,
        "dataset_type": ("OSM API verified-coordinate adapter" if data.get("realscale_source")
                          else "Overpass JSON" if "elements" in data else "GeoJSON"),
        "upstream_osm_provenance": data.get("realscale_source"),
        "osm_timestamp_if_provided": (data.get("osm3s") or {}).get("timestamp_osm_base"),
        "center_wgs84":[dem.lat,dem.lon],"radius_m":radius,
        "projection":"Same local WGS84 tangent plane as CQDEM1; X east, Z south, 1 m/block",
        "counts":dict(sorted(counts.items())),
        "named_landmark_candidates":landmarks,
        "missing_landmark_names_in_source":[name for name,ids in landmarks.items()
                                             if not ids],
        "data_validation": "Source-only spatial audit. IDs/names are user-provided OSM/GeoJSON claims, NOT independently verified landmark geometry or height.",
        "limitations": [
            "An absent OSM name does not mean the real-world landmark is absent.",
            "BBox overlap is approximate: actual source geometry may lie outside the square.",
            "No verification of bridge deck heights, tunnel depths, facades or roof meshes.",
            "No water level is inferred from DEM. No real-world aerial imagery copied.",
        ],
        "features":[{k:v for k,v in entry.items() if not k.startswith("_")}
                    for entry in entries],
    }
    return report, entries

def render_svg(report, entries, radius):
    size = 2*radius
    root = ET.Element("{" + SVG_NS + "}svg", {
        "viewBox":f"{-radius} {-radius} {size} {size}",
        "width":"1000","height":"1000","version":"1.1",
        "role":"img","aria-label":"Chaotianmen source-only OSM GIS footprint audit"})
    svg_elem(root,"title",text="Chaotianmen GIS audit — measured source footprints only")
    svg_elem(root,"desc",text="X east; Z south. Grey=OSM buildings, orange=OSM highway, purple=rail, blue=source water polygon. No inferred 3D city.")
    svg_elem(root,"rect",{"x":str(-radius),"y":str(-radius),
                          "width":str(size),"height":str(size),"fill":"#f5f5ef"})
    grid = svg_elem(root,"g",{"stroke":"#dadbd4","stroke-width":"0.75"})
    for coord in range(int(math.ceil(-radius/100))*100,radius+1,100):
        svg_elem(grid,"path",{"d":f"M {coord} {-radius} V {radius} M {-radius} {coord} H {radius}"})
    for kind in ("water","building","highway","railway"):
        fill,stroke=COLORS[kind]
        group=svg_elem(root,"g",{"fill":fill,"stroke":stroke,
                "stroke-width":str(1.1 if kind in ("highway","railway") else 0.6),
                "fill-rule":"evenodd","stroke-linejoin":"round","stroke-linecap":"round"})
        for f in entries:
            if f["kind"] != kind:
                continue
            path_parts=[]
            for points,closed in f["_paths"]:
                if not points:
                    continue
                # World X/Z already measured, not an invented geometry.
                path_parts.append("M "+" L ".join(
                    f"{x:.2f} {z:.2f}" for x,z in points)+(" Z" if closed else ""))
            if not path_parts:
                continue
            path=svg_elem(group,"path",{"d":" ".join(path_parts),
                       "data-osm-id":f["id"],"data-geometry":f["geometry_kind"]})
            svg_elem(path,"title",text=f["id"]+" "+f["name"])
    center=svg_elem(root,"g",{"stroke":"#dd1746","stroke-width":"1.6"})
    svg_elem(center,"path",{"d":"M -10 0 H 10 M 0 -10 V 10"})
    svg_elem(root,"text",{"x":str(-radius+12),"y":str(-radius+22),
              "font-size":"15","fill":"#222222"},
             "Chaotianmen source GIS | 1 m = 1 block | N ↑")
    svg_elem(root,"text",{"x":str(-radius+12),"y":str(radius-14),
              "font-size":"13","fill":"#222222"},
             "© OpenStreetMap contributors (if OSM-derived), ODbL | NOT a validated 3D city")
    return ET.tostring(root,encoding="unicode",xml_declaration=False)+"\n"

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--input",required=True,type=Path)
    ap.add_argument("--dem",type=Path,default=DEFAULT_DEM)
    ap.add_argument("--radius",type=int,default=500)
    ap.add_argument("--output",type=Path,required=True,
                    help="New empty output directory; writes audit.json and plan.svg")
    args=ap.parse_args(argv)
    if args.input.stat().st_size > 32*1024*1024:
        ap.error("Source exceeds 32 MiB offline audit limit")
    if args.output.exists() and any(args.output.iterdir()):
        ap.error("Audit output must be a new empty directory")
    raw=args.input.read_bytes()
    data=load_gis_source(args.input)
    if not isinstance(data,dict):
        ap.error("Expected JSON object with OSM/GeoJSON vectors")
    dem=Dem(args.dem)
    report,entries=inspect(data,dem,args.radius,hashlib.sha256(raw).hexdigest())
    args.output.mkdir(parents=True,exist_ok=True)
    (args.output/"audit.json").write_text(
        json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (args.output/"plan.svg").write_text(
        render_svg(report,entries,args.radius),encoding="utf-8")
    print(json.dumps({"features_in_pilot_bbox":len(entries),
          "source_sha256":report["source_sha256"],
          "counts":report["counts"],
          "missing_named_landmark_tags":report["missing_landmark_names_in_source"],
          "outputs":[str(args.output/"audit.json"),str(args.output/"plan.svg")]},
          ensure_ascii=False,indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
