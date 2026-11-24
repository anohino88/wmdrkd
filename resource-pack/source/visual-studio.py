"""Produce an editable Blender scene and matching comparison renders from shipped model JSON."""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector, Quaternion

args = sys.argv[sys.argv.index("--")+1:]
project, baseline, output = (Path(arg).resolve() for arg in args[:3])
fight = "--fight" in args[3:]
ascension = fight or "--ascension" in args[3:]
reforge = ascension or "--reforge" in args[3:]
current = project / "resource-pack/pack/assets/augmentwild"
previous = baseline / "resource-pack/pack/assets/augmentwild"
output.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = 8
scene.cycles.use_denoising = False
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = 288 if reforge else 180
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
camera.ortho_scale = 14 if reforge else 17
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


def fight_stage():
    """Use one block = 16 model units, with a 1.8-block neutral target."""
    fixture = bpy.data.collections.new("Paper coordinates · one block = 16 units")
    scene.collection.children.link(fixture)
    palette = {}
    for name, color in {"stone":(.078,.10,.16,1),"seam":(.10,.13,.17,1),"target":(.21,.28,.32,1)}.items():
        mat = bpy.data.materials.new(name)
        mat.diffuse_color = color
        palette[name] = mat

    def box(center, size, tone):
        bpy.ops.mesh.primitive_cube_add(size=1,location=center)
        obj = bpy.context.object
        obj.dimensions = size
        for collection in list(obj.users_collection):
            collection.objects.unlink(obj)
        fixture.objects.link(obj)
        obj.data.materials.append(palette[tone])

    for x in (-16,0,16):
        for y in (-16,0,16):
            box((x,y,-1),(15.86,15.86,2),"seam" if (x+y)%32 else "stone")
    box((-12,-4,3.5),(6,3.5,7),"target")
    box((-12,-4,14),(8,4,14),"target")
    box((-12,-4,25),(7,7,7),"target")
    return fixture


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
growth = [key for key in keys if key not in active and key not in ("bomb_impact","void_collapse")]
for key in active + (growth if ascension else []) + (["crescent","impact","fracture"] if reforge else ["crescent"]):
    shape = keys[key][0] if key in keys else key
    for label,root,keypath in [
        ("before",previous,("fx/sig_" if reforge and key in keys else "proc/" if key in keys else "fx/")+key),
        ("after",current,"fx/" + (("sig_" + shape) if key in keys else shape)),
    ]:
        group,pivot = create(root,f"{label} · {key}",model(root,keypath))
        render(label+"-"+key)
        group.hide_render = True
        if label == "after":
            gallery.append((group,pivot))
        else:
            group.hide_viewport = True
fixture = fight_stage() if fight else None
studio_eye = eye.location.copy()
simulated_views = ((3,"near"),(8,"mid"),(16,"far"),(24,"edge"))
for key,(shape,frames) in keys.items():
    root_name = "sig_"+shape
    pair = next(((group,pivot) for group,pivot in gallery if group.name == f"after · {key}"),None)
    group,pivot = pair or create(current,key,model(current,"fx/"+root_name))
    camera.ortho_scale = 31 if reforge else 39
    group.hide_render = False
    animate(pivot,frames)
    if key in active or key in ("bomb_impact","void_collapse","thousand_swords"):
        for i,frame in enumerate(frames):
            scene.frame_set(int(frame[0])+1)
            render(f"motion-{key}-{i}")
    if reforge and key in active:
        if fixture:
            fixture.hide_render = False
            camera.ortho_scale = 53
            eye.location = studio_eye + Vector((0,0,4))
            eye.rotation_euler = (Vector((0,0,7))-eye.location).to_track_quat("-Z","Y").to_euler()
        for tick in range(int(frames[-1][0]) + 1):
            scene.frame_set(tick+1)
            render(f"clip-{key}-{tick:02}")
        if fixture:
            # Horizontal distance uses game blocks. 70 degrees is a reproducible camera setting.
            scene.frame_set(int(frames[len(frames)//2][0])+1)
            camera.type = "PERSP"
            camera.angle = math.radians(70)
            for distance,label in simulated_views:
                eye.location = (0,-distance*16,26)
                eye.rotation_euler = (Vector((0,0,10))-eye.location).to_track_quat("-Z","Y").to_euler()
                render(f"view-{key}-{label}")
            camera.type = "ORTHO"
            eye.location = studio_eye
            eye.rotation_euler = (-eye.location).to_track_quat("-Z","Y").to_euler()
            fixture.hide_render = True
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
bpy.ops.wm.save_as_mainfile(filepath=str(output/("visual-fight.blend" if fight else "visual-ascension.blend" if ascension else "visual-reforge.blend" if reforge else "visual-revolution.blend")),compress=True)
