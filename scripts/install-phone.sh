#!/bin/sh
# One-command online build + install + launch on the connected phone.
# Usage: sh scripts/install-phone.sh
set -e
cd "$(dirname "$0")/.."
./gradlew :app:installOnlineDebug -x lint
adb shell am start -n com.hamaz.mobileharness/com.jarves.mh.MainActivity --activity-single-top
