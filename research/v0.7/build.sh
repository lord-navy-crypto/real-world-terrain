#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes build/tests dist
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 21 -d build/classes
find src/test/java -name '*.java' -print0 | xargs -0 javac --release 21 -cp build/classes -d build/tests
java -Xmx1g -cp build/classes:build/tests org.realscale.geo.GeoTests
jar --create --file dist/RealScale_GeoCore_v0.7.jar --main-class org.realscale.geo.GeoCli -C build/classes .
java -Xmx1g -jar dist/RealScale_GeoCore_v0.7.jar 785312 0 0 dist/sample-785312
java -Xmx1g -cp build/classes org.realscale.geo.GeoSurvey | tee dist/wide_area_survey.txt
