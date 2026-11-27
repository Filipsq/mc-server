#!/usr/bin/env python3
"""BoxPvP map generator for mchosting.pl ("Serwerownia" theme).

Builds the whole map as a voxel model, then writes:
  - a datapack with `function boxpvp:build` (builds the map in stages),
    `function boxpvp:reset/<mine>` (refills a mine) and `function boxpvp:tp`,
  - strefy.json: zone coordinates for the future mchosting-boxpvp plugin,
  - podglad.png: a top-down preview of the map.

Usage: python3 generator.py [output_dir]
strefy.json and podglad.png are written next to this script (they are committed),
the datapack zip goes to output_dir (default: ./build, not committed).
Only the standard library, numpy and Pillow are needed. Output is deterministic.
"""

from __future__ import annotations

import json
import math
import random
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np

# ---------------------------------------------------------------- world shape
R = 100  # outer wall at |x| == R or |z| == R
FLOOR = 64  # floor block level, players walk at y = 65
Y_MIN, Y_MAX = 50, 105  # model height range (inclusive)
WALL_TOP = 84
FILL_LIMIT = 32768  # vanilla /fill block limit
COMMANDS_PER_STAGE = 2500

SIZE = 2 * R + 1
HEIGHT = Y_MAX - Y_MIN + 1

palette: list[str] = ["air"]
palette_index: dict[str, int] = {"air": 0}
world = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)


def bid(block: str) -> int:
    if block not in palette_index:
        palette_index[block] = len(palette)
        palette.append(block)
    return palette_index[block]


