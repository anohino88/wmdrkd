#!/usr/bin/env python3
"""Bake local asset sources or verify and zip the checked-in Minecraft pack."""
import argparse
import hashlib
import io
import itertools
import json
import os
import re
import runpy
import subprocess
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "resource-pack/source"
PACK = ROOT / "resource-pack/pack"
NAMESPACE = Path("assets/augmentwild")
ASSETS = PACK / NAMESPACE
MANIFEST = ROOT / "ASSET_MANIFEST.json"
PALETTE = {
    "rune": "#e9cf83", "blade": "#8de4dc", "ward": "#76b8fa", "growth": "#92dc9e",
    "bolt": "#ebe2a3", "impact": "#f8b578", "fire": "#ff8768", "frost": "#acefff",
    "void": "#c69ef8", "blood": "#f4889f", "time": "#91dec6", "rush": "#bddcfb",
    "helix":"#b59eee", "fracture":"#91dbea", "crescent":"#e7b9fc",
    "supply": "#82d7bc", "premium": "#ffe19c",
}
SIGNATURES = runpy.run_path(str(SOURCE / "signature-models.py"))
FORMS = {"sig_" + key: color for key,color in SIGNATURES["PALETTES"].items()}
PALETTE.update(FORMS)
MENU_MARKS = runpy.run_path(str(SOURCE / "menu-marks.py"))
SYNTH = runpy.run_path(str(SOURCE / "sound-design.py"))
SOURCE_SOUNDS = {"cast", "evolve", "supply"}
SOUNDS = SOURCE_SOUNDS | SYNTH["CUES"].keys()
PACK_DIGEST = ROOT / "src/main/resources/art-pack.sha1"
RELEASE_METADATA = {"release":"7.6","minecraft":"26.3","inventory_title":"native"}
PACK_METADATA = {"pack": {"description": "AugmentWild • combat art for Java 26.3",
                          "min_format": [97, 1], "max_format": [97, 1]}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",",":")) + "\n", encoding="utf-8")


def skill_font():
    skills = ("bomb_alchemy","calamity_giant","duel_contract","earthquake_mage","shadow_dash","time_rewind","void_lord")
    return {"providers":[{"type":"bitmap","file":f"augmentwild:item/augment/{skill}.png","height":8,
                          "ascent":7,"chars":[chr(0xe100 + i)]} for i,skill in enumerate(skills)]}


def inputs():
    resources = ROOT / "src/main/resources"
    official = (resources / "official-augments.yml").read_text().split("\nsettings:")[0]
    rarities = dict(re.findall(r"^  (\w+):\n    group: \w+\n    rarity: (\w+)", official, re.M))
    catalog = ROOT / "src/main/java/io/github/augmentwild/paper/AugmentCatalog.java"
    rarities.update(re.findall(r'add\(registry, config, "(\w+)", AugmentRarity\.(\w+)', catalog.read_text()))
    assert len(rarities) == 84, "Expected 84 augment IDs"
    motifs = dict((fields[0], fields[2]) for line in (SOURCE / "art.tsv").read_text().splitlines()
                  if line and not line.startswith("#") for fields in [line.split("|")])
    assert len(rarities) + 19 == len(motifs) and set(rarities) <= motifs.keys(), "Artwork coverage differs from the catalog"
    sources = json.loads(MANIFEST.read_text())["external_assets"]
    outputs = set()
    assert {a["used_by"] for a in sources} == motifs.keys() | SOURCE_SOUNDS, "Missing source provenance"
    for asset in sources:
        path = (ROOT / asset["path"]).resolve()
        assert path.is_relative_to(SOURCE)
        assert asset["modification_allowed"] and asset["redistribution_allowed"]
        assert asset["license"] in ("CC-BY-3.0", "CC0-1.0")
        raw = path.read_bytes()
        assert hashlib.sha256(raw).hexdigest() == asset["sha256"], path
        if path.suffix == ".svg":
            assert b"<!" not in raw and b"<?" not in raw
            svg = ElementTree.fromstring(raw)
            ns = "{http://www.w3.org/2000/svg}"
            assert svg.tag == ns + "svg" and svg.attrib == {"viewBox":"0 0 512 512"}, path
            assert len(svg) == 2 and all(node.tag == ns + "path" and not len(node)
                                         and set(node.attrib) <= {"d","fill"} for node in svg), path
        else:
            assert path.suffix == ".ogg" and raw.startswith(b"OggS"), path
        assert asset["output"] not in outputs
        outputs.add(asset["output"])
    return rarities, motifs, sources


