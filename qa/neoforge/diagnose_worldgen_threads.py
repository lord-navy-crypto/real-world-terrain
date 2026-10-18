#!/usr/bin/env python3
"""Read-only classification of actual JVM Thread.print dumps after stalled worldgen.

Interpretation matters: RCON read timeout is NOT proof the rendering thread
is slow. This tool reports where server and worker threads were observed
during a stalled far chunk request. Hypotheses remain hypotheses until
matching repeated stack samples support them.
"""
import collections
import json
import re
from pathlib import Path

HEADER=re.compile(r'^"([^"]+)"')
FRAME=re.compile(r'^\s+at\s+([^\s(]+)')
STATE=re.compile(r'java\.lang\.Thread\.State:\s+(\S+)')
MARKERS={
    "structure_jigsaw":(
        "JigsawPlacement", "AlternateJigsaw", "JigsawStructure",
        "StructureStart", "StructureTemplate", "StructureManager"),
    "height_query":(
        ".getBaseHeight", ".getBaseColumn", "iterateNoiseColumn",
        "Heightmap", "NoiseBasedChunkGenerator"),
    "realscale_density":(
        "MountainReliefDensity", "GrandOverworldCave",
        "GrandNetherCave", "RealDemSeamless", "SkyIsland",
        "RidgeErosionDensity"),
    "lithostitched":("lithostitched.", "Lithostitched", "lithostitched/"),
    "chunk_future_wait":(
        "CompletableFuture.join", "CompletableFuture.get",
        "BlockableEventLoop.managedBlock", "ServerChunkCache.getChunk"),
    "chunk_features":(
        "ChunkStatusTasks", "applyBiomeDecoration",
        "PlacedFeature.place", "ChunkGenerator.applyBiomeDecoration"),
}
MAX_STACK=12
def parse_threads(raw):
    threads=[]
    current=None
    for line in raw.splitlines():
        match=HEADER.match(line)
        if match:
            if current is not None:
                threads.append(current)
            name=match.group(1)
            current={"name":name,"state":"UNKNOWN","frames":[]}
            continue
        if current is None:
            continue
        state=STATE.search(line)
        if state:
            current["state"]=state.group(1)
        frame=FRAME.match(line)
        if frame:
            current["frames"].append(frame.group(1))
    if current is not None:
        threads.append(current)
    return threads

def related(t):
    name=t["name"]
    return any(token in name for token in
               ("Server thread","Worker-Main","ForkJoinPool","RCON"))

def diagnose_dumps(texts):
    if not texts:
        raise ValueError("No thread snapshots")
    observed=[]
    signatures=collections.Counter()
    states=collections.Counter()
    flags=collections.Counter()
    for index,raw in enumerate(texts):
        threads=parse_threads(raw)
        if not threads:
            raise ValueError("No Java thread headers in snapshot "+str(index))
        for t in threads:
            if not related(t):
                continue
            frames=t["frames"]
            active={key for key,words in MARKERS.items()
                    if any(word in frame for frame in frames for word in words)}
            if not active and t["name"]!="Server thread":
                continue
            for flag in active:
                flags[flag]+=1
            states[t["state"]]+=1
            if frames:
                # Avoid raw thread dump paths and environment variables in logs.
                signatures[frames[0]]+=1
            observed.append({
                "sample":index,
                "thread":t["name"][:100],
                "state":t["state"],
                "markers":sorted(active),
                "frames":frames[:MAX_STACK]
            })
    likely=[]
    if flags["chunk_future_wait"]:
        likely.append("Observed chunk/future wait; inspect who supplies the awaited chunk.")
    if flags["structure_jigsaw"] and flags["height_query"]:
        likely.append("Observed jigsaw and height-query stacks in sampling window; quantify repeated structure height probes.")
    if flags["realscale_density"]:
        likely.append("RealScale terrain density appears on sampled stacks; benchmark exact samples before optimizing.")
    if not likely:
        likely.append("No characteristic blocker observed; inspect full saved dumps and server log.")
    return {
        "samples":len(texts),
        "matched_thread_observations":len(observed),
        "thread_states":dict(states),
        "marker_observations":dict(flags),
        "top_leaf_frames":[{"frame":k,"count":n}
                           for k,n in signatures.most_common(12)],
        "sampled_threads":observed[:30],
        "hypotheses_not_proven_causes":likely,
        "method":("jcmd Thread.print observation only; stacks can be waiting "
                  "on work elsewhere and do not establish CPU attribution.")
    }

def report_files(paths):
    dumps=[Path(p).read_text(encoding="utf-8",errors="replace") for p in paths]
    return diagnose_dumps(dumps)

if __name__=="__main__":
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("thread_dump",nargs="+",type=Path)
    p.add_argument("--output",type=Path)
    a=p.parse_args()
    answer=json.dumps(report_files(a.thread_dump),indent=2)+"\n"
    if a.output:
        a.output.parent.mkdir(parents=True,exist_ok=True)
        a.output.write_text(answer,encoding="utf-8")
    print(answer,end="")
