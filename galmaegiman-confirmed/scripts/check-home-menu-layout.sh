#!/usr/bin/env bash
# Android SDK 없이 홈의 순수 기하 규칙만 검사한다. 네이티브 빌드 검사가 아니다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
java --add-modules jdk.compiler com.sun.tools.javac.Main -d "$classes" \
  "$root/app/android/app/src/main/java/org/dpdns/gmm/HomeMenuLayout.java" \
  "$root/scripts/checks/HomeMenuLayoutTest.java"
java -cp "$classes" org.dpdns.gmm.HomeMenuLayoutTest