def fill(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> None:
    xa, xb = sorted((x1, x2))
    ya, yb = sorted((y1, y2))
    za, zb = sorted((z1, z2))
    world[xa + R : xb + R + 1, ya - Y_MIN : yb - Y_MIN + 1, za + R : zb + R + 1] = bid(block)


def put(x: int, y: int, z: int, block: str) -> None:
    world[x + R, y - Y_MIN, z + R] = bid(block)


def get(x: int, y: int, z: int) -> str:
    return palette[world[x + R, y - Y_MIN, z + R]]


def box(x1, y1, z1, x2, y2, z2) -> dict:
    return {"min": [min(x1, x2), min(y1, y2), min(z1, z2)], "max": [max(x1, x2), max(y1, y2), max(z1, z2)]}


# ------------------------------------------------------------------- texts
@dataclass
class Label:
    x: float
    y: float
    z: float
    text: str
    color: str
    scale: float = 1.5


labels: list[Label] = []

# ------------------------------------------------------------------ mines
@dataclass
class Mine:
    id: str
    tier: int
    name: str
    unlock_level: int
    cx: int
    cz: int
    gate: str  # side of the enclosure with the gate: east, west, south
    rim: list[str]  # storage blocks on the enclosure rim
    blocks: list[tuple[str, int]]  # (block, weight)


MINES = [
    Mine("drewno", 1, "Drewno", 1, -74, 40, "east", ["oak_planks"],
         [("oak_log", 40), ("birch_log", 30), ("spruce_log", 30)]),
    Mine("kamien", 2, "Kamień", 3, 74, 40, "west", ["stone_bricks"],
         [("stone", 60), ("cobblestone", 25), ("andesite", 15)]),
    Mine("wegiel", 3, "Węgiel", 6, -74, 0, "east", ["coal_block"],
         [("stone", 60), ("coal_ore", 40)]),
    Mine("zelazo", 4, "Żelazo", 10, 74, 0, "west", ["iron_block"],
         [("stone", 55), ("iron_ore", 35), ("coal_ore", 10)]),
    Mine("zloto", 5, "Złoto", 15, -74, -40, "east", ["gold_block"],
         [("deepslate", 55), ("deepslate_gold_ore", 35), ("deepslate_iron_ore", 10)]),
    Mine("redstone", 6, "Redstone i lapis", 20, 74, -40, "west", ["redstone_block", "lapis_block"],
         [("deepslate", 50), ("deepslate_redstone_ore", 25), ("deepslate_lapis_ore", 25)]),
    Mine("diament", 7, "Diament", 30, -22, -76, "south", ["diamond_block"],
         [("deepslate", 60), ("deepslate_diamond_ore", 30), ("deepslate_emerald_ore", 10)]),
    Mine("netheryt", 8, "Netheryt", 40, 22, -76, "south", ["netherite_block"],
         [("netherrack", 55), ("blackstone", 25), ("ancient_debris", 12), ("nether_gold_ore", 8)]),
]
MINE_HALF = 10  # enclosure wall at cx +- 10
ORE_HALF = 8  # minable 17 x 17
ORE_BOTTOM = FLOOR - 8  # 9 layers: y 56..64
GATE_HALF = 2  # gate 5 wide
ENCLOSURE_TOP = FLOOR + 5  # walls y 65..69, rim at y 70


def mine_ores(m: Mine) -> None:
    rng = random.Random(f"boxpvp-{m.id}")
    names = [b for b, _ in m.blocks]
    weights = [w for _, w in m.blocks]
    for x in range(m.cx - ORE_HALF, m.cx + ORE_HALF + 1):
        for z in range(m.cz - ORE_HALF, m.cz + ORE_HALF + 1):
            for y in range(ORE_BOTTOM, FLOOR + 1):
                put(x, y, z, rng.choices(names, weights)[0])


def gate_cells(m: Mine):
    """Wall columns (x, z) that are left open for the gate."""
    if m.gate in ("east", "west"):
        gx = m.cx + (MINE_HALF if m.gate == "east" else -MINE_HALF)
        return [(gx, z) for z in range(m.cz - GATE_HALF, m.cz + GATE_HALF + 1)]
    gz = m.cz + MINE_HALF
    return [(x, gz) for x in range(m.cx - GATE_HALF, m.cx + GATE_HALF + 1)]


def build_mine(m: Mine) -> dict:
    cx, cz, h = m.cx, m.cz, MINE_HALF
    # bedrock tub under the floor, walk ring at floor level
    fill(cx - 9, ORE_BOTTOM - 1, cz - 9, cx + 9, FLOOR, cz + 9, "bedrock")
    fill(cx - 9, FLOOR, cz - 9, cx + 9, FLOOR, cz + 9, "polished_blackstone")
    fill(cx - h, FLOOR, cz - h, cx + h, FLOOR, cz + h, "polished_blackstone")
    mine_ores(m)
    # enclosure: black rack walls, gray frame every 4 blocks, tier LEDs inside
    for i in range(-h, h + 1):
        for (x, z) in ((cx + i, cz - h), (cx + i, cz + h), (cx - h, cz + i), (cx + h, cz + i)):
            frame = i % 4 == 0 or abs(i) == h
            fill(x, FLOOR + 1, z, x, ENCLOSURE_TOP, z, "gray_concrete" if frame else "black_concrete")
            if not frame and i % 2 == 1:
                put(x, FLOOR + 3, z, "sea_lantern")
            put(x, ENCLOSURE_TOP + 1, z, m.rim[(i + h) % len(m.rim)])
    for (x, z) in ((cx - h, cz - h), (cx - h, cz + h), (cx + h, cz - h), (cx + h, cz + h)):
        fill(x, FLOOR + 1, z, x, ENCLOSURE_TOP + 2, z, "iron_block")
        put(x, ENCLOSURE_TOP + 3, z, "sea_lantern")
    cells = gate_cells(m)
    for (x, z) in cells:
        fill(x, FLOOR + 1, z, x, FLOOR + 4, z, "air")
        put(x, FLOOR, z, "cyan_concrete")
    # gate frame: iron columns + lintel in rim material
    (ax, az), (bx, bz) = cells[0], cells[-1]
    if m.gate in ("east", "west"):
        out = 1 if m.gate == "east" else -1
        for z in (az - 1, bz + 1):
            fill(ax + out, FLOOR + 1, z, ax + out, FLOOR + 5, z, "iron_block")
        fill(ax + out, FLOOR + 5, az - 1, ax + out, FLOOR + 5, bz + 1, m.rim[0])
        label_pos = (ax + out + 0.5 * out + 0.5, FLOOR + 6.6, (az + bz) / 2 + 0.5)
        entry = (ax - out + 0.5, FLOOR + 1, (az + bz) / 2 + 0.5, -90 if m.gate == "west" else 90)
    else:
        for x in (ax - 1, bx + 1):
            fill(x, FLOOR + 1, az + 1, x, FLOOR + 5, az + 1, "iron_block")
        fill(ax - 1, FLOOR + 5, az + 1, bx + 1, FLOOR + 5, az + 1, m.rim[0])
        label_pos = ((ax + bx) / 2 + 0.5, FLOOR + 6.6, az + 2.0)
        entry = ((ax + bx) / 2 + 0.5, FLOOR + 1, az - 0.5, 180)
    labels.append(Label(*label_pos, f"Kopalnia {m.tier}: {m.name}\\nod poziomu {m.unlock_level}",
                        "gold" if m.tier >= 7 else "aqua"))
    gx = [c[0] for c in cells]
    gz = [c[1] for c in cells]
    return {
        "id": m.id,
        "tier": m.tier,
        "name": m.name,
        "unlockLevel": m.unlock_level,
        "resetMinutes": 5 if m.tier <= 4 else 8,
        "fill": box(cx - ORE_HALF, ORE_BOTTOM, cz - ORE_HALF, cx + ORE_HALF, FLOOR, cz + ORE_HALF),
        "area": box(cx - 9, ORE_BOTTOM, cz - 9, cx + 9, ENCLOSURE_TOP + 1, cz + 9),
        "gate": box(min(gx), FLOOR + 1, min(gz), max(gx), FLOOR + 4, max(gz)),
        "entry": {"x": entry[0], "y": entry[1], "z": entry[2], "yaw": entry[3]},
        "blocks": [{"block": f"minecraft:{b}", "weight": w} for b, w in m.blocks],
    }


# --------------------------------------------------------------- map parts
def floor_and_paths() -> None:
    # data-centre raised floor: andesite tiles, gray seams every 6 blocks
    fill(-R, FLOOR, -R, R, FLOOR, R, "polished_andesite")
    for c in range(-R, R + 1, 6):
        fill(c, FLOOR, -R, c, FLOOR, R, "light_gray_concrete")
        fill(-R, FLOOR, c, R, FLOOR, c, "light_gray_concrete")
    for x in range(-96, 97, 12):
        for z in range(-96, 97, 12):
            put(x, FLOOR, z, "sea_lantern")

    def path(x1, z1, x2, z2):
        """5-wide cable tray (black) with a cyan line in the middle."""
        fill(x1, FLOOR, z1, x2, FLOOR, z2, "black_concrete")
        if abs(x2 - x1) <= abs(z2 - z1):
            mx = (x1 + x2) // 2
            fill(mx, FLOOR, z1, mx, FLOOR, z2, "cyan_concrete")
        else:
            mz = (z1 + z2) // 2
            fill(x1, FLOOR, mz, x2, FLOOR, mz, "cyan_concrete")

    path(-2, 26, 2, 65)  # spawn -> arena
    path(-2, -65, 2, -26)  # arena -> north
    path(-62, -62, 62, -58)  # north corridor
    for s in (-1, 1):
        path(s * 62, -62, s * 58, 82)  # side aisles along the mine gates
        path(s * 57, -2, s * 27, 2)  # arena -> aisle
        path(s * 57, 78, s * 37, 82)  # safe zone side exits
        path(s * 22 - 2, -65, s * 22 + 2, -58)  # north mine gates


def outer_wall() -> None:
    for i in range(-R, R + 1):
        for (x, z) in ((i, -R), (i, R), (-R, i), (R, i)):
            frame = i % 8 == 0
            fill(x, FLOOR, z, x, WALL_TOP, z, "gray_concrete" if frame else "black_concrete")
            if not frame:
                for y in range(FLOOR + 3, WALL_TOP - 1, 2):
                    put(x, y, z, "polished_blackstone")  # rack units
                rng = random.Random(f"led-{x}-{z}")
                for y in range(FLOOR + 3, WALL_TOP - 1, 2):
                    if i % 8 in (2, 6) and rng.random() < 0.45:
                        put(x, y, z, rng.choice(["lime_concrete", "lime_concrete", "cyan_concrete", "sea_lantern"]))
            put(x, WALL_TOP, z, "sea_lantern" if frame else "iron_block")
    # barrier band so nobody camps on top of the wall
    for (x1, z1, x2, z2) in ((-R, -R, R, -R), (-R, R, R, R), (-R, -R, -R, R), (R, -R, R, R)):
        fill(x1, WALL_TOP + 1, z1, x2, WALL_TOP + 10, z2, "barrier")


def rack(x: int, z: int, along_x: bool) -> None:
    """Small 2-long server rack used as PvP cover."""
    x2, z2 = (x + 1, z) if along_x else (x, z + 1)
    fill(x, FLOOR + 1, z, x2, FLOOR + 3, z2, "black_concrete")
    put(x, FLOOR + 2, z, "lime_concrete")
    put(x2, FLOOR + 2, z2, "cyan_concrete")
    fill(x, FLOOR + 4, z, x2, FLOOR + 4, z2, "smooth_stone_slab")


ARENA_R = 26


def arena() -> dict:
    for x in range(-ARENA_R - 1, ARENA_R + 2):
        for z in range(-ARENA_R - 1, ARENA_R + 2):
            d = math.hypot(x, z)
            if d <= ARENA_R + 0.5:
                block = "deepslate_tiles"
                if ARENA_R - 0.5 < d:
                    block = "cyan_concrete"
                elif 12.5 < d <= 13.5:
                    block = "black_concrete"
                put(x, FLOOR, z, block)
            elif d <= ARENA_R + 1.5:
                gap = abs(x) <= 2 or abs(z) <= 2
                put(x, FLOOR, z, "polished_blackstone_bricks")
                if not gap:
                    put(x, FLOOR + 1, z, "polished_blackstone_bricks")
    for x in range(-ARENA_R, ARENA_R + 1, 13):
        for z in range(-ARENA_R, ARENA_R + 1, 13):
            if math.hypot(x, z) <= ARENA_R and (x, z) != (0, 0) and abs(x) != abs(z):
                put(x, FLOOR, z, "sea_lantern")
    # "CPU" in the middle: a 2-step platform (future King of the Hill)
    fill(-5, FLOOR + 1, -5, 5, FLOOR + 1, 5, "polished_blackstone_slab")
    fill(-4, FLOOR + 1, -4, 4, FLOOR + 1, 4, "polished_blackstone_bricks")
    fill(-4, FLOOR + 2, -4, 4, FLOOR + 2, 4, "smooth_stone_slab")
    fill(-3, FLOOR + 2, -3, 3, FLOOR + 2, 3, "black_concrete")
    for i in range(-3, 4, 2):  # gold "pins" on the chip edge
        for (x, z) in ((i, -3), (i, 3), (-3, i), (3, i)):
            put(x, FLOOR + 2, z, "gold_block")
    fill(-1, FLOOR + 2, -1, 1, FLOOR + 2, 1, "sea_lantern")
    # four cooling towers as cover
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = sx * 15, sz * 15
            fill(x - 1, FLOOR + 1, z - 1, x + 1, FLOOR + 3, z + 1, "gray_concrete")
            fill(x - 1, FLOOR + 2, z - 1, x + 1, FLOOR + 2, z + 1, "iron_bars")
            fill(x, FLOOR + 1, z, x, FLOOR + 3, z, "black_concrete")
            fill(x - 1, FLOOR + 4, z - 1, x + 1, FLOOR + 4, z + 1, "smooth_stone_slab")
            put(x, FLOOR + 4, z, "sea_lantern")
    # low cover between towers and the CPU
    for (x, z) in ((-9, 0), (9, 0), (0, -9), (0, 9)):
        fill(x - (0 if x else 1), FLOOR + 1, z - (1 if x else 0), x + (0 if x else 1), FLOOR + 1, z + (1 if x else 0),
             "polished_blackstone_bricks")
    labels.append(Label(0.5, FLOOR + 5.5, 0.5, "RDZEŃ\\narena PvP", "red", 2.0))
    return {"center": [0, FLOOR, 0], "radius": ARENA_R, "hill": box(-3, FLOOR + 2, -3, 3, FLOOR + 6, 3)}


SAFE = (-36, 66, 36, 99)  # x1, z1, x2, z2


def safe_zone() -> dict:
    x1, z1, x2, z2 = SAFE
    fill(x1, FLOOR + 1, z1, x2, FLOOR + 1, z2 - 1, "smooth_quartz")
    for c in range(x1, x2 + 1, 6):
        fill(c, FLOOR + 1, z1, c, FLOOR + 1, z2 - 1, "light_blue_concrete")
    for c in range(z1, z2, 6):
        fill(x1, FLOOR + 1, c, x2, FLOOR + 1, c, "light_blue_concrete")
    # edge: quartz wall with glass on top, openings north / west / east
    for x in range(x1, x2 + 1):
        if abs(x) > 3:
            put(x, FLOOR + 2, z1, "white_concrete")
            put(x, FLOOR + 3, z1, "light_blue_stained_glass")
    for x in (x1, x2):
        for z in range(z1, z2):
            if not 78 <= z <= 82:
                put(x, FLOOR + 2, z, "white_concrete")
                put(x, FLOOR + 3, z, "light_blue_stained_glass")
    # half steps into the zone
    fill(-3, FLOOR + 1, z1 - 1, 3, FLOOR + 1, z1 - 1, "smooth_quartz_slab")
    fill(x1 - 1, FLOOR + 1, 78, x1 - 1, FLOOR + 1, 82, "smooth_quartz_slab")
    fill(x2 + 1, FLOOR + 1, 78, x2 + 1, FLOOR + 1, 82, "smooth_quartz_slab")
    # spawn pad
    for x in range(-6, 7):
        for z in range(78, 91):
            d = math.hypot(x, z - 84)
            if d <= 5.5:
                put(x, FLOOR + 1, z, "cyan_concrete" if d > 4.5 else ("sea_lantern" if d < 1 else "white_concrete"))
    # big monitor on the south wall
    fill(-13, FLOOR + 2, 99, 13, FLOOR + 14, 99, "black_concrete")
    fill(-12, FLOOR + 3, 99, 12, FLOOR + 13, 99, "light_blue_concrete")
    fill(-12, FLOOR + 3, 99, 12, FLOOR + 3, 99, "cyan_concrete")
    fill(-2, FLOOR + 2, 98, 2, FLOOR + 2, 98, "iron_block")
    labels.append(Label(0.5, FLOOR + 10, 98.4, "mchosting.pl\\nBoxPvP", "white", 4.0))
    labels.append(Label(0.5, FLOOR + 5.5, z1 + 0.5, "STREFA BEZPIECZNA\\nbez PvP", "green", 1.5))

    npcs = []
    kiosks = [
        ("skup", "Skup surowców", -24, 74, "lime"),
        ("ulepszenia", "Ulepszenia kilofa", 24, 74, "light_blue"),
        ("sklep", "Sklep: sprzęt", -24, 89, "orange"),
        ("schowek", "Schowek", 24, 89, "purple"),
    ]
    for kid, name, cx, cz, color in kiosks:
        y0 = FLOOR + 2
        face = 1 if cx < 0 else -1  # counter faces the middle of the zone
        for (x, z) in ((cx - 3, cz - 3), (cx - 3, cz + 3), (cx + 3, cz - 3), (cx + 3, cz + 3)):
            fill(x, y0, z, x, y0 + 3, z, "iron_block")
        fill(cx - 3, y0 + 4, cz - 3, cx + 3, y0 + 4, cz + 3, f"{color}_concrete")
        put(cx, y0 + 4, cz, "sea_lantern")
        fill(cx - 3 * face, y0, cz - 2, cx - 3 * face, y0 + 2, cz + 2, f"{color}_stained_glass")
        fill(cx + 2 * face, y0, cz - 1, cx + 2 * face, y0, cz + 1, "smooth_quartz")
        labels.append(Label(cx + 0.5, y0 + 5.6, cz + 0.5, name, "yellow", 1.6))
        npcs.append({"id": kid, "name": name, "x": cx + 0.5, "y": y0, "z": cz + 0.5, "yaw": -90 if face == 1 else 90})
    return {
        "area": box(x1, FLOOR, z1, x2, FLOOR + 30, z2),
        "spawn": {"x": 0.5, "y": FLOOR + 2, "z": 84.5, "yaw": 180},
        "npcs": npcs,
        "holograms": [
            {"id": "top-zabojstwa", "title": "Top zabójstw", "x": -8.5, "y": FLOOR + 5, "z": 96.5},
            {"id": "top-serie", "title": "Top serii", "x": 9.5, "y": FLOOR + 5, "z": 96.5},
        ],
    }


COVER = [(34, 12, False), (44, 30, True), (30, 42, False), (12, 44, True), (44, -30, True), (30, -44, False),
         (12, -46, True), (40, -12, False), (46, 52, True)]


def cover() -> None:
    for (x, z, ax) in COVER:
        for sx in (-1, 1):
            rack(sx * x - (1 if sx < 0 and ax else 0), z, ax)


# ----------------------------------------------------------------- output
def split_box(b):
    x1, y1, z1, x2, y2, z2 = b
    vol = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1)
    if vol <= FILL_LIMIT:
        yield b
        return
    dims = [x2 - x1, y2 - y1, z2 - z1]
    d = dims.index(max(dims))
    lo, hi = (x1, y1, z1)[d], (x2, y2, z2)[d]
    mid = (lo + hi) // 2
    a, c = list(b), list(b)
    a[d + 3] = mid
    c[d] = mid + 1
    yield from split_box(tuple(a))
    yield from split_box(tuple(c))


