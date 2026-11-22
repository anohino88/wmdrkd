"""Produce an editable Blender scene and matching comparison renders from shipped model JSON."""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector, Quaternion

project, baseline, output = (Path(arg).resolve() for arg in sys.argv[sys.argv.index("--")+1:])
current = project / "resource-pack/pack/assets/augmentwild"
previous = baseline / "resource-pack/pack/assets/augmentwild"
output.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = 8
scene.cycles.use_denoising = False
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = 180
scene.render.resolution_percentage = 100
scene.render.fps = 20
scene.view_settings.view_transform = "AgX"
scene.world = bpy.data.worlds.new("Low ambient light")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.20,.26,.38,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value = .44
camera = bpy.data.cameras.new("Fixed orthographic comparison")
eye = bpy.data.objects.new("Fixed orthographic comparison",camera)
scene.collection.objects.link(eye)
eye.location = (18,-25,19)
eye.rotation_euler = (-eye.location).to_track_quat("-Z","Y").to_euler()
camera.type = "ORTHO"
camera.ortho_scale = 17
scene.camera = eye
for point,energy,size in [((8,-12,18),2900,9),((-16,-5,10),1700,10),((0,13,10),3200,7)]:
    light = bpy.data.lights.new("Studio softbox","AREA")
    holder = bpy.data.objects.new("Studio softbox",light)
    scene.collection.objects.link(holder)
    holder.location = point
    holder.rotation_euler = (-holder.location).to_track_quat("-Z","Y").to_euler()
    light.shape,light.size,light.energy = "DISK",size,energy


def coord(x,y,z):
    return Vector((x - 8,8 - z,y - 8))


def material(root,reference):
    image = bpy.data.images.load(str(root / ("textures/" + reference.split(":")[1] + ".png")),check_existing=True)
    key = str(image.filepath)
    if key in materials:
        return materials[key]
    mat = bpy.data.materials.new(Path(key).parent.name + "." + Path(key).stem)
    mat.use_nodes = True
    node = mat.node_tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    node.interpolation = "Closest"
    shader = mat.node_tree.nodes["Principled BSDF"]
    mat.node_tree.links.new(node.outputs["Color"],shader.inputs["Base Color"])
    mat.node_tree.links.new(node.outputs["Color"],shader.inputs["Emission Color"])
    mat.node_tree.links.new(node.outputs["Alpha"],shader.inputs["Alpha"])
    shader.inputs["Emission Strength"].default_value = .36
    shader.inputs["Roughness"].default_value = .42
    materials[key] = mat
    return mat


def create(root,name,model):
    collection = bpy.data.collections.new(name)
    scene.collection.children.link(collection)
    pivot = bpy.data.objects.new(name+" · animation",None)
    collection.objects.link(pivot)
    texture = {name: material(root,image) for name,image in model["textures"].items()}
    for part in model["elements"]:
        low,high = part["from"],part["to"]
        dx,dy,dz = (b-a for a,b in zip(low,high))
        center = coord(*[(a+b)/2 for a,b in zip(low,high)])
        bpy.ops.mesh.primitive_cube_add(size=1,location=center)
        obj = bpy.context.object
        for original in list(obj.users_collection):
            original.objects.unlink(obj)
        collection.objects.link(obj)
        obj.dimensions = (dx,dz,dy)
        if turn := part.get("rotation"):
            axis = {"x":(1,0,0),"y":(0,0,1),"z":(0,-1,0)}[turn["axis"]]
            angle = Quaternion(axis,math.radians(turn["angle"]))
            origin = coord(*turn["origin"])
            obj.location = origin + angle @ (center - origin)
            obj.rotation_mode = "QUATERNION"
            obj.rotation_quaternion = angle
        shade = next(iter(part["faces"].values()))["texture"][1:]
        obj.name = f"{name} · {shade}"
        obj.data.materials.append(texture[shade])
        obj.parent = pivot
        if shade == "sigil":
            for face in obj.data.polygons:
                for index in face.loop_indices:
                    vert = obj.data.vertices[obj.data.loops[index].vertex_index].co
                    obj.data.uv_layers.active.data[index].uv = (vert.x+.5,vert.z+.5)
    return collection,pivot


def render(name):
    scene.render.filepath = str(output/(name+".png"))
    bpy.ops.render.render(write_still=True)


def model(root,key):
    return json.loads((root/"models"/(key+".json")).read_text())


def animate(pivot,frames):
    pivot.rotation_mode = "QUATERNION"
    for tick,right,up,forward,yaw,pitch,roll,width,height,depth in frames:
        pivot.location = (right*16,-forward*16,up*16)
        pivot.scale = (width,depth,height)
        pivot.rotation_quaternion = (Quaternion((0,0,1),math.radians(yaw))
                                     @ Quaternion((1,0,0),math.radians(pitch))
                                     @ Quaternion((0,-1,0),math.radians(roll)))
        for prop in ("location","scale","rotation_quaternion"):
            pivot.keyframe_insert(data_path=prop,frame=1+tick)
    for channel in pivot.animation_data.action.fcurves:
        for point in channel.keyframe_points:
            point.interpolation = "LINEAR"


materials = {}
gallery = []
keys = {}
for line in (project/"src/main/resources/art-signatures.tsv").read_text().splitlines():
    if line and not line.startswith("#"):
        key,shape,sound,timeline = line.split("|")
        keys[key] = shape, [tuple(map(float,point.split(","))) for point in timeline.split(";")]
active = list(keys)[:7]
for key in active + ["crescent"]:
    shape = keys[key][0] if key in keys else key
    for label,root,keypath in [
        ("before",previous,("proc/" if key in keys else "fx/")+key),
        ("after",current,"fx/" + (("sig_" + shape) if key in keys else shape)),
    ]:
        group,pivot = create(root,f"{label} · {key}",model(root,keypath))
        render(label+"-"+key)
        group.hide_render = True
        if label == "after":
            gallery.append((group,pivot))
        else:
            group.hide_viewport = True
for key,(shape,frames) in keys.items():
    root_name = "sig_"+shape
    pair = next(((group,pivot) for group,pivot in gallery if group.name == f"after · {key}"),None)
    group,pivot = pair or create(current,key,model(current,"fx/"+root_name))
    camera.ortho_scale = 39
    group.hide_render = False
    animate(pivot,frames)
    if key in active or key in ("bomb_impact","void_collapse","thousand_swords"):
        for i,frame in enumerate(frames):
            scene.frame_set(int(frame[0])+1)
            render(f"motion-{key}-{i}")
    scene.frame_set(1)
    group.hide_render = True
    if pair is None:
        gallery.append((group,pivot))
for index,(group,pivot) in enumerate(gallery):
    group.hide_render = False
    pivot.delta_location = ((index%5)*26,(index//5)*26,0)
    scene.timeline_markers.new(group.name,frame=1+index)
scene.frame_start,scene.frame_end = 1,24
scene.frame_set(7)
bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(output/"visual-revolution.blend"),compress=True)
