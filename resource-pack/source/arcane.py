"""Original cutout surfaces and runic metalwork for Java item models."""
from pathlib import Path
import json
import math
import subprocess

# The contours are designed in a 128-unit drawing space, independently of the Minecraft cuboids.
# Keep silhouettes readable against both the Nether and a snow biome.
CUT = {
    "edge": '<path d="M4 112C56 106 97 63 122 4C122 71 77 109 38 122L10 125 29 117Z"/><path d="M13 98C57 86 78 66 92 31C82 76 60 92 13 98Z" class="wire"/>',
    "riven": '<path d="M5 94 38 63 43 37 64 48 82 20 96 42 123 22 110 60 69 72 58 94 35 90 12 109 2 102Z"/><path d="M4 103 41 75 53 78 67 56 98 53 122 24M66 56 63 42M52 78 49 100 26 119M96 53 113 73" class="wire"/>',
    "slab": '<path d="M18 103 28 58 65 8 102 37 109 86 66 120Z"/><path d="M28 58 67 50 65 8 102 37 67 50 66 120 109 86" class="wire"/>',
    "escapement": '<path fill-rule="evenodd" d="M52 4H76L81 17 94 22 109 18 118 29 111 44 117 56 124 58V73L114 80 109 92 111 107 96 118 81 111 74 117 72 125H55L49 115 36 111 22 116 11 104 17 89 10 73 3 71V54L15 48 19 35 14 23 28 12 41 18 50 16Z M99 64A35 35 0 1 0 29 64A35 35 0 0 0 99 64Z"/><circle cx="64" cy="64" r="43" class="wire"/>',
    "hands": '<path d="M64 5 71 21 68 59 104 80 110 94 91 93 62 71 48 80 57 63 58 24Z"/><circle cx="64" cy="64" r="10" class="wire"/>',
    "singularity": '<path d="M64 2 100 27 121 67 93 108 64 126 25 101 7 64 30 27Z"/><path d="M64 2 85 51 121 67 85 79 64 126 42 80 7 64 42 47Z" class="wire"/><path d="M51 50 74 48 84 65 73 81 51 80 43 63Z" fill="#110c22"/>',
    "orbit": '<path d="M3 78C32 10 100 13 125 47 94 18 47 38 16 78 43 101 102 88 123 55 116 102 34 124 3 78Z"/><path d="M14 76 35 65 33 84ZM105 41 122 43 114 55Z" class="wire"/>',
    "vessel": '<path d="M45 11H81L89 23 104 29 119 54 113 84 92 112 64 124 32 111 12 88 8 57 24 33 39 26Z"/><path d="M23 61 38 41 91 40 107 59 102 80 82 101H45L28 85Z" fill="#2d0b22" stroke="#f8b16f" stroke-width="4"/><path d="M44 18H82M29 94 48 106H79L99 93M43 54 57 68 69 49 82 74 96 56" class="wire"/>',
    "flame": '<path d="M65 3C93 27 108 62 102 88L120 73C116 108 91 123 67 125 19 128 9 95 19 72L32 89C23 55 59 34 65 3Z"/><path d="M63 50C85 70 92 97 73 113 48 124 35 102 48 81L57 92Z" fill="#fff1b2"/>',
    "seal": '<path fill-rule="evenodd" d="M64 3 105 22 124 64 108 104 64 125 20 104 4 64 19 24Z M64 21 34 35 21 64 35 92 64 107 92 91 107 64 92 35Z"/><path d="M37 38V68L64 89 91 68V38L64 54Z M37 68 91 68 M64 54V89" class="wire"/>',
    "gauntlet": '<path d="M15 64 11 38 17 25 29 23 33 8 49 3 62 10 75 4 88 14 101 14 112 25 113 54 101 76 97 110 79 125 44 125 25 106 24 84Z"/><path d="M16 36 34 41 49 30 64 37 77 28 97 34 112 27M32 51 49 63 78 57 99 44M24 84 50 94 76 91 101 76M48 105 76 105 64 118Z" class="wire"/>',
    "cuff": '<path fill-rule="evenodd" d="M17 27 35 10 92 11 111 27 114 91 91 116 34 115 13 92Z M39 44 36 72 49 90 80 90 92 71 89 44 77 34 50 34Z"/><path d="M18 51 35 32M19 79 36 95M109 51 91 32M108 79 90 95" class="wire"/>',
    "shock": '<path fill-rule="evenodd" d="M3 64 14 45 12 34 29 35 36 14 51 22 65 4 77 20 98 17 99 33 118 42 111 53 125 67 113 81 117 92 101 95 94 115 79 108 64 124 49 109 30 114 26 98 11 93 17 78Z M22 65 37 86 62 99 92 87 106 65 94 43 65 29 37 41Z"/>',
    "shield": '<path fill-rule="evenodd" d="M13 15 64 4 115 15 111 63 96 94 64 125 32 94 17 62Z M30 28 33 60 48 86 64 101 82 86 95 60 98 28 64 20Z"/><path d="M64 26 91 53 64 87 37 53Z M37 53H91M64 26V87" class="wire"/>',
    "heart": '<path d="M64 27C43-3 5 10 7 49 9 74 45 106 64 122 83 106 119 74 121 49 123 10 85-3 64 27Z"/><path d="M12 59H37L47 40 61 80 73 54 84 62H115" class="wire"/>',
    "locomotive": '<path d="M4 92 16 77 22 57 34 55 34 39 48 39 51 53 78 53 78 26 113 26 120 39 120 100 4 100Z"/><path d="M25 73H78V60H44M86 37H108V56H86Z M6 92H120" class="wire"/>',
    "blade": '<path d="M61 3 76 18 74 78 93 82 92 91 71 90 72 111 80 119 65 127 49 119 57 111 58 90 36 92 35 83 55 78 55 18Z"/><path d="M62 12 64 79M61 96 69 103 61 108 69 115" class="wire"/>',
    "turret": '<path d="M4 86 14 70 36 70 42 46 58 38 78 44 85 50 121 46 125 56 86 65 94 72 109 75 123 89 117 110H13Z"/><path d="M18 98H110M22 84H108M45 70 53 54 73 53 81 61H106" class="wire"/>',
    "crown": '<path d="M10 32 34 54 37 21 55 52 64 7 74 51 91 20 95 54 119 32 102 94 64 117 26 94Z"/><path d="M20 71 64 88 107 72M64 44 77 64 64 76 51 65Z" class="wire"/>',
    "stack": '<path d="M7 91 64 65 121 91 64 119Z M15 65 64 41 113 65 64 89Z M26 42 64 22 102 42 64 62Z M47 20 64 4 81 20 64 34Z"/><path d="M9 92 64 118 119 92M18 66 64 87 110 66" class="wire"/>',
    "conduit": '<path d="M6 107 31 22 44 12 50 37 37 47 26 99 56 89 56 42 50 29 64 2 79 29 72 42 72 89 101 99 91 47 78 38 84 12 97 22 122 107 64 124Z"/><path d="M26 99 64 110 101 99M41 56 63 78 87 55M64 11V96" class="wire"/>',
    "bolt": '<path d="M5 88 21 67 54 49 80 22 123 17 103 53 78 68 66 90 31 111 37 88Z"/><path d="M24 86 99 35 109 29M44 58 50 70 41 93M76 36 82 55 71 79" class="wire"/>',
}

