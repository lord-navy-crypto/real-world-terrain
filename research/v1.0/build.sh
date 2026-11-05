#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes reports dist
javac --release 21 -encoding UTF-8 -d build/classes $(find src/main/java src/test/java -name '*.java')
python3 -m unittest discover -v -s tests 2>&1 | tee reports/python_unit.log
java -cp build/classes org.realscale.geo.RegionalWatershedTests | tee reports/java_legacy_tests.log
java -cp build/classes org.realscale.geo.MeasuredGeoTests data/USGS_3DEP_BigSur_20261011/BigSur_Observed_16x16_32m.rgt.gz | tee reports/ACTUAL_USGS_Java_invariants.log
jar --create --file dist/RealScale_RealData_GeoCore_v1.0.jar --main-class org.realscale.geo.MeasuredGeoCli -C build/classes org/realscale/geo
java -jar dist/RealScale_RealData_GeoCore_v1.0.jar data/USGS_3DEP_BigSur_20261011/BigSur_Observed_16x16_32m.rgt.gz dist/USGS_BigSur_actual_16x16 measured | tee reports/ACTUAL_USGS_java_report.log
python3 tools/verify_usgs_source.py data/USGS_3DEP_BigSur_20261011 | tee reports/ACTUAL_USGS_provenance_verified.json
python3 tools/calibrate_from_tile.py data/USGS_3DEP_BigSur_20261011/BigSur_Observed_16x16_32m.rgt.gz reports/ACTUAL_USGS_BigSur_variograms.json > /dev/null
