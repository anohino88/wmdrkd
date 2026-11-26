#!/usr/bin/env python3
"""Label offline Blender output and export the actual pack geometry as editable projects."""
import argparse
import json
from pathlib import Path
import re
import runpy
import shutil
import subprocess

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "resource-pack/pack/assets/augmentwild"
HELPERS = runpy.run_path(str(ROOT / "scripts/visual-release-sheet.py"))
EXPORT = runpy.run_path(str(ROOT / "scripts/arcane-artifacts.py"))
AUDIT = runpy.run_path(str(ROOT / "scripts/audit-art.py"))
FAMILY = runpy.run_path(str(ROOT / "scripts/resource-pack.py"))["EVOLUTION"]["FAMILIES"]
ACTIVE = ("shadow_dash", "earthquake_mage", "time_rewind", "void_lord",
          "bomb_alchemy", "duel_contract", "calamity_giant")
GROUPS = ("steel_king", "infinite_barrage", "calamity_train", "hell_lord",
          "thousand_swords", "stacking", "life_echo")
EVOLUTIONS = ("steel_heart", "sword_wind", "berserk_engine", "tank_engine",
              "magic_missile", "infernal_conductor")
PRIORITY = ("calamity_giant", "calamity_train", "thousand_swords", "time_rewind")
OFFICIAL = (ROOT/"src/main/resources/official-augments.yml").read_text()
CATALOG = (ROOT/"src/main/java/io/github/augmentwild/paper/AugmentCatalog.java").read_text()
DEFINITIONS = {key:(label,rarity,amount) for key,rarity,label,amount in re.findall(
    r"^  (\w+):\n    group: \w+\n    rarity: (\w+)\n    name: '([^']+)'[\s\S]*?    amount: ([^\n]+)", OFFICIAL,re.M)}
DEFINITIONS.update({key:(label,rarity,unit.strip()) for key,rarity,label,unit in re.findall(
    r'config, "(\w+)", AugmentRarity\.(\w+), "([^"]+)", .*", "([^"]+)", Effect\.', CATALOG)})
NAMES = {key:value[0] for key,value in DEFINITIONS.items()}
NAMES.update(zip(GROUPS, ("강철의 왕", "무한 포격", "재앙의 기관차", "지옥의 군주",
                          "천 개의 검", "누적의 공명", "생명의 잔향"), strict=True))


def panel(output, frames, title, rows, width=202, subtitle="Blender 오프라인 · 배포 JSON 사용"):
    columns = max(len(cells) for _, cells in rows)
    image, draw = HELPERS["page"](title, subtitle, 200+columns*width, 105+len(rows)*157)
    for row, (label, cells) in enumerate(rows):
        y = 103 + row*157
        draw.text((20,y+57), label, font=HELPERS["font"](17), fill="#dfe9ef")
        for col, (key, caption) in enumerate(cells):
            with Image.open(frames/(key+".png")) as frame:
                HELPERS["tile"](image, draw, frame.convert("RGBA"), 175+col*width, y,
                                width-8, 150, caption)
    image.save(output, optimize=True)


def animate(path, files, label, durations):
    frames = []
    for index, file in enumerate(files):
        image, draw = HELPERS["page"](label, "Blender 오프라인", 260, 258)
        with Image.open(file) as picture:
            HELPERS["tile"](image, draw, picture.convert("RGBA"), 38, 88, 184, 161,
                            f"{sum(durations[:index])/1000:.2f} s")
        frames.append(image)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=durations, loop=0)


def audio(baseline, output):
    before = baseline / "resource-pack/pack/assets/augmentwild"
    pairs = (("sword","sweep"),("void","rift"),("shadow","rift"),("metal","shield_form"),
             ("machine","cast"),("earth","stone_break"),("arcane","cast"),("life","shield_form"),
             ("growth","evolve"),("time","rewind"),("motion","sweep"),("flame","ignite"))
    command = ["ffmpeg", "-loglevel", "error", "-y"]
    for new,old in pairs:
        command += ["-i",str(before/f"sounds/{old}.ogg"),"-i",str(ART/f"sounds/family_{new}.ogg")]
    streams = ";".join(f"[{i}:a]aresample=44100,aformat=channel_layouts=mono,apad=pad_dur=0.25[a{i}]"
                       for i in range(24))
    join = "".join(f"[a{i}]" for i in range(24)) + "concat=n=24:v=0:a=1[out]"
    subprocess.run(command+["-filter_complex",streams+";"+join,"-map","[out]","-map_metadata","-1","-fflags","+bitexact","-flags:a","+bitexact",str(output)],check=True)


