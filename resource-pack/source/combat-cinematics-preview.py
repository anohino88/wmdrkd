"""Render representative public VFX contact frames from the existing Blender studio."""
import math
from pathlib import Path
import sys

import bpy

out = Path(sys.argv[sys.argv.index("--") + 1])
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
forms = {
    "arc": ("crescent", (1.6,1.15,.55), (-.36,.36), .16, 2.2),
    "guard": ("fracture", (1.4,.75,1.1), (0,0), .25, -.52),
    "blast": ("impact", (1.4,1.4,.3), (0,0), .06, .8),
    "void": ("helix", (1.2,1.2,.86), (0,0), -.33, 2.7),
}
for group in scene.collection.children:
    group.hide_render = any(obj.type == "MESH" for obj in group.objects)

scene.camera.data.ortho_scale = 28
scene.cycles.samples = 16
for name,(model,scale,travel,rise,spin) in forms.items():
    group = bpy.data.collections[model]
    pivot = next(obj for obj in group.objects if obj.type == "EMPTY")
    pivot.delta_location = (0,0,0)
    group.hide_render = False
    for frame in range(9):
        u = frame / 8
        energy = (.18+.82*u*2) if u <= .5 else (1-.93*(2*u-1))
        pivot.scale = tuple(energy*factor for factor in scale)
        pivot.location = (16*(travel[0]+u*(travel[1]-travel[0])),0,16*rise*u)
        pivot.rotation_euler = (.5*u if name == "void" else 0,0,spin*u)
        scene.render.filepath = str(out / f"{name}-{frame:02}.png")
        bpy.ops.render.render(write_still=True)
    group.hide_render = True