# Core drawing, alloy at the edge, body shadow, mount. XY planes face a player,
# XZ applies floor effects, crossed planes give angular silhouettes from side views.
CORES = {
    "shadow_dash": ("edge","#e4aeff","#45146b","floor"),
    "earthquake_mage": ("riven","#ffba69","#351b20","floor"),
    "time_rewind": ("escapement","#e9cc85","#185d61","floor"),
    "void_lord": ("singularity","#e4a4ff","#2b0c58","cross"),
    "bomb_alchemy": ("vessel","#ffb77e","#542438","cross"),
    "duel_contract": ("seal","#f8dd83","#4a2c22","floor"),
    "calamity_giant": ("gauntlet","#eeb28f","#442735","cross"),
    "steel_heart": ("heart","#bff1ff","#133948","cross"),
    "magic_missile": ("bolt","#95e5ff","#163e64","cross"),
    "berserk_engine": ("escapement","#ffa77b","#651d27","cross"),
    "infernal_conductor": ("conduit","#ffcd7d","#871a23","cross"),
    "tank_engine": ("turret","#acc9d8","#24394b","face"),
    "sword_wind": ("blade","#a8ffe9","#22596a","cross"),
    "steel_king": ("crown","#d6fcff","#1c435b","cross"),
    "calamity_train": ("locomotive","#ffdb96","#643636","face"),
    "infinite_barrage": ("bolt","#d9eeff","#274372","cross"),
    "hell_lord": ("conduit","#ffe097","#481126","cross"),
    "thousand_swords": ("blade","#fff2c8","#494478","cross"),
    "life_echo": ("heart","#b6ffe5","#3c315c","cross"),
    "stacking": ("stack","#e5f7be","#254c43","cross"),
}
PUBLIC = {
    "crescent": ("edge","#fffafa","#41638b","floor"),
    "impact": ("shock","#ffdda9","#8a4025","floor"),
    "fracture": ("shield","#c7f5ff","#286bb4","face"),
}
# The slot controls borrow the facets and reflective edge from the combat alloys.
GUI = {
    "bezel": ("shield","#8faab5","#18243c","face"),
    "rail_left": ("blade","#d1ae6a","#322950","face"),
    "rail_right": ("bolt","#b9e4d4","#193344","face"),
    "atlas": ("seal","#f5d593","#503352","face"),
}

