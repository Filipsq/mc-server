"""Hub map "Dolina": a valley ringed by mountains, built by a data pack.

Builds a block model in numpy, turns it into `fill` commands and writes:
  - build/hub-datapack.zip: data pack (not committed; run this script to make it),
  - podglad.png: a top-down preview (committed, next to this script).

Usage: python3 generator.py [output dir]   (needs numpy and Pillow)
"""
import math, os, json, shutil, sys, zipfile
from pathlib import Path
import numpy as np

R = 92            # model half-size
YB = 92           # model base y
H = 92            # model height -> y 92..183
F = 100           # valley floor y (players stand on 101)
N = 2 * R + 1
rng = np.random.default_rng(7)

names = ["minecraft:air"]; index = {"minecraft:air": 0}


def bid(name):
    if not name.startswith("minecraft:"):
        name = "minecraft:" + name
    if name not in index:
        index[name] = len(names); names.append(name)
    return index[name]


grid = np.zeros((N, H, N), dtype=np.int32)


def inb(x, y, z):
    return -R <= x <= R and -R <= z <= R and YB <= y < YB + H


def put(x, y, z, b):
    if inb(x, y, z):
        grid[x + R, y - YB, z + R] = bid(b)


def get(x, y, z):
    return names[grid[x + R, y - YB, z + R]] if inb(x, y, z) else "minecraft:air"


def box(x1, y1, z1, x2, y2, z2, b):
    xa, xb = sorted((x1, x2)); ya, yb = sorted((y1, y2)); za, zb = sorted((z1, z2))
    xa, za = max(xa, -R), max(za, -R); xb, zb = min(xb, R), min(zb, R)
    ya, yb = max(ya, YB), min(yb, YB + H - 1)
    if xa > xb or ya > yb or za > zb:
        return
    grid[xa + R:xb + R + 1, ya - YB:yb - YB + 1, za + R:zb + R + 1] = bid(b)


labels = []


def label(x, y, z, text, color="white", scale=1.0, bold=False):
    labels.append((x, y, z, text, color, scale, bold))


