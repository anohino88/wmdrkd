#!/usr/bin/env python3
"""Assemble Korean-labelled offline render, native-layout and animation comparisons."""
import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
INK = "#dfe9ef"
MUTED = "#9aafc2"
METAL = "#202c3a"
GOLD = "#f5c785"


def font(size,bold=False):
    return ImageFont.truetype(BOLD if bold else FONT,size,index=1)


def page(title,subtitle,width,height):
    image = Image.new("RGB",(width,height),"#101923")
    draw = ImageDraw.Draw(image)
    draw.rectangle((22,22,26,72),fill=GOLD)
    draw.text((41,15),title,font=font(32,True),fill=INK)
    draw.text((42,65),subtitle,font=font(16),fill=MUTED)
    return image,draw


def tile(image, draw, picture, x, y, width, height, label):
    draw.rounded_rectangle((x,y,x+width-1,y+height-1),radius=8,fill=METAL)
    art = picture.copy()
    art.thumbnail((width-16,height-34),Image.Resampling.LANCZOS)
    image.paste(art,(x+(width-art.width)//2,y+4+(height-34-art.height)//2),art)
    draw.text((x+width//2,y+height-10),label,font=font(14),fill=INK,anchor="mb")


def art(root,key):
    path = root/"resource-pack/pack/assets/augmentwild/textures/item"/(key+".png")
    return Image.open(path).convert("RGBA")


def layouts(root):
    data = root/"src/main/resources/art-screen-samples.properties"
    return {screen:{int(slot):key for cell in cells.split() for slot,key in [cell.split("=")]}
            for row in data.read_text().splitlines() for screen,cells in [row.split("=",1)]}


def inventory(root, screen, name, rows):
    raw = Image.new("RGBA",(528,18*rows*3+54),"#767e8a")
    d = ImageDraw.Draw(raw)
    d.text((22,7),name,font=font(21,True),fill="#f3f5f6")
    for slot in range(rows*9):
        x,y = 21+slot%9*54,51+slot//9*54
        d.rectangle((x,y,x+49,y+49),fill="#414b59",outline="#2e3845",width=2)
    for slot,key in layouts(root)[screen].items():
        if key.startswith("proc/"):
            key = "augment/"+key.split("/")[1]
        p = art(root,key).resize((46,46),Image.Resampling.LANCZOS)
        raw.alpha_composite(p,(23+slot%9*54,53+slot//9*54))
    return raw


def hud(root,new):
    raw = Image.new("RGBA",(528,195),"#192631")
    d = ImageDraw.Draw(raw)
    d.ellipse((234,34,294,94),outline="#586879",width=2)
    d.line((254,64,274,64),fill=INK)
    d.line((264,54,264,74),fill=INK)
    d.rectangle((13,124,514,160),fill="#101822")
    if new:
        x = 25
        for slot,key,state in [(1,"shadow_dash","✓"),(2,"time_rewind","12s"),(3,"bomb_alchemy","4s")]:
            tone = GOLD if slot == 1 else MUTED
            if slot == 1:
                d.rounded_rectangle((x-5,128,x+73,155),radius=4,outline=GOLD)
            d.text((x,129),str(slot),font=font(16,True),fill=tone)
            icon = art(root,"augment/"+key).resize((17,17),Image.Resampling.LANCZOS)
            raw.alpha_composite(icon,(x+14,134))
            d.text((x+36,130),state,font=font(16),fill=tone)
            x += 86
        d.text((277,130),"그림자 질주 · 사용 가능",font=font(17,True),fill=INK)
    else:
        d.text((23,129),"▶1 그림자 질주 · 사용 가능  ·  2 시간 역행 · 12초 …",
               font=font(16),fill=INK)
    d.text((18,166),"선택 1   |   재사용 대기 2·3",font=font(14),fill=MUTED)
    return raw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline",type=Path)
    parser.add_argument("renders",type=Path)
    parser.add_argument("output",type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    official = (ROOT/"src/main/resources/official-augments.yml").read_text()
    names = {k:(label,rarity) for k,rarity,label in re.findall(r"^  (\w+):\n    group: \w+\n    rarity: (\w+)\n    name: '([^']+)'",official,re.M)}
    names.update({k:(label,rarity) for k,rarity,label in re.findall(r'config, "(\w+)", AugmentRarity\.(\w+), "([^"]+)"',
                  (ROOT/"src/main/java/io/github/augmentwild/paper/AugmentCatalog.java").read_text())})
    active = ["shadow_dash","earthquake_mage","time_rewind","void_lord","bomb_alchemy","duel_contract","calamity_giant"]
    def render(name):
        return Image.open(args.renders/(name+".png")).convert("RGBA")

    image,d = page("전투 모델 비교","오프라인 Blender 렌더 · 동일 시점 · 동일 조명 · 동일 배율",1240,870)
    for i,key in enumerate(active+["crescent"]):
        x,y = 22+(i%4)*301,112+(i//4)*368
        title = names[key][0] if key in names else "공개 검기"
        d.text((x+145,y-8),title,font=font(19,True),fill=INK,anchor="mt")
        for j,phase in enumerate(("before","after")):
            tile(image,d,render(phase+"-"+key),x+j*146,y+36,140,265,("7.5","7.6")[j])
    image.save(args.output/"model-comparison.png",optimize=True)

    image,d = page("액티브 모션과 충돌·종료","오프라인 Blender · 실제 런타임 키프레임과 모델 JSON",1110,1538)
    tracks = active+["bomb_impact","void_collapse","thousand_swords"]
    for i,key in enumerate(tracks):
        files = sorted(args.renders.glob(f"motion-{key}-*.png"))
        x,y = 22,107+i*141
        d.text((x,y+65),names.get(key,({"bomb_impact":"폭발","void_collapse":"공허 붕괴","thousand_swords":"천검"}[key] if key not in names else "",))[0],
               font=font(16,True),fill=GOLD,anchor="lm")
        for j,file in enumerate(files):
            tile(image,d,Image.open(file).convert("RGBA"),168+j*182,y,174,132,f"KEY {j+1}")
    image.save(args.output/"motion-frames.png",optimize=True)

    gif = []
    for key in active+["bomb_impact","void_collapse","thousand_swords"]:
        files = sorted(args.renders.glob(f"motion-{key}-*.png"))
        title = names.get(key,(key.replace("_"," "),))[0]
        for j,file in enumerate(files):
            frame,d = page(title,"오프라인 Blender · 런타임 키프레임",490,350)
            tile(frame,d,Image.open(file).convert("RGBA"),86,104,318,232,f"KEY {j+1}/{len(files)}")
            gif.append(frame)
    gif[0].save(args.output/"effects.gif",save_all=True,append_images=gif[1:],duration=125,loop=0,optimize=True)

    image,d = page("인벤토리·스킬 HUD 비교","오프라인 배치 시안 · 기본 인벤토리 좌표와 실제 팩 아이콘",1146,1000)
    roots = (args.baseline,ROOT)
    for j,(label,root) in enumerate(zip(("7.5","7.6"),roots)):
        x = 26+j*552
        for i,(screen,caption) in enumerate([("menu","빠른 메뉴"),("choice","증강 선택"),("detail","증강 상세")]):
            tile(image,d,inventory(root,screen,caption,3),x,106+i*216,542,208,label+" · "+caption)
        tile(image,d,hud(root,j == 1),x,754,542,215,label+" · 스킬 HUD")
    image.save(args.output/"gui-hud-comparison.png",optimize=True)

    image,d = page("증강 아이콘 84종","동일 심볼의 7.5(왼쪽)와 7.6(오른쪽) 텍스처",1240,1600)
    tiers = {"SILVER":0,"GOLD":1,"PRISMATIC":2}
    for i,(key,(label,rank)) in enumerate(sorted(names.items(),key=lambda item:(tiers[item[1][1]],item[0]))):
        pair = Image.new("RGBA",(138,67))
        pair.alpha_composite(art(args.baseline,"augment/"+key),(1,1))
        pair.alpha_composite(art(ROOT,"augment/"+key),(72,1))
        tile(image,d,pair,23+i%7*172,109+i//7*121,165,114,label)
    assert len(names) == 84
    image.save(args.output/"icons.png",optimize=True)


if __name__ == "__main__":
    main()