# Load-bearing volumes have a separate finish, with the cutout artwork used for inscriptions
# and tapered plates on their outer plane. Coordinates are Minecraft item-model units.
SOLIDS = {
    "sig_calamity_giant": (
        ((4.5,2.5,6),(11.7,8.0,10.3),"dark"),
        ((3.8,7.8,4.6),(12.4,10.8,11.3),"metal"),
        ((3.0,10.7,5.3),(5.2,12.9,10.4),"light"),
        ((5.3,10.6,4.8),(7.3,13.3,10.8),"metal"),
        ((7.4,10.5,4.9),(9.5,13.7,11.0),"light"),
        ((9.6,10.4,5.2),(11.6,13.0,10.5),"metal"),
        ((2.6,7.6,5.5),(4.4,10.6,10.9),"accent"),
        ((4.2,2.8,5.6),(11.9,4.2,10.9),"light"),
    ),
    "sig_bomb_alchemy": (
        ((4.4,5,4.4),(11.6,10.2,11.6),"dark"),
        ((3.65,6.45,6),(12.35,9.45,10),"metal"),
        ((6,6.68,3.65),(10,9.22,12.35),"metal"),
        ((5.5,4,5.5),(10.5,5.4,10.5),"accent"),
        ((5.3,9.74,5.3),(10.7,11.43,10.7),"light"),
        ((6.6,10.9,6.6),(9.4,12.7,9.4),"dark"),
    ),
    "sig_calamity_train": (
        ((1.6,4.3,4.7),(14.1,7.0,11.05),"dark"),
        ((3.9,7.02,4.9),(10.9,9.7,11.1),"metal"),
        ((4.75,9.71,5.5),(10.5,10.5,10.5),"light"),
        ((10.6,7.1,5.2),(13.6,12.6,10.8),"dark"),
        ((11.1,12.61,4.9),(14.2,13.4,11.13),"accent"),
        ((2.7,9.21,6.8),(4.7,11.7,9.2),"metal"),
    ),
    "sig_void_lord": (
        ((6.5,2.7,6.5),(9.5,5.2,9.5),"accent"),
        ((5.25,4.7,5.25),(10.75,10.96,10.75),"dark"),
        ((4.7,6.3,5.9),(11.3,9.85,10.1),"metal"),
        ((6,10.7,6),(10,12.3,10),"accent"),
        ((6.7,12.0,6.7),(9.3,13.5,9.3),"light"),
    ),
    "sig_tank_engine": (
        ((2.3,4.0,3.8),(13.7,6.14,12.2),"dark"),
        ((3.4,6.15,4.1),(12.6,8.42,11.9),"metal"),
        ((5.0,8.45,5.4),(10.4,10.8,10.6),"accent"),
        ((9.8,9,7),(14.6,10.4,9.3),"light"),
    ),
}
FACES = ("north","south","east","west","up","down")


