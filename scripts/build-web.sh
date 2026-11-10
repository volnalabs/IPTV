#!/bin/sh
# Собирает веб-версию под платформу.
#   scripts/build-web.sh samsung|lg|desktop <папка> [версия]
set -e
platform=$1; out=$2; version=${3:-1.0.0}
root=$(cd "$(dirname "$0")/.." && pwd)
rm -rf "$out"; mkdir -p "$out"
cp "$root"/web/* "$out"/
sed -i.bak "s/var VERSION = '[^']*'/var VERSION = '$version'/" "$out/app.js"
case "$platform" in
  samsung)
    cp "$root"/samsung/* "$out"/
    sed -i.bak "s/version=\"1.0.0\" viewmodes/version=\"$version\" viewmodes/" "$out/config.xml" ;;
  lg)
    cp "$root"/lg/* "$out"/
    sed -i.bak "s/\"version\": \"1.0.0\"/\"version\": \"$version\"/" "$out/appinfo.json" ;;
  desktop) ;;
  *) echo "Неизвестная платформа: $platform" >&2; exit 1 ;;
esac
rm -f "$out"/*.bak
