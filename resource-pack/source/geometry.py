"""Create Blender meshes and export axis-aligned Minecraft item-model cuboids."""
import json
import math
import runpy
import sys
from pathlib import Path

import bpy

SIGNATURES = runpy.run_path(str(Path(__file__).with_name("signature-models.py")))

ROOT = Path(sys.argv[sys.argv.index("--") + 1])
COLORS = ("dark", "metal", "light", "accent")
FACES = ("north", "south", "east", "west", "up", "down")


def bar(x, y, z, sx, sy, sz, color="light", axis="z", angle=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x - 8, 8 - z, y - 8))
    mesh = bpy.context.object
    mesh.dimensions = (sx, sz, sy)
    bpy.context.view_layer.update()
    mesh["aw_texture"] = color
    mesh["aw_axis"] = axis
    mesh["aw_angle"] = angle
    return mesh


def ring(radius, y, color, width=1.25):
    for i in range(12):
        angle = i * math.tau / 12
        bar(8 + math.cos(angle)*radius, y, 8 + math.sin(angle)*radius, width, .65, width, color)


def stepped_line(count, point, color):
    for i in range(count):
        x, y, z = point(i)
        bar(x, y, z, 1.12, 1.12, 1.12, color)


def form(kind):
    if kind == "helix":
        for i in range(20):
            angle = i * math.tau / 10
            radius = 2.5 + i * .12
            bar(8+radius*math.cos(angle),3+i*.47,8+radius*math.sin(angle),
                .82,.33,.82,"accent" if i%3 else "light")
        for i in range(11):
            angle = math.pi + i * math.tau / 8
            bar(8+2.15*math.cos(angle),3.38+i*.81,8+2.15*math.sin(angle),.47,.24,.47,"metal")
        return
    if kind == "fracture":
        # Six separate pieces of a bowed shield leave a visible central break.
        for i in range(6):
            angle = i * math.tau / 6
            bar(8+4.2*math.cos(angle),8+4.2*math.sin(angle),8+.3*i,
                1.3+.16*i,2.1,.72,"metal" if i%2 else "light")
            bar(8+4.2*math.cos(angle),8+4.2*math.sin(angle)+.16,7.54+.3*i,
                .62+.09*i,1.25,.18,"accent")
        return
    if kind == "crescent":
        for i in range(15):
            angle = .55 + i*.30
            thickness = .42 + .85*math.sin(math.pi*i/14)
            bar(8+5.4*math.cos(angle),7.5+.11*i,8+5.4*math.sin(angle),
                thickness,thickness*.7,thickness,"light" if i%3 else "accent")
        for i in range(7):
            angle = .78 + i*.52
            bar(8+3.75*math.cos(angle),7.26+.21*i,8+3.75*math.sin(angle),.57,.27,.61,"metal")
        return
    if kind.startswith("tier_growth_"):
        stage = int(kind[-1])
        bar(8,7,8,2.5,3.4,2.4,"light","z",45)
        for level in range(stage):
            size = 3.4 + level * 2.3
            for arm in range(4):
                angle = arm * math.tau / 4 + level * math.pi/4
                bar(8+math.cos(angle)*size/2,5.2+level*.91,8+math.sin(angle)*size/2,
                    2.6,.32,1.15,"accent" if level == stage-1 else "metal","y",45 if arm%2 else 0)
        return
    if kind in ("supply", "premium"):
        premium = kind == "premium"
        bar(8,6.7,8,9.5,4.5,7.25,"dark")
        bar(8,9.35,8,9.8,1.04,7.68,"metal")
        for x in (3.62,12.38):
            for z in (4.83,11.17):
                bar(x,6.72,z,1.12,4.70,1.17,"metal")
                bar(x,9.78,z,1.35,.45,1.42,"light")
        for x in (5.85,10.15):
            bar(x,8.12,4.19,1.03,1.52,.27,"light","z",22.5)
        bar(8,7.91,4.12,1.55,1.58,.52,"accent","z",45)
        bar(8,5.23,4.19,7.1,.34,.17,"metal")
        if premium:
            bar(8,10.17,8,6.65,.8,5.19,"accent")
            for x in (5.35,10.65):
                bar(x,10.98,8,.77,2.22,.81,"light","z",22.5 if x<8 else -22.5)
            bar(8,11.14,8,1.72,2.36,1.67,"light","z",45)
        else:
            bar(8,10.04,8,6.35,.52,3.28,"light")
        return
    if kind in ("rune", "time", "void"):
        ring(5, 7.8, "metal")
        if kind == "rune":
            for x, z in ((8, 3), (3, 8), (13, 8), (8, 13)):
                bar(x, 8.4, z, 1.5, .7, 1.5, "accent")
            bar(8, 8, 8, 2.4, 1, 2.4, "light")
        elif kind == "time":
            bar(8, 8, 6, .65, .8, 4.5)
            bar(9.2, 8.23, 8, 2.5, .8, .65, "accent")
        else:
            ring(2.4, 8.1, "accent", .8)
            bar(8, 7.6, 8, 3, .7, 3, "dark")
        return
    if kind in ("blade", "bolt", "rush", "blood"):
        stepped_line(9, lambda i: (4+i*.85, 5+i*.67, 8+(i-4)**2*.11), "light")
        if kind == "blade":
            stepped_line(7, lambda i: (4.16+i*.91, 4.2+i*.62, 8.6+(i-4)**2*.11), "accent")
        elif kind == "rush":
            for offset in (-2, 2):
                stepped_line(5, lambda i: (4.22+i*.95, 4.76+i*.67, 8+offset+i*.12), "metal")
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
            bar(8, y, 8, width, 1.49, .65, "metal")
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
    if kind.startswith("sig_") or kind in ("crescent","impact","fracture"):
        SIGNATURES["build"](kind.removeprefix("sig_"),bar)
    else:
        form(kind)
    # Silhouette details around the original body, in 0..16 item coordinates.
    if kind == "time":
        for x, z in ((8,4.3),(8,11.7),(4.3,8),(11.7,8)):
            bar(x, 8.4, z, .55, .8, .55, "accent")
    elif kind == "impact_legacy":
        for i in range(4):
            bar(5+i*1.8, 7.5, 8+(-1)**i, 1.8, .5, .65, "dark")
    elif kind == "blade":
        bar(4.2, 5.1, 9.4, 2.4, .5, 2.2, "metal")
    elif kind == "frost":
        for x, z in ((4,10),(8,11),(12,5)):
            bar(x, 7.3, z, .55, 3.6, .55)
    elif kind == "growth":
        for x, z in ((4,6),(9,5),(12,10)):
            bar(x, 8.1, z, 2.5, .55, 1.3, "metal")
    elif kind == "fire":
        for x, z in ((6,9),(9,5),(11,7)):
            bar(x, 11.5, z, .65, 1.4, .65, "light")
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
        part = {"from": low, "to": high, "faces": {face: {"texture": "#"+shade} for face in FACES}}
        if mesh["aw_angle"]:
            part["rotation"] = {"origin": [round(c,3) for c in center],
                                "axis": mesh["aw_axis"], "angle": mesh["aw_angle"]}
        parts.append(part)
    textures = {color: f"augmentwild:fx/{kind}/{color}" for color in COLORS}
    out.write_text(json.dumps({"textures": textures, "elements": parts}, separators=(",",":"))+"\n")


for name in ("rune", "blade", "ward", "growth", "bolt", "impact", "fire", "frost",
             "void", "blood", "time", "rush", "supply", "premium", "helix","fracture","crescent","tier_growth_1","tier_growth_2","tier_growth_3"):
    export(name)

for name in SIGNATURES["PALETTES"]:
    export("sig_" + name)