def solid(low, high, shade):
    return {"from":low,"to":high,"faces":{side:{"texture":"#" + shade} for side in FACES}}


def drawing(shape, edge, dark, variant=0):
    content = CUT[shape]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
<defs>
 <linearGradient id="body" x2="{.25 + .15 * variant}" y2="1" gradientUnits="objectBoundingBox">
  <stop stop-color="{edge}"/><stop offset=".25" stop-color="{dark}"/><stop offset=".41" stop-color="{edge}"/>
  <stop offset=".48" stop-color="{dark}"/><stop offset=".76" stop-color="#11152a"/><stop offset=".91" stop-color="{dark}"/>
  <stop offset="1" stop-color="{edge}"/>
 </linearGradient>
 <linearGradient id="rim" x2="1" y2=".87"><stop stop-color="#fff3d8"/><stop offset=".54" stop-color="{edge}"/><stop offset="1" stop-color="{dark}"/></linearGradient>
</defs>
<style>.wire{{fill:none;stroke:url(#rim);stroke-width:2.6;stroke-linejoin:round;stroke-linecap:round}}</style>
<g fill="url(#body)" stroke="{dark}" stroke-width="2.3" stroke-linejoin="round">{content}</g>
</svg>"""


def plane(axis, inset=0):
    low, high = [.55,.55,.55], [15.45,15.45,15.45]
    index, sides = {"z": (2, ("north","south")), "x": (0, ("east","west")), "y": (1, ("up","down"))}[axis]
    low[index], high[index] = 7.94 + inset, 8.06 + inset
    return {"from":low,"to":high,"shade":False,"faces":{side:{"texture":"#surface","uv":[0,0,16,16]} for side in sides}}


def components(project):
    """Use the same track IDs and art shapes as the Paper loader."""
    out={}
    for line in (project/"src/main/resources/arcane-parts.tsv").read_text().splitlines():
        if line and not line.startswith("#"):
            model, root, artwork, mount, _ = line.split("|")
            _, edge, dark, _ = CORES[root]
            out[model] = (artwork,edge,dark,mount)
    return out


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data,separators=(",",":"))+"\n")


def bake(project, assets):
    work = project / "build/arcane-svg"
    work.mkdir(parents=True,exist_ok=True)
    models = {f"fx/sig_{k}": v for k,v in CORES.items()}
    models.update({f"fx/{k}":v for k,v in (PUBLIC | components(project)).items()})
    models.update({f"gui/{k}":v for k,v in GUI.items()})
    for key,(shape,edge,dark,mount) in models.items():
        svg = work / (key.replace("/","-") + ".svg")
        svg.write_text(drawing(shape,edge,dark))
        texture = f"item/{key}" if key.startswith("gui/") else key
        path = assets / f"textures/{texture}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["inkscape",str(svg),f"--export-filename={path}"],check=True,capture_output=True)
        reference = f"augmentwild:{texture}"
        if key.startswith("gui/"):
            write(assets / f"models/{texture}.json",{"parent":"minecraft:item/generated","textures":{"layer0":reference}})
        else:
            axes = {"floor": ("y",), "face": ("z",), "cross": ("z","x")}[mount]
            kind = key.rsplit("/",1)[1]
            volumes = SOLIDS.get(kind,())
            textures = {"surface":reference}
            if volumes:
                textures.update({shade:f"augmentwild:fx/{kind}/{shade}" for _,_,shade in volumes})
            surfaces = [plane(axis,-4.72 if volumes else 0) for axis in axes]
            write(assets / f"models/{key}.json",{"ambientocclusion":False,"gui_light":"front",
                  "textures":textures,"elements":[solid(*spec) for spec in volumes] + surfaces})
        write(assets / f"items/{key}.json",{"model":{"type":"minecraft:model","model":f"augmentwild:{texture}"}})
