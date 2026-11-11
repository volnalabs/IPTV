#!/bin/sh
# Собирает веб-версию под платформу. Нужен Node.js.
#   scripts/build-web.sh samsung|lg|desktop <папка> [версия]
#
# В пакет попадает оболочка (index.html, boot.js), плеер и само приложение одним файлом base.js.
# Тот же набор, что в base.js, прикладывается к релизу как iptv-web.json — по нему установленные
# приложения обновляются сами (см. scripts/bundle-web.js).
set -e
platform=$1; out=$2; version=${3:-1.0.0}
root=$(cd "$(dirname "$0")/.." && pwd)
rm -rf "$out"; mkdir -p "$out"
cp "$root"/web/shell.html "$out"/index.html
cp "$root"/web/boot.js "$root"/web/hls.min.js "$root"/web/fflate.min.js "$root"/web/*.LICENSE.txt "$out"/
node "$root"/scripts/bundle-web.js "$version" "$out"/iptv-web.json "$out"/base.js
rm "$out"/iptv-web.json
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
