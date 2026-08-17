#!/usr/bin/env python3
"""Generate the dark map style shipped as an app asset.

Fetches OpenFreeMap's liberty style (the only OpenFreeMap style with 3D
buildings, POIs, and house numbers -- their hosted `dark` style has none of
those, which is why we recolor liberty instead of using it) and rewrites its
paint colors with a curated dark palette. Layer IDs, sources, glyphs, and
sprites are untouched, so everything that keys off liberty layer names (the
2D/3D building toggle) keeps working.

Usage:
    python3 scripts/make_dark_style.py [path-to-liberty.json]

Writes app/src/main/assets/liberty_dark.json. Fails loudly if liberty grows a
layer this script has no rule for, so upstream drift is caught on regeneration.
"""

import json
import pathlib
import re
import sys
import urllib.request

LIBERTY_URL = "https://tiles.openfreemap.org/styles/liberty"
OUT_PATH = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/liberty_dark.json"

BG = "#12151a"
HALO = "rgba(18,21,26,0.9)"
TEXT = "#c9cdd4"
TEXT_BRIGHT = "#dfe3ea"
TEXT_DIM = "#a6adb8"
WATER = "hsl(213,45%,16%)"
WATERWAY = "hsl(213,40%,24%)"
WATER_TEXT = "#8fb4d4"

# Road palette: fills sit above the ground tone, casings below it, and the
# motorway keeps liberty's warm hue so the road hierarchy survives at night.
ROAD_CLASSES = {
    "motorway": ("hsl(33,25%,42%)", "hsl(33,30%,16%)"),
    "motorway_link": ("hsl(33,20%,36%)", "hsl(33,30%,16%)"),
    "trunk_primary": ("hsl(33,12%,34%)", "hsl(33,18%,14%)"),
    "secondary_tertiary": ("hsl(220,8%,28%)", "hsl(220,10%,12%)"),
    "link": ("hsl(220,8%,24%)", "hsl(220,10%,11%)"),
    "minor": ("hsl(220,8%,24%)", "hsl(220,10%,11%)"),
    "street": ("hsl(220,8%,24%)", "hsl(220,10%,11%)"),
    "service_track": ("hsl(220,7%,20%)", "hsl(220,10%,11%)"),
    "path_pedestrian": ("hsl(220,6%,28%)", "hsl(220,10%,11%)"),
    "major_rail": ("hsl(220,8%,26%)", None),
    "major_rail_hatching": ("hsl(220,8%,34%)", None),
    "transit_rail": ("hsl(220,8%,26%)", None),
    "transit_rail_hatching": ("hsl(220,8%,34%)", None),
}

# Exact-id paint overrides. `None` = keep the layer untouched.
EXACT = {
    "background": {"background-color": BG},
    "natural_earth": {"raster-brightness-max": 0.25},
    "water": {"fill-color": WATER},
    "park": {"fill-color": "hsl(130,18%,14%)", "fill-outline-color": "hsl(130,18%,20%)"},
    "park_outline": {"line-color": "hsl(130,18%,22%)"},
    "landuse_residential": {"fill-color": "hsl(220,8%,11%)"},
    "landcover_wood": {"fill-color": "hsl(130,14%,12%)"},
    "landcover_grass": {"fill-color": "hsl(120,12%,13%)"},
    "landcover_ice": {"fill-color": "hsl(210,25%,16%)"},
    "landcover_sand": {"fill-color": "hsl(45,15%,16%)"},
    # The wetland sprite pattern is drawn for light ground; fade it way down.
    "landcover_wetland": {"fill-opacity": 0.1},
    "landuse_pitch": {"fill-color": "hsl(150,15%,15%)"},
    "landuse_track": {"fill-color": "hsl(150,15%,15%)"},
    "landuse_cemetery": {"fill-color": "hsl(130,8%,14%)"},
    "landuse_hospital": {"fill-color": "hsl(0,15%,14%)"},
    "landuse_school": {"fill-color": "hsl(50,12%,14%)"},
    "aeroway_fill": {"fill-color": "hsl(220,8%,16%)"},
    "aeroway_runway": {"line-color": "hsl(220,8%,28%)"},
    "aeroway_taxiway": {"line-color": "hsl(220,8%,28%)"},
    "building": {"fill-color": "hsl(220,8%,20%)", "fill-outline-color": "hsl(220,8%,30%)"},
    "building-3d": {"fill-extrusion-color": "hsl(220,8%,20%)"},
    "boundary_2": {"line-color": "hsl(220,8%,48%)"},
    "boundary_3": {"line-color": "hsl(220,8%,40%)"},
    "boundary_disputed": {"line-color": "hsl(220,8%,48%)"},
    # The pedestrian-polygon sprite is drawn for light ground and glares white
    # on dark (piers, plazas); swap the pattern for a plain walkable-surface tone.
    "road_area_pattern": {"fill-pattern": None, "fill-color": "hsl(220,7%,25%)"},
    "road_one_way_arrow": None,
    "road_one_way_arrow_opposite": None,
    "highway-shield-non-us": None,
    "highway-shield-us-interstate": None,
    "road_shield_us": None,
}

