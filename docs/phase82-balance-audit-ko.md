# PHASE 8.2 밸런스 감사

이번 배포에는 피해·확률·쿨다운·성장·보급 보상 수정 제안이 적용되지 않았다. 84종의 조건과 실제 활성화 지점은 [전수 활성화 감사](art71/augment-audit.md), 시각 계열과 16px 검토는 [아이콘 감사](art82/icons-audit-ko.md)에 연결한다. 다음 수치를 운영 서버 데이터로 확인하기 전에는 상향 또는 하향 판단을 내리지 않는다.

| 검토 범위 | 확인할 실제 기록 | 주의할 해석 |
| --- | --- | --- |
| 강철심장·누적의 공명·공격 추가 피해 | 회차별 보유, 확정 HP 손실, 진화 시점 | 긴 생존 시간이 성장과 승률을 함께 올릴 수 있다. |
| 무한 반복·마법 미사일·검술 연쇄 | 발동과 실제 체력 손실, 합성·원본 공격 구분 | 발동 건수는 고유 공격 횟수와 다르다. |
| 생명의 잔향·치명 방지 | 확정 보호막 흡수와 보유 표본 | 정상 체력 피해와 흡수량은 별개다. |
| 이동·채광·경험치 | 제시·선택·회차와 때에 따른 활용 | 직접 피해로 환산하지 않는다. |
| 보급 가치 | 지급 성공·실패, 등장 후 획득, 주변 교전 | 인벤토리에서의 실제 보상 사용까지는 수집하지 않았다. |

## 통계 보관

정상적인 일반 경기의 익명 집계는 plugins/AugmentWild/analytics 아래에 저장한다. 파일명은 경기 UUID이며 첫 행은 스키마, 경기 UUID, 게임 버전, UTC 종료 시각, 참가 인원이다. 이어지는 행은 증강 ID별 지표와 회차·희귀도별 제시 건수다. 운영 로그나 CSV에 참가자 UUID와 이름을 추가하지 않는다. HP 감소는 기존 전투 확인 경로에서 증강에 확정 귀속한 경우만 개별 기록하고 나머지는 unclassified로 집계한다.

/aw admin stats augment <id> [버전]에서 표본·비율·합계를 확인하고 /aw admin stats export는 analytics/export.csv 생성 작업을 예약한다. 소유 표본은 경기 중 한 번이라도 획득한 참가 기록이다. 마감 전에 취소된 경기, 1인 연습, 실험실은 저장 경로로 전달되지 않는다. 보관 기간 기본값은 90일이다. 손상 파일은 보존하고 WARN으로 보고한다. 같은 경기 ID 파일은 두 번 합산하지 않는다.

운영 서버의 실측 표본은 이번 개발 환경에 없다. 승률과 피해에 대한 조정안은 제시하지 않는다. 자동 시험의 표본은 실제 승률로 사용하지 않는다.

## PART 4 · 기본 설정과 실제 발동 경로

기본값의 `amount`는 HP·배율·지속량 등 증강에 따라 단위가 다르다. 확률 또는 실제 피해로 일괄 환산하지 않는다. 실제 계산은 아래 호출 경로와 설정을 함께 대조한다. 이 표는 운영 승률 자료가 아니다.

