#!/usr/bin/env python3
"""Summarize real JVM JFR ExecutionSample call stacks, not estimated CPU times.

Each sample is an independent on-CPU Java thread stack; inclusive counts
overlap. Never add them and call the sum a share of server CPU usage.
"""
import collections
import json
import pathlib
import subprocess
import sys

TARGET_PREFIX=("net.minecraft.","net.neoforged.","org.realscale.")
def method_name(frame):
    method=frame.get("method") or {}
    owner=(method.get("type") or {}).get("name","")
    return owner+"."+method.get("name","") if owner else ""

def summarize_events(events):
    leaf=collections.Counter()
    inclusive=collections.Counter()
    category=collections.Counter()
    total=0
    worldgen=0
    for item in events:
        if item.get("type")!="jdk.ExecutionSample":
            continue
        frames=(((item.get("values") or {}).get("stackTrace") or {}).get("frames") or [])
        stack=[method_name(f) for f in frames if method_name(f)]
        total+=1
        if not stack:continue
        leaf[stack[0]]+=1
        selected=[name for name in stack if name.startswith(TARGET_PREFIX)]
        if not selected:continue
        worldgen+=1
        for name in set(selected):
            inclusive[name]+=1
        # Nearest relevant frame is not necessarily the actual CPU leaf:
        # expose both and distinguish inclusive from leaf measurements.
        closest=selected[0]
        if closest.startswith("org.realscale."):
            category["RealScale implementation"]+=1
        elif ".world.level.biome." in closest:
            category["Minecraft biome"]+=1
        elif ".world.level.levelgen." in closest:
            category["Minecraft terrain and structure generation"]+=1
        else:
            category["Other Minecraft/NeoForge"]+=1
    def top(c,n=30):
        return [{"method":name,"samples":count} for name,count in c.most_common(n)]
    if total<10:raise ValueError("Not enough JVM execution samples; profiling did not attach")
    return {"execution_samples":total,"stacks_with_minecraft_or_realscale":worldgen,
            "leaf_methods":top(leaf),"inclusive_worldgen_methods":top(inclusive),
            "nearest_relevant_stack_category":category,
            "interpretation":"JFR sampled stacks only. Inclusive counts overlap and are not wall-clock durations."}

def parse_jfr(file):
    result=subprocess.run(["jfr","print","--json","--events","jdk.ExecutionSample",
                           str(file)],capture_output=True,text=True,check=True,timeout=150)
    document=json.loads(result.stdout)
    return summarize_events((document.get("recording") or {}).get("events") or [])

if __name__=="__main__":
    if len(sys.argv)!=3:
        raise SystemExit("usage: summarize_worldgen_jfr.py recording.jfr report.json")
    report=parse_jfr(pathlib.Path(sys.argv[1]))
    out=pathlib.Path(sys.argv[2])
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2)+"\n")
    print("REALSCALE_WORLDGEN_JFR_TOP:",json.dumps(report)[:25000],flush=True)
