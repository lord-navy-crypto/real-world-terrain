package org.realscale.neoforge;

/** Standalone assertions for pure worldgen classifiers; no game runtime needed. */
public final class HabitatMathSmoke {
    private HabitatMathSmoke() {}
    private static void check(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        usingEcotones();
        usingCaves();
        System.out.println("PASS: ecotone and cave habitat math");
    }
    private static void usingEcotones() {
        var forest = EcotoneMath.Habitat.FOREST;
        var meadow = EcotoneMath.Habitat.MEADOW;
        check(EcotoneMath.compatible(forest, meadow) == EcotoneMath.Transition.WOODLAND_MEADOW, "forest meadow");
        check(EcotoneMath.compatible(meadow, forest) == EcotoneMath.Transition.WOODLAND_MEADOW, "symmetric transition");
        check(EcotoneMath.compatible(EcotoneMath.Habitat.RIVER, meadow) == EcotoneMath.Transition.NONE, "protected river");
        check(EcotoneMath.compatible(EcotoneMath.Habitat.DESERT, EcotoneMath.Habitat.GRASSLAND)
                == EcotoneMath.Transition.SEMI_ARID_SCRUBLAND, "dry ecotone");
        check(EcotoneMath.compatible(EcotoneMath.Habitat.TAIGA, EcotoneMath.Habitat.ALPINE)
                == EcotoneMath.Transition.SUBALPINE_WOODLAND, "alpine ecotone");
        check(EcotoneMath.insideBand(0, 32, 15, false), "center should be covered");
        check(!EcotoneMath.insideBand(22, 32, 15, false), "outside band");
        check(!EcotoneMath.insideBand(0, 32, 15, true), "never overwrite water channel");
        check(!EcotoneMath.insideBand(0, 32, 55, false), "avoid cliff");
        check(!EcotoneMath.insideBand(0, 80, 15, false), "reject wide ecotone");
        check(!EcotoneMath.insideBand(Double.NaN, 32, 0, false), "reject NaN");
        check(EcotoneMath.weight(0, 32) == 1.0, "edge weight central");
        check(EcotoneMath.weight(16, 32) == 0.0, "edge weight boundary");
        check(Math.abs(EcotoneMath.weight(-5, 32) - EcotoneMath.weight(5, 32)) < 1e-12,
                "distance symmetry");
    }
    private static void usingCaves() {
        var none = CaveHabitatMath.CaveHabitat.NONE;
        check(CaveHabitatMath.select(20, 100, false, .9, true, false, false, false) == none, "no air cave");
        check(CaveHabitatMath.select(94, 100, true, .9, true, false, false, false) == none, "too shallow");
        check(CaveHabitatMath.select(-20, 100, true, .9, true, true, false, false)
                == CaveHabitatMath.CaveHabitat.VOLCANIC, "lava");
        check(CaveHabitatMath.select(0, 120, true, .95, true, false, true, false)
                == CaveHabitatMath.CaveHabitat.UNDERGROUND_SPRINGS, "spring");
        check(CaveHabitatMath.select(0, 120, true, .7, true, false, false, false)
                == CaveHabitatMath.CaveHabitat.DAMP_LIMESTONE, "wet karst");
        check(CaveHabitatMath.select(0, 120, true, .15, true, false, false, false)
                == CaveHabitatMath.CaveHabitat.DRY_KARST, "dry karst");
        check(CaveHabitatMath.select(-20, 100, true, .9, false, false, false, false)
                == CaveHabitatMath.CaveHabitat.DEEP_FUNGAL, "fungal");
        check(CaveHabitatMath.select(0, 100, true, .4, false, false, false, true)
                == CaveHabitatMath.CaveHabitat.GLACIAL, "glacial");
        check(CaveHabitatMath.select(0, 100, true, Double.NaN, true, false, false, false) == none, "invalid moisture");
    }
}
