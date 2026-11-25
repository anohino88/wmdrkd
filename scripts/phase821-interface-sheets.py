#!/usr/bin/env python3
"""Compare deployed inventory items and slot examples with the Phase 8.2 assets."""
import argparse
import itertools
import math
import re
import runpy
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parents[1]
shared = runpy.run_path(str(root/"scripts/visual-release-sheet.py"))
page, font, layout, art = (shared[key] for key in ("page","font","layouts","art"))
design = runpy.run_path(str(root/"resource-pack/source/premium-interface.py"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("baseline",type=Path)
parser.add_argument("output",type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
before, after = args.baseline, root
slots = {repo:layout(repo) for repo in (before,after)}
baseline_rows = {name:row for name,row, in [
    ("menu",3),("choice",3),("detail",3),("codex",6),("slots",3),("settings",3),("lab",6),
    ("result",3),("supply",3),("journal",5),("warning",1),("scout",5)]}
names = {
    "menu":"빠른 메뉴","choice":"증강 선택","detail":"증강 상세","codex":"증강 도감",
    "slots":"스킬 설정","settings":"HUD 설정","lab":"전투 실험실","result":"경기 결과",
    "supply":"보급","journal":"전투 기록","warning":"확인","scout":"정찰",
    "status":"경기 현황","track":"적 탐지","admin":"서버 진단","graphics":"그래픽 설정","controls":"조작 설정",
    "pack":"리소스팩","death":"사망 분석","owned":"보유 증강","evolution":"진화",
    "synergy":"시너지","resonance":"계열 공명","filters":"도감 필터"
}
ink, quiet, gold = "#e5ecf6","#9bafc1","#f1cf91"


def board(draw, image, repo, screen, x, y):
    draw.text((x,y), "8.2" if repo == before else "8.2.1  PART 1",font=font(18,True),fill=gold)
    height = (baseline_rows if repo == before else design["ROWS"]).get(screen,0)
    if not height:
        draw.text((x,y+92),"동일 전용 화면 자료 없음",font=font(15),fill=quiet)
        return
    draw.rounded_rectangle((x,y+35,x+343,y+69+height*35),radius=7,fill="#a2aab5",outline="#64768a",width=2)
    draw.text((x+12,y+41),names[screen],font=font(15,True),fill="#1b2735")
    for slot in range(height*9):
        sx, sy = x+14+(slot%9)*35, y+68+(slot//9)*35
        draw.rectangle((sx,sy,sx+31,sy+31),fill="#485360",outline="#313a47",width=1)
        if key := slots[repo][screen].get(slot):
            if key.startswith("proc/"):
                key = "augment/"+key.split("/")[1]
            if repo == after and key.startswith("augment/"):
                key = "folio/"+key.split("/")[1]
            icon = art(repo,key).resize((30,30),Image.Resampling.NEAREST)
            image.paste(icon,(sx+1,sy+1),icon)


def compare(filename, label, screens):
    columns = 2 if len(screens) > 2 else 1
    board_width = 760
    image, draw = page(label,"배포 텍스처와 슬롯 자료를 조합한 오프라인 비교 · Minecraft 촬영 자료 아님",
                       columns*board_width+38,322*math.ceil(len(screens)/columns)+103)
    for i,screen in enumerate(screens):
        x, y = 24 + i%columns*board_width, 108+i//columns*322
        draw.text((x,y),names[screen],font=font(20,True),fill=ink)
        board(draw,image,before,screen,x,y+28)
        board(draw,image,after,screen,x+365,y+28)
    image.save(args.output/filename,optimize=True)


compare("main-choice.png","빠른 메뉴와 증강 선택",["menu","choice"])
compare("archive.png","증강 기록관",["codex","filters","detail","owned","evolution","synergy","resonance","scout"])
compare("support.png","전투·운영 화면과 개인 설정",
        ["status","slots","settings","graphics","controls","supply","track","journal","death","result","admin","lab","pack","warning"])
official = (root/"src/main/resources/official-augments.yml").read_text()
entries = {key:(label,tier) for key,tier,label in re.findall(
    r"^  (\w+):\n    group: \w+\n    rarity: (\w+)\n    name: '([^']+)'",official,re.M)}
entries.update({key:(label,tier) for key,tier,label in re.findall(
    r'config, "(\w+)", AugmentRarity\.(\w+), "([^"]+)"',
    (root/"src/main/java/io/github/augmentwild/paper/AugmentCatalog.java").read_text())})
assert len(entries) == 84
ordered = sorted(entries,key=lambda k:(["SILVER","GOLD","PRISMATIC"].index(entries[k][1]),entries[k][0]))
image, draw = page("84종 증강 아이콘 비교","각 쌍의 왼쪽은 8.2, 오른쪽은 PART 1 · 16px로 축소한 후 2배 확대",1394,1220)
families = dict(row.split("=",1) for row in (root/"src/main/resources/art-families.properties").read_text().splitlines())
raster = {}
contrast = {}
for i,key in enumerate(ordered):
    x,y = 20 + i%7*194, 107 + i//7*91
    label,tier = entries[key]
    draw.rectangle((x,y,x+185,y+83),fill="#1a2634",outline="#334759")
    draw.text((x+9,y+9),label,font=font(13,True),fill=ink)
    draw.text((x+9,y+60),tier,font=font(10),fill=quiet)
    for j,repo in enumerate((before,after)):
        icon = art(repo,("augment/" if repo == before else "folio/")+key).resize((16,16),Image.Resampling.LANCZOS)
        enlarged = icon.resize((32,32),Image.Resampling.NEAREST)
        image.paste(enlarged,(x+91+j*43,y+29),enlarged)
        if j:
            central = icon.crop((3,3,13,13)).convert("L")
            pixels = tuple(central.getdata())
            raster[key] = pixels
            contrast[key] = max(pixels)-min(pixels)
image.save(args.output/"icons-84.png",optimize=True)

rows = [
    "# PHASE 8.2.1 PART 1 · 증강 아이콘 감사", "",
    "16px 중앙 10×10의 명암 RMS로 인접 아이콘을 찾았다. 색을 제외한 정적 비교이며, 간격이 좁으면 인게임 식별 여부를 우선 확인해야 한다.",
    "원본 생성기는 128px에서 프레임과 재질을 제작하고 32px 아이템 텍스처를 생성한다. 16종 중심 실루엣을 새로 그렸다. 다른 중심 심볼은 기존 출처를 유지하고 넓은 면적과 새로운 명암을 적용했다.",
    "인게임 식별성과 레퍼런스 대비 품질은 전체 84종에서 미검증이다.", "",
    "| 증강 | 계열 | 희귀도 | 중심 실루엣 | 최근접 · RMS | 중심 명암 범위 |",
    "| --- | --- | --- | --- | --- | ---: |",
]
def distance(a,b):
    return math.sqrt(sum((x-y)**2 for x,y in zip(raster[a],raster[b])) / 100)
for key in ordered:
    closest = min((other for other in ordered if other != key),key=lambda other:distance(key,other))
    label,tier = entries[key]
    shape = "재제작" if key in design["REBUILT"] else "기존 심볼 가공"
    rows.append(f"| {label} · `{key}` | {families[key]} | {tier} | {shape} | `{closest}` · {distance(key,closest):.1f} | {contrast[key]} |")
(args.output/"icons-audit-ko.md").write_text("\n".join(rows)+"\n")
