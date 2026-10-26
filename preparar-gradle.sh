#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
VERSION=8.9
for cmd in java curl unzip sha256sum; do
    command -v "$cmd" >/dev/null || { echo "Falta $cmd. Instala JDK 17, curl y unzip." >&2; exit 1; }
done
if [[ -f gradle/wrapper/gradle-wrapper.jar && -f gradlew ]]; then
    chmod +x gradlew
    echo "El wrapper ya existe."
    exit 0
fi
if command -v gradle >/dev/null; then
    gradle wrapper --gradle-version "$VERSION" --distribution-type bin
else
    CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/cronmaster-gradle"
    mkdir -p "$CACHE"
    if [[ ! -x "$CACHE/gradle-$VERSION/bin/gradle" ]]; then
        TMP="$(mktemp -d)"
        trap 'rm -rf "$TMP"' EXIT
        BASE="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
        curl --fail --location --show-error --proto '=https' --proto-redir '=https' "$BASE" -o "$TMP/gradle.zip"
        curl --fail --location --show-error --proto '=https' --proto-redir '=https' "$BASE.sha256" -o "$TMP/checksum"
        EXPECTED="$(tr -d '\r\n ' < "$TMP/checksum")"
        [[ "$EXPECTED" =~ ^[[:xdigit:]]{64}$ ]] || { echo "Checksum no válido" >&2; exit 1; }
        (cd "$TMP"; printf '%s  gradle.zip\n' "$EXPECTED" | sha256sum -c -)
        unzip -q "$TMP/gradle.zip" -d "$CACHE"
    fi
    "$CACHE/gradle-$VERSION/bin/gradle" wrapper --gradle-version "$VERSION" --distribution-type bin
fi
chmod +x gradlew
printf '\nWrapper preparado. Compila con: ./gradlew :app:testDebugUnitTest :app:assembleDebug\n'
