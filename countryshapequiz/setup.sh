#!/bin/bash
# Fetches map data, flags and Earth textures into $GEO_ASSETS (default /home/claude/geoassets)
set -e
A=${GEO_ASSETS:-/home/claude/geoassets}; mkdir -p "$A"; cd "$A"
export GIT_LFS_SKIP_SMUDGE=1
[ -f ne/geojson/ne_50m_admin_0_countries.geojson ] || { git clone -q --depth 1 --filter=blob:none --sparse https://github.com/nvkelso/natural-earth-vector ne && git -C ne sparse-checkout set --no-cone /geojson/ne_50m_admin_0_countries.geojson; }
[ -d flags/svg ] || { git clone -q --depth 1 --filter=blob:none --sparse https://github.com/hampusborgos/country-flags flags && git -C flags sparse-checkout set --no-cone /svg/; }
[ -f tg/example/img/earth-blue-marble.jpg ] || { git clone -q --depth 1 --filter=blob:none --sparse https://github.com/vasturiano/three-globe tg && git -C tg sparse-checkout set --no-cone /example/img/; }
echo "assets ready in $A"
