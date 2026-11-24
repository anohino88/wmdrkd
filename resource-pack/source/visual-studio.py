"""Produce an editable Blender scene and matching comparison renders from shipped model JSON."""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector, Quaternion

args = sys.argv[sys.argv.index("--")+1:]
project, baseline, output = (Path(arg).resolve() for arg in args[:3])
arcane = "--arcane" in args[3:]
fight = arcane or "--fight" in args[3:]
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
camera.ortho_scale = 20 if arcane else 14 if reforge else 17
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


def surface(part, material, collection, parent, name):
    xyz = list(zip(part["from"],part["to"]))
    for face in part["faces"]:
        axis,which,u,v = {
            "north":(2,0,0,1),"south":(2,1,0,1),
            "east":(0,1,2,1),"west":(0,0,2,1),
            "up":(1,1,0,2),"down":(1,0,0,2),
        }[face]
        verts = []
        for a,b in ((0,0),(1,0),(1,1),(0,1)):
            point = [0,0,0]
            point[axis],point[u],point[v] = xyz[axis][which],xyz[u][a],xyz[v][b]
            verts.append(coord(*point))
        mesh = bpy.data.meshes.new(name + " " + face)
        mesh.from_pydata(verts,[],[(0,1,2,3)])
        mesh.materials.append(material)
        mesh.uv_layers.new()
        for index,uv in enumerate(((0,0),(1,0),(1,1),(0,1))):
            mesh.uv_layers.active.data[index].uv = uv if axis != 1 else (uv[0],1-uv[1])
        obj = bpy.data.objects.new(mesh.name,mesh)
        collection.objects.link(obj)
        obj.parent = parent


def create(root,name,model):
    collection = bpy.data.collections.new(name)
    scene.collection.children.link(collection)
    pivot = bpy.data.objects.new(name+" · animation",None)
    collection.objects.link(pivot)
    texture = {name: material(root,image) for name,image in model["textures"].items()}
    for part in model["elements"]:
        shade = next(iter(part["faces"].values()))["texture"][1:]
        if len(part["faces"]) == 2 and shade == "surface":
            surface(part,texture[shade],collection,pivot,name)
            continue
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


def offset_at(frames,tick):
    before = frames[0]
    for after in frames[1:]:
        if tick <= after[0]:
            mix = (tick-before[0])/(after[0]-before[0])
            return tuple(a+(b-a)*mix for a,b in zip(before[1:4],after[1:4]))
        before = after
    return before[1:4]


def animate(pivot,frames,root=None):
    pivot.rotation_mode = "QUATERNION"
    for tick,right,up,forward,yaw,pitch,roll,width,height,depth in frames:
        dx,dy,dz = offset_at(root,tick) if root else (0,0,0)
        pivot.location = ((right+dx)*16,-(forward+dz)*16,(up+dy)*16)
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
parts = {}
if arcane:
    for line in (project/"src/main/resources/arcane-parts.tsv").read_text().splitlines():
        if line and not line.startswith("#"):
            name,_,_,_,track = line.split("|")
            parts.setdefault(name[4:-2],[]).append((name,[tuple(map(float,p.split(","))) for p in track.split(";")]))
active = list(keys)[:7]
if "--pacing" in args[3:]:
    # Run the same authored frames as Paper and keep a neutral 1.8-block target in the scene.
    fixture = fight_stage()
    camera.ortho_scale = 53
    overview = Vector((18,-25,23))
    scene.render.resolution_x = scene.render.resolution_y = 240
    scene.cycles.samples = 6
    near_views = (3,8,16,24)
    linked = active + ["bomb_impact","steel_heart","steel_king","calamity_train","thousand_swords"]
    if "--only" in args[3:]:
        linked = [args[args.index("--only") + 1]]
    old_keys = {}
    old_parts = {}
    for path, target in (("art-signatures-80.tsv",old_keys),("arcane-parts-80.tsv",old_parts)):
        for line in (project/"src/main/resources"/path).read_text().splitlines():
            if line and not line.startswith("#"):
                fields = line.split("|")
                frames = [tuple(map(float,f.split(","))) for f in fields[-1].split(";")]
                if target is old_keys:
                    target[fields[0]] = (fields[1],frames)
                else:
                    target.setdefault(fields[0][4:-2],[]).append((fields[0],frames))
    for label,tracks,rig in (("80",old_keys,old_parts),("81",keys,parts)):
        for key in linked:
            shape, track = tracks[key]
            def group(name, data, anchor=None):
                collection, handle = create(current,label+" · "+name,model(current,"fx/"+name))
                animate(handle,data,anchor)
                return collection
            groups = [group("sig_"+shape,track)]
            groups += [group(name,data,track) for name,data in rig.get(key,[])]
            eye.location = overview
            eye.rotation_euler = (Vector((0,0,7))-overview).to_track_quat("-Z","Y").to_euler()
            end = int(track[-1][0])
            for tick in sorted(set(range(0,end+1,2))|{end}):
                scene.frame_set(tick+1)
                render(f"pacing-{label}-{key}-{tick:03}")
            if label == "81":
                scene.frame_set(int(track[-3][0])+1)
                camera.type = "PERSP"
                camera.angle = math.radians(70)
                for distance in near_views:
                    eye.location = (0,-distance*16,26)
                    eye.rotation_euler = (Vector((0,0,10))-eye.location).to_track_quat("-Z","Y").to_euler()
                    render(f"distance-{key}-{distance}")
                camera.type = "ORTHO"
            for collection in groups:
                collection.hide_render = True
    eye.location = overview
    eye.rotation_euler = (Vector((0,0,7))-overview).to_track_quat("-Z","Y").to_euler()
    render("pacing-idle")
    sys.exit(0)

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
if "--stills" in args[3:]:
    sys.exit(0)
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
    layers = []
    for name,track in parts.get(key,[]):
        child,handle = create(current,name,model(current,"fx/"+name))
        animate(handle,track,frames)
        child.hide_render = False
        layers.append((child,handle))
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
    for child,handle in layers:
        child.hide_render = True
        for obj in list(child.objects):
            child.objects.unlink(obj)
            group.objects.link(obj)
        # Attach through a non-animated group transform so baked runtime offsets remain local.
        bpy.data.collections.remove(child)
    if pair is None:
        gallery.append((group,pivot))
for index,(group,pivot) in enumerate(gallery):
    group.hide_render = False
    for obj in group.objects:
        if obj.parent is None:
            obj.delta_location = ((index%5)*26,(index//5)*26,0)
    scene.timeline_markers.new(group.name,frame=1+index)
scene.frame_start,scene.frame_end = 1,24
scene.frame_set(7)
bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(output/("arcane-engine.blend" if arcane else "visual-fight.blend" if fight else "visual-ascension.blend" if ascension else "visual-reforge.blend" if reforge else "visual-revolution.blend")),compress=True)
