#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes dist
javac --release 21 -encoding UTF-8 -d build/classes $(find src/main/java src/test/java -name '*.java')
java -cp build/classes org.realscale.geo.RegionalWatershedTests | tee reports/unit-tests.log
jar --create --file dist/RealScale_GeoCore_v0.9.jar --main-class org.realscale.geo.GeoRegionCli -C build/classes org/realscale/geo
java -jar dist/RealScale_GeoCore_v0.9.jar 785312 128 dist/sample_785312 | tee reports/demo.log
