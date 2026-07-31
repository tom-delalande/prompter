#!/usr/bin/env bash
# Generate every launcher/store icon for the app from the master SVG in assets/.
# Rerun after editing the SVG. Optionally pass the SVG path as the first arg.
#
# Requires one of:
#   rsvg-convert (librsvg)   recommended: brew install librsvg
#   python3 + cairosvg
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SVG="${1:-$ROOT_DIR/assets/icon.svg}"

if [[ ! -f "$SVG" ]]; then
    echo "error: icon not found: $SVG" >&2
    exit 1
fi
SVG="$(cd "$(dirname "$SVG")" && pwd)/$(basename "$SVG")"

if command -v rsvg-convert >/dev/null 2>&1; then
    RENDERER="rsvg-convert"
elif python3 -c 'import cairosvg' >/dev/null 2>&1; then
    RENDERER="cairosvg"
else
    echo "error: need 'rsvg-convert' (brew install librsvg) or python3 + cairosvg" >&2
    exit 1
fi
echo "using renderer: $RENDERER"

render_png() {
    local size="$1" out="$2"
    mkdir -p "$(dirname "$out")"
    if [[ "$RENDERER" == "rsvg-convert" ]]; then
        rsvg-convert -w "$size" -h "$size" "$SVG" -o "$out"
    else
        python3 - "$SVG" "$out" "$size" <<'PY'
import sys
import cairosvg
cairosvg.svg2png(url=sys.argv[1], write_to=sys.argv[2],
                 output_width=int(sys.argv[3]), output_height=int(sys.argv[3]))
PY
    fi
    echo "  $out"
}

echo "-> Android adaptive icon drawables"
python3 "$SCRIPT_DIR/svg_icon.py" "$SVG" "$ROOT_DIR/androidApp/src/main/res/drawable"

echo "-> Android legacy mipmap PNGs"
for entry in "mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192"; do
    set -- $entry
    dpi="$1"
    size="$2"
    for name in ic_launcher ic_launcher_round; do
        render_png "$size" "$ROOT_DIR/androidApp/src/main/res/mipmap-$dpi/$name.png"
    done
done

IOS_ICONSET="$ROOT_DIR/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
echo "-> iOS AppIcon.appiconset"
rm -rf "$IOS_ICONSET"
mkdir -p "$IOS_ICONSET"

ENTRIES="$IOS_ICONSET/.entries.txt"
cat > "$ENTRIES" <<'EOF'
40|iphone-20@2x.png|iphone|2x|20x20
60|iphone-20@3x.png|iphone|3x|20x20
58|iphone-29@2x.png|iphone|2x|29x29
87|iphone-29@3x.png|iphone|3x|29x29
80|iphone-40@2x.png|iphone|2x|40x40
120|iphone-40@3x.png|iphone|3x|40x40
120|iphone-60@2x.png|iphone|2x|60x60
180|iphone-60@3x.png|iphone|3x|60x60
20|ipad-20@1x.png|ipad|1x|20x20
40|ipad-20@2x.png|ipad|2x|20x20
29|ipad-29@1x.png|ipad|1x|29x29
58|ipad-29@2x.png|ipad|2x|29x29
40|ipad-40@1x.png|ipad|1x|40x40
80|ipad-40@2x.png|ipad|2x|40x40
76|ipad-76@1x.png|ipad|1x|76x76
152|ipad-76@2x.png|ipad|2x|76x76
167|ipad-83.5@2x.png|ipad|2x|83.5x83.5
1024|AppIcon-1024.png|ios-marketing|1x|1024x1024
EOF

while IFS='|' read -r px fn _idiom _scale _size; do
    render_png "$px" "$IOS_ICONSET/$fn"
done < "$ENTRIES"

python3 - "$IOS_ICONSET" "$ENTRIES" <<'PY'
import json
import os
import sys

outdir, entries_file = sys.argv[1], sys.argv[2]
images = []
with open(entries_file) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        _px, fn, idiom, scale, size = line.split("|")
        images.append({"filename": fn, "idiom": idiom, "scale": scale, "size": size})
data = {"images": images, "info": {"author": "xcode", "version": 1}}
with open(os.path.join(outdir, "Contents.json"), "w") as f:
    json.dump(data, f, indent=2, sort_keys=True)
    f.write("\n")
print("  Contents.json")
PY
rm -f "$ENTRIES"

echo "-> Play Store icon (fastlane)"
render_png 512 "$ROOT_DIR/fastlane/metadata/android/en-US/images/icon.png"

echo
echo "Done. Icons regenerated from $(basename "$SVG")."
