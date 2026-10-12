#!/usr/bin/env python3
"""Verify NATURAL vanilla structure starts in actual saved NeoForge Anvil data.

Unlike /place structure, NBT starts are generated via world structure
placement. /locate alone is insufficient (it finds potential candidates).
Missing starts are UNVERIFIED, not 'no structures exist globally'.
"""
import argparse
import json
from collections import Counter
from pathlib import Path
from realscale_headless_qa import iter_region_chunks, get_block

VOID={"minecraft:air","minecraft:cave_air","minecraft:void_air",
      "minecraft:water","minecraft:lava"}
SKIP={"INVALID","minecraft:empty","minecraft:invalid"}

def structure_bounds(start):
    """In Minecraft 1.21.1 structure starts often OMIT a root BB; children carry it."""
    bb=start.get("BB",[])
    if isinstance(bb,list) and len(bb)==6 and all(isinstance(v,int) for v in bb):
        return bb,"start_BB"
    children=start.get("Children",[])
    boxes=[c.get("BB") for c in children if isinstance(c,dict)]
    boxes=[b for b in boxes if isinstance(b,list) and len(b)==6
           and all(isinstance(v,int) for v in b)]
    if not boxes:
        return None,"missing_bounding_boxes"
    return [min(b[0] for b in boxes),min(b[1] for b in boxes),
            min(b[2] for b in boxes),max(b[3] for b in boxes),
            max(b[4] for b in boxes),max(b[5] for b in boxes)],"children_BB"


