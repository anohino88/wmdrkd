#!/usr/bin/env python3
"""Compose the shipped model renders. Blender renders are not client footage."""
import argparse
from pathlib import Path
import runpy

from PIL import Image, ImageDraw

parser = argparse.ArgumentParser()
parser.add_argument("renders", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)

helpers = runpy.run_path(str(Path(__file__).with_name("visual-release-sheet.py")))
page, font = helpers["page"], helpers["font"]
names = {
    "shadow_dash":"그림자 도약", "earthquake_mage":"지진술사", "time_rewind":"시간 역행",
    "void_lord":"공허 군주", "bomb_alchemy":"폭탄 연금술", "duel_contract":"결투의 계약",
    "calamity_giant":"재앙의 거인", "crescent":"공개 검기", "impact":"공개 폭발·충격파",
    "fracture":"공개 방어",
}

def paint(canvas, art, origin, width):
    resized = art.resize((width,width),Image.Resampling.LANCZOS)
    canvas.paste(resized,origin,resized)

for id, label in names.items():
    board,draw = page(label,"오프라인 Blender · 같은 카메라, 조명, 배율",900,504)
    for position,(version,variant) in enumerate((("7.6","before"),("7.7","after"))):
        x = 27+position*438
        draw.rounded_rectangle((x,104,x+407,484),radius=8,fill="#202c3a")
        paint(board,Image.open(args.renders/f"{variant}-{id}.png").convert("RGBA"),(x+31,113),344)
        draw.text((x+204,472),version,font=font(18,True),fill="#dfe9ef",anchor="ms")
    board.save(args.output/f"{id}.png",optimize=True)

    files = sorted(args.renders.glob(f"clip-{id}-*.png"))
    if not files:
        continue
    raw = [Image.open(file).convert("RGBA") for file in files]
    boxes = [box for art in raw if (box := art.getbbox())]
    size = min(raw[0].width,max(max(b[2] for b in boxes)-min(b[0] for b in boxes),
                max(b[3] for b in boxes)-min(b[1] for b in boxes))+24)
    center = [(min(b[i] for b in boxes)+max(b[i+2] for b in boxes))//2 for i in (0,1)]
    left,top = [max(0,min(c-size//2,raw[0].width-size)) for c in center]
    crop = (left,top,left+size,top+size)
    frames = []
    for tick, art in enumerate(raw):
        board,draw = page(label,"오프라인 Blender · Paper 키프레임 기반",448,470)
        paint(board,art.crop(crop),(30,77),388)
        stage = "준비" if tick <= 2 else "전개" if tick < len(raw)*.6 else "소멸"
        draw.text((224,449),f"{stage} · {tick}틱",font=font(18),fill="#dfe9ef",anchor="ms")
        frames.append(board)
    frames[0].save(args.output/f"{id}.gif",save_all=True,append_images=frames[1:],
                   duration=[250]+[50]*(len(frames)-2)+[350],loop=0,optimize=True)
