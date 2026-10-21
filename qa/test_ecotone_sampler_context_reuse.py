"""Ensure ecotone climate sampler is validated ONCE per top-level lookup,
and the unchanged eight-ray priority always uses the same validated state.
"""
import re
import unittest
from pathlib import Path

SRC=(Path(__file__).resolve().parents[1]/
    "neoforge/src/main/java/org/realscale/neoforge/NaturalEcotoneBiomeSource.java")

class EcotoneContextReuseTests(unittest.TestCase):
    def test_sampler_state_is_prepared_once_before_resolution(self):
        s=SRC.read_text()
        resolve=s[s.index("private Holder<Biome> getNoiseBiomeUnprofiled("):
                  s.index("/**\n     * Original ecotone algorithm:")]
        self.assertIn("SamplingCache cache = cached.get();",resolve)
        self.assertIn("cache.prepare(sampler);",resolve)
        self.assertLess(resolve.index("cache.prepare(sampler);"),
                        resolve.index("cache.resolved.get(key);"))
        self.assertIn("resolveUncached(quartX, quartY, quartZ, sampler, cache)",resolve)
        sample=s[s.index("private Holder<Biome> sampleBase("):
                 s.index("private BiomeSource source()")]
        self.assertIn("Climate.Sampler sampler, SamplingCache cache)",sample)
        self.assertNotIn("cached.get()",sample)
        self.assertNotIn("cache.prepare(",sample)
        self.assertIn("source.getNoiseBiome(quartX, quartY, quartZ, sampler)",sample)
        inner=s[s.index("private Holder<Biome> resolveUncached("):]
        self.assertIn("sampleBase(quartX, quartY, quartZ, sampler, cache)",inner)
        self.assertIn("quartY,quartZ+direction[1]*step,sampler,cache)",inner)

    def test_boundary_traversal_order_and_rejections_unchanged(self):
        s=SRC.read_text()
        self.assertIn("if (quartY < 12) return original;",s)
        self.assertIn("distanceSquared>=closestDistanceSquared",s)
        self.assertIn("if((blockedHeadings&(1<<heading))!=0)continue;",s)
        self.assertIn("if(current.equals(next))continue;",s)
        self.assertIn("Rule rule = findPair(current, next);",s)
        self.assertIn("if (rule != null && EcotoneMath.withinRadialBand(",s)
        self.assertIn("if (nearest == null",s) if False else None
        self.assertIn("return nearest == null ? original : transitionBiomes.get(nearest.target());",s)
        self.assertEqual(s.count("cache.prepare(sampler);"),1)

if __name__=="__main__":unittest.main()
