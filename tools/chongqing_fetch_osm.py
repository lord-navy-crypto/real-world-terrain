#!/usr/bin/env python3
"""Fetch authentic OSM Chaotianmen ways; never scrape Google Maps.

Uses Overpass QL with complete way geometry. Fail closed on outage,
empty result, oversize or non-OSM response; no synthetic city data.
"""
import argparse
import datetime
import hashlib
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from chongqing_osm_to_minecraft import DEFAULT_DEM, Dem, osm_features

DEFAULT_ENDPOINT = "https://overpass.kumi.systems/api/interpreter"
TAGS = ('way["building"]', 'way["highway"]',
        'way["railway"]', 'way["natural"="water"]',
        'way["waterway"="riverbank"]')

def query(dem, radius, buffer_m=100):
    if radius<32 or radius>512:
        raise ValueError("Radius outside 1km pilot")
    dy=(radius+buffer_m)/dem.m_lat
    dx=(radius+buffer_m)/dem.m_lon
    bbox="%.7f,%.7f,%.7f,%.7f" % (
        dem.lat-dy,dem.lon-dx,dem.lat+dy,dem.lon+dx)
    q="[out:json][timeout:45];(\n"+"\n".join(
        "  "+tag+"("+bbox+");" for tag in TAGS
    )+"\n);out meta geom;\n"
    return q,bbox

def retrieve(url, q, timeout=65):
    if not url.startswith("https://") or "?" in url or "#" in url:
        raise ValueError("Use trusted HTTPS base API endpoint")
    req=urllib.request.Request(
        url,data=urllib.parse.urlencode({"data":q}).encode("utf-8"),
        headers={"User-Agent":"RealScale-Chongqing-GIS/0.1 educational OSM/ODbL",
                 "Content-Type":"application/x-www-form-urlencoded",
                 "Accept":"application/json"},method="POST")
    with urllib.request.urlopen(req,timeout=timeout) as response:
        if response.status!=200:
            raise RuntimeError("Overpass HTTP "+str(response.status))
        body=response.read(12*1024*1024+1)
        if len(body)>12*1024*1024:
            raise RuntimeError("OSM query exceeds 12 MiB safety cap")
    parsed=json.loads(body)
    if "elements" not in parsed or "remark" in parsed:
        raise ValueError("Overpass returned error/non-OSM payload")
    osm_features(parsed) # reject missing or unusable geometries
    return parsed

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dem",type=Path,default=DEFAULT_DEM)
    parser.add_argument("--radius",type=int,default=500)
    parser.add_argument("--endpoint",default=DEFAULT_ENDPOINT)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--dry-run",action="store_true")
    args=parser.parse_args(argv)
    dem=Dem(args.dem)
    q,bbox=query(dem,args.radius)
    if args.dry_run:
        print(q)
        print("BBOX:",bbox)
        return 0
    if args.output.exists():
        parser.error("Source already exists; use a new versioned filename")
    try:
        data=retrieve(args.endpoint,q)
    except (urllib.error.URLError,OSError,ValueError,RuntimeError) as exc:
        parser.error("No authentic OSM data acquired: "+str(exc))
    meta={
        "source":"© OpenStreetMap contributors, ODbL",
        "source_endpoint":args.endpoint,
        "world_center":[dem.lat,dem.lon],
        "bbox_south_west_north_east":bbox,
        "snapshot_osm_timestamp":(data.get("osm3s") or {}).get("timestamp_osm_base"),
        "queried_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "query_sha256":hashlib.sha256(q.encode("utf-8")).hexdigest(),
        "ways_with_complete_geometry":len(osm_features(data)),
        "limitation":"Way-only geometry; relations, bridge heights and water planes are incomplete."
    }
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(data,ensure_ascii=False,
                     sort_keys=True,separators=(",",":"))+"\n",encoding="utf-8")
    (args.output.parent/(args.output.stem+".provenance.json")).write_text(
        json.dumps(meta,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("Authentic OSM snapshot saved:",args.output)
    print(json.dumps(meta,ensure_ascii=False,indent=2))
    return 0

if __name__=="__main__":
    raise SystemExit(main())
