"""Fail CI if the distributable contains cross-loader or modern resources."""
import gzip
import json
import sys
import zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from convert_gdmc_architecture import parse_nbt

jar = Path(sys.argv[1])
with zipfile.ZipFile(jar) as z:
    names = set(z.namelist())
    assert "META-INF/mods.toml" in names
    assert "META-INF/neoforge.mods.toml" not in names
    assert "data/realscale/worldgen/world_preset/natural_automate_three_realms.json" in names
    assert not any("/neoforge/biome_modifier/" in n for n in names)
    assert not any("/structure/" in n and n.endswith(".nbt") for n in names)
    assert json.loads(z.read("pack.mcmeta"))["pack"]["pack_format"] == 15
    for n in names:
        if n.endswith(".class"):
            assert b"net/neoforged/" not in z.read(n), n
        elif "/forge/biome_modifier/" in n and n.endswith(".json"):
            assert json.loads(z.read(n))["type"].startswith("forge:"), n
        elif n.startswith("resourcepacks/") and n.endswith("/pack.mcmeta"):
            assert json.loads(z.read(n))["pack"]["pack_format"] == 15, n
    arch = [n for n in names if n.startswith("data/realscale/structures/architecture/") and n.endswith(".nbt")]
    urban = [n for n in names if n.startswith("data/realscale/structures/urban/") and n.endswith(".nbt")]
    assert len(arch) == 100, len(arch)
    assert len(urban) == 4, len(urban)
    for n in arch + urban:
        _, root = parse_nbt(gzip.decompress(z.read(n)))
        assert root["DataVersion"] == 3465, n
        assert root["blocks"] and root["palette"] and len(root["size"]) == 3, n
        assert not any(p["Name"] in {"minecraft:tuff_bricks", "minecraft:short_grass", "minecraft:carpet"} for p in root["palette"]), n
        pack = "architecture_pilot" if n in arch else "urban_districts"
        mirror = "resourcepacks/realscale_structures_" + pack + "/" + n
        assert mirror in names and z.read(mirror) == z.read(n), n
    print(f"FORGE_JAR_AUDIT_OK architecture={len(arch)} urban={len(urban)}")
