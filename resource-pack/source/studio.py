"""Render the exported game cuboids with Blender. No Minecraft client is involved."""
import json
import math
import re
import sys
from pathlib import Path
import bpy
from mathutils import Vector, Quaternion

root, output = (Path(arg).resolve() for arg in sys.argv[sys.argv.index("--")+1:])
assets = root / "resource-pack/pack/assets/augmentwild"
output.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
scene.render.engine = "CYCLES"
scene.cycles.samples = 5
scene.cycles.use_denoising = False
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = 200
scene.render.resolution_percentage = 100
scene.world.color = (.35,.35,.35)
camera = bpy.data.cameras.new("Orthographic product view")
cam = bpy.data.objects.new("Camera",camera)
scene.collection.objects.link(cam)
cam.location = (12,-23,15)
cam.rotation_euler = (-cam.location).to_track_quat("-Z","Y").to_euler()
camera.type = "ORTHO"
camera.ortho_scale = 18
scene.camera = cam
for loc, energy, size in [((-7,-10,15),3200,9),((8,7,11),4200,8),((5,-10,4),1100,7)]:
    light = bpy.data.lights.new("Studio softbox","AREA")
    ob = bpy.data.objects.new("Softbox",light)
    scene.collection.objects.link(ob)
    ob.location = loc
    ob.rotation_euler = (-ob.location).to_track_quat("-Z","Y").to_euler()
    light.energy, light.shape, light.size = energy,"DISK",size
def render(name):
    scene.render.filepath = str(output/(name+".png"))
    bpy.ops.render.render(write_still=True)
def build(kind, model):
    group = bpy.data.collections.new(kind)
    scene.collection.children.link(group)
    pivot = bpy.data.objects.new(kind+" · pivot",None)
    group.objects.link(pivot)
    mats = {}
    for shade, ref in model["textures"].items():
        tex = assets / ("textures/"+ref.split(":")[1]+".png")
        image = bpy.data.images.load(str(tex),check_existing=True)
        mat = bpy.data.materials.new(kind+"."+shade)
        mat.use_nodes = True
        node = mat.node_tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        mat.node_tree.links.new(node.outputs["Color"],bsdf.inputs["Base Color"])
        mat.node_tree.links.new(node.outputs["Alpha"],bsdf.inputs["Alpha"])
        bsdf.inputs["Roughness"].default_value = .42
        mats[shade] = mat
    for part in model["elements"]:
        a,b = (Vector(part[k]) for k in ("from","to"))
        x,y,z = (a+b)/2
        bpy.ops.mesh.primitive_cube_add(size=1,location=(x-8,8-z,y-8))
        ob = bpy.context.object
        for old in list(ob.users_collection): old.objects.unlink(ob)
        group.objects.link(ob)
        ob.parent = pivot
        ob.dimensions = (b.x-a.x,b.z-a.z,b.y-a.y)
        shade = next(iter(part["faces"].values()))["texture"][1:]
        ob.name = kind+"."+shade
        ob.data.materials.append(mats[shade])
        if shade == "sigil":
            # Front/back in the exported model have explicit full-icon UVs.
            uv = ob.data.uv_layers.active.data
            for face in ob.data.polygons:
                if abs(face.normal.y) > .9:
                    for i in face.loop_indices:
                        co = ob.data.vertices[ob.data.loops[i].vertex_index].co
                        uv[i].uv = (co.x+.5,co.z+.5)
    return group,pivot

groups = []
for file in sorted((assets/"models/fx").glob("*.json")):
    group,pivot = build(file.stem,json.loads(file.read_text()))
    render(file.stem)
    group.hide_render = True
    groups.append((group,pivot))
# Use the Paper motion constants so the contact sheet follows the shipped timing.
java = (root/"src/main/java/io/github/augmentwild/paper/PaperArt.java").read_text()
scales = {key:[float(n.strip().rstrip("f")) for n in values.split(",")]
          for key,values in re.findall(r"float\[\] (\w+) = \{([^}]+)\}",java)}
casts = java.split("Map<String, Motion> CASTS")[1].split("Map<String, Motion> GROWTH")[0]
for key, scale, rise, spin, travel in re.findall(r'"(\w+)", new Motion\((\w+),([^,]+),([^,]+),([^\)]+)\)',casts):
    group,pivot = build(key,json.loads((assets/f"models/proc/{key}.json").read_text()))
    for i,stage in enumerate(scales[scale]):
        t = i/2
        # Model units are 1/16 of a block. Keep the camera wide enough for the full path.
        pivot.scale = (stage,)*3
        pivot.location = (0,-float(travel.strip("f"))*16*t,float(rise.strip("f"))*16*t)
        pivot.rotation_euler.z = float(spin.strip("f"))*t
        for path in ("scale","location","rotation_euler"):
            pivot.keyframe_insert(data_path=path,frame=1+6*i)
        camera.ortho_scale = 52
        scene.frame_set(1+6*i)
        render(key+"-"+str(i))
    group.hide_render = True
    groups.append((group,pivot))
for index,(group,pivot) in enumerate(groups):
    group.hide_render = False
    pivot.delta_location = ((index%5)*20,(index//5)*20,0)
scene.frame_start,scene.frame_end = 1,13
scene.frame_set(1)
bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(output/"augmentwild-studio.blend"),compress=True)
