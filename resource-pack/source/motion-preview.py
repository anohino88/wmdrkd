"""Render the shipped studio's seven active skill actions together over 16 ticks."""
import sys
from pathlib import Path
import bpy

out = Path(sys.argv[sys.argv.index("--") + 1])
out.mkdir(exist_ok=True, parents=True)
scene = bpy.context.scene
skills = ("shadow_dash","time_rewind","void_lord","bomb_alchemy","earthquake_mage","calamity_giant","duel_contract")
for collection in scene.collection.children:
    if collection.name not in skills:
        collection.hide_render = any(obj.type == "MESH" for obj in collection.objects)
        continue
    for obj in collection.objects:
        if obj.parent is None:
            obj.delta_location = (0,0,0)
for skill in skills:
    for name in skills:
        bpy.data.collections[name].hide_render = name != skill
    for frame in range(1,17):
        scene.frame_set(frame)
        scene.render.filepath = str(out / f"{skill}-{frame:02}.png")
        bpy.ops.render.render(write_still=True)
