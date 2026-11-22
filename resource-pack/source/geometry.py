"""Create Blender meshes and export axis-aligned Minecraft item-model cuboids."""
import json
import math
import sys
from pathlib import Path

import bpy

ROOT = Path(sys.argv[sys.argv.index("--") + 1])
COLORS = ("dark", "metal", "light", "accent")
FACES = ("north", "south", "east", "west", "up", "down")


def bar(x, y, z, sx, sy, sz, color="light"):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x - 8, 8 - z, y - 8))
    mesh = bpy.context.object
    mesh.dimensions = (sx, sz, sy)
    bpy.context.view_layer.update()
    mesh["aw_texture"] = color
    return mesh


def ring(radius, y, color):
    for i in range(12):
        angle = i * math.tau / 12
        bar(8 + math.cos(angle)*radius, y, 8 + math.sin(angle)*radius, 1.25, .65, 1.25, color)


def stepped_line(count, point, color):
    for i in range(count):
        x, y, z = point(i)
        bar(x, y, z, 1.12, 1.12, 1.12, color)


def form(kind):
    if kind in ("supply", "premium"):
        bar(8, 7, 8, 9, 4, 7, "dark")
        bar(8, 9.6, 8, 9.5, 1.2, 7.5, "metal")
        for x in (5, 11):
            bar(x, 7, 8, .75, 4.2, 7.2, "metal")
            bar(x, 10.3, 8, .75, .4, 7.5, "light")
        bar(8, 8, 4.35, 2.0, 1.6, .35, "accent")
        if kind == "premium":
            for y, width in ((10.8, 3), (11.7, 2), (12.6, 1)):
                bar(8, y, 8, width, .9, width, "accent")
        return
    if kind in ("rune", "time", "void"):
        ring(5, 7.8, "metal")
        if kind == "rune":
            for x, z in ((8, 3), (3, 8), (13, 8), (8, 13)):
                bar(x, 8.4, z, 1.5, .7, 1.5, "accent")
            bar(8, 8, 8, 2.4, 1, 2.4, "light")
        elif kind == "time":
            bar(8, 8, 6, .65, .8, 4.5)
            bar(9.2, 8, 8, 2.5, .8, .65, "accent")
        else:
            ring(2.4, 8.1, "accent")
            bar(8, 7.6, 8, 3, .7, 3, "dark")
        return
    if kind in ("blade", "bolt", "rush", "blood"):
        stepped_line(9, lambda i: (4+i*.85, 5+i*.67, 8+(i-4)**2*.11), "light")
        if kind == "blade":
            stepped_line(7, lambda i: (4+i*.91, 4.2+i*.62, 8.6+(i-4)**2*.11), "accent")
        elif kind == "rush":
            for offset in (-2, 2):
                stepped_line(5, lambda i: (4+i*.95, 5+i*.67, 8+offset), "metal")
        elif kind == "bolt":
            for y, size in ((9, 2), (10, 3), (11, 2)):
                bar(11, y, 10, size, 1, size, "accent")
        else:
            for x, y in ((4, 4), (8, 5), (12, 7)):
                bar(x, y, 8, 1.2, 2.5, 1.2, "accent")
        return
    if kind == "impact":
        ring(5.4, 7, "accent")
        for i in range(4):
            angle = i * math.tau / 4
            bar(8 + math.cos(angle)*3.5, 8.7, 8 + math.sin(angle)*3.5, 1.6, 2, 1.6)
    elif kind == "ward":
        for y, width in ((5, 2), (6.5, 4), (8, 6), (9.5, 7), (11, 6)):
            bar(8, y, 8, width, 1.55, .65, "metal")
            bar(8, y, 7.58, width*.65, 1, .25, "light")
        bar(8, 8.5, 7.3, 1.5, 2.5, .4, "accent")
    elif kind in ("growth", "frost", "fire"):
        for i, (x,z) in enumerate(((8,8),(5,6),(11,9))):
            steps = 4-i
            for j in range(steps):
                size = 2.8-.45*j
                shift = j*.4 if kind == "fire" else -j*.3 if kind == "growth" else 0
                bar(x+shift, 6+j*1.1, z, size, 1.12, size, "accent" if j%2 else "light")
            if kind == "growth":
                bar(x, 5, z, 2.5, .55, 4, "metal")
            if kind == "frost":
                bar(x, 6, z, 4, .65, .65, "metal")
    else:
        raise ValueError(kind)


def export(kind):
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    form(kind)
    out = ROOT / "assets/augmentwild/models/fx" / f"{kind}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    parts = []
    for mesh in bpy.context.scene.objects:
        x, depth, height = mesh.location
        sx, sz, sy = mesh.dimensions
        center = (x+8, height+8, 8-depth)
        low = [round(c-d/2, 3) for c,d in zip(center,(sx,sy,sz))]
        high = [round(c+d/2, 3) for c,d in zip(center,(sx,sy,sz))]
        shade = mesh["aw_texture"]
        parts.append({"from": low, "to": high, "faces": {face: {"texture": "#"+shade} for face in FACES}})
    textures = {color: f"augmentwild:fx/{kind}/{color}" for color in COLORS}
    out.write_text(json.dumps({"textures": textures, "elements": parts}, separators=(",",":"))+"\n")


for name in ("rune", "blade", "ward", "growth", "bolt", "impact", "fire", "frost",
             "void", "blood", "time", "rush", "supply", "premium"):
    export(name)