def mesh(x1, y1, z1, x2, y2, z2, skip_air=True):
    """Greedy-merge the model inside a box into cuboids: (x1,y1,z1,x2,y2,z2,block)."""
    sub = world[x1 + R : x2 + R + 1, y1 - Y_MIN : y2 - Y_MIN + 1, z1 + R : z2 + R + 1]
    nx, ny, nz = sub.shape
    rects_by_layer = []
    for yy in range(ny):
        layer = sub[:, yy, :]
        done = np.zeros(layer.shape, dtype=bool)
        rects = []
        for zz in range(nz):
            xx = 0
            while xx < nx:
                v = layer[xx, zz]
                if done[xx, zz] or (skip_air and v == 0):
                    xx += 1
                    continue
                xe = xx
                while xe + 1 < nx and layer[xe + 1, zz] == v and not done[xe + 1, zz]:
                    xe += 1
                ze = zz
                while ze + 1 < nz and np.all(layer[xx : xe + 1, ze + 1] == v) and not done[xx : xe + 1, ze + 1].any():
                    ze += 1
                done[xx : xe + 1, zz : ze + 1] = True
                rects.append((xx, zz, xe, ze, int(v)))
                xx = xe + 1
        rects_by_layer.append(rects)
    # merge identical rectangles in consecutive layers
    out, open_ = [], {}
    for yy, rects in enumerate(rects_by_layer):
        nxt = {}
        for r in rects:
            start = open_.pop(r, yy)
            nxt[r] = start
        for r, start in open_.items():
            out.append((r, start, yy - 1))
        open_ = nxt
    for r, start in open_.items():
        out.append((r, start, ny - 1))
    cmds = []
    for (ax, az, bx, bz, v), ya, yb in sorted(out, key=lambda t: (t[1], t[0][1], t[0][0])):
        for b in split_box((ax + x1, ya + y1, az + z1, bx + x1, yb + y1, bz + z1)):
            if b[0] == b[3] and b[1] == b[4] and b[2] == b[5]:
                cmds.append(f"setblock {b[0]} {b[1]} {b[2]} minecraft:{palette[v]}")
            else:
                cmds.append("fill {} {} {} {} {} {} minecraft:{}".format(*b, palette[v]))
    return cmds


