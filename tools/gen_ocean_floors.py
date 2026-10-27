"""Generates the per-biome ocean floor density functions and measures the result.

The idea: vanilla picks ocean biomes from just two noises - continentalness (deep vs shallow) and temperature (5 bands).
Terrain height never looks at biomes, but it CAN look at those same two noises. So we make the sea floor a function of
(continentalness, temperature) using the same thresholds the biome picker uses, and floors line up with biomes."""
import json, os, sys

# Usage:  python tools/gen_ocean_floors.py [ramp_width]      (run from anywhere; it finds the repo root itself)
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W = float(sys.argv[1]) if len(sys.argv) > 1 else 0.12            # temperature ramp width, in noise units. Bigger = gentler slopes
                                                                  # between neighbouring biomes (must stay under half the narrowest band, 0.15)
DATA = f"{REPO}/src/main/resources/data"
OCEAN = f"{DATA}/oceanography/worldgen/density_function"
MC = f"{DATA}/minecraft/worldgen/density_function"

# ---- the targets from your TODO (max depth = lowest floor y allowed inside each biome) ----------------------------------
#                       frozen  cold   normal lukewarm warm       <- temperature bands, coldest first
TEMP_EDGES = [-1.0, -0.45, -0.15, 0.20, 0.55, 1.0]               # vanilla's five temperature bands for ocean biomes
DEEP_Y     = [-300,   -400,  -400,  -350,   30]                   # Deep Frozen / Deep Cold / Deep Ocean / Deep Lukewarm / Warm
SHELF_Y    = [ -20,    -40,   -20,    10,   30]                   # Frozen / Cold / Ocean / Lukewarm / Warm

# ---- y -> density-function "offset" ------------------------------------------------------------------------------------
GRAD_FROM_Y, GRAD_FROM_V, GRAD_TO_Y, GRAD_TO_V = -448, 4.5, 320, -1.5     # the depth.json we shipped last time
OCEAN_FACTOR = 3.95                                                          # vanilla terrain factor for every ocean c < -0.19
SURFACE_DENSITY = 0.703125 / 4                                               # terrain turns solid at depth*factor = 0.1758

def gradient(y): return GRAD_FROM_V + (y - GRAD_FROM_Y) * (GRAD_TO_V - GRAD_FROM_V) / (GRAD_TO_Y - GRAD_FROM_Y)
def offset_for_floor(y): return round(-gradient(y) + SURFACE_DENSITY / OCEAN_FACTOR, 5)

def floor_spline(values_y, coordinate="oceanography:temperature"):
    """Flat value per temperature band; wherever neighbouring bands differ, the DEEPER band ramps toward the shallower one
    so that every biome stays at or above its own cap and the steep part lives inside the deeper biome."""
    E = TEMP_EDGES
    pts = [(E[0], values_y[0])]
    for i in range(len(values_y) - 1):
        b, left, right = E[i + 1], values_y[i], values_y[i + 1]
        if left == right: continue
        if left < right: pts += [(b - W, left), (b, right)]       # left band is deeper: ramp inside it
        else:            pts += [(b, left), (b + W, right)]       # right band is deeper: ramp inside it
    pts.append((E[-1], values_y[-1]))
    for a, c in zip(pts, pts[1:]): assert a[0] < c[0], f"ramps overlap near {a}, {c}: lower the ramp width"
    return {"type": "minecraft:spline", "spline": {"coordinate": coordinate,
            "points": [{"location": round(x, 4), "derivative": 0, "value": offset_for_floor(y)} for x, y in pts]}}

def c_spline(points):
    return {"type": "minecraft:spline", "spline": {"coordinate": "minecraft:overworld/continents",
            "points": [{"location": x, "derivative": 0, "value": v} for x, v in points]}}

def lerp(a, b, w):        # a + w * (b - a)
    return {"type": "minecraft:add", "argument1": a, "argument2":
            {"type": "minecraft:mul", "argument1": w, "argument2":
             {"type": "minecraft:add", "argument1": b, "argument2": {"type": "minecraft:mul", "argument1": -1, "argument2": a}}}}

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f: json.dump(obj, f, indent=2); f.write("\n")

# same expression the noise router uses for biome temperature, so terrain and biomes read the very same noise
dump(f"{OCEAN}/temperature.json", {"type": "minecraft:shifted_noise", "noise": "minecraft:temperature",
     "shift_x": "minecraft:shift_x", "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 0.25, "y_scale": 0.0})

# base_offset.json is the untouched vanilla terrain offset (kept so land stays exactly vanilla). It is committed to the repo
# and never regenerated here.
assert os.path.exists(f"{OCEAN}/base_offset.json"), "base_offset.json is missing - restore it from git"

# where the ocean modification applies at all (0 on land/coast and on the mushroom-island edge, 1 over the sea)
dump(f"{OCEAN}/ocean_mask.json",     c_spline([(-1.05, 0), (-0.8, 1), (-0.35, 1), (-0.15, 0)]))
# 1 = deep-ocean floor, 0 = shelf floor.  The slope between them sits inside the DEEP biome (c -0.6 .. -0.455)
dump(f"{OCEAN}/ocean_deepness.json", c_spline([(-0.6, 1), (-0.455, 0)]))

dump(f"{OCEAN}/shelf_floor.json", floor_spline(SHELF_Y))
dump(f"{OCEAN}/deep_floor.json",  floor_spline(DEEP_Y))
dump(f"{OCEAN}/ocean_target.json", {"type": "minecraft:flat_cache", "argument":
     lerp("oceanography:shelf_floor", "oceanography:deep_floor", "oceanography:ocean_deepness")})

# the real overworld offset: vanilla on land, the biome-shaped floor over the sea
dump(f"{MC}/overworld/offset.json", {"type": "minecraft:clamp",
     "input": lerp("oceanography:base_offset", "oceanography:ocean_target", "oceanography:ocean_mask"),
     "min": -4.3, "max": 64})
print(f"wrote density functions (ramp width {W}) into {REPO}")
