#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(dirname "$script_dir")"
test_output="$(mktemp -d /tmp/gmm-background-timeline.XXXXXX)"
trap 'rm -rf "$test_output"' EXIT
# java 실행 파일이 PATH에 없으면 JAVA 명시. APK/Gradle/Android 컴파일은 실행하지 않는다.
java_runner="${JAVA:-java}"
"$java_runner" --add-modules jdk.compiler com.sun.tools.javac.Main -d "$test_output" \
  "$project_dir/app/android/app/src/main/java/org/dpdns/gmm/BackgroundMotionTimeline.java" \
  "$project_dir/test/java/org/dpdns/gmm/BackgroundMotionTimelineCheck.java" \
  "$project_dir/app/android/app/src/main/java/org/dpdns/gmm/HarborBoatGeometry.java" \
  "$project_dir/test/java/org/dpdns/gmm/HarborBoatGeometryCheck.java"
"$java_runner" -cp "$test_output" org.dpdns.gmm.BackgroundMotionTimelineCheck

"$java_runner" -cp "$test_output" org.dpdns.gmm.HarborBoatGeometryCheck

