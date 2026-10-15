#!/usr/bin/env python3
"""All four 2km USGS transition/fallback sides need independent Anvil evidence."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_usgs_v2_edges import side_band_counts


class FourSideCoverageTests(unittest.TestCase):
    def test_four_cardinal_sides_counted_independently(self):
        transition=[(-896,8),(896,8),(8,-896),(8,896)]
        exterior=[(-2352,8),(2352,8),(8,-2352),(8,2352)]
        expected={"west":1,"east":1,"north":1,"south":1}
        self.assertEqual(side_band_counts(transition,864,928),expected)
        self.assertEqual(side_band_counts(exterior,2320,2400),expected)

    def test_west_east_only_must_not_count_as_four_side_coverage(self):
        points=[(-2352,8),(2352,8)]*30
        counts=side_band_counts(points,2320,2400)
        self.assertEqual(counts["west"],30)
        self.assertEqual(counts["east"],30)
        self.assertEqual(counts["north"],0)
        self.assertEqual(counts["south"],0)
        self.assertTrue(any(count<24 for count in counts.values()))

    def test_outside_bands_and_off_axis_columns_do_not_count(self):
        samples=[(-896,200),(896,200),(200,-2352),(200,2352),
                 (0,0),(8,1000),(2600,8)]
        expected={"west":0,"east":0,"north":0,"south":0}
        self.assertEqual(side_band_counts(samples,864,928),expected)
        self.assertEqual(side_band_counts(samples,2320,2400),expected)

    def test_boundary_coordinates_are_inclusive(self):
        samples=[(-2320,32),(2400,-32),(-32,-2400),(32,2320)]
        self.assertEqual(side_band_counts(samples,2320,2400),
                         {"west":1,"east":1,"north":1,"south":1})


if __name__=="__main__":
    unittest.main()
