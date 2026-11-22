#!/usr/bin/env python3
"""Compose labelled inspection sheets from baked PNGs and studio.py renders."""
import argparse
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "resource-pack/source"
TEXTURES = ROOT / "resource-pack/pack/assets/augmentwild/textures"
OUT = ROOT / "docs/art71"
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

def font(size,bold=False):
    return ImageFont.truetype(BOLD if bold else FONT,size,index=1)

def sheet(name, title, detail, items, cols, width, height, art_height):
    margin, gap, header = 32, 8, 95
    rows = (len(items)+cols-1)//cols
    img = Image.new("RGB",(2*margin+cols*(width+gap)-gap,header+rows*(height+gap)+24),"#101822")
    draw = ImageDraw.Draw(img)
    draw.text((margin,15),title,font=font(29,True),fill="#e8eeef")
    draw.text((margin,61),detail,font=font(15),fill="#9eafbb")
    for i,(label,pic,accent) in enumerate(items):
        x,y = margin+i%cols*(width+gap),header+i//cols*(height+gap)
        draw.rounded_rectangle((x,y,x+width-1,y+height-1),8, fill="#1c2936")
        draw.line((x+9,y+7,x+30,y+7),fill=accent,width=2)
        scale = min((width-10)/pic.width,(art_height-5)/pic.height)
        pic = pic.resize((max(1,round(pic.width*scale)),max(1,round(pic.height*scale))),Image.Resampling.LANCZOS)
        img.paste(pic,(x+(width-pic.width)//2,y+5+(art_height-pic.height)//2),pic)
        draw.text((x+width//2,y+height-9),label,font=font(14),fill="#e4eded",anchor="mb")
    img.save(OUT/(name+".png"),optimize=True)

def read(path): return Image.open(path).convert("RGBA")
def icon(key): return read(TEXTURES/("item/"+key+".png"))
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("renders",type=Path)
    args=parser.parse_args()
    OUT.mkdir(exist_ok=True)
    official=(ROOT/"src/main/resources/official-augments.yml").read_text()
    names={k:(n,r) for k,r,n in re.findall(r"^  (\w+):\n    group: \w+\n    rarity: (\w+)\n    name: '([^']+)'",official,re.M)}
    catalog=(ROOT/"src/main/java/io/github/augmentwild/paper/AugmentCatalog.java").read_text()
    names.update({k:(n,r) for k,r,n in re.findall(r'config, "(\w+)", AugmentRarity\.(\w+), "([^"]+)"',catalog)})
    colors={"SILVER":"#a6b9cc","GOLD":"#f1bd6f","PRISMATIC":"#dcb1ff"}
    cards=[(n,icon("augment/"+k),colors[r]) for k,(n,r) in sorted(names.items(),key=lambda e:(list(colors).index(e[1][1]),e[0]))]
    assert len(cards)==84
    sheet("icons","증강 도감","84종  /  실버 · 골드 · 프리즘  /  리소스팩 텍스처",cards,7,138,108,75)
    models=sorted(p.stem for p in (TEXTURES.parent/"models/fx").glob("*.json"))
    rendered=lambda k: read(args.renders/(k+".png"))
    sheet("models","형태 라이브러리","14개  /  내보낸 Minecraft 큐보이드의 Blender 정사영 렌더",
          [(k,rendered(k),"#85c7c0") for k in models],5,172,158,127)
    active=["shadow_dash","earthquake_mage","time_rewind","calamity_giant","duel_contract","bomb_alchemy","void_lord"]
    frames = {}
    for key in active:
        series = [rendered(key+"-"+str(i)) for i in range(3)]
        bounds = [pic.getchannel("A").getbbox() for pic in series]
        area = (min(b[0] for b in bounds)-3,min(b[1] for b in bounds)-3,
                max(b[2] for b in bounds)+3,max(b[3] for b in bounds)+3)
        frames[key] = [pic.crop(area) for pic in series]
    sheet("animation","액티브 모션","7종  /  시작 · 확장 · 수축  /  모델 단위와 이동 폭을 유지한 Blender 키프레임",
          [(names[k][0]+" · "+stage,frames[k][i],"#caaaee") for k in active for i,stage in enumerate(["시작","확장","수축"])],3,205,140,106)
    sheet("supply","보급 외형","동일 배율  /  Blender 정사영 렌더",[(n,rendered(k),c) for k,n,c in
          [("supply","일반 보급","#8cc9af"),("premium","고급 보급","#eac67b")]],2,230,195,159)
    sheet("rarity","선택 카드","실제 선택 아이콘의 테두리와 하단 마름모  /  등급별 비교",
          [(n,icon("augment/"+k),colors[r]) for n,k,r in [("실버","stack_dinosaur","SILVER"),("골드","magic_missile","GOLD"),("프리즘","time_rewind","PRISMATIC")]],3,150,151,111)
    labels={'menu': '빠른 메뉴', 'choice': '증강 선택', 'detail': '상세 카드', 'codex': '도감', 'slots': '스킬 퀵슬롯', 'settings': '개인 설정', 'lab': '연출 실험실', 'result': '경기 결과', 'supply': '보급 길잡이', 'journal': '전투 기록', 'warning': '종료 확인', 'scout': '망원경 정찰'}
    layouts={}
    for line in (ROOT/"src/main/resources/art-screen-samples.properties").read_text().splitlines():
        screen,items=line.split("=",1)
        layouts[screen]=(labels[screen],{int(slot):key for pair in items.split() for slot,key in [pair.split("=")]})
    panels=[]
    for key,(rows,accent,_) in json.loads((SOURCE/"screens.json").read_text()).items():
        title,slots=layouts[key]
        panel=read(TEXTURES/f"gui/panels/{key}.png")
        canvas=Image.new("RGBA",panel.size,"#777e87")
        d=ImageDraw.Draw(canvas)
        for i in range(rows*9):
            x,y=7+i%9*18,17+i//9*18
            d.rectangle((x,y,x+16,y+16),fill="#414e5b",outline="#34404c")
        canvas.alpha_composite(panel)
        for i,k in slots.items():
            tile=icon(k if "/" in k else "gui/"+k).resize((16,16),Image.Resampling.LANCZOS)
            canvas.alpha_composite(tile,(8+i%9*18,18+i//9*18))
        d.text((8,0),title,font=font(9,True),fill="#f0f6f7")
        panels.append((title,canvas,accent))
    sheet("interfaces","화면 구성","12종  /  실제 패널 텍스처와 예시 슬롯 데이터로 합성한 설계 미리보기",panels,3,279,221,181)
if __name__=="__main__": main()