LABELS = {
    "waterway_line_label": (WATER_TEXT, BG),
    "water_name_point_label": (WATER_TEXT, BG),
    "water_name_line_label": (WATER_TEXT, BG),
    "poi_r20": (TEXT_DIM, BG),
    "poi_r7": (TEXT_DIM, BG),
    "poi_r1": (TEXT_DIM, BG),
    "poi_transit": ("#9fb3c8", BG),
    "highway-name-path": (TEXT_DIM, BG),
    "highway-name-minor": (TEXT_DIM, BG),
    "highway-name-major": (TEXT_DIM, BG),
    "airport": ("#9fb3c8", BG),
    "label_other": (TEXT, HALO),
    "label_village": (TEXT, HALO),
    "label_town": (TEXT_BRIGHT, HALO),
    "label_state": ("#aab0ba", HALO),
    "label_city": (TEXT_BRIGHT, HALO),
    "label_city_capital": (TEXT_BRIGHT, HALO),
    "label_country_3": (TEXT_BRIGHT, HALO),
    "label_country_2": (TEXT_BRIGHT, HALO),
    "label_country_1": (TEXT_BRIGHT, HALO),
}

WATERWAY_RE = re.compile(r"^waterway_(tunnel|river|other)$")
ROAD_RE = re.compile(r"^(tunnel|road|bridge)_(.+?)(_casing)?$")


def road_overrides(layer_id):
    m = ROAD_RE.match(layer_id)
    if not m:
        return None
    prefix, road_class, casing = m.group(1), m.group(2), m.group(3)
    if road_class not in ROAD_CLASSES:
        return None
    fill, casing_color = ROAD_CLASSES[road_class]
    color = casing_color if casing else fill
    if color is None:
        return None
    if prefix == "tunnel" and not casing:
        color = dim(color)
    return {"line-color": color}


def dim(hsl_color):
    """Tunnels read one step darker than their surface road."""
    m = re.match(r"hsl\((\d+),(\d+)%,(\d+)%\)", hsl_color)
    h, s, lightness = m.groups()
    return f"hsl({h},{s}%,{max(int(lightness) - 6, 8)}%)"


def overrides_for(layer):
    layer_id = layer["id"]
    if layer_id in EXACT:
        return EXACT[layer_id]
    if layer_id in LABELS:
        text, halo = LABELS[layer_id]
        result = {"text-color": text, "text-halo-color": halo}
        return result
    if WATERWAY_RE.match(layer_id):
        return {"line-color": WATERWAY}
    road = road_overrides(layer_id)
    if road is not None:
        return road
    raise SystemExit(f"no dark rule for layer '{layer_id}' -- liberty drifted, add a rule")


def main():
    if len(sys.argv) > 1:
        style = json.loads(pathlib.Path(sys.argv[1]).read_text())
    else:
        with urllib.request.urlopen(LIBERTY_URL) as response:
            style = json.load(response)

    for layer in style["layers"]:
        rules = overrides_for(layer)
        if not rules:
            continue
        paint = layer.setdefault("paint", {})
        for prop, value in rules.items():
            if value is None:
                paint.pop(prop, None)
            else:
                paint[prop] = value

    style["name"] = "Liberty Dark (Mockarr)"
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(style, separators=(",", ":")) + "\n")
    print(f"wrote {OUT_PATH} ({OUT_PATH.stat().st_size // 1024} KB, {len(style['layers'])} layers)")


if __name__ == "__main__":
    main()