def audit(root:Path):
    files=[p for p in root.glob("run/**/region/r.*.*.mca")
           if "DIM" not in str(p) and p.stat().st_size>=8192]
    chunks={}
    starts=[]
    status_hist=Counter()
    structure_key_samples=Counter()
    chunks_with_structure_compound=0
    chunks_with_structure_start_entries=0
    bounding_box_sources=Counter()
    structures_by_saved_chunk_status=Counter()
    skipped_structure_starts=Counter()
    for f in files:
        for chunk in iter_region_chunks(f):
            cx,cz=chunk.get("xPos"),chunk.get("zPos")
            if not isinstance(cx,int) or not isinstance(cz,int):continue
            chunks[(cx,cz)]=chunk
            status_hist[str(chunk.get("Status",chunk.get("status","MISSING_STATUS")))]+=1
            structure_key_samples[str(tuple(sorted(k for k in chunk if "struct" in k.lower())))]+=1
            structures=chunk.get("structures",chunk.get("Structures",{}))
            if isinstance(structures,dict):
                chunks_with_structure_compound+=bool(structures)
            starts_map=structures.get("starts",structures.get("Starts",{})) if isinstance(structures,dict) else {}
            chunks_with_structure_start_entries+=bool(starts_map)
            for key,obj in starts_map.items():
                if not isinstance(obj,dict):continue
                sid=obj.get("id",key)
                if sid in SKIP:continue
                bb,bbox_source=structure_bounds(obj)
                if bb is None:
                    skipped_structure_starts[str(sid)]+=1
                    continue
                bounding_box_sources[bbox_source]+=1
                structures_by_saved_chunk_status[str(chunk.get("Status",chunk.get("status","MISSING_STATUS")))]+=1
                starts.append((sid,key,(cx,cz),bb,obj.get("Children",[]),bbox_source))
    samples=[]
    for sid,key,where,box,children,bbox_source in starts:
        # Bound expensive floor reads; sample enough pieces across each
        # structure without treating every underground corridor as a house.
        stride=max(1,len(children)//24)
        for c in children[::stride][:24]:
            b=c.get("BB",[]) if isinstance(c,dict) else []
            if not isinstance(b,list) or len(b)!=6:continue
            x1,y1,z1,x2,y2,z2=b
            if not all(isinstance(q,int) for q in b):continue
            if x1>x2 or z1>z2 or y1>y2:continue
            for x,z in (((x1+x2)//2,(z1+z2)//2),(x1,z1),(x2,z2)):
                cchunk=chunks.get((x//16,z//16))
                if not cchunk:
                    samples.append({"structure":sid,"x":x,"z":z,
                                    "status":"piece_column_not_generated"})
                    continue
                if cchunk.get("Status",cchunk.get("status"))!="minecraft:full":
                    samples.append({"structure":sid,"x":x,"z":z,
                                    "status":"piece_chunk_not_fully_generated"})
                    continue
                bottom=y1-1
                # Warning only; foundations/bridges, buried structures and
                # intentionally floating pieces can be valid gameplay.
                solid=any(get_block(cchunk,x%16,bottom-down,z%16) not in VOID
                          for down in range(16))
                samples.append({"structure":sid,"x":x,"z":z,
                                "status":"supported" if solid else "possible_floating"})
    by_type=Counter(item[0] for item in starts)
    by_status=Counter(s["status"] for s in samples)
    located_file=root/"vanilla_structure_locate_qa.json"
    located=json.loads(located_file.read_text()) if located_file.is_file() else []
    located_checks=[]
    for item in located:
        if not item.get("located"):continue
        x,z=item["x"],item["z"]
        center=(x//16,z//16)
        nearby=[(cx,cz) for cx,cz in chunks if abs(cx-center[0])<=2 and abs(cz-center[1])<=2]
        at=chunks.get(center)
        located_checks.append({
            "id":item["structure"],"located_x":x,"located_z":z,
            "candidate_center_chunk":list(center),
            "candidate_chunk_saved":bool(at),
            "candidate_chunk_status":str(at.get("Status",at.get("status"))) if at else None,
            "nearby_saved_chunks":len(nearby),
            "candidate_chunk_structure_keys":list(at.get("structures",at.get("Structures",{})).get("starts",{}))
                if at and isinstance(at.get("structures",at.get("Structures")),dict) else [],
            "candidate_chunk_live_start_ids":[
                v.get("id") for v in at.get("structures",at.get("Structures",{})).get("starts",{}).values()
                if isinstance(v,dict) and v.get("id") not in SKIP
            ] if at and isinstance(at.get("structures",at.get("Structures")),dict) else []
        })
    report={
        "region_files":len(files),"saved_chunks":len(chunks),
        "saved_chunk_statuses":dict(status_hist),
        "chunks_with_nonempty_structure_compound":chunks_with_structure_compound,
        "chunks_with_start_entries":chunks_with_structure_start_entries,
        "structure_root_key_shapes":dict(structure_key_samples),
        "located_candidate_chunk_evidence":located_checks,
        "natural_structure_starts":len(starts),
        "bbox_sources":dict(bounding_box_sources),
        "structures_by_saved_chunk_status":dict(structures_by_saved_chunk_status),
        "skipped_starts_missing_usable_bbox":dict(skipped_structure_starts),
        "fully_generated_structure_starts":structures_by_saved_chunk_status.get("minecraft:full",0),
        "conclusive_saved_full_structure":structures_by_saved_chunk_status.get("minecraft:full",0)>0,
        "structure_ids":dict(sorted(by_type.items())),
        "piece_floor_support_samples":len(samples),
        "support_results":dict(sorted(by_status.items())),
        "structure_start_examples":[
            {"id":sid,"key":key,"chunk":list(where),"bbox":box,
             "pieces":len(children),"bbox_source":source}
            for sid,key,where,box,children,source in starts[:30]
        ],
        "unsupported_piece_examples":[x for x in samples
                                        if x["status"]=="possible_floating"][:20],
        "status":"STARTS_VERIFIED" if starts else "NOT_VERIFIED_NO_STARTS_SAMPLED",
        "warning_if_zero_starts":("Structure location queries do not prove placement. Check saved chunk statuses, "
                                  "candidate NBT, biomes and generate-structures in server config."
                                  if not starts else None),
        "note":"Natural NBT starts (including children-only bounding boxes), NOT /place results. "
               "Anvil starts may be saved at structure_starts stage before physical blocks are finished: "
               "use fully_generated_structure_starts and candidate chunk status separately. "
               "Floor support is a warning, not integrity proof."
    }
    return report

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("root",type=Path)
    ap.add_argument("--require-starts",action="store_true")
    args=ap.parse_args()
    report=audit(args.root)
    out=args.root/"vanilla_structure_anvil_qa.json"
    out.write_text(json.dumps(report,indent=2,ensure_ascii=False))
    print(json.dumps(report,indent=2,ensure_ascii=False))
    print("STRUCTURE_AUDIT_REPORT:",out)
    if args.require_starts and report["fully_generated_structure_starts"]==0:
        raise SystemExit("NOT VERIFIED: no fully generated vanilla structure starts; partial starts do not prove built structures")
    if report["saved_chunks"]==0:
        raise SystemExit("NO ANVIL DATA: cannot assess structures")

if __name__=="__main__":
    main()