def label_cmd(lb: Label) -> str:
    parts = lb.text.split("\\n")
    extra = "".join(f',"\\n{p}"' for p in parts[1:])
    text = f'{{text:"{parts[0]}",color:"{lb.color}",bold:true,extra:[{extra[1:]}]}}' if extra else \
        f'{{text:"{parts[0]}",color:"{lb.color}",bold:true}}'
    return (f"summon minecraft:text_display {lb.x} {lb.y} {lb.z} {{Tags:[\"boxpvp\"],billboard:\"center\","
            f"alignment:\"center\",shadow:1b,background:1073741824,text:{text},"
            f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
            f"translation:[0f,0f,0f],scale:[{lb.scale}f,{lb.scale}f,{lb.scale}f]}}}}")


def write_datapack(out: Path, mines: list[dict], spawn: dict) -> Path:
    files: dict[str, str] = {}
    ns = "data/boxpvp/function"
    header = "# Generated by maps/boxpvp/generator.py - do not edit by hand.\n"

    clear = [f"fill {b[0]} {b[1]} {b[2]} {b[3]} {b[4]} {b[5]} minecraft:air"
             for b in split_box((-R, Y_MIN, -R, R, Y_MAX, R))]
    body = mesh(-R, Y_MIN, -R, R, Y_MAX, R)
    stages = [clear] + [body[i : i + COMMANDS_PER_STAGE] for i in range(0, len(body), COMMANDS_PER_STAGE)]
    n = len(stages)
    for i, cmds in enumerate(stages):
        tail = [f"schedule function boxpvp:build/stage_{i + 1} 2t",
                f'tellraw @a [{{"text":"[BoxPvP] ","color":"aqua"}},{{"text":"budowanie {i + 1}/{n}","color":"gray"}}]']
        if i == n - 1:
            tail = ["schedule function boxpvp:build/finish 2t"]
        files[f"{ns}/build/stage_{i}.mcfunction"] = header + "\n".join(cmds + tail) + "\n"
    files[f"{ns}/build.mcfunction"] = header + "\n".join([
        "forceload add -100 -100 100 100",
        "kill @e[type=minecraft:text_display,tag=boxpvp]",
        'tellraw @a {"text":"[BoxPvP] Start budowania mapy, poczekaj chwilę...","color":"aqua"}',
        "schedule function boxpvp:build/stage_0 10t",
    ]) + "\n"
    sx, sy, sz = spawn["x"], spawn["y"], spawn["z"]
    files[f"{ns}/build/finish.mcfunction"] = header + "\n".join(
        [label_cmd(lb) for lb in labels] + [
            f"setworldspawn {math.floor(sx)} {sy} {math.floor(sz)}",
            "worldborder center 0.5 0.5",
            f"worldborder set {SIZE}",
            "forceload remove all",
            'tellraw @a {"text":"[BoxPvP] Mapa gotowa. Wpisz /function boxpvp:tp","color":"green"}',
        ]) + "\n"
    files[f"{ns}/tp.mcfunction"] = header + f"tp @s {sx} {sy} {sz} {spawn['yaw']} 0\n"
    for m in mines:
        a, b = m["fill"]["min"], m["fill"]["max"]
        cmds = mesh(a[0], a[1], a[2], b[0], b[1], b[2], skip_air=False)
        files[f"{ns}/reset/{m['id']}.mcfunction"] = header + "\n".join(cmds) + "\n"
    files[f"{ns}/reset/all.mcfunction"] = header + "\n".join(f"function boxpvp:reset/{m['id']}" for m in mines) + "\n"
    files["pack.mcmeta"] = json.dumps({"pack": {
        "description": "mchosting.pl BoxPvP - mapa Serwerownia",
        "min_format": 88, "max_format": 200}}, indent=2, ensure_ascii=False) + "\n"

    path = out / "boxpvp-datapack.zip"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zf.writestr(info, files[name])
    print(f"datapack: {path} ({len(body)} commands in {n} stages, {len(files)} files)")
    return path


