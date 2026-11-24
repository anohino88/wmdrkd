#!/usr/bin/env python3
"""Build offline comparison sheets from the actual exported models and native slot samples."""
import argparse
from pathlib import Path
import runpy

from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("baseline",type=Path)
parser.add_argument("renders",type=Path)
parser.add_argument("output",type=Path)
parser.add_argument("--versions", nargs=2, default=("7.7","7.8"))
args = parser.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
root = Path(__file__).resolve().parents[1]
m = runpy.run_path(str(root/"scripts/visual-release-sheet.py"))
page,tile,font,inventory = (m[key] for key in ("page","tile","font","inventory"))
groups = {
    "evolution":("진화 6종",dict(steel_heart="강철심장",magic_missile="마법 미사일",
        berserk_engine="폭주 기관",infernal_conductor="지옥의 전도체",tank_engine="탱크 엔진",sword_wind="검풍")),
    "synergy":("시너지 5종과 공명 2종",dict(steel_king="강철의 왕",calamity_train="재앙의 기관차",
        infinite_barrage="무한 포격",hell_lord="지옥의 군주",thousand_swords="천 개의 검",
        life_echo="생명의 잔향",stacking="누적의 공명")),
}
for key,(title,entries) in groups.items():
    image,draw=page(title,"오프라인 Blender 렌더 · 동일한 카메라와 조명",1160,105+247*((len(entries)+2)//3))
    for i,(model,name) in enumerate(entries.items()):
        x,y=22+(i%3)*376,106+(i//3)*247
        draw.text((x+180,y+3),name,font=font(18,True),fill="#f5c785",anchor="mt")
        for j,(release,tag) in enumerate(zip(args.versions,("before","after"))):
            with Image.open(args.renders/f"{tag}-{model}.png") as src:
                pic=src.convert("RGBA")
            tile(image,draw,pic,x+j*178,y+30,174,207,release)
    image.save(args.output/f"{key}.png",optimize=True)

image,draw=page("메인 메뉴 · 증강 선택 · 도감 · 스킬","오프라인 배치 · 배포 슬롯과 아이콘 텍스처",1146,1230)
for j,(release,project) in enumerate(zip(args.versions,(args.baseline,root))):
    for i,(screen,name,rows) in enumerate((("menu","빠른 메뉴",3),("choice","증강 선택",3),("codex","도감 목록",6),("slots","스킬 설정",3))):
        y=(106,336,566,996)[i]
        tile(image,draw,inventory(project,screen,name,rows),26+j*552,y,542,222 if rows==3 else 426,release+" · "+name)
image.save(args.output/"menus.png",optimize=True)

if args.versions in (["7.8","7.9"],["7.9","8.0"]):
    image,draw = page("3슬롯 스킬 HUD","오프라인 예시 · 배포 팩의 슬롯 아이콘과 런타임 상태 문구",1146,340)
    for col,(release,project,statuses) in enumerate(zip(args.versions,(args.baseline,root),(("비어 있음","잠김","잠김"),("비어 있음","잠김","잠김")))):
        x=27 + col*552
        draw.rounded_rectangle((x,104,x+530,310),radius=8,fill="#202c3a")
        draw.text((x+18,119),release,font=font(18,True),fill="#dfe9ef")
        for index,(skill,status) in enumerate(zip((None,"time_rewind","calamity_giant"),statuses)):
            left=x+18+index*168
            active=index==2
            color="#ecc68a" if active else "#9fb5cc"
            draw.rounded_rectangle((left,168,left+158,230),radius=6,fill="#131e2a",outline=color,width=1)
            draw.text((left+7,186),str(index+1),font=font(17,active),fill=color)
            if skill:
                icon=m["art"](project,"augment/"+skill).resize((32,32),Image.Resampling.LANCZOS)
                image.paste(icon,(left+31,178),icon)
            if release=="8.0":
                state = Image.open(project/"resource-pack/pack/assets/augmentwild/textures/gui/skill_state.png").convert("RGBA")
                pixel = (48,32,32)[index]
                symbol = state.crop((pixel,0,pixel+16,16)).resize((32,32),Image.Resampling.NEAREST)
                image.paste(symbol,(left+78,185),symbol)
            else:
                draw.text((left+70,187),status,font=font(16),fill=color)
        draw.text((x+18,254),"재앙의 거인 · 부활 보호 중",
                  font=font(17),fill="#dfe9ef")
    image.save(args.output/"hud.png",optimize=True)
