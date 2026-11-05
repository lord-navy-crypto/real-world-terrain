#!/usr/bin/env python3
"""Three-way saved-Anvil variation triage for an isolated Beardifier A/B/A.

The two reference worlds use identical source, seed, properties and flags.
A candidate-only difference is evidence to investigate, NOT proof of a bug:
Minecraft structure/feature scheduling can itself be nondeterministic.

For stable reference voxels (A == control), count candidate-only changes.
For variable reference voxels (A != control), distinguish candidate values
matching either reference world from new third values.
Includes all three-way heightmap, structure and biome differences.
"""
import collections
import json
import pathlib
import sys

from compare_beardifier_saved_regions import WAYPOINTS, load_chunk, sections


def classify(a, b, control):
    """One voxel: unchanged / candidate-only / reference-variable / third-value."""
    if a == control:
        return "candidate_only" if b != a else "unchanged"
    if b == a or b == control:
        return "reference_variable"
    return "third_value"


def summarize_three_way(first, candidate, repeat, origin=(0, 0)):
    counts = collections.Counter()
    examples = {}
    transition_counts = collections.Counter()
    for kind in ("blocks", "biomes"):
        as_=sections(first,kind=="blocks")
        bs=sections(candidate,kind=="blocks")
        cs=sections(repeat,kind=="blocks")
        length=4096 if kind=="blocks" else 64
        absent=("minecraft:air",()) if kind=="blocks" else None
        for sy in sorted(set(as_) | set(bs) | set(cs)):
            a=as_.get(sy,(absent,)*length)
            b=bs.get(sy,(absent,)*length)
            c=cs.get(sy,(absent,)*length)
            for i in range(length):
                tag=classify(a[i],b[i],c[i])
                if tag=="unchanged":
                    continue
                key=f"{kind}_{tag}"
                counts[key]+=1
                if tag in ("candidate_only", "third_value"):
                    transition_counts[(kind,repr(a[i])[:80],repr(b[i])[:80])]+=1
                if len(examples.get(key,[]))<5:
                    if kind=="blocks":
                        coord={"x":(origin[0]//16)*16+(i%16),
                               "y":int(sy)*16+(i//256),
                               "z":(origin[1]//16)*16+((i//16)%16)}
                    else:
                        coord={"biome_section_y":int(sy),"biome_index":i}
                    examples.setdefault(key,[]).append(
                        {"position":coord,"reference":str(a[i])[:90],
                         "candidate":str(b[i])[:90],"control":str(c[i])[:90]})
    metadata={}
    for kind in ("Heightmaps","structures"):
        a=first.get(kind,{})
        b=candidate.get(kind,{})
        c=repeat.get(kind,{})
        for name in sorted(set(a)|set(b)|set(c)):
            cat=classify(a.get(name),b.get(name),c.get(name))
            if cat!="unchanged":
                counts[f"{kind}_{cat}"]+=1
                metadata[f"{kind}.{name}"]=cat
    most_common=[
        {"kind":kind,"reference":old,"candidate":new,"count":count}
        for (kind,old,new),count in transition_counts.most_common(12)
    ]
    return {"counts":dict(sorted(counts.items())),
            "examples":examples,
            "metadata_categories":metadata,
            "candidate_only_transitions":most_common,
            "review_required":any(k.endswith(("candidate_only","third_value"))
                for k in counts)}


def compare(first_root,candidate_root,control_root):
    results=[]
    for x,z in WAYPOINTS:
        a=load_chunk(first_root,x,z)
        b=load_chunk(candidate_root,x,z)
        c=load_chunk(control_root,x,z)
        detail=summarize_three_way(a,b,c,(x,z))
        detail["waypoint"]={"x":x,"z":z}
        results.append(detail)
    review_required=any(item["review_required"] for item in results)
    return {
        "experiment":"Vanilla baseline / Y256 candidate / vanilla control",
        "reference_note":"Identical references can still have natural feature-scheduling variance",
        "interpretation":{
            "candidate_only":"Y256 differs where both original vanilla worlds agree; investigate",
            "reference_variable":"Two Y256 references disagree; candidate equals one of them",
            "third_value":"All three differ; stronger candidate investigation required",
            "unchanged":"All three agree (excluded from counts)"},
        "release_blocked_for_review":review_required,
        "waypoints":results,
    }


if __name__=="__main__":
    if len(sys.argv) not in (4,5):
        raise SystemExit("usage: triage_beardifier_three_worlds.py baseline_dir candidate_dir control_dir [output.json]")
    result=compare(*sys.argv[1:4])
    encoded=json.dumps(result,indent=2,sort_keys=True)
    if len(sys.argv)==5:
        output=pathlib.Path(sys.argv[4])
        output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(encoded+"\n",encoding="utf-8")
    print("REALSCALE_BEARDIFIER_3WAY_ANVIL_TRIAGE",encoded,flush=True)
    if result["release_blocked_for_review"]:
        print("REALSCALE_BEARDIFIER_TRIAGE_REVIEW_REQUIRED: candidate differs on voxels outside observed reference A/A variation",flush=True)
    else:
        print("REALSCALE_BEARDIFIER_TRIAGE_NO_CANDIDATE_ONLY_DIFFERENCE",flush=True)