# ---- noise -------------------------------------------------------------
def value_noise(size, cell, seed):
    r = np.random.default_rng(seed)
    g = r.random((size // cell + 3, size // cell + 3))
    xs = np.arange(size) / cell
    i = xs.astype(int); f = xs - i
    f = f * f * (3 - 2 * f)
    a = g[i][:, i]; b = g[i + 1][:, i]; c = g[i][:, i + 1]; d = g[i + 1][:, i + 1]
    fx = f[:, None]; fz = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


n1 = value_noise(N, 24, 1); n2 = value_noise(N, 9, 2); n3 = value_noise(N, 4, 3)
noise = 0.6 * n1 + 0.3 * n2 + 0.1 * n3            # 0..1
xs = np.arange(-R, R + 1)
X, Z = np.meshgrid(xs, xs, indexing="ij")
ang = np.arctan2(Z, X)
rad = np.hypot(X, Z)

# mountain ring: rises from r=58 to peak ~r=80, wobbly radius
wob = 5 * np.sin(ang * 3 + 1.3) + 3 * np.sin(ang * 7 + 0.4)
rr = rad + wob
t = np.clip((rr - 58) / 22, 0, 1)
peak = 34 + 26 * noise                              # 34..60 blocks above floor
height = F + (t ** 1.6) * peak + (noise - 0.5) * 4 * t
# valley floor: gentle rolling +-1
height = np.where(rr < 58, F + np.round((n2 - 0.5) * 2.2), height)
# outer fall-off beyond r=86 so the model edge isn't a flat wall
PADS = [(0, -60, 24, 10), (62, 0, 10, 26), (0, 62, 16, 12), (-60, 0, 12, 22)]
for (cx, cz, rx, rz) in PADS:
    dxp = np.maximum(np.abs(X - cx) - rx, 0); dzp = np.maximum(np.abs(Z - cz) - rz, 0)
    dp = np.hypot(dxp, dzp)
    height = np.minimum(height, F + dp * 2.0)
# paths: keep a gentle corridor to each district
for (dx, dz) in ((0, -1), (1, 0), (0, 1), (-1, 0)):
    lat = np.abs(X) if dx == 0 else np.abs(Z)
    along = (Z * dz if dx == 0 else X * dx)
    dp = np.maximum(lat - 5, 0)
    dp = dp + np.maximum(along - 50, 0) * 1.6
    height = np.where(along > 0, np.minimum(height, F + dp * 1.3), height)
height = np.round(height).astype(int)
height = np.clip(height, YB + 2, YB + H - 4)

STRIPES = ["white_terracotta", "light_gray_terracotta", "lime_terracotta", "green_terracotta",
           "cyan_terracotta", "white_terracotta", "terracotta", "lime_terracotta", "light_blue_terracotta"]

for ix in range(N):
    for iz in range(N):
        x, z = ix - R, iz - R
        h = height[ix, iz]
        r_ = rr[ix, iz]
        if rad[ix, iz] > 88:
            continue
        box(x, YB, z, x, h - 4, z, "stone")
        if r_ < 58:
            box(x, h - 3, z, x, h - 1, z, "dirt"); put(x, h, z, "grass_block")
        else:
            # slope steepness decides grass vs striped rock
            hn = max(height[min(ix + 1, N - 1), iz], height[max(ix - 1, 0), iz], height[ix, min(iz + 1, N - 1)], height[ix, max(iz - 1, 0)])
            steep = hn - h if hn > h else h - min(height[min(ix + 1, N - 1), iz], height[max(ix - 1, 0), iz], height[ix, min(iz + 1, N - 1)], height[ix, max(iz - 1, 0)])
            for y in range(h - 3, h + 1):
                put(x, y, z, STRIPES[((y + int(noise[ix, iz] * 3)) // 3) % len(STRIPES)])
            if h > F + 46:
                put(x, h, z, "snow_block")
            elif steep <= 1 and h < F + 30:
                put(x, h, z, "grass_block"); put(x, h - 1, z, "dirt")

# ---- river ring with waterfalls ----------------------------------------
RIV1, RIV2 = 40, 44
for ix in range(N):
    for iz in range(N):
        x, z = ix - R, iz - R
        if RIV1 <= rad[ix, iz] <= RIV2:
            h = F
            box(x, F - 3, z, x, F + 3, z, "air")
            put(x, F - 3, z, "gravel")
            box(x, F - 2, z, x, F - 1, z, "water")
        elif RIV1 - 1 <= rad[ix, iz] < RIV1 or RIV2 < rad[ix, iz] <= RIV2 + 1:
            box(x, F + 1, z, x, F + 3, z, "air")
            put(x, F, z, "mossy_cobblestone"); put(x, F - 1, z, "stone")
# waterfalls: from mountain into the valley, a channel to the river
for a0 in (math.radians(-135), math.radians(45)):
    for r_ in range(45, 75):
        x = int(round(r_ * math.cos(a0))); z = int(round(r_ * math.sin(a0)))
        for w in (-1, 0, 1):
            xx, zz = x + int(round(w * -math.sin(a0))), z + int(round(w * math.cos(a0)))
            if not (-R <= xx <= R and -R <= zz <= R):
                continue
            h = height[xx + R, zz + R]
            put(xx, h, zz, "water")
            box(xx, h + 1, zz, xx, h + 4, zz, "air")
            put(xx, h - 1, zz, "stone")

# ---- clear sky above the terrain inside the valley (trees placed later) ---
# ---- central plaza -----------------------------------------------------
for x in range(-20, 21):
    for z in range(-20, 21):
        d = math.hypot(x, z)
        if d <= 19.5:
            box(x, F - 2, z, x, F - 1, z, "stone")
            put(x, F, z, "polished_andesite" if (x + z) % 2 == 0 else "andesite")
            box(x, F + 1, z, x, F + 6, z, "air")
        if 16.5 < d <= 17.5:
            put(x, F, z, "lime_stained_glass"); put(x, F - 1, z, "sea_lantern")
        if 17.5 < d <= 19.5:
            put(x, F, z, "smooth_quartz")
        if d <= 7.5:
            put(x, F, z, "smooth_quartz")
        if 6.5 < d <= 7.5:
            put(x, F, z, "lime_concrete")
# monument: a tall crystal on a stepped base
for k, rad_ in enumerate((5.4, 4.4, 3.4)):
    for x in range(-6, 7):
        for z in range(-6, 7):
            if math.hypot(x, z) <= rad_:
                put(x, F + 1 + k, z, "polished_deepslate" if k < 2 else "deepslate_tiles")
box(-1, F + 4, -1, 1, F + 16, 1, "lime_stained_glass")
box(0, F + 4, 0, 0, F + 19, 0, "verdant_froglight")
for (x, z) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
    box(x, F + 4, z, x, F + 10, z, "lime_stained_glass")
box(-1, F + 17, -1, 1, F + 17, 1, "lime_stained_glass")
label(0, F + 23, 0, "HUB", "green", 6, True)
label(0, F + 21.6, 0, "mchosting.pl", "white", 2.2)

# ---- paths (N, E, S, W) with bridges over the river ---------------------
for (dx, dz) in ((0, -1), (1, 0), (0, 1), (-1, 0)):
    for r_ in range(19, 58):
        for w in range(-3, 4):
            x = dx * r_ + (dz != 0) * 0 + (dx == 0) * w
            z = dz * r_ + (dz == 0) * w
            hgt = F
            if RIV1 - 1 <= r_ <= RIV2 + 1:   # bridge
                put(x, F, z, "spruce_planks")
                if abs(w) == 3:
                    put(x, F + 1, z, "spruce_fence")
                put(x, F - 1, z, "air")
            else:
                box(x, F - 2, z, x, F - 1, z, "stone")
                put(x, F, z, "dirt_path" if abs(w) < 3 else "stone_bricks")
                box(x, F + 1, z, x, F + 5, z, "air")
        if r_ % 8 == 0 and not (RIV1 - 1 <= r_ <= RIV2 + 1):
            for w in (-4, 4):
                x = dx * r_ + (dx == 0) * w; z = dz * r_ + (dz == 0) * w
                box(x, F + 1, z, x, F + 3, z, "spruce_fence"); put(x, F + 4, z, "lantern")
                put(x, F, z, "stone_bricks")

# flatten building pads at the district ends (r 50..62)
def pad(cx, cz, rx, rz, top="polished_andesite"):
    box(cx - rx, F - 2, cz - rz, cx + rx, F - 1, cz + rz, "stone")
    box(cx - rx, F, cz - rz, cx + rx, F, cz + rz, top)


def gate(cx, cz, facing, w, h, fill, frame, name=None, color="white", sub=None, y=F):
    hw = w // 2
    if facing in "ns":
        box(cx - hw - 1, y + 1, cz, cx + hw + 1, y + h + 1, cz, frame)
        box(cx - hw, y + 1, cz, cx + hw, y + h, cz, fill)
        lx, lz = cx + 0.5, cz + (1.6 if facing == "n" else -0.6)
    else:
        box(cx, y + 1, cz - hw - 1, cx, y + h + 1, cz + hw + 1, frame)
        box(cx, y + 1, cz - hw, cx, y + h, cz + hw, fill)
        lx, lz = cx + (-0.6 if facing == "e" else 1.6), cz + 0.5
    if name:
        label(lx, y + h + 2.8, lz, name, color, 2.2, True)
    if sub:
        label(lx, y + h + 2.0, lz, sub, "gray", 1.1)


# North: minigames plaza with 4 gates in an arc
pad(0, -60, 22, 8)
box(-22, F, -52, 22, F, -52, "lime_concrete")
for i, (n, g, c) in enumerate([("Spleef", "light_blue_stained_glass", "aqua"), ("TNT Run", "red_stained_glass", "red"),
                               ("Parkour", "purple_stained_glass", "light_purple"), ("Duels", "orange_stained_glass", "gold")]):
    x = -16 + i * 11
    gate(x, -64 - (1 if i in (0, 3) else 0), "n", 7, 8, g, "quartz_pillar", n, c)
label(0.5, F + 16, -58, "MINIGRY", "gold", 5, True)

# East: modes, two big gates cut into the mountain
pad(62, 0, 9, 24)
gate(68, -12, "e", 11, 13, "red_stained_glass", "polished_blackstone_bricks", "BoxPvP", "red", "szybkie PvP")
gate(68, 12, "e", 11, 13, "crimson_hyphae", "polished_blackstone_bricks", "LifeSteal SMP", "dark_red", "sezon 1")
gate(68, 0, "e", 5, 7, "gray_stained_glass", "deepslate_bricks", "OneBlock", "gray", "wkrótce")
label(58, F + 19, 0.5, "TRYBY", "red", 5, True)

# South: alley of customer-server portals along the path
pad(0, 62, 14, 10)
for row_x, facing in ((-9, "e"), (9, "w")):
    for j in range(5):
        z = 46 + j * 5
        top = j == 4
        gate(row_x, z, facing, 3, 4, "cyan_stained_glass", "gold_block" if top else "smooth_quartz",
             "Serwer TOP" if top else "Wolne miejsce", "gold" if top else "white",
             "najwięcej graczy w tygodniu" if top else "/hub z Twojego serwera")
for j in range(5):   # more slots at the end of the alley
    gate(-8 + j * 4, 70, "n", 3, 4, "cyan_stained_glass", "smooth_quartz", "Wolne miejsce" if j == 2 else None)
label(0.5, F + 15, 58, "ALEJA SERWERÓW", "aqua", 4, True)

# West: amphitheatre
pad(-60, 0, 10, 20)
box(-70, F + 1, -10, -64, F + 2, 10, "polished_blackstone_bricks")
box(-64, F + 2, -10, -64, F + 2, 10, "lime_concrete")
box(-71, F + 3, -10, -71, F + 16, 10, "black_concrete")
box(-71, F + 3, -11, -71, F + 17, -11, "verdant_froglight"); box(-71, F + 3, 11, -71, F + 17, 11, "verdant_froglight")
box(-71, F + 17, -11, -71, F + 17, 11, "verdant_froglight")
for i in range(5):
    rr_ = 13 + i * 2
    for a in np.linspace(-1.1, 1.1, 120):
        x = int(round(-67 + rr_ * math.cos(a))); z = int(round(rr_ * math.sin(a)))
        if abs(z) <= 3 and i < 5 and x > -56:   # keep the path open
            pass
        box(x, F + 1, z, x, F + i, z, "polished_andesite")
        put(x, F + 1 + i, z, "spruce_stairs[facing=west]")
box(-56, F + 1, -3, -52, F + 5, 3, "air"); box(-56, F, -3, -52, F, 3, "dirt_path")
label(-70.3, F + 10, 0.5, "STREFA EVENTÓW", "light_purple", 3, True)

# ---- trees and flowers in the valley + lower slopes ----------------------
def tree(x, z, kind):
    h = height[x + R, z + R]
    if get(x, h, z) != "minecraft:grass_block":
        return
    if kind == "cherry":
        th = 5 + int(rng.integers(0, 3))
        box(x, h + 1, z, x, h + th, z, "cherry_log")
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(-1, 3):
                    if dx * dx + dz * dz + dy * dy * 2 <= 10 and rng.random() < 0.9:
                        if get(x + dx, h + th + dy, z + dz) == "minecraft:air":
                            put(x + dx, h + th + dy, z + dz, "cherry_leaves[persistent=true]")
    else:
        th = 7 + int(rng.integers(0, 4))
        box(x, h + 1, z, x, h + th, z, "spruce_log")
        for k in range(th - 5, th + 2):
            rad_ = max(0, (th + 1 - k) // 2)
            for dx in range(-rad_, rad_ + 1):
                for dz in range(-rad_, rad_ + 1):
                    if abs(dx) + abs(dz) <= rad_ + 1 and (dx, dz) != (0, 0) or k >= th:
                        if get(x + dx, h + k, z + dz) == "minecraft:air":
                            put(x + dx, h + k, z + dz, "spruce_leaves[persistent=true]")


def clear_ok(x, z):
    d = math.hypot(x, z)
    if d < 23 or RIV1 - 3 <= d <= RIV2 + 3:
        return False
    if abs(x) <= 6 or abs(z) <= 6:
        return False
    if -72 <= x <= -50 and abs(z) <= 24: return False
    if 52 <= x and abs(z) <= 26: return False
    if abs(x) <= 24 and z <= -50: return False
    if abs(x) <= 16 and z >= 42: return False
    return True


for _ in range(260):
    x = int(rng.integers(-80, 81)); z = int(rng.integers(-80, 81))
    d = math.hypot(x, z)
    if d > 76 or not clear_ok(x, z):
        continue
    tree(x, z, "cherry" if d < 52 and rng.random() < 0.55 else "spruce")
FLOWERS = ["poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "short_grass", "short_grass", "short_grass", "fern"]
for ix in range(N):
    for iz in range(N):
        x, z = ix - R, iz - R
        h = height[ix, iz]
        if get(x, h, z) == "minecraft:grass_block" and get(x, h + 1, z) == "minecraft:air" and rng.random() < 0.06:
            put(x, h + 1, z, FLOWERS[int(rng.integers(0, len(FLOWERS)))])

# ---- invisible wall on the ridge so nobody walks out ----------------------
for ix in range(N):
    for iz in range(N):
        if 71.5 <= rr[ix, iz] <= 73:
            x, z = ix - R, iz - R
            h = height[ix, iz]
            box(x, h + 1, z, x, YB + H - 1, z, "barrier")

# ---- export ---------------------------------------------------------------
LIMIT = 32768


def cuboids(g):
    done = np.zeros(g.shape, dtype=bool)
    out = []
    X_, Y_, Z_ = g.shape
    for y in range(Y_):
        layer = g[:, y, :]
        if not layer.any():
            continue
        for x in range(X_):
            row = layer[x]
            for z in np.nonzero(row)[0]:
                if done[x, y, z]:
                    continue
                b = row[z]
                z2 = z
                while z2 + 1 < Z_ and g[x, y, z2 + 1] == b and not done[x, y, z2 + 1]:
                    z2 += 1
                x2 = x
                while x2 + 1 < X_ and np.all(g[x2 + 1, y, z:z2 + 1] == b) and not done[x2 + 1, y, z:z2 + 1].any():
                    x2 += 1
                y2 = y
                while (y2 + 1 < Y_ and np.all(g[x:x2 + 1, y2 + 1, z:z2 + 1] == b)
                       and not done[x:x2 + 1, y2 + 1, z:z2 + 1].any()
                       and (x2 - x + 1) * (y2 + 2 - y) * (z2 - z + 1) <= LIMIT):
                    y2 += 1
                done[x:x2 + 1, y:y2 + 1, z:z2 + 1] = True
                out.append((x, y, z, x2, y2, z2, b))
    return out


def write_pack(dst, part_size=4000):
    if os.path.exists(dst):
        shutil.rmtree(dst)
    fn = os.path.join(dst, "data/hub/function")
    os.makedirs(os.path.join(fn, "build"))
    # Same format range as maps/boxpvp (Paper 26.3).
    json.dump({"pack": {"description": "mchosting.pl Hub - mapa Dolina", "min_format": 88, "max_format": 200}},
              open(os.path.join(dst, "pack.mcmeta"), "w"), indent=2, ensure_ascii=False)
    lo, hi = -R, R
    parts = cuboids(grid)
    late = ("water", "lantern", "stairs", "fence", "leaves", "poppy", "dandelion", "cornflower", "allium",
            "daisy", "bluet", "short_grass", "fern", "barrier")
    solid = [p for p in parts if not any(k in names[p[6]] for k in late)]
    deco = [p for p in parts if any(k in names[p[6]] for k in late)]
    clear = []
    for y in range(YB, YB + H, 1):
        for x0 in range(lo, hi + 1, 128):
            clear.append(f"fill {x0} {y} {lo} {min(hi, x0 + 127)} {y} {hi} minecraft:air")
    cmds = clear + [f"fill {x - R} {y + YB} {z - R} {x2 - R} {y2 + YB} {z2 - R} {names[b]}"
                    for (x, y, z, x2, y2, z2, b) in solid + deco]
    chunks_ = [cmds[i:i + part_size] for i in range(0, len(cmds), part_size)]
    for i, c in enumerate(chunks_):
        nxt = (f"schedule function hub:build/p{i + 1} 10t" if i + 1 < len(chunks_) else "function hub:build/finish")
        pct = int(100 * (i + 1) / len(chunks_))
        c = c + [f'title @a actionbar {{text:"Budowa huba: {pct}%",color:"yellow"}}', nxt]
        open(os.path.join(fn, f"build/p{i}.mcfunction"), "w").write("\n".join(c) + "\n")
    lab = ["kill @e[type=minecraft:text_display,tag=hub_label]"]
    for (x, y, z, t, c, s, bold) in labels:
        tx = x + (0.5 if float(x).is_integer() else 0); tz = z + (0.5 if float(z).is_integer() else 0)
        b = ",bold:true" if bold else ""
        lab.append(f'summon minecraft:text_display {tx} {y} {tz} {{Tags:["hub_label"],billboard:"center",'
                   f'text:{{text:"{t}",color:"{c}"{b}}},transformation:{{left_rotation:[0f,0f,0f,1f],'
                   f'right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[{s}f,{s}f,{s}f]}}}}')
    lab += [f"setworldspawn 0 {F + 1} 10",
            "forceload remove all",
            f'tellraw @a {{text:"Hub gotowy. Wpisz: function hub:tp",color:"green"}}']
    open(os.path.join(fn, "build/finish.mcfunction"), "w").write("\n".join(lab) + "\n")
    open(os.path.join(fn, "build.mcfunction"), "w").write("\n".join([
        "# Loads the hub area, then builds it in parts (one every half second).",
        f"forceload add {lo} {lo} {hi} {hi}",
        f'tellraw @a {{text:"Hub: ładuję teren, budowa ruszy za 3 sekundy ({len(chunks_)} części)...",color:"yellow"}}',
        "schedule function hub:build/p0 60t"]) + "\n")
    open(os.path.join(fn, "tp.mcfunction"), "w").write(f"tp @s 0 {F + 1} 10 180 0\n")
    return len(cmds), len(chunks_)


def preview(path, scale=4):
    """Top-down view: the highest block of each column, darker when lower."""
    from PIL import Image
    from colors import COL

    hide = {index.get("minecraft:barrier", -1)}
    img = Image.new("RGB", (N * scale, N * scale), (24, 26, 30))
    px = img.load()
    for x in range(N):
        for z in range(N):
            col = grid[x, :, z]
            ys = [y for y in np.nonzero(col)[0] if col[y] not in hide]
            if not ys:
                continue
            y = ys[-1]
            name = names[col[y]].split(":")[1].split("[")[0]
            shade = 0.55 + 0.45 * y / H
            c = tuple(int(min(255, v * shade)) for v in COL.get(name, (255, 0, 255)))
            for dx in range(scale):
                for dz in range(scale):
                    px[x * scale + dx, z * scale + dz] = c
    img.save(path)


def main():
    here = Path(__file__).parent
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else here / "build"
    out.mkdir(parents=True, exist_ok=True)
    pack_dir = out / "hub-datapack"
    n, p = write_pack(str(pack_dir))
    zip_path = out / "hub-datapack.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(pack_dir.rglob("*")):
            if f.is_file():
                info = zipfile.ZipInfo(f.relative_to(pack_dir).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                zf.writestr(info, f.read_bytes())
    shutil.rmtree(pack_dir)
    preview(here / "podglad.png")
    print(f"datapack: {zip_path} ({n} commands in {p} parts, {len(names)} block types)")


if __name__ == "__main__":
    main()
