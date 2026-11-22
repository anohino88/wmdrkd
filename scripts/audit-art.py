#!/usr/bin/env python3
"""Check selected production call sites and emit the Korean per-augment art inventory."""
import argparse
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PAPER=ROOT/"src/main/java/io/github/augmentwild/paper"
OUTPUT=ROOT/"docs/art71/augment-audit.md"
# A representative activation path for every ID. The acquisition/preview paths do not count.
ROUTES = """
grant:stack_dinosaur
confirmedHit announceProc:heavy_fist
mobDeath progress grant:blood_record muscle_memory
breakOre progress grant:mineral_addiction mineral_devourer
sword syntheticHit announceProc:sword_wind
applyHitEffects:berserk_engine blood_trace bounty_hunter
evadeOrResist:reflex
sync:overgrowth gigantification mad_scientist vein_overload
meleeEffects grant:steel_heart
hydra syntheticHit announceProc:ultimate_hydra
tickBurns syntheticHit announceProc:infernal_conductor
ready:eureka
advanceMissiles syntheticHit announceProc:magic_missile
meleeEffects:phantom_weapon
move grant:tank_engine
confirmedHit:giant_slayer
applyHitEffects syntheticHit announceProc:split_attack storm_chaser storm_blade clone_hand
cast:calamity_giant earthquake_mage shadow_dash duel_contract time_rewind void_lord bomb_alchemy
repetitions:infinite_repeat
syntheticHit:jeweled_gauntlet
revive announceProc:immortal_berserker russian_doll phoenix_heart
offhand syntheticHit announceProc:dual_wielder
orbit syntheticHit announceProc:center_of_universe
drawSword grant:draw_the_sword
reactAtLowHealth:final_judgment last_demon
exchangeCommitted:forbidden_reconstruction forbidden_deal
eat grant:iron_stomach
damageTaken grant:wound_record
meleeEffects area syntheticHit announceProc:chain_impact
applyHitEffects announceProc:frenzy_rhythm
reactAtLowHealth gainHealth:survival_instinct
meleeEffects gainHealth:predator
evadeOrResist area syntheticHit announceProc:falling_shockwave
applyHitEffects area syntheticHit announceProc:explosive_arrow
damageTaken:counter_shield
mobDeath grant:heart_harvester endless_evolution
applyHitEffects gainHealth:blood_crown
evadeOrResist:immortal_armor
applyVenom:venom_swarm
fireBow:split_bow
stealTime:time_thief
applyHitEffects grant:blood_god
worldErosion:world_erosion
damageTaken charge visual:kinetic_reversal last_bastion
breakOre:ore_ward
move charge visual:afterimage_stride
attackReactions visual:ricochet_arrow frost_bind delayed_blast
triad charge visual:triad_reactor
experience:xp_silver xp_gold xp_prism
mine trigger:miner
setModifier:speed
afterCombat trigger:heal armor berserk pursuit
afterCombat:rear fall defense
"""
BASE_EFFECT = dict(xp_silver="XP",xp_gold="XP",xp_prism="XP",miner="MINING",speed="SPEED",heal="HEAL",
                   rear="REAR",fall="FALL",armor="ARMOR",berserk="BERSERK",defense="DEFENSE",pursuit="PURSUIT")
DISPATCH = {"forbidden_deal":"PaperAugments.chooseExchange","xp_silver":"AugmentCatalog.load","xp_gold":"AugmentCatalog.load","xp_prism":"AugmentCatalog.load",
            "rear":"PaperAugments.combat","fall":"PaperAugments.combat","defense":"PaperAugments.combat","eureka":"PaperOfficialCombat.cooldown",
            "speed":"PaperAugments.syncEffects"}

def bodies(source,method):
    # Restrict each match to a declared instance method. Join overloads (e.g. grant).
    pattern = r"^    (?:public |private |protected |static |final )*[\w<>?,.]+ "+method+r"\([^;{}]*\)\s*\{"
    definitions=list(re.finditer(pattern,source,re.M))
    assert definitions,method
    result=[]
    for m in definitions:
        end=source.index("\n    }",m.end())
        result.append(source[m.end():end])
    return "\n".join(result)