| 증강 · 등급 | 발동 조건 · 기준값 | 추가 발동 설정 | 조합 관계 | 실제 구현 경로 |
| --- | --- | --- | --- | --- |
| `afterimage_stride` 잔상 질주 · SILVER | 수평 이동을 누적해 다음 근접 공격 강화 및 신속 · amount=1.5, threshold=24, duration=6, cooldown=14, cap=0 | — | 그림자 · — | `PaperOfficialCombat.move → charge → visual` |
| `armor` 전투 적응 · GOLD | 기본 카탈로그 · PaperAugments.afterCombat · amount=3.0, duration=5, cooldown=12 | — | 금속 · — | `PaperAugments.afterCombat → trigger` |
| `berserk` 광전사의 심장 · PRISMATIC | 기본 카탈로그 · PaperAugments.afterCombat · amount=0.25, duration=5, cooldown=20 | — | 생명 · — | `PaperAugments.afterCombat → trigger` |
| `berserk_engine` 폭주 기관 · SILVER | 유효 타격마다 이동 속도 증가 · amount=0.06, threshold=0, duration=5, cooldown=0, cap=15 | — | 기계 · — | `PaperOfficialCombat.applyHitEffects` |
| `blood_crown` 피의 왕관 · GOLD | 근접 피해 흡혈, 플레이어 처치 시 흡수 체력 · amount=0.25, threshold=0, duration=0, cooldown=2, cap=0 | — | 생명 · — | `PaperOfficialCombat.applyHitEffects → gainHealth` |
| `blood_god` 피의 신 · PRISMATIC | 근접 흡혈과 플레이어 타격·처치에 따른 영구 성장 · amount=0.35, threshold=3, duration=0, cooldown=0, cap=10 | — | 생명 · stacking | `PaperOfficialCombat.applyHitEffects → grant` |
| `blood_record` 피의 기록 · SILVER | 자연 적대 몬스터 처치마다 영구 최대 체력 성장 · amount=2, threshold=5, duration=0, cooldown=0, cap=200 | — | 성장 · stacking | `PaperOfficialCombat.mobDeath → progress → grant` |
| `blood_trace` 혈흔 추적 · SILVER | 공격한 대상을 발광시킨 뒤 다음 타격 피해 증가 · amount=0.3, threshold=0, duration=6, cooldown=9, cap=0 | delay=0.5, damage=2, cooldown=14 | 생명 · — | `PaperOfficialCombat.applyHitEffects` |
| `bomb_alchemy` 폭탄 연금술 · GOLD | 전용 눈덩이 폭탄과 착탄 지점의 3초 불 지대 · amount=6, threshold=0, duration=3, cooldown=18, cap=0 | — | 기계 · — | `PaperOfficialCombat.cast` |
| `bounty_hunter` 현상금 사냥꾼 · GOLD | 적을 지정하여 피해 증가와 처치 시 회복·신속 · amount=0.25, threshold=0, duration=6, cooldown=12, cap=0 | — | 마법 · — | `PaperOfficialCombat.applyHitEffects` |
| `calamity_giant` 재앙의 거인 · PRISMATIC | 크기·최대 체력·사거리 증가, 질주 밀치기 및 지면 강타 · amount=50, threshold=0, duration=0, cooldown=8, cap=0 | — | 대지 · — | `PaperOfficialCombat.cast` |
| `center_of_universe` 우주의 중심 · PRISMATIC | 주변을 도는 오브가 가까운 적에게 자동 공격 · amount=4, threshold=6, duration=0, cooldown=1, cap=0 | — | 공허 · — | `PaperOfficialCombat.orbit → syntheticHit → announceProc` |
| `chain_impact` 연쇄 충격 · SILVER | 근접 누적 타격으로 주변 적에게 감속 충격파 · amount=3, threshold=3, duration=2, cooldown=6, cap=0 | — | 마법 · — | `PaperOfficialCombat.meleeEffects → area → syntheticHit → announceProc` |
| `clone_hand` 복제의 손 · PRISMATIC | 근접 타격 누적으로 원래 공격의 복제 피해 · amount=1, threshold=3, duration=0, cooldown=3, cap=0 | — | 그림자 · — | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` |
| `counter_shield` 역습의 방패 · SILVER | 방패로 피해 차단 시 회복하고 다음 근접 타격 강화 · amount=0.4, threshold=0, duration=0, cooldown=10, cap=0 | push=0.4 | 금속 · — | `PaperOfficialCombat.damageTaken` |
| `defense` 최후의 저항 · PRISMATIC | 기본 카탈로그 · PaperAugments.combat · amount=0.35, duration=0, cooldown=25 | — | 금속 · — | `PaperAugments.combat → PaperAugments.afterCombat` |
| `delayed_blast` 지연 폭발 · GOLD | 직접 피해 누적 시 마지막 적중 위치에 폭발 예고 · amount=4, threshold=12, duration=1, cooldown=20, cap=0 | — | 화염 · — | `PaperOfficialCombat.attackReactions → visual` |
| `draw_the_sword` 검을 뽑아라 · PRISMATIC | 근접 검기 적중마다 영구 검기 피해 성장과 방패 사용 제한 · amount=0.02, threshold=0, duration=0, cooldown=0, cap=200 | — | 검술 · stacking | `PaperOfficialCombat.drawSword → grant` |
| `dual_wielder` 양손잡이 · PRISMATIC | 보조 손 무기로 추가 공격하고 방패 사용 제한 · amount=1, threshold=0, duration=0, cooldown=0, cap=0 | — | 검술 · — | `PaperOfficialCombat.offhand → syntheticHit → announceProc` |
| `duel_contract` 결투의 계약 · GOLD | 지정 적과 결투하여 공격 피해와 받는 피해를 증가 · amount=0.3, threshold=0, duration=8, cooldown=18, cap=0 | — | 마법 · — | `PaperOfficialCombat.cast` |
| `earthquake_mage` 지진술사 · PRISMATIC | 돌진한 경로를 1초 뒤 폭발 · amount=10, threshold=0, duration=1, cooldown=12, cap=0 | — | 대지 · — | `PaperOfficialCombat.cast` |
| `endless_evolution` 끝없는 진화 · PRISMATIC | 적대 몹·플레이어 처치로 최대 체력과 피해 성장 · amount=2, threshold=8, duration=0, cooldown=0, cap=8 | — | 성장 · stacking | `PaperOfficialCombat.mobDeath → grant` |
| `eureka` 유레카 · GOLD | XP 레벨 5당 증강 재사용 대기시간 감소 · amount=0.05, threshold=5, duration=0, cooldown=0, cap=14 | — | 시간 · — | `PaperOfficialCombat.cooldown → PaperOfficialCombat.ready` |
| `explosive_arrow` 폭발 화살 · SILVER | 화살 적중 누적으로 주변 적에게 폭발 피해 · amount=4, threshold=3, duration=0, cooldown=8, cap=0 | — | 화염 · — | `PaperOfficialCombat.applyHitEffects → area → syntheticHit → announceProc` |
| `fall` 낙하 강타 · GOLD | 기본 카탈로그 · PaperAugments.combat · amount=0.25, duration=0, cooldown=0 | — | 대지 · — | `PaperAugments.combat → PaperAugments.afterCombat` |
| `falling_shockwave` 낙하 충격파 · SILVER | 높은 곳에서 낙하 시 피해 감소 및 주변 적에게 충격파 · amount=5, threshold=4, duration=0, cooldown=12, cap=0 | — | 대지 · — | `PaperOfficialCombat.evadeOrResist → area → syntheticHit → announceProc` |
| `final_judgment` 최후의 심판 · PRISMATIC | 저체력에서 5초 예고 후 잃은 체력에 비례하는 심판 · amount=30, threshold=0, duration=5, cooldown=35, cap=0 | — | 검술 · — | `PaperOfficialCombat.reactAtLowHealth` |
| `forbidden_deal` 금단의 거래 · PRISMATIC | 최대 체력을 줄이고 골드 추가 선택 및 공격 피해 증가 · amount=0.2, threshold=2, duration=0, cooldown=0, cap=0 | — | 공허 · — | `PaperAugments.chooseExchange → PaperOfficialCombat.exchangeCommitted` |
| `forbidden_reconstruction` 금단의 재구성 · PRISMATIC | 실버 교환, 프리즘 추가 선택, 체력 감소 및 자연 회복 제한 · amount=0.5, threshold=2, duration=10, cooldown=0, cap=0 | — | 공허 · — | `PaperOfficialCombat.exchangeCommitted` |
| `frenzy_rhythm` 광기의 리듬 · SILVER | 같은 적을 연속 타격할 때 피해 증가 · amount=0.07, threshold=0, duration=4, cooldown=0, cap=4 | speed-ticks=30, cooldown=12 | 이동 · — | `PaperOfficialCombat.applyHitEffects → announceProc` |
| `frost_bind` 서리 속박 · GOLD | 같은 적에게 직접 공격을 누적하면 추가 피해와 감속 · amount=2, threshold=3, duration=2, cooldown=14, cap=0 | — | 마법 · — | `PaperOfficialCombat.attackReactions → visual` |
| `giant_slayer` 거인 학살자 · GOLD | 상대 최대 체력이 높을수록 피해 증가 · amount=1.2, threshold=0, duration=0, cooldown=0, cap=0 | — | 대지 · — | `PaperOfficialCombat.confirmedHit` |
| `gigantification` 거인화 · PRISMATIC | 최대 체력·크기·근접 피해 증가 및 이동 속도 감소 · amount=20, threshold=0, duration=0, cooldown=0, cap=0 | — | 대지 · — | `PaperOfficialCombat.sync` |
| `heal` 응급 회복 · SILVER | 기본 카탈로그 · PaperAugments.afterCombat · amount=1.0, duration=4, cooldown=30 | — | 생명 · — | `PaperAugments.afterCombat → trigger` |
| `heart_harvester` 심장 수확자 · GOLD | 적대 몹과 플레이어 처치로 체력 성장, 플레이어 처치 시 회복 · amount=2, threshold=10, duration=0, cooldown=0, cap=8 | — | 생명 · stacking | `PaperOfficialCombat.mobDeath → grant` |
| `heavy_fist` 육중한 주먹 · SILVER | 자신의 최대 체력 비례 근접 추가 피해 · amount=0.08, threshold=0, duration=0, cooldown=0, cap=0 | — | 대지 · — | `PaperOfficialCombat.confirmedHit → announceProc` |
| `immortal_armor` 불멸의 갑옷 · GOLD | 받는 피해 일부를 흡수 체력으로 전환 · amount=0.35, threshold=0, duration=0, cooldown=12, cap=0 | — | 금속 · — | `PaperOfficialCombat.evadeOrResist` |
| `immortal_berserker` 불멸의 광전사 · PRISMATIC | 유효 타격으로 광폭화, 흡혈 및 경기당 1회 치명 피해 방지 · amount=1.0, threshold=10, duration=8, cooldown=0, cap=1 | — | 생명 · revival | `PaperOfficialCombat.revive → announceProc` |
| `infernal_conductor` 지옥의 전도체 · GOLD | 타격 시 화상 중첩, 처치 시 전염 및 누적 화상 피해로 진화 · amount=0.6, threshold=0, duration=6, cooldown=0, cap=6 | — | 화염 · — | `PaperOfficialCombat.tickBurns → syntheticHit → announceProc` |
| `infinite_repeat` 무한 반복 · PRISMATIC | 허용된 발동을 두 번, 매 세 번째에는 세 번 처리 · amount=2, threshold=3, duration=0, cooldown=0, cap=0 | — | 마법 · — | `PaperOfficialCombat.repetitions` |
| `iron_stomach` 철의 위장 · SILVER | 종류가 다른 음식마다 최대 체력 성장과 음식 섭취 시 회복 · amount=2, threshold=2, duration=0, cooldown=12, cap=4 | — | 금속 · stacking | `PaperOfficialCombat.eat → grant` |
| `jeweled_gauntlet` 보석 건틀릿 · PRISMATIC | 합성 증강 피해에 치명타 적용 · amount=2.5, threshold=0, duration=0, cooldown=0, cap=0 | — | 마법 · — | `PaperOfficialCombat.syntheticHit` |
| `kinetic_reversal` 반동 반격 · SILVER | 근접 피해를 견디면 다음 근접 공격으로 반격 · amount=2, threshold=6, duration=4, cooldown=16, cap=0 | — | 대지 · — | `PaperOfficialCombat.damageTaken → charge → visual` |
| `last_bastion` 최후의 요새 · PRISMATIC | 플레이어 직접 공격을 누적해서 방어 영역과 종료 충격파 발동 · amount=0.2, threshold=3, duration=3, cooldown=35, cap=0 | — | 금속 · — | `PaperOfficialCombat.damageTaken → charge → visual` |
| `last_demon` 최후의 악마 · PRISMATIC | 낮은 체력에서 강화 피해·흡혈·신속 II·저항 I · amount=0.6, threshold=0, duration=7, cooldown=30, cap=0 | — | 화염 · — | `PaperOfficialCombat.reactAtLowHealth` |
| `mad_scientist` 미친 과학자 · PRISMATIC | 주기적으로 거대·소형 상태를 오가며 체력 비율 유지 · amount=40, threshold=0, duration=12, cooldown=0, cap=0 | — | 마법 · — | `PaperOfficialCombat.sync` |
| `magic_missile` 마법 미사일 · GOLD | 유효 타격마다 추적 미사일을 준비하고 누적 발사 수에 따라 진화 · amount=3, threshold=3, duration=0, cooldown=0, cap=0 | window=5, threshold=3, damage=3, cooldown=14 | 마법 · — | `PaperOfficialCombat.advanceMissiles → syntheticHit → announceProc` |
| `miner` 광부의 기세 · SILVER | 기본 카탈로그 · PaperAugments.mine · amount=0.35, duration=6, cooldown=12 | — | 대지 · — | `PaperAugments.mine → trigger` |
| `mineral_addiction` 광물 중독 · SILVER | 자연 광석 채굴에 따라 공격 피해 증가 · amount=0.04, threshold=12, duration=0, cooldown=0, cap=200 | — | 성장 · stacking | `PaperOfficialCombat.breakOre → progress → grant` |
| `mineral_devourer` 광물 포식자 · GOLD | 자연 광석 누적으로 근접 피해, 목숨 제한 시작 때 황금사과 · amount=0.03, threshold=10, duration=0, cooldown=0, cap=8 | — | 성장 · stacking | `PaperOfficialCombat.breakOre → progress → grant` |
| `muscle_memory` 근육 기억 · SILVER | 자연 적대 몬스터 처치로 근접 피해 증가 · amount=0.02, threshold=8, duration=0, cooldown=0, cap=10 | — | 대지 · stacking | `PaperOfficialCombat.mobDeath → progress → grant` |
| `ore_ward` 광맥 방벽 · SILVER | 자연 광석을 채굴해 다음 공격 피해를 막는 방벽 충전 · amount=2, threshold=8, duration=0, cooldown=10, cap=2 | — | 금속 · — | `PaperOfficialCombat.breakOre` |
| `overgrowth` 과잉 성장 · SILVER | 획득 및 경기 시간이 지나면 최대 체력 성장 · amount=16, threshold=300, duration=0, cooldown=0, cap=0 | — | 성장 · — | `PaperOfficialCombat.sync` |
| `phantom_weapon` 환영 무기 · GOLD | 화살·검기 적중 시 허용된 근접 효과만 발동 · amount=1, threshold=0, duration=0, cooldown=0, cap=0 | — | 그림자 · — | `PaperOfficialCombat.meleeEffects` |
| `phoenix_heart` 불사조 심장 · PRISMATIC | 경기당 한 번 치명 피해 방지 및 회복과 밀쳐내기 · amount=0.5, threshold=1, duration=2, cooldown=0, cap=0 | — | 생명 · revival | `PaperOfficialCombat.revive → announceProc` |
| `predator` 포식자 · SILVER | 근접 유효 타격 누적으로 회복 · amount=3, threshold=3, duration=0, cooldown=5, cap=0 | ratio=0.5, cap=2, duration=4 | 생명 · — | `PaperOfficialCombat.meleeEffects → gainHealth` |
| `pursuit` 연쇄 추격 · PRISMATIC | 기본 카탈로그 · PaperAugments.afterCombat · amount=0.2, duration=4, cooldown=12 | — | 이동 · — | `PaperAugments.afterCombat → trigger` |
| `rear` 배후 공격 · GOLD | 기본 카탈로그 · PaperAugments.combat · amount=0.2, duration=0, cooldown=0 | — | 그림자 · — | `PaperAugments.combat → PaperAugments.afterCombat` |
| `reflex` 반사 신경 · SILVER | 위협 공격 회피와 공격자 쪽 안전 이동 · amount=1, threshold=0, duration=0, cooldown=12, cap=0 | duration=3, ratio=0.15 | 이동 · — | `PaperOfficialCombat.evadeOrResist` |
| `ricochet_arrow` 도탄 화살 · GOLD | 직접 발사한 화살 명중 시 다른 적에게 도탄 · amount=0.35, threshold=0, duration=0, cooldown=12, cap=4 | — | 마법 · — | `PaperOfficialCombat.attackReactions → visual` |
| `russian_doll` 러시아 인형 · PRISMATIC | 치명 피해를 제한적으로 막고 체력 감소와 이동 가속 · amount=0.25, threshold=3, duration=0, cooldown=0, cap=0 | — | 생명 · revival | `PaperOfficialCombat.revive → announceProc` |
| `shadow_dash` 그림자 도약 · GOLD | 바라보는 방향으로 돌진하고 3초 내 첫 타격 증가 · amount=0.5, threshold=0, duration=3, cooldown=15, cap=0 | — | 그림자 · — | `PaperOfficialCombat.cast` |
| `speed` 기동 훈련 · SILVER | 기본 카탈로그 · PaperAugments.syncEffects · amount=0.08, duration=0, cooldown=0 | — | 이동 · — | `PaperAugments.syncEffects → PaperAugments.setModifier` |
| `split_attack` 분열하는 공격 · GOLD | 유효 공격 네 번 후 다음 공격을 세 번으로 분할 · amount=0.75, threshold=4, duration=0, cooldown=0, cap=0 | — | 검술 · — | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` |
| `split_bow` 분열의 활 · GOLD | 발사 3회마다 본 화살 피해를 높이고 두 갈래 추가 투사체 발사 · amount=1.25, threshold=3, duration=0, cooldown=6, cap=0 | — | 마법 · — | `PaperOfficialCombat.fireBow` |
| `stack_dinosaur` 중첩 공룡 · SILVER | 다른 영구 스택 획득 배율 · amount=2, threshold=0, duration=0, cooldown=0, cap=0 | — | 성장 · — | `PaperOfficialCombat.grant` |
| `steel_heart` 강철심장 · GOLD | 근접 유효 타격으로 영구 최대 체력 증가 · amount=2, threshold=0, duration=0, cooldown=10, cap=200 | — | 금속 · stacking | `PaperOfficialCombat.meleeEffects → grant` |
| `storm_blade` 폭풍의 검 · GOLD | 근접 네 번째 타격으로 대상과 주변에 낙뢰 피해 · amount=6, threshold=4, duration=0, cooldown=8, cap=0 | — | 검술 · — | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` |
| `storm_chaser` 폭풍 추적자 · GOLD | 유효 이동 후 다음 타격에 낙뢰 · amount=12, threshold=6, duration=0, cooldown=8, cap=0 | duration=2, radius=2, cooldown=12 | 이동 · — | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` |
| `survival_instinct` 생존 본능 · SILVER | 저체력에서 체력 회복과 신속 II · amount=6, threshold=0, duration=4, cooldown=22, cap=0 | — | 생명 · — | `PaperOfficialCombat.reactAtLowHealth → gainHealth` |
| `sword_wind` 검풍 · SILVER | 근접 타격 누적으로 벽을 관통하지 않는 전방 검기 · amount=8, threshold=3, duration=0, cooldown=0, cap=0 | duration=5, damage=2, cooldown=15 | 검술 · — | `PaperOfficialCombat.sword → syntheticHit → announceProc` |
| `tank_engine` 탱크 엔진 · GOLD | 수평 이동 거리마다 크기와 최대 체력 성장 · amount=4, threshold=250, duration=0, cooldown=0, cap=20 | — | 기계 · stacking | `PaperOfficialCombat.move → grant` |
| `time_rewind` 시간 역행 · PRISMATIC | 4초 전 안전 위치로 복귀하고 당시 체력까지 제한 회복 · amount=14, threshold=4, duration=0, cooldown=35, cap=0 | — | 시간 · — | `PaperOfficialCombat.cast` |
| `time_thief` 시간 도둑 · GOLD | 타격한 적의 이로운 효과를 줄이고 감속과 신속을 부여 · amount=1, threshold=0, duration=4, cooldown=12, cap=0 | — | 시간 · — | `PaperOfficialCombat.stealTime` |
| `triad_reactor` 삼중 반응로 · PRISMATIC | 근접·원거리·증강 또는 방어 반응 후 다음 공격이 폭발 · amount=6, threshold=0, duration=8, cooldown=30, cap=0 | — | 기계 · — | `PaperOfficialCombat.triad → charge → visual` |
| `ultimate_hydra` 궁극의 히드라 · GOLD | 근접 피해를 주변 적에게 전파하고 네 번째 타격마다 넓은 충격파 · amount=0.5, threshold=4, duration=0, cooldown=0, cap=0 | — | 검술 · — | `PaperOfficialCombat.hydra → syntheticHit → announceProc` |
| `vein_overload` 광맥 과부하 · SILVER | 자연 광석 채굴로 이동 속도 성장, PvP 시작 시 흡수 체력 · amount=0.02, threshold=10, duration=0, cooldown=0, cap=8 | — | 대지 · stacking | `PaperOfficialCombat.sync` |
| `venom_swarm` 맹독 군락 · GOLD | 근접 중독과 중독시킨 적 처치 시 주변 맹독 · amount=4, threshold=0, duration=4, cooldown=10, cap=0 | — | 마법 · — | `PaperOfficialCombat.applyVenom` |
| `void_lord` 공허 군주 · PRISMATIC | 주변을 당기고 2초간 공허 피해 · amount=8, threshold=0, duration=2, cooldown=25, cap=0 | — | 공허 · — | `PaperOfficialCombat.cast` |
| `world_erosion` 세계 침식 · PRISMATIC | 주변 적의 최대 체력을 낮추고 일부를 흡수 체력으로 획득 · amount=2, threshold=0, duration=10, cooldown=3, cap=0 | — | 공허 · — | `PaperOfficialCombat.worldErosion` |
| `wound_record` 상처의 기록 · SILVER | 받은 피해 누적으로 체력 성장 및 저체력 진입 시 저항 · amount=2, threshold=20, duration=4, cooldown=22, cap=4 | — | 성장 · stacking | `PaperOfficialCombat.damageTaken → grant` |
| `xp_gold` 경험 가속 · GOLD | 기본 카탈로그 · AugmentCatalog.load · amount=1.0, duration=0, cooldown=0 | — | 성장 · — | `AugmentCatalog.load → PaperAugments.experience` |
| `xp_prism` 경험 폭발 · PRISMATIC | 기본 카탈로그 · AugmentCatalog.load · amount=2.0, duration=0, cooldown=0 | — | 성장 · — | `AugmentCatalog.load → PaperAugments.experience` |
| `xp_silver` 경험 축적 · SILVER | 기본 카탈로그 · AugmentCatalog.load · amount=0.5, duration=0, cooldown=0 | — | 성장 · — | `AugmentCatalog.load → PaperAugments.experience` |

## 중첩 위험 검토

합성 피해의 깊이 제한과 원본 공격의 재귀 방지는 기존 회귀를 재실행한다. 치명 방지와 보호막은 취소 처리·확정 소비 시점이 달라 기존 Paper 프로브의 뒤늦은 취소 및 값 수정 사례를 유지했다. 개별 조합의 운영 승률·숙련도·실제 밸런스는 평가할 표본이 없다. 이번 변경에는 피해·쿨다운·확률·보급 보상 변경이 없다.