def item(key, model):
    write_json(ASSETS / f"items/{key}.json", {"model":{"type":"minecraft:model","model":f"augmentwild:{model}"}})


def badge(path, original, rarity, motif, profile):
    from PIL import Image, ImageDraw, ImageChops, ImageColor
    # Render only the upstream mask. Input comes from the pinned, hashed source tree.
    render = subprocess.run(["inkscape", str(original), "--export-width=80", "--export-type=png",
                             "--export-filename=-"], capture_output=True, check=True,
                            env={**os.environ, "INKSCAPE_PROFILE_DIR": profile}).stdout
    art = Image.open(io.BytesIO(render)).convert("RGBA")
    silhouette = ImageChops.multiply(art.convert("L"), art.getchannel("A"))
    silhouette = silhouette.crop(silhouette.getbbox())
    silhouette.thumbnail((43,43), Image.Resampling.LANCZOS)
    im = Image.new("RGBA",(64,64))
    draw = ImageDraw.Draw(im)
    rim = {"SILVER":"#a6b9cc","GOLD":"#f1bd6f","PRISMATIC":"#dcb1ff","GUI":"#77d8c5"}[rarity]
    shapes = {
        "SILVER": [(12,5),(52,5),(59,12),(59,52),(52,59),(12,59),(5,52),(5,12)],
        "GOLD": [(11,3),(53,3),(59,14),(57,46),(48,57),(32,62),(16,57),(7,46),(5,14)],
        "PRISMATIC": [(32,1),(52,10),(62,30),(56,51),(32,63),(8,51),(2,30),(12,10)],
        "GUI": [(14,4),(50,4),(60,14),(60,50),(50,60),(14,60),(4,50),(4,14)],
    }
    dark = {"SILVER":"#101e2b","GOLD":"#201820","PRISMATIC":"#1b152d","GUI":"#101b27"}[rarity]
    draw.polygon(shapes[rarity],fill=dark,outline=rim,width=2)
    for i in range(0,len(shapes[rarity]),2):
        a,b = shapes[rarity][i-1],shapes[rarity][i]
        draw.line((a,b),fill="#f8edcf" if rarity == "GOLD" else "#d9e7f7",width=1)
    if rarity == "PRISMATIC":
        for x,y in ((22,15),(44,20),(16,41),(46,44)):
            draw.line((x,y,32,32),fill="#614a87",width=1)
    if rarity == "GOLD":
        draw.line((15,8,49,8,53,13),fill="#e39853",width=2)
    draw.polygon([(32,8),(48,14),(54,31),(48,49),(32,55),(16,49),(10,31),(16,14)],
                 fill="#1c273a")
    draw.polygon([(13,30),(20,16),(32,10),(31,22),(19,34)],fill="#2b3a50")
    draw.line([(14,43),(22,51),(32,55),(42,51),(50,43)],fill="#344860",width=2)
    if rarity == "PRISMATIC":
        draw.line([(8,29),(15,13),(31,5),(48,13),(56,28)],fill="#f6e8fe",width=2)
    rgb = ImageColor.getrgb(PALETTE[motif])
    art = Image.new("RGBA",silhouette.size,rgb)
    art.putalpha(silhouette)
    im.alpha_composite(art,((64-art.width)//2,(62-art.height)//2))
    tier = {"SILVER":1,"GOLD":2,"PRISMATIC":3,"GUI":0}[rarity]
    for i in range(tier):
        x = 32 + (2*i-tier+1)*4
        draw.polygon([(x,52),(x+2,55),(x,58),(x-2,55)],fill=rim)
    path.parent.mkdir(parents=True,exist_ok=True)
    im.save(path,optimize=True)


def bake(rarities, motifs, sources):
    from PIL import Image, ImageColor
    write_json(PACK / "pack.mcmeta", PACK_METADATA)
    write_json(PACK / "augmentwild-pack.json", RELEASE_METADATA)
    with tempfile.TemporaryDirectory() as profile:
        for asset in sources:
            output = PACK / asset["output"]
            source = ROOT / asset["path"]
            key = asset["used_by"]
            if key in SOURCE_SOUNDS:
                output.parent.mkdir(parents=True,exist_ok=True)
                subprocess.run(["ffmpeg","-v","error","-y","-i",str(source),"-map_metadata","-1","-ac","1",
                                "-af","volume=0.5,afade=t=out:st=0.18:d=0.13","-t","0.32","-c:a","libvorbis",
                                "-fflags","+bitexact","-flags:a","+bitexact",str(output)],check=True)
            else:
                badge(output,source,rarities.get(key,"GUI"),motifs[key],profile)
                model = output.relative_to(ASSETS / "textures").with_suffix("").as_posix()
                write_json(ASSETS / f"models/{model}.json",
                           {"parent":"minecraft:item/generated","textures":{"layer0":f"augmentwild:{model}"}})
                item(model.removeprefix("item/"),model)
    with tempfile.TemporaryDirectory() as audio:
        SYNTH["bake"](ASSETS/"sounds",Path(audio)/"cue.wav")
    for mark in MENU_MARKS["marks"]():
        key = f"gui/{mark}"
        MENU_MARKS["icon"](mark).save(ASSETS/f"textures/item/{key}.png",optimize=True)
        write_json(ASSETS/f"models/item/{key}.json",
                   {"parent":"minecraft:item/generated","textures":{"layer0":f"augmentwild:item/{key}"}})
        item(key,f"item/{key}")
    write_json(ASSETS / "sounds.json", {key: {"sounds":[f"augmentwild:{key}"]} for key in sorted(SOUNDS)})
    subprocess.run(["blender","-b","-t","2","--factory-startup","--python-exit-code","1","--python",
                    str(SOURCE / "geometry.py"),"--",str(PACK)],check=True)
    for motif, color in PALETTE.items():
        item(f"fx/{motif}",f"fx/{motif}")
        rgb = ImageColor.getrgb(color)
        for shade, factor in {"dark":.23,"metal":.59,"light":1,"accent":.82}.items():
            path = ASSETS / f"textures/fx/{motif}/{shade}.png"
            path.parent.mkdir(parents=True,exist_ok=True)
            im = Image.new("RGB",(16,16))
            for y in range(16):
                for x in range(16):
                    edge = min(x,y,15-x,15-y)
                    bevel = 1 if edge >= 2 else (1.24 if x+y < 15 else .69)
                    grain = (x*11 + y*7) % 13 / 13
                    tone = factor * bevel * (.76 + .17*(x+y)/30 + .07*grain)
                    im.putpixel((x,y),tuple(min(255,round(p*tone)) for p in rgb))
            im.save(path,optimize=True)
    from PIL import ImageDraw
    for filled in range(5):
        key = f"gui/gauge_{filled}"
        tile = Image.open(ASSETS / "textures/item/gui/gauge.png").convert("RGBA")
        draw = ImageDraw.Draw(tile)
        for segment in range(4):
            x = 10 + segment * 11
            draw.rectangle((x,49,x+8,55), fill="#ade8d7" if segment < filled else "#404e60")
        tile.save(ASSETS / f"textures/item/{key}.png",optimize=True)
        write_json(ASSETS / f"models/item/{key}.json",
                   {"parent":"minecraft:item/generated","textures":{"layer0":f"augmentwild:item/{key}"}})
        item(key,f"item/{key}")
    for key in rarities:
        motif = motifs[key]
        model = json.loads((ASSETS / f"models/fx/{f'sig_{key}' if key in SIGNATURES['ACTIVE'] else motif}.json").read_text())
        model["textures"]["sigil"] = f"augmentwild:item/augment/{key}"
        front = min(part["from"][2] for part in model["elements"]) - .25
        model["elements"].append({"from":[5,5,front],"to":[11,11,front+.05],
            "faces":{side:{"texture":"#sigil","uv":[0,0,16,16]} for side in ("north","south")}})
        write_json(ASSETS / f"models/proc/{key}.json",model)
        item(f"proc/{key}",f"proc/{key}")
    import runpy
    runpy.run_path(str(SOURCE / "panels.py"))["bake"](PACK)
    (PACK / "pack.png").write_bytes((ASSETS / "textures/item/gui/menu.png").read_bytes())
    (ROOT / "src/main/resources/art-motifs.properties").write_text(
        "".join(f"{key}={value}\n" for key,value in sorted(motifs.items()) if key in rarities))


def verify(rarities, motifs, sources):
    assert json.loads((PACK / "pack.mcmeta").read_text()) == PACK_METADATA
    assert json.loads((PACK / "augmentwild-pack.json").read_text()) == RELEASE_METADATA
    keys = {f"augment/{key}" if key in rarities else key for key in motifs}
    keys |= {f"fx/{key}" for key in PALETTE} | {f"proc/{key}" for key in rarities}
    keys |= {f"gui/gauge_{filled}" for filled in range(5)} | {f"gui/{key}" for key in MENU_MARKS["marks"]()}
    models = {p.relative_to(ASSETS / "items").with_suffix("").as_posix() for p in (ASSETS / "items").rglob("*.json")}
    assert models == keys, models ^ keys
    for key in keys:
        definition = json.loads((ASSETS / f"items/{key}.json").read_text())["model"]
        assert definition["type"] == "minecraft:model"
        model = json.loads((ASSETS / "models" / (definition["model"].removeprefix("augmentwild:")+".json")).read_text())
        for texture in model["textures"].values():
            assert (ASSETS / f"textures/{texture.removeprefix('augmentwild:')}.png").read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
        if key.startswith("proc/"):
            augment = key.split("/")[1]
            shape = f"sig_{augment}" if augment in SIGNATURES["ACTIVE"] else motifs[augment]
            family = json.loads((ASSETS / f"models/fx/{shape}.json").read_text())
            assert model["elements"][:-1] == family["elements"]
            assert model["textures"]["sigil"] == f"augmentwild:item/augment/{augment}"
            glyph = model["elements"][-1]
            assert 0 <= glyph["from"][2] < glyph["to"][2] < min(e["from"][2] for e in family["elements"])
        if key.startswith("fx"):
            assert 1 <= len(model["elements"]) <= 32
            for part in model["elements"]:
                assert all(0 <= a < b <= 16 for a,b in zip(part["from"],part["to"])), (key,part)
                assert set(part["faces"]) == {"north","south","east","west","up","down"}
                assert all(face["texture"][1:] in model["textures"] for face in part["faces"].values())
                if turn := part.get("rotation"):
                    assert turn["axis"] in ("x","y","z") and turn["angle"] in (-45,-22.5,22.5,45)
                    assert set(turn) == {"axis","angle","origin"}
                    assert all(0 < position < 16 for position in turn["origin"])
            # Overlapping outward faces on the same plane flicker under camera rotation.
            for a,b in itertools.combinations(model["elements"],2):
                if a.get("rotation") or b.get("rotation"):
                    continue
                for axis in range(3):
                    overlap = all(min(a["to"][i],b["to"][i]) > max(a["from"][i],b["from"][i])
                                  for i in range(3) if i != axis)
                    if overlap:
                        assert all(a[side][axis] != b[side][axis] for side in ("from","to")), (key,a,b)
            assert len({tuple(part["from"] + part["to"]) for part in model["elements"]}) == len(model["elements"])
    tracks = {}
    for row in (ROOT/"src/main/resources/art-signatures.tsv").read_text().splitlines():
        if not row or row.startswith("#"):
            continue
        key,shape,sound,frames = row.split("|")
        offsets = [tuple(map(float,frame.split(","))) for frame in frames.split(";")]
        assert key not in tracks and f"fx/sig_{shape}" in keys and sound in SOUNDS
        assert len(offsets) >= 4 and all(len(frame) == 10 for frame in offsets)
        assert offsets[0][0] == 0 and offsets[-1][0] <= 30
        assert all(a[0] < b[0] for a,b in itertools.pairwise(offsets))
        tracks[key] = offsets
    active = [tracks[key] for key in SIGNATURES["ACTIVE"]]
    assert len({tuple(track) for track in active}) == 7
    assert len({hashlib.sha256((ASSETS/f"models/fx/sig_{key}.json").read_bytes()).hexdigest()
                for key in SIGNATURES["ACTIVE"]}) == 7
    screens = json.loads((SOURCE / "screens.json").read_text())
    samples = dict(row.split("=",1) for row in (ROOT / "src/main/resources/art-screen-samples.properties").read_text().splitlines())
    assert samples.keys() == screens.keys()
    for screen, controls in samples.items():
        pairs = [pair.split("=") for pair in controls.split()]
        assert len({slot for slot,key in pairs}) == len(pairs)
        assert all(0 <= int(slot) < screens[screen][0]*9 and key in keys for slot,key in pairs)
    assert len(screens) == 12 and not (ASSETS / "font/interface.json").exists()
    for screen,(rows,_,slots) in screens.items():
        assert all(0 <= slot < rows*9 for slot in slots)
        data = (ASSETS / f"textures/gui/panels/{screen}.png").read_bytes()
        assert (int.from_bytes(data[16:20],"big"),int.from_bytes(data[20:24],"big")) == (176,18*rows+18)
    for provider in skill_font()["providers"]:
        assert 0 <= provider["ascent"] <= provider["height"] <= 8
        path = ASSETS / "textures" / provider["file"].removeprefix("augmentwild:")
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"), path
    assert len({hashlib.sha256((ASSETS / f"textures/item/augment/{key}.png").read_bytes()).digest()
                for key in rarities}) == 84, "Icons must be distinct"
    assert json.loads((ASSETS / "sounds.json").read_text()) == {key: {"sounds":[f"augmentwild:{key}"]} for key in SOUNDS}
    for asset in sources:
        assert (PACK / asset["output"]).is_file(), asset["output"]
    for key in SOUNDS:
        assert (ASSETS / f"sounds/{key}.ogg").read_bytes().startswith(b"OggS")
    assert (ROOT / "src/main/resources/art-motifs.properties").read_text().splitlines() == [
        f"{key}={motifs[key]}" for key in sorted(rarities)]
    assert set(p.name for p in (PACK / "assets").iterdir()) == {"augmentwild"}


def package(output):
    output.parent.mkdir(parents=True, exist_ok=True)
    files = [(path.relative_to(PACK).as_posix(), path) for path in PACK.rglob("*") if path.is_file()]
    files += [(name, ROOT / name) for name in ("ASSET_MANIFEST.json","THIRD_PARTY_NOTICES.md")]
    files += [(f"resource-pack/source/{name}", SOURCE / name) for name in ("game-icons-license.txt","kenney-license.txt")]
    with zipfile.ZipFile(output,"w") as archive:
        for name,path in sorted(files):
            info = zipfile.ZipInfo(name)
            info.external_attr = 0o644 << 16
            archive.writestr(info,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
    print(f"{output} | SHA-1 {hashlib.sha1(output.read_bytes()).hexdigest()} | {output.stat().st_size} bytes")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bake",action="store_true",help="Rebuild baked files with Blender, Inkscape, Pillow and FFmpeg")
    parser.add_argument("--output",type=Path,default=ROOT / "build/resource-pack/augmentwild-26.3.zip")
    args = parser.parse_args()
    data = inputs()
    if args.bake:
        bake(*data)
    font = ASSETS / "font/skills.json"
    if args.bake:
        write_json(font, skill_font())
    assert json.loads(font.read_text()) == skill_font()
    verify(*data)
    package(args.output)
    digest = hashlib.sha1(args.output.read_bytes()).hexdigest()
    if args.bake:
        PACK_DIGEST.write_text(digest+"\n",encoding="utf-8")
    assert PACK_DIGEST.read_text().strip() == digest, "JAR expected pack SHA-1 differs from checked pack ZIP"


if __name__ == "__main__":
    main()
