#!/usr/bin/env python3
"""Present actual authored poses at 50 ms per game tick, without playback slow motion."""
import argparse
from pathlib import Path
import runpy

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT/"scripts/visual-release-sheet.py"))
page, font = helpers["page"],helpers["font"]
labels = dict(shadow_dash="그림자 도약",earthquake_mage="지진술사",time_rewind="시간 역행",void_lord="공허 군주",
              bomb_alchemy="폭탄 연금술",duel_contract="결투의 계약",calamity_giant="재앙의 거인",
              bomb_impact="폭탄 충돌",steel_heart="강철심장 진화",steel_king="강철의 왕",
              calamity_train="재앙의 기관차",thousand_swords="천 개의 검")
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("renders",type=Path)
parser.add_argument("output",type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True,exist_ok=True)


def track(resource):
    return {row.split("|")[0]:[tuple(map(float,key.split(","))) for key in row.split("|")[-1].split(";")]
            for row in (ROOT/"src/main/resources"/resource).read_text().splitlines() if row and not row.startswith("#")}


def visible(frames):
    def envelope(frame):
        w,h,d=frame[-3:]
        return max(w*h,w*d,d*h)
    limit=max(map(envelope,frames))*.7
    return int(sum(b[0]-a[0] for a,b in zip(frames,frames[1:]) if min(envelope(a),envelope(b))>=limit))


def load(path):
    return Image.open(path).convert("RGBA")


def image_for(frames,tick,blank):
    return blank if tick > max(frames) else frames[max(k for k in frames if k <= tick)]


before,after=track("art-signatures-80.tsv"),track("art-signatures.tsv")
table=["# PHASE 8.1 재생 시간","",
       "기준 20TPS. 유지 표본은 최대 투영 면적의 70% 이상인 두 인접 키프레임 사이의 누적 시간이다. 실제 화면 가독성 측정은 아니다.","",
       "| 연출 | 8.0 길이 | 8.1 길이 | 8.0 형태 유지 | 8.1 형태 유지 |",
       "| --- | ---: | ---: | ---: | ---: |"]
blank=load(args.renders/"pacing-idle.png")
for key,name in labels.items():
    images=[]
    for version in ("80","81"):
        images.append({int(path.stem.rsplit("-",1)[1]):load(path) for path in
                       args.renders.glob(f"pacing-{version}-{key}-*.png")})
    old,new = images
    end = max(new)
    sequence = sorted(set(old)|set(new)|{max(old)+1})
    timing=[50*(b-a) for a,b in zip(sequence,sequence[1:])]+[50]
    cards, normal = [],[]
    for tick in sequence:
        board,draw=page(name,"오프라인 Blender · 동일 배율 · 실제 정상 속도",600,390)
        for x,version,pics in ((25,"8.0",old),(325,"8.1",new)):
            picture=image_for(pics,tick,blank)
            board.paste(picture,(x,100),picture)
            draw.text((x+120,355),f"{version} · {min(tick,max(pics))/20:.2f}초",
                      font=font(17),fill="#dfe9ef",anchor="mt")
        cards.append(board)
        if tick in new:
            frame,caption=page(name,"PHASE 8.1 · 정상 속도 · 20 TPS",324,375)
            frame.paste(new[tick],(42,92),new[tick])
            caption.text((162,336),f"{tick/20:.2f}초",font=font(17),fill="#dfe9ef",anchor="mt")
            normal.append(frame)
    cards[0].save(args.output/(key+"-compare.gif"),save_all=True,append_images=cards[1:],duration=timing,loop=0,optimize=True)
    if key in list(labels)[:7]:
        ticks=sorted(new)
        normal[0].save(args.output/(key+".gif"),save_all=True,append_images=normal[1:],
                       duration=[50*(b-a) for a,b in zip(ticks,ticks[1:])]+[50],loop=0,optimize=True)
    table.append(f"| {name} | {max(old)}틱 | {end}틱 | {visible(before[key])}틱 | {visible(after[key])}틱 |")
(args.output/"timings-ko.md").write_text("\n".join(table)+"\n")

stage,draw=page("형성 · 동작 · 유지 · 감쇠 · 종료","오프라인 Blender · 각 단계의 배포 포즈",1190,2060)
near,view=page("3 · 8 · 16 · 24블록","오프라인 Blender · 1.8블록 표적 · 70° 시야",1190,2060)
for row,(key,name) in enumerate(list(labels.items())[:8]):
    frames=after[key]
    contact=(7,8,6,9,4,8,4,3)[row]
    ticks=(0,contact,int(frames[-3][0]),int(frames[-2][0]),int(frames[-1][0]))
    y=105+row*241
    draw.text((24,y),name,font=font(20,True),fill="#dfe9ef")
    view.text((24,y),name,font=font(20,True),fill="#dfe9ef")
    for col,(tick,label) in enumerate(zip(ticks,("형성","동작","유지","감쇠","종료"))):
        eligible=list(args.renders.glob(f"pacing-81-{key}-*.png"))
        path=min(eligible,key=lambda p: abs(tick-int(p.stem.rsplit("-",1)[1])))
        real_tick=int(path.stem.rsplit("-",1)[1])
        helpers["tile"](stage,draw,load(path),22+col*230,y+27,222,202,f"{label} · {real_tick}틱")
    for col,distance in enumerate((3,8,16,24)):
        helpers["tile"](near,view,load(args.renders/f"distance-{key}-{distance}.png"),22+col*289,y+27,276,202,f"{distance}블록")
stage.save(args.output/"stages.png",optimize=True)
near.save(args.output/"distances.png",optimize=True)
