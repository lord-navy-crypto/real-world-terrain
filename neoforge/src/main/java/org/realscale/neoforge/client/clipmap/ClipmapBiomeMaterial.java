package org.realscale.neoforge.client.clipmap;
import java.util.Locale;
/** Compact, stable biome classes streamed with visible-surface height tiles. */
public final class ClipmapBiomeMaterial {
 public static final byte UNKNOWN=0,GRASSLAND=1,FOREST=2,DESERT=3,SNOW=4,ROCK=5,WETLAND=6,SAVANNA=7,WATER=8,BADLANDS=9;
 private ClipmapBiomeMaterial(){}
 public static byte fromBiomePath(String biomePath){
  if(biomePath==null||biomePath.isBlank())return UNKNOWN;
  String p=biomePath.toLowerCase(Locale.ROOT);
  if(any(p,"ocean","river"))return WATER;
  if(any(p,"badlands","eroded_badlands","wooded_badlands"))return BADLANDS;
  if(any(p,"desert","beach","dune","salt","playa"))return DESERT;
  if(any(p,"snow","frozen","ice_spikes","tundra","glacier"))return SNOW;
  if(any(p,"stony","windswept","peaks","mountain","alpine","scree"))return ROCK;
  if(any(p,"swamp","mangrove","marsh","wetland","bog"))return WETLAND;
  if(p.contains("savanna"))return SAVANNA;
  if(any(p,"forest","jungle","taiga","grove","cherry","woodland"))return FOREST;
  if(any(p,"plains","meadow","grassland","prairie","fields"))return GRASSLAND;
  return UNKNOWN;
 }
 public static byte majority(byte a,byte b,byte c,byte d){
  byte[] v={a,b,c,d};byte winner=UNKNOWN;int best=0;
  for(byte candidate:v){if(candidate==UNKNOWN)continue;int n=0;for(byte x:v)if(x==candidate)n++;if(n>best){best=n;winner=candidate;}}
  return winner;
 }
 public static int baseColor(byte m){return switch(m){
  case GRASSLAND->0x789052;case FOREST->0x527B45;case DESERT->0xC1AC7F;
  case SNOW->0xE1E7EA;case ROCK->0x85857F;case WETLAND->0x526B49;
  case SAVANNA->0xA99A5C;case WATER->0x64869A;case BADLANDS->0xA65E45;default->-1;};}
 private static boolean any(String s,String...n){for(String x:n)if(s.contains(x))return true;return false;}
}