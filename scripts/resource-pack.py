#!/usr/bin/env python3
"""Bake local asset sources or verify and zip the checked-in Minecraft pack."""
import argparse
import hashlib
import io
import itertools
import json
import os
import re
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
SOUNDS = {"cast", "evolve", "supply"}
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
    assert {a["used_by"] for a in sources} == motifs.keys() | SOUNDS, "Missing source provenance"
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
    draw.polygon([(17,3),(47,3),(61,17),(61,47),(47,61),(17,61),(3,47),(3,17)],fill="#121827",outline=rim,width=2)
    draw.line([(13,45),(13,19),(20,12),(44,12),(51,19),(51,45)],fill="#303d55",width=2)
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
    with tempfile.TemporaryDirectory() as profile:
        for asset in sources:
            output = PACK / asset["output"]
            source = ROOT / asset["path"]
            key = asset["used_by"]
            if key in SOUNDS:
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
    write_json(ASSETS / "sounds.json", {key: {"sounds":[f"augmentwild:{key}"]} for key in sorted(SOUNDS)})
    subprocess.run(["blender","-b","-t","2","--factory-startup","--python-exit-code","1","--python",
                    str(SOURCE / "geometry.py"),"--",str(PACK)],check=True)
    for motif, color in PALETTE.items():
        item(f"fx/{motif}",f"fx/{motif}")
        rgb = ImageColor.getrgb(color)
        for shade, factor in {"dark":.23,"metal":.59,"light":1,"accent":.82}.items():
            path = ASSETS / f"textures/fx/{motif}/{shade}.png"
            path.parent.mkdir(parents=True,exist_ok=True)
            im = Image.new("RGB",(8,8))
            for y in range(8):
                for x in range(8):
                    tone = factor * (.87 + .13*(x+y)/14)
                    im.putpixel((x,y),tuple(round(p*tone) for p in rgb))
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
        model = json.loads((ASSETS / f"models/fx/{motif}.json").read_text())
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
    keys = {f"augment/{key}" if key in rarities else key for key in motifs}
    keys |= {f"fx/{key}" for key in PALETTE} | {f"proc/{key}" for key in rarities}
    keys |= {f"gui/gauge_{filled}" for filled in range(5)}
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
            family = json.loads((ASSETS / f"models/fx/{motifs[augment]}.json").read_text())
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
            # Overlapping outward faces on the same plane flicker under camera rotation.
            for a,b in itertools.combinations(model["elements"],2):
                for axis in range(3):
                    overlap = all(min(a["to"][i],b["to"][i]) > max(a["from"][i],b["from"][i])
                                  for i in range(3) if i != axis)
                    if overlap:
                        assert all(a[side][axis] != b[side][axis] for side in ("from","to")), (key,a,b)
            assert len({tuple(part["from"] + part["to"]) for part in model["elements"]}) == len(model["elements"])
    screens = json.loads((SOURCE / "screens.json").read_text())
    samples = dict(row.split("=",1) for row in (ROOT / "src/main/resources/art-screen-samples.properties").read_text().splitlines())
    assert samples.keys() == screens.keys()
    for screen, controls in samples.items():
        pairs = [pair.split("=") for pair in controls.split()]
        assert len({slot for slot,key in pairs}) == len(pairs)
        assert all(0 <= int(slot) < screens[screen][0]*9 and key in keys for slot,key in pairs)
    providers = json.loads((ASSETS / "font/interface.json").read_text())["providers"]
    assert len(screens) == 12 and len(providers) == 13
    for index, (screen, (rows, _, slots)) in enumerate(screens.items()):
        provider = providers[index + 1]
        assert all(0 <= slot < rows * 9 for slot in slots)
        assert provider["chars"] == [chr(0xe010 + index)] and provider["height"] == 18 * rows + 18
        assert provider["file"] == f"augmentwild:gui/panels/{screen}.png"
        data = (ASSETS / f"textures/gui/panels/{screen}.png").read_bytes()
        assert (int.from_bytes(data[16:20],"big"), int.from_bytes(data[20:24],"big")) == (176,provider["height"])
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


if __name__ == "__main__":
    main()
