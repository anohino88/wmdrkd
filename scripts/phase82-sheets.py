"""Offline comparisons made with deployed PNGs, slot tables and Blender renders."""
import argparse
import math
import runpy
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(root/"scripts/visual-release-sheet.py"))
page,tile,font,inventory = (helpers[key] for key in ("page","tile","font","inventory"))
args = argparse.ArgumentParser(description=__doc__)
args.add_argument("baseline", type=Path)
args.add_argument("renders", type=Path)
args.add_argument("output", type=Path)
base,renders,out = vars(args.parse_args()).values()
out.mkdir(parents=True,exist_ok=True)
board,draw=page("보급과 성장 모델","오프라인 Blender · 동일 배율 · 실제 배포 모델",1080,820)
for row,(model,name) in enumerate((("supply","일반 보급"),("premium","고급 보급"))):
    for col,version in enumerate(("81","82")):
        with Image.open(renders/f"{version}-{model}.png") as image:
            tile(board,draw,image.convert("RGBA"),24+col*510,105+row*220,493,210,
                 f"8.{version[-1]} · {name}")
for index,model in enumerate(("tier_growth_1","tier_growth_2","tier_growth_3","sig_steel_heart")):
    with Image.open(renders/f"82-{model}.png") as image:
        tile(board,draw,image.convert("RGBA"),24+index*258,557,243,242,
             ("25%","50%","75%","진화 · 강철심장")[index])
board.save(out/"models.png",optimize=True)
board,draw=page("메인 메뉴 · 증강 후보 · 설정","오프라인 슬롯 표와 배포 아이콘",1146,820)
for col,(version,source) in enumerate((("8.1",base),("8.2",root))):
    for row,(screen,name) in enumerate((("menu","메뉴"),("choice","증강 선택"),("settings","설정"))):
        tile(board,draw,inventory(source,screen,name,3),26+col*552,106+row*232,542,215,version+" · "+name)
board.save(out/"menus.png",optimize=True)
folder=Path("resource-pack/pack/assets/augmentwild/textures/item/augment")
families=dict(line.split("=") for line in (root/"src/main/resources/art-families.properties").read_text().splitlines())
images={file.stem: file for file in sorted((root/folder).glob("*.png"))}
assert len(images)==84 and set(images)==families.keys()
def core(path):
    return Image.open(path).convert("RGB").resize((16,16),Image.Resampling.LANCZOS).crop((4,4,12,12))
def similarity(a,b):
    return math.sqrt(sum((x-y)**2 for x,y in zip(a,b))/len(a))
cores={name:bytes(core(file).tobytes()) for name,file in images.items()}
nearest={key:min((similarity(cores[key],cores[other]),other) for other in cores if other!=key) for key in cores}
audit=["# PHASE 8.2 증강 아이콘 감사","",
       "16px 중앙 8×8의 RGB RMS 거리는 색과 형태가 비슷한 후보를 찾는 정적 지표다. 최소값은 가장 유사한 후보를 나타낸다. 인게임 식별성은 미검증이다.","",
       "| 증강 | 시각 계열 | 가장 가까운 후보 | RMS | 재제작 |","| --- | --- | --- | ---: | --- |"]
board,draw=page("증강 아이콘 84종","실제 에셋 16px 축소 · 우측 표본은 새 심볼",1600,1290)
for index,(key,file) in enumerate(images.items()):
    rms,relative=nearest[key]
    changed=(base/folder/file.name).read_bytes()!=file.read_bytes()
    x,y=24+index%7*223,106+index//7*96
    for offset,source in ((0,base/folder/file.name),(41,file)):
        with Image.open(source) as original:
            icon=original.convert("RGBA").resize((16,16),Image.Resampling.LANCZOS).resize((36,36),Image.Resampling.NEAREST)
            board.paste(icon,(x+offset,y),icon)
    draw.text((x,y+40),key,font=font(13),fill="#dfe9ef")
    audit.append(f"| {key} | {families[key]} | {relative} | {rms:.1f} | {'신규 심볼' if changed else '기존 심볼'} |")
board.save(out/"icons.png",optimize=True)
(out/"icons-audit-ko.md").write_text("\n".join(audit)+"\n")
