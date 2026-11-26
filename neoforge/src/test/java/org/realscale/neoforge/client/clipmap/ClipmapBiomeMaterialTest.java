package org.realscale.neoforge.client.clipmap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ClipmapBiomeMaterialTest {
 @Test void classifiesBiomeFamilies(){
  assertEquals(ClipmapBiomeMaterial.WATER,ClipmapBiomeMaterial.fromBiomePath("deep_ocean"));
  assertEquals(ClipmapBiomeMaterial.DESERT,ClipmapBiomeMaterial.fromBiomePath("desert"));
  assertEquals(ClipmapBiomeMaterial.SNOW,ClipmapBiomeMaterial.fromBiomePath("snowy_slopes"));
  assertEquals(ClipmapBiomeMaterial.ROCK,ClipmapBiomeMaterial.fromBiomePath("rocky_alpine_meadow"));
  assertEquals(ClipmapBiomeMaterial.WETLAND,ClipmapBiomeMaterial.fromBiomePath("mangrove_transition"));
  assertEquals(ClipmapBiomeMaterial.FOREST,ClipmapBiomeMaterial.fromBiomePath("old_growth_pine_taiga"));
  assertEquals(ClipmapBiomeMaterial.BADLANDS,ClipmapBiomeMaterial.fromBiomePath("eroded_badlands"));
  assertEquals(ClipmapBiomeMaterial.UNKNOWN,ClipmapBiomeMaterial.fromBiomePath("modded_unknown_biome"));
 }
 @Test void majorityIgnoresUnknownAndBreaksTies(){
  assertEquals(ClipmapBiomeMaterial.FOREST,ClipmapBiomeMaterial.majority((byte)0,(byte)2,(byte)2,(byte)3));
  assertEquals(ClipmapBiomeMaterial.GRASSLAND,ClipmapBiomeMaterial.majority((byte)1,(byte)3,(byte)1,(byte)3));
  assertEquals(ClipmapBiomeMaterial.UNKNOWN,ClipmapBiomeMaterial.majority((byte)0,(byte)0,(byte)0,(byte)0));
 }
 @Test void knownMaterialsHaveColors(){for(byte id=1;id<=9;id++)assertNotEquals(-1,ClipmapBiomeMaterial.baseColor(id));}
}