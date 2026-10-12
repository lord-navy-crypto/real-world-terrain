#!/usr/bin/env python3
"""RealScale Terrain Lab: safe, per-world selectable worldgen profiles.

Creates a NEW, uniquely named Minecraft world preset, noise settings and four
density functions inside the mod's resources. Does not change the eight
existing world IDs, Y=63 sea level, archived releases or existing save data.

Apply settings BEFORE building/restarting the Minecraft client. Worldgen
registries cannot be edited safely in the middle of a running world.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys

PRESETS = (
    "realism_max", "georeal_ultimate", "himalayan", "grand_canyon",
    "usgs_big_sur", "continental", "realistic_mountains", "ultimate",
)
EDITABLE = tuple(x for x in PRESETS if x != "usgs_big_sur")
PROFILES = {
    "natural":  (1.0, 0, 50, 6),
    "gentle":   (0.9, -12, 35, 3),
    "alpine":   (1.12, 12, 50, 6),
    "dramatic": (1.2, 24, 65, 9),
}
ENGLISH = {
    "realism_max":"Realism MAX", "georeal_ultimate":"GeoReal Ultimate",
    "himalayan":"Himalayan", "grand_canyon":"Grand Canyon",
    "usgs_big_sur":"USGS Big Sur (measured DEM; read-only)",
    "continental":"Continental", "realistic_mountains":"Realistic Mountains",
    "ultimate":"Ultimate",
}
CHINESE = {
    "realism_max":"综合真实地形", "georeal_ultimate":"多尺度地貌",
    "himalayan":"喜马拉雅", "grand_canyon":"大峡谷",
    "usgs_big_sur":"USGS Big Sur（实测 DEM，不允许修改）",
    "continental":"大陆巨构", "realistic_mountains":"真实山脉",
    "ultimate":"终极地形",
}
NORMAL = ("realism_max","georeal_ultimate","himalayan","grand_canyon","usgs_big_sur")
GEO_ROOT = Path("data/realscale/worldgen")
LABEL_ROOT = Path("assets/minecraft/lang")
TAG_ROOT = Path("data/minecraft/tags/worldgen/world_preset")

def resource_root():
    return Path(__file__).resolve().parents[1] / "neoforge/src/main/resources"

def jread(root, path):
    return json.loads((root/path).read_text(encoding="utf-8"))

def serialized(obj):
    return json.dumps(obj,indent=2,ensure_ascii=False)+"\n"

def replace_refs(node, replacements):
    if isinstance(node,str):
        return replacements.get(node,node)
    if isinstance(node,list):
        return [replace_refs(item,replacements) for item in node]
    if isinstance(node,dict):
        return {key:replace_refs(value,replacements) for key,value in node.items()}
    return node

def validate(preset, relief, elevation, caves, river_grade):
    if preset not in PRESETS:
        raise ValueError("Unknown terrain preset")
    if preset == "usgs_big_sur":
        raise ValueError("USGS Big Sur is a measured DEM pilot: original preset only (no rescaling)")
    if not 0.8 <= relief <= 1.25:
        raise ValueError("Relief must be 0.80 to 1.25")
    if not -48 <= elevation <= 48:
        raise ValueError("Elevation must be -48 to 48 blocks")
    if not 20 <= caves <= 80:
        raise ValueError("Cave activity must be 20 to 80")
    if river_grade not in (0,3,6,9):
        raise ValueError("River slope must be Off (0), Conservative (3), Standard (6) or Flexible (9)")

def files_for(root, preset, relief, elevation, caves, river_grade, inland=True):
    validate(preset,relief,elevation,caves,river_grade)
    options=dict(preset=preset,relief=round(float(relief),3),
                 elevation=int(elevation),caves=int(caves),
                 river_grade=int(river_grade),inland=bool(inland))
    checksum=hashlib.sha256(json.dumps(options,sort_keys=True).encode()).hexdigest()[:10]
    name="lab_"+preset+"_"+checksum+"_g"+str(river_grade)
    density_folder="terrain_lab/"+name
    density_id="realscale:"+density_folder
    existing="realscale:"+preset
    repl={existing+"/terrain_density":density_id+"/terrain_density",
          existing+"/preliminary_density":density_id+"/preliminary_density",
          existing+"/cave_density":density_id+"/cave_density"}
    new_height=density_id+"/height_signal"
    base=GEO_ROOT/"density_function"/preset
    input_terrain=jread(root,base/"terrain_density.json")
    ref_height=existing+"/height_signal"
    def swap_height(obj):
        if obj==ref_height:return new_height
        if isinstance(obj,dict):return {key:swap_height(value) for key,value in obj.items()}
        if isinstance(obj,list):return [swap_height(x) for x in obj]
        return obj
    terrain=swap_height(input_terrain)
    if terrain==input_terrain:
        raise ValueError("Preset does not reference an editable height signal: "+preset)
    height={"type":"minecraft:add",
            "argument1":{"type":"minecraft:mul",
                         "argument1":float(options["relief"]),
                         "argument2":ref_height},
            "argument2":options["elevation"]/128.0}
    preliminary=replace_refs(jread(root,base/"preliminary_density.json"),repl)
    caves_base=replace_refs(jread(root,base/"cave_density.json"),repl)
    # Density >0 means solid. Negative cave bias opens slightly more voxels.
    bias=round((50-options["caves"])/250.0,5)
    new_caves={"type":"minecraft:add","argument1":caves_base,"argument2":bias}
    original_settings=jread(root,GEO_ROOT/"noise_settings"/(preset+".json"))
    if original_settings["sea_level"]!=63:raise ValueError("Refuse to modify a non-Y63 preset")
    noise=replace_refs(original_settings,repl)
    if inland:
        for target in noise.get("spawn_target",[]):
            if "continentalness" in target:
                target["continentalness"][0]=0.12
    original_preset=jread(root,GEO_ROOT/"world_preset"/(preset+".json"))
    world_preset=copy.deepcopy(original_preset)
    world_preset["dimensions"]["minecraft:overworld"]["generator"]["settings"]="realscale:"+name
    output={
        GEO_ROOT/"density_function"/density_folder/"height_signal.json":height,
        GEO_ROOT/"density_function"/density_folder/"terrain_density.json":terrain,
        GEO_ROOT/"density_function"/density_folder/"preliminary_density.json":preliminary,
        GEO_ROOT/"density_function"/density_folder/"cave_density.json":new_caves,
        GEO_ROOT/"noise_settings"/(name+".json"):noise,
        GEO_ROOT/"world_preset"/(name+".json"):world_preset,
    }
    return name,options,output

def apply(root, preset, relief, elevation, caves, river_grade, inland=True, dry_run=False):
    name,options,output=files_for(root,preset,relief,elevation,caves,river_grade,inland)
    paths={path:serialized(obj) for path,obj in output.items()}
    preset_id="realscale:"+name
    for tag in ("normal","extended"):
        path=TAG_ROOT/(tag+".json")
        obj=jread(root,path)
        if preset_id not in obj["values"]:
            obj["values"].append(preset_id)
        paths[path]=serialized(obj)
    for lang,names in (("en_us",ENGLISH),("zh_cn",CHINESE)):
        path=LABEL_ROOT/(lang+".json")
        labels=jread(root,path)
        label=(f"RealScale Lab | {names[preset]} "
               f"| relief {relief:.2f}, height {elevation:+d}, caves {caves}, river {river_grade}"
               if lang=="en_us"
               else f"地形实验室 | {names[preset]} | 起伏 {relief:.2f}, 高差 {elevation:+d}, 洞穴 {caves}, 河流 {river_grade}")
        labels["generator.realscale."+name]=label
        paths[path]=serialized(labels)
    # Never silently modify a previously generated worldgen codec: if a
    # profile already exists, its complete serialized settings stay identical.
    for path,obj in output.items():
        dest=root/path
        if dest.exists() and dest.read_text(encoding="utf-8")!=serialized(obj):
            raise ValueError("Existing terrain profile must not be overwritten: "+str(dest))
    if not dry_run:
        for path,body in paths.items():
            dest=root/path
            dest.parent.mkdir(parents=True,exist_ok=True)
            if not dest.exists() or dest.read_text(encoding="utf-8")!=body:
                dest.write_text(body,encoding="utf-8")
    return name,options,list(paths)

def gui(root, launch):
    try:
        import tkinter as tk
        from tkinter import ttk,messagebox
    except ImportError as ex:
        raise RuntimeError("This Python has no tkinter; use CLI options instead") from ex
    app=tk.Tk()
    app.title("RealScale Terrain Lab | NeoForge 1.21.1")
    app.geometry("540x510")
    main=ttk.Frame(app,padding=20)
    main.pack(fill="both",expand=True)
    ttk.Label(main,text="RealScale Terrain Lab",font=("",18,"bold")).pack(anchor="w",pady=(0,8))
    ttk.Label(main,text="Each Apply creates a new world preset. Existing worlds are unchanged.").pack(anchor="w")
    preset=tk.StringVar(value="realism_max")
    profile=tk.StringVar(value="natural")
    relief=tk.DoubleVar(value=1.0)
    elevation=tk.IntVar(value=0)
    caves=tk.IntVar(value=50)
    grade=tk.IntVar(value=6)
    inland=tk.BooleanVar(value=True)
    def chooser(label,variable,values):
        ttk.Label(main,text=label).pack(anchor="w",pady=(9,0))
        box=ttk.Combobox(main,textvariable=variable,values=values,state="readonly")
        box.pack(fill="x")
        return box
    chooser("Original terrain mode",preset,EDITABLE)
    chooser("Suggested tuning mode",profile,tuple(PROFILES))
    def slider(label,variable,frm,to,resolution=1):
        ttk.Label(main,text=label).pack(anchor="w",pady=(9,0))
        frame=ttk.Frame(main)
        frame.pack(fill="x")
        tk.Scale(frame,from_=frm,to=to,resolution=resolution,orient="horizontal",
                 showvalue=True,variable=variable,length=440).pack(side="left",fill="x",expand=True)
    slider("Terrain relief multiplier",relief,0.8,1.25,.01)
    slider("Elevation offset (blocks)",elevation,-48,48)
    slider("Cave activity (higher = more caves)",caves,20,80)
    chooser("Safe river grade: Off=0, Steep-safe=3, Normal=6, Flexible=9",
            grade,(0,3,6,9))
    ttk.Checkbutton(main,text="Prefer inland spawn climate",variable=inland).pack(anchor="w",pady=(7,0))
    def change_profile(*_):
        relief.set(PROFILES[profile.get()][0])
        elevation.set(PROFILES[profile.get()][1])
        caves.set(PROFILES[profile.get()][2])
        grade.set(PROFILES[profile.get()][3])
    profile.trace_add("write",change_profile)
    result={"done":False}
    def save():
        try:
            name,_,_=apply(root,preset.get(),round(relief.get(),2),elevation.get(),caves.get(),grade.get(),inland.get())
        except Exception as ex:
            messagebox.showerror("Terrain Lab",str(ex))
            return
        result["done"]=True
        messagebox.showinfo("New world preset ready",
            "Created: "+name+"\n\nRestart/rebuild the Minecraft client and select the "
            "RealScale Lab preset under Create New World > World Type.\n"
            "This DOES NOT alter a world already generated.")
        app.destroy()
    ttk.Button(main,text="Create selectable world preset",command=save).pack(fill="x",pady=(18,4))
    app.mainloop()
    return result["done"] and launch

def main(argv=None):
    parser=argparse.ArgumentParser(description="Generate a selectable RealScale world preset with real numeric controls")
    parser.add_argument("--gui",action="store_true",help="Open graphical profile chooser")
    parser.add_argument("--list",action="store_true",help="List preset and tuning choices")
    parser.add_argument("--preset",choices=PRESETS,default="realism_max")
    parser.add_argument("--profile",choices=PROFILES,default="natural")
    parser.add_argument("--relief",type=float,default=None)
    parser.add_argument("--elevation",type=int,default=None)
    parser.add_argument("--caves",type=int,default=None)
    parser.add_argument("--river-grade",type=int,choices=(0,3,6,9),default=None)
    parser.add_argument("--coastal-spawn",action="store_true",help="Do not prefer inland world spawn")
    parser.add_argument("--dry-run",action="store_true")
    parser.add_argument("--launch",action="store_true",help="Launch runClient after generating a preset")
    parser.add_argument("--root",type=Path,default=None,help=argparse.SUPPRESS)
    args=parser.parse_args(argv)
    if args.list:
        print("Presets:",", ".join(PRESETS))
        print("Tuning modes:",", ".join(PROFILES))
        print("Adjustable: relief 0.80..1.25, elevation -48..48, caves 20..80, river grade 0/3/6/9.")
        print("USGS Big Sur is a read-only measured DEM.")
        return 0
    root=args.root or resource_root()
    if args.gui:
        launch=gui(root,args.launch)
    else:
        defaults=PROFILES[args.profile]
        relief=round(args.relief if args.relief is not None else defaults[0],3)
        elevation=args.elevation if args.elevation is not None else defaults[1]
        caves=args.caves if args.caves is not None else defaults[2]
        grade=args.river_grade if args.river_grade is not None else defaults[3]
        name,opts,files=apply(root,args.preset,relief,elevation,caves,grade,
                              not args.coastal_spawn,args.dry_run)
        print("Prepared world type: realscale:"+name)
        print("Settings:",json.dumps(opts,sort_keys=True))
        print("World preset files:",len(files))
        print("Apply to a NEW WORLD only. Select it in Create World > World Type.")
        launch=args.launch and not args.dry_run
    if launch:
        work=root.parents[3] # project root: neoforge/src/main/resources -> project/neoforge
        if not (work/"build.gradle").exists():
            raise RuntimeError("Expected build.gradle at "+str(work))
        return subprocess.call(["gradle","--no-daemon","--console=plain","runClient"],cwd=work)
    return 0

if __name__=="__main__":
    try:sys.exit(main())
    except (ValueError,RuntimeError,FileNotFoundError) as e:
        print("Terrain Lab error:",str(e),file=sys.stderr)
        sys.exit(1)
