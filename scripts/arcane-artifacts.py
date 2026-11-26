#!/usr/bin/env python3
"""Export editable project files and views from the checked-in Minecraft models."""
import argparse
import base64
import hashlib
from pathlib import Path
import json
import runpy
import uuid

from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]
ART = runpy.run_path(str(PROJECT/"resource-pack/source/arcane.py"))
HELPERS = runpy.run_path(str(PROJECT/"scripts/visual-release-sheet.py"))


def stable_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "https://github.com/anohino88/wmdrkd/art/" + name))


def blockbench(assets, output, supply=False):
    output.mkdir(parents=True,exist_ok=True)
    rigs = ART["components"](PROJECT)
    assemblies = {root: ["sig_" + root] + [part for part in rigs if part.startswith("rig_" + root + "_")]
                  for root in ART["CORES"]}
    if supply:
        assemblies.update({root: [root, root + "_lid"] for root in ("supply", "premium")})
    for root, branches in assemblies.items():
        elements,textures,outliner = [],[],[]
        for branch in branches:
            data = json.loads((assets/f"models/fx/{branch}.json").read_text())
            indices = {}
            for alias, reference in data["textures"].items():
                name = reference.split(":")[1]
                existing = next((i for i, tex in enumerate(textures) if tex["name"] == name+".png"), None)
                if existing is not None:
                    indices[alias] = existing
                    continue
                file = assets/f"textures/{name}.png"
                with Image.open(file) as image:
                    width,height = image.size
                indices[alias] = len(textures)
                textures.append({"name":name+".png","id":str(len(textures)),
                    "width":width,"height":height,"uv_width":16,"uv_height":16,"mode":"bitmap","saved":True,
                    "uuid":stable_uuid(root+":"+name),"source":"data:image/png;base64,"+base64.b64encode(file.read_bytes()).decode()})
            group = {"name":branch,"origin":[8,8,8],"rotation":[0,0,0],"export":True,"isOpen":False,
                     "uuid":stable_uuid(root+"/"+branch),"children":[]}
            for index, part in enumerate(data["elements"]):
                ident = stable_uuid(root+"/"+branch+"/"+str(index))
                dimensions = list(zip(part["from"],part["to"]))
                faces = {}
                for face,surface in part["faces"].items():
                    a,b = {"up":(0,2),"down":(0,2),"east":(2,1),"west":(2,1),"north":(0,1),"south":(0,1)}[face]
                    u1,u2 = dimensions[a]
                    v1,v2 = dimensions[b]
                    faces[face] = {"uv":surface.get("uv",[u1,16-v2,u2,16-v1]),"texture":indices[surface["texture"][1:]]}
                elements.append({"name":branch+" "+str(index),"type":"cube","from":part["from"],"to":part["to"],
                                 "origin":[8,8,8],"uuid":ident,"faces":faces,"box_uv":False,"rescale":False,"export":True})
                rotation = part.get("rotation")
                if rotation:
                    elements[-1]["origin"] = rotation["origin"]
                    angles = rotation.get("euler") or [rotation.get(axis, 0) for axis in "xyz"]
                    if "axis" in rotation:
                        angles["xyz".index(rotation["axis"])] = rotation["angle"]
                    elements[-1]["rotation"] = angles
                group["children"].append(ident)
            outliner.append(group)
        (output/(root+".bbmodel")).write_text(json.dumps({"meta":{"format_version":"4.10","model_format":"free","box_uv":False},
            "name":root,"model_identifier":root,"resolution":{"width":16,"height":16},
            "elements":elements,"outliner":outliner,"textures":textures},separators=(",",":"))+"\n")


def inventory_sheet(baseline, output):
    path = Path("resource-pack/pack/assets/augmentwild/textures/item/augment")
    final = sorted((PROJECT/path).glob("*.png"))
    assert len(final) == 84
    page, font = HELPERS["page"], HELPERS["font"]
    graphic,draw = page("증강 아이콘 84종 · 7.9 / 8.0","왼쪽 7.9 · 오른쪽 8.0 · 우하단 16px 표본",1370,1440)
    table = ["# PHASE 8.0 아이콘 검토","",
        "배포 아이콘 전체를 16px로 축소해 알파 픽셀과 자산 일치 여부를 확인했다. 게임 배경 대비와 의미 식별은 클라이언트 검토 대기다.",
        "", "| 증강 ID | 신규 PNG 해상도 | 16px 내 불투명 표본 | 검토 상태 |","| --- | --- | --- | --- |"]
    fingerprints = set()
    for index,file in enumerate(final):
        x,y=20+index%7*192,111+index//7*108
        before = Image.open(baseline/path/file.name).convert("RGBA")
        after = Image.open(file).convert("RGBA")
        small = after.resize((16,16),Image.Resampling.LANCZOS)
        pixels = sum(a >= 128 for a in small.getchannel("A").getdata())
        assert pixels > 50 and before.tobytes() != after.tobytes(), file.name
        digest = hashlib.sha256(small.tobytes()).hexdigest()
        assert digest not in fingerprints, file.name
        fingerprints.add(digest)
        for j,icon in enumerate((before,after)):
            icon = icon.resize((59,59), Image.Resampling.LANCZOS)
            graphic.paste(icon,(x+6+j*67,y),icon)
        graphic.paste(small,(x+146,y+39),small)
        draw.text((x+3,y+65),file.stem,font=font(13),fill="#dfe9ef")
        table.append(f"| `{file.stem}` | {after.width}×{after.height} | {pixels}/256 | 정적 PASS · 인게임 NOT_RUN |")
    graphic.save(output/"icons.png",optimize=True)
    (output/"icons-ko.md").write_text("\n".join(table)+"\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline",type=Path)
    parser.add_argument("sheets",type=Path)
    parser.add_argument("projects",type=Path)
    args = parser.parse_args()
    args.sheets.mkdir(parents=True,exist_ok=True)
    inventory_sheet(args.baseline,args.sheets)
    blockbench(PROJECT/"resource-pack/pack/assets/augmentwild",args.projects)

if __name__ == "__main__":
    main()