COLORS = {
    "polished_andesite": (132, 135, 134), "light_gray_concrete": (125, 125, 115), "black_concrete": (20, 22, 26),
    "cyan_concrete": (21, 119, 136), "sea_lantern": (172, 199, 190), "gray_concrete": (54, 57, 61),
    "iron_block": (220, 220, 220), "deepslate_tiles": (54, 54, 55), "polished_blackstone_bricks": (48, 42, 50),
    "polished_blackstone": (53, 48, 56), "polished_blackstone_slab": (53, 48, 56), "smooth_stone_slab": (158, 158, 158),
    "gold_block": (246, 208, 61), "smooth_quartz": (235, 229, 222), "light_blue_concrete": (35, 137, 198),
    "white_concrete": (207, 213, 214), "light_blue_stained_glass": (102, 153, 216), "lime_concrete": (94, 168, 24),
    "orange_concrete": (224, 97, 0), "purple_concrete": (100, 31, 156), "bedrock": (85, 85, 85),
    "barrier": (60, 0, 0), "netherite_block": (66, 61, 63), "diamond_block": (98, 237, 228),
    "redstone_block": (175, 24, 5), "lapis_block": (31, 67, 140), "coal_block": (16, 15, 15),
    "stone_bricks": (122, 121, 122), "oak_planks": (162, 130, 78), "iron_bars": (136, 139, 135),
    "smooth_quartz_slab": (235, 229, 222),
}