def timing(output, frames):
    files = sorted((ROOT/"src/main/resources").glob("art-signatures.tsv"))
    entries = {parts[0]:[tuple(map(float,pose.split(","))) for pose in parts[-1].split(";")]
               for file in files for line in file.read_text().splitlines()
               if line and not line.startswith("#") for parts in [line.split("|")]}
    rows = ["# 시간표와 오프라인 가시 범위", "",
            "각 샘플은 실루엣 알파의 반 이상인 픽셀을 센다. 2틱마다 촬영한 프레임 중 "
            "최대 투영 픽셀의 70% 이상인 표본이 차지하는 시간을 추산했다. "
            "장애물, 파티클, 카메라 움직임과 클라이언트 FPS는 이 측정에 포함되지 않는다.", "",
            "| 연출 | 기준 및 현재 키프레임 종료 | 투영 유지 추산 |",
            "| --- | ---: | ---: |"]
    for key in ACTIVE+("bomb_impact",):
        duration = int(entries[key][-1][0])
        counts = []
        for frame in sorted(frames.glob(f"move-{key}-*.png"))[:-1]:
            with Image.open(frame) as image:
                counts.append(sum(image.convert("RGBA").getchannel("A").histogram()[128:]))
        assert counts and max(counts) > 0
        visible = 2*sum(count >= .7*max(counts) for count in counts)
        rows.append(f"| {NAMES.get(key,'폭탄 충돌')} | {duration}틱 | {visible}틱 |")
    output.write_text("\n".join(rows)+"\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("frames", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    out, frames = args.output, args.frames
    out.mkdir(parents=True, exist_ok=True)
    for keys, filename, title in ((ACTIVE,"active","액티브 7종"), (EVOLUTIONS,"evolution","진화 6종"),
                                  (GROUPS,"synergy","시너지 5종과 공명 2종")):
        panel(out/(filename+"-models.png"),frames,title+" · 조형 비교",
              [(NAMES[key],[(phase+"-sig_"+key,version) for phase,version in
                            (("before","PART 2"),("after","PART 3"))]) for key in keys])
        if keys != ACTIVE:
            panel(out/(filename+"-frames.png"),frames,title+" · 런타임 키프레임",[
                (NAMES[key],[(p.stem, str(int(p.stem.rsplit("-",1)[1]))+"틱") for p in
                             sorted(frames.glob(f"move-{key}-*.png"))]) for key in keys],164)
    for key in ACTIVE:
        files = sorted(frames.glob(f"move-{key}-*.png"))
        assert len(files) > 12
        animate(out/(key+".gif"),files[:-1],NAMES[key],[100]*(len(files)-1))
    for key,label in (("supply","일반 보급"),("premium","고급 보급")):
        files = sorted(frames.glob(f"crate-{key}-*.png"))
        assert len(files) == 7
        animate(out/(key+".gif"),files,label+" · 개봉",[500]*6+[250])
    panel(out/"supply.png",frames,"보급 외형과 힌지",[
        (label,[("before-"+key+"_closed","PART 2"), ("after-"+key+"_closed","PART 3"),
                ("crate-"+key+"-03","50%"), ("crate-"+key+"-06","완료")])
        for key,label in (("supply","일반 보급"),("premium","고급 보급"))],172)
    panel(out/"growth.png",frames,"성장 단계의 구조 변화",[
        (label,[(prefix+str(stage),f"{stage}단계") for stage in range(1,5)])
        for label,prefix in (("공용","after-tier_growth_1"[:-1]),("금속","after-growth_metal_"),
                             ("기계","after-growth_machine_"),("검","after-growth_sword_"),
                             ("생명","after-growth_life_"),("비전","after-growth_arcane_"))],172)
    panel(out/"readability.png",frames,"거리와 표시 품질의 비교",[
        (NAMES[key], [("standard-"+key,"독립 부품 미표시")] +
         [(f"distance-{key}-{dist}",f"고급 · {dist}블록") for dist in (3,8,16,24)])
        for key in PRIORITY],164,"오프라인 투영 · 거리별 동일 카메라 설정 · 표준은 별도 근접 시점")
    panel(out/"families.png",frames,"12개 발동 계열",[
        ("",[(f"after-family_{shape}",label+" · "+shape) for label,shape in batch])
        for batch in (list(FAMILY.items())[:4],list(FAMILY.items())[4:8],list(FAMILY.items())[8:])],172)
    EXPORT["blockbench"](ART,out/"blockbench",supply=True)
    for file in (out/"blockbench").glob("*.bbmodel"):
        model=json.loads(file.read_text())
        assert len({tex["uuid"] for tex in model["textures"]})==len(model["textures"])
        assert {cube["uuid"] for cube in model["elements"]}=={cid for group in model["outliner"] for cid in group["children"]}
        assert all(0 <= face["texture"] < len(model["textures"]) for cube in model["elements"] for face in cube["faces"].values())
    shutil.copyfile(frames/"visual-evolution.blend",out/"visual-evolution.blend")
    audit = AUDIT["report"]()
    motifs = dict(line.split("=") for line in (ROOT/"src/main/resources/art-families.properties").read_text().splitlines())
    rows = ["# 증강 84종 발동 연결", "",
            "이름·등급·효과는 배포 카탈로그를 따르고 주요 실제 호출 간선을 감사했다. 일반 발동은 계열 모델에 고유 문양을 결합한다.",
            "간소 모드는 바닐라 파티클을 사용한다. 지속·공개 항목은 확인된 대표 경로다. 그래픽 클라이언트 검증은 84종 모두 NOT_RUN이다.", "",
            "| ID · 이름 · 희귀도 | 조건과 효과 · 기본 설정 | 실제 코드 | 계열 · 모델 | 모션 · 지속/종료 | 파티클 대체 · 가시성 | 음원 |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for line in audit.splitlines():
        if line.startswith("| `"):
            cols = [col.strip() for col in line.split("|")]
            key=cols[1].strip("`")
            family=motifs[key]
            label,rarity,amount=DEFINITIONS[key]
            motion = cols[5] if key in ACTIVE else cols[5].rsplit(" · ",1)[-1]
            sound = f"family_{FAMILY[family]}"
            model = f"proc/{key}"
            if key in ACTIVE:
                model += f" / fx/sig_{key}"
                sound += " / " + motion.split(" · ")[2]
            rows.append(f"| `{key}` · {label} · {rarity} | {cols[2]} · {amount} | {cols[3]} | "
                        f"{family} · `{model}` | {motion} | {cols[7]} · {cols[8]} | `{sound}` |")
    assert len(rows) == 91 and len(DEFINITIONS) == 84
    (out/"augments-ko.md").write_text("\n".join(rows)+"\n")
    audio(args.baseline,out/"sounds.ogg")
    timing(out/"timing-ko.md",frames)


if __name__ == "__main__":
    main()
