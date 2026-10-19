"""Biome decoration must be topologically sortable at every generation stage.

This checks data-pack-defined lists before NeoForge adds modifiers. It guards
against regressions where newly added habitats reverse two common vanilla
features, producing a FeatureSorter cycle and crashing new-world spawning.
"""
import json
import unittest
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BIOMES=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/biome"
MUTATED=(
"mediterranean_chaparral","temperate_tallgrass_prairie",
"monsoon_woodland","montane_cloud_scrub","brackish_tidal_marsh",
"coastal_pine_barrens","frost_heath_moor","arid_rocksteppe",
)
TEMPLATES={
"mediterranean_chaparral":"seasonal_savanna_wetland",
"temperate_tallgrass_prairie":"montane_spruce_meadow",
"monsoon_woodland":"seasonal_savanna_wetland",
"montane_cloud_scrub":"boreal_parkland",
"brackish_tidal_marsh":"peat_bog_taiga",
"coastal_pine_barrens":"boreal_parkland",
"frost_heath_moor":"montane_spruce_meadow",
"arid_rocksteppe":"seasonal_savanna_wetland",
}

class WorldgenFeatureOrder(unittest.TestCase):
    def test_new_biome_vegetation_is_proven_template_subsequence(self):
        for biome,template in TEMPLATES.items():
            with self.subTest(biome=biome):
                target=json.loads((BIOMES/(biome+".json")).read_text())["features"][9]
                safe=json.loads((BIOMES/(template+".json")).read_text())["features"][9]
                indices=[safe.index(feature) for feature in target]
                self.assertEqual(indices,sorted(indices))
                self.assertEqual(len(indices),len(set(indices)))

    def test_all_shipped_biome_feature_lists_are_acyclic(self):
        biomes=sorted(BIOMES.glob("*.json"))
        self.assertGreaterEqual(len(biomes),84)
        graphs=[defaultdict(set) for _ in range(11)]
        owners=[defaultdict(set) for _ in range(11)]
        for filename in biomes:
            features=json.loads(filename.read_text())["features"]
            self.assertEqual(len(features),11,filename.name)
            for step,items in enumerate(features):
                self.assertEqual(len(items),len(set(items)),(filename.name,step))
                for a,b in zip(items,items[1:]):
                    graphs[step][a].add(b)
                    owners[step][(a,b)].add(filename.stem)
        for step,graph in enumerate(graphs):
            all_nodes=set(graph)
            for neighbors in graph.values():all_nodes.update(neighbors)
            state={}
            stack=[]
            def visit(node):
                st=state.get(node,0)
                if st==2:return
                if st==1:
                    path=stack[stack.index(node):]+[node]
                    edges=list(zip(path,path[1:]))
                    where=[sorted(owners[step][e]) for e in edges]
                    self.fail(f"FeatureSorter order cycle stage={step}: {path} in {where}")
                state[node]=1
                stack.append(node)
                for neighbor in sorted(graph.get(node,())):visit(neighbor)
                stack.pop()
                state[node]=2
            for node in sorted(all_nodes):
                if node not in state:visit(node)

if __name__=="__main__":unittest.main()