def render_preview(out: Path, mines: list[dict]) -> None:
    from PIL import Image, ImageDraw, ImageFont

    s = 4
    img = Image.new("RGB", (SIZE * s, SIZE * s), (0, 0, 0))
    px = img.load()
    for xi in range(SIZE):
        for zi in range(SIZE):
            col = world[xi, :, zi]
            nz = np.nonzero((col != 0) & (col != palette_index.get("barrier", -1)))[0]
            if len(nz) == 0:
                continue
            top = int(nz[-1])
            name = palette[col[top]]
            c = COLORS.get(name)
            if c is None:  # ores: use a mid tone per block family
                c = (120, 90, 60) if "log" in name else (90, 90, 95)
                if "ore" in name or "debris" in name:
                    c = (200, 160, 90)
            shade = 0.75 + 0.25 * min(1.0, (top + Y_MIN - FLOOR + 2) / 8)
            c = tuple(int(v * shade) for v in c)
            for dx in range(s):
                for dz in range(s):
                    px[xi * s + dx, zi * s + dz] = c
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
    small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)

    def text(x, z, t, f=font, fill=(255, 255, 255)):
        X, Z = (x + R) * s, (z + R) * s
        d.multiline_text((X, Z), t, font=f, fill=fill, anchor="mm", align="center", stroke_width=3,
                         stroke_fill=(0, 0, 0))

    for m in mines:
        c = m["fill"]
        cx, cz = (c["min"][0] + c["max"][0]) / 2, (c["min"][2] + c["max"][2]) / 2
        text(cx, cz, f"{m['tier']}. {m['name']}\npoz. {m['unlockLevel']}+")
    text(0, -18, "RDZEŃ (arena)", fill=(255, 120, 120))
    text(0, 74, "STREFA BEZPIECZNA\nspawn + sklep", fill=(120, 255, 140))
    text(0, -96, "N (z-)", small)
    text(0, 103 - 6, "S (z+)", small)
    img.save(out / "podglad.png")
    print(f"preview: {out / 'podglad.png'}")


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "build"
    out.mkdir(parents=True, exist_ok=True)
    here = Path(__file__).parent
    floor_and_paths()
    cover()
    zone = safe_zone()
    arena_info = arena()
    mines = [build_mine(m) for m in MINES]
    outer_wall()

    spec = {
        "version": 1,
        "map": "serwerownia",
        "note": "Generated by generator.py. Coordinates are block positions, boxes are inclusive.",
        "bounds": box(-R + 1, ORE_BOTTOM - 1, -R + 1, R - 1, WALL_TOP + 10, R - 1),
        "floorY": FLOOR,
        "spawn": zone["spawn"],
        "safeZone": zone["area"],
        "npcs": zone["npcs"],
        "holograms": zone["holograms"],
        "arena": arena_info,
        "mines": mines,
    }
    (here / "strefy.json").write_text(json.dumps(spec, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"zones: {here / 'strefy.json'}")
    write_datapack(out, mines, zone["spawn"])
    render_preview(here, mines)


if __name__ == "__main__":
    main()