def report():
    names=(ROOT/"src/main/resources/official-augments.yml").read_text().split("\nsettings:")[0]
    conditions=dict(re.findall(r"^  (\w+):\n    group: [\s\S]*?    condition: '([^']+)'",names,re.M))
    old=(ROOT/"docs/phase63-audit-ko.md").read_text()
    conditions.update({k:c for k,c in re.findall(r"^\| `(\w+)` \| ([^|]+) \|",old,re.M) if k in BASE_EFFECT})
    routes={key:path.split() for row in ROUTES.strip().splitlines() for path,ids in [row.split(":")] for key in ids.split()}
    motifs=dict(line.split("=") for line in (ROOT/"src/main/resources/art-motifs.properties").read_text().splitlines())
    assert set(routes)==set(conditions)==set(motifs) and len(routes)==84
    sources={p.stem:p.read_text() for p in PAPER.glob("*.java")}
    rows=[]
    for key,route in routes.items():
        src="PaperAugments" if key in BASE_EFFECT else "PaperOfficialCombat"
        code=[bodies(sources[src],method) for method in route]
        for body,callee in zip(code,route[1:]):
            assert callee+"(" in body,(key,callee)
        assert "art.cue(" in code[-1] or "art.cast(" in code[-1],(key,route[-1])
        tag='"'+key+'"' if key not in BASE_EFFECT else "Effect."+BASE_EFFECT[key]
        dispatch=DISPATCH.get(key)
        dispatch_code=bodies(sources[dispatch.split(".")[0]],dispatch.split(".")[1]) if dispatch else ""
        assert tag in "".join(code)+dispatch_code or (key=="forbidden_deal" and "exchangeId" in dispatch_code
            and '"forbidden_deal"' in sources["PaperAugments"]),key
        model=ROOT/f"resource-pack/pack/assets/augmentwild/models/proc/{key}.json"
        assert json.loads(model.read_text())["textures"]["sigil"]=="augmentwild:item/augment/"+key
        family=motifs[key]
        fallback=({"fire":"FLAME","frost":"SNOWFLAKE","time":"SNOWFLAKE","blade":"CRIT","impact":"CRIT","void":"PORTAL",
                  "growth":"HAPPY_VILLAGER","blood":"HAPPY_VILLAGER"}).get(family,"ELECTRIC_SPARK")
        path=(dispatch+" → " if dispatch else "")+src+"."+" → ".join(route)
        rows.append(f"| `{key}` | {conditions[key]} | `{path}` | `{family}` | {fallback} | PASS | NOT_RUN |")
    return """# 증강 발동 연출 대조표

각 ID는 고유 아이콘 `augment/<ID>`와 전투용 `proc/<ID>`를 가진다. 전투 모델은 해당 Blender 형태와 고유 문양을 결합한다. 아래 경로는 해당 증강의 실제 효과를 대표하는 발동 또는 능력 적용 지점이다. 충전·스택 증가·교환은 각각 해당 상태가 변경될 때 표시한다.

발동 시 전용 모델을 열고 형태별로 3~5틱 발동과 5~10틱 소멸을 보간한 뒤 제거한다. 패밀리별 이동·회전·확장과 액티브 7종의 개별 모션을 사용한다. 효과음은 Kenney의 `augmentwild:cast`이며 패밀리별 음높이를 사용한다. 간소 표현은 본인에게만 바닐라 파티클 4개와 자수정 소리를 재생한다. 소리·시각 설정과 동시 표시 상한이 적용된다. 아래 종료는 시각 연출의 종료다. 버프 만료 시각은 기존 전투 원장이 관리한다.

**검사 범위**  소스 검사는 카탈로그의 전체 ID, 선언된 실제 메서드, 호출 간선, 시각 출력 지점, 모델의 고유 텍스처 참조를 확인한다. Paper 연출 프로브는 별도로 합성 뷰어에게 84개를 재생해 실제 엔티티의 모델 ID와 수명을 검사한다. 개별 실전 발동의 시청각 일치는 클라이언트 검증 열에 기록한다.

| ID · 아이콘 · 효과 모델 | 발동 조건 | 코드 경로 | Blender 원형 | 간소 파티클 | 소스·모델 검사 | 클라이언트 |
|---|---|---|---|---|---|---|
"""+ "\n".join(sorted(rows))+"\n"

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--write",action="store_true")
    args=parser.parse_args()
    result=report()
    if args.write: OUTPUT.write_text(result)
    else: assert result==OUTPUT.read_text(),"Regenerate the augment audit"
    print("ART_ROUTES_PASS catalog=84 proc_models=84")
