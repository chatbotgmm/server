#!/usr/bin/env bash
# Local debug build only. Does not connect to EC2 or deploy anything.
set -euo pipefail
cd "$(dirname "$0")/.."
# Explicit existing domain; never inherit loopback or use release signing secrets.
args=(-p app/android -PgmmApplicationId=org.gmm.producttest -PgmmVersionCode=1002 -PgmmServerUrl=https://gmm.dpdns.org --no-daemon --max-workers=2)
if [[ -n "${GMM_LOCAL_GRADLE_INIT:-}" ]]; then args+=(-I "$GMM_LOCAL_GRADLE_INIT"); fi
# The init script is optional local build tooling, not a runtime server setting.
env -u GMM_ANDROID_KEYSTORE -u GMM_ANDROID_STORE_PASSWORD -u GMM_ANDROID_KEY_ALIAS -u GMM_ANDROID_KEY_PASSWORD \
  app/android/gradlew "${args[@]}" :app:clean :app:assembleDebug
config=app/android/app/build/generated/source/buildConfig/debug/org/dpdns/gmm/BuildConfig.java
grep -F 'SERVER_URL = "https://gmm.dpdns.org"' "$config" >/dev/null
printf '%s\n' 'Built debug org.gmm.producttest targeting https://gmm.dpdns.org; server deployment NOT performed.'
