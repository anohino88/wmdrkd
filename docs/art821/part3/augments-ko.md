# 증강 84종 발동 연결

이름·등급·효과는 배포 카탈로그를 따르고 주요 실제 호출 간선을 감사했다. 일반 발동은 계열 모델에 고유 문양을 결합한다.
간소 모드는 바닐라 파티클을 사용한다. 지속·공개 항목은 확인된 대표 경로다. 그래픽 클라이언트 검증은 84종 모두 NOT_RUN이다.

| ID · 이름 · 희귀도 | 조건과 효과 · 기본 설정 | 실제 코드 | 계열 · 모델 | 모션 · 지속/종료 | 파티클 대체 · 가시성 | 음원 |
| --- | --- | --- | --- | --- | --- | --- |
| `afterimage_stride` · 잔상 질주 · SILVER | 수평 이동을 누적해 다음 근접 공격 강화 및 신속 · 1.5 | `PaperOfficialCombat.move → charge → visual` | 그림자 · `proc/afterimage_stride` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_shadow` |
| `armor` · 전투 적응 · GOLD | 상대 근접 피해 · 방어력 | `PaperAugments.afterCombat → trigger` | 금속 · `proc/armor` | 경기 시계 오라·만료 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `berserk_engine` · 폭주 기관 · SILVER | 유효 타격마다 이동 속도 증가 · 0.06 | `PaperOfficialCombat.applyHitEffects` | 기계 · `proc/berserk_engine` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_machine` |
| `berserk` · 광전사의 심장 · PRISMATIC | 공격자 저체력 근접 · % 공격력 | `PaperAugments.afterCombat → trigger` | 생명 · `proc/berserk` | 경기 시계 오라·만료 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `blood_crown` · 피의 왕관 · GOLD | 근접 피해 흡혈, 플레이어 처치 시 흡수 체력 · 0.25 | `PaperOfficialCombat.applyHitEffects → gainHealth` | 생명 · `proc/blood_crown` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `blood_god` · 피의 신 · PRISMATIC | 근접 흡혈과 플레이어 타격·처치에 따른 영구 성장 · 0.35 | `PaperOfficialCombat.applyHitEffects → grant` | 생명 · `proc/blood_god` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `blood_record` · 피의 기록 · SILVER | 자연 적대 몬스터 처치마다 영구 최대 체력 성장 · 2 | `PaperOfficialCombat.mobDeath → progress → grant` | 성장 · `proc/blood_record` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `blood_trace` · 혈흔 추적 · SILVER | 공격한 대상을 발광시킨 뒤 다음 타격 피해 증가 · 0.3 | `PaperOfficialCombat.applyHitEffects` | 생명 · `proc/blood_trace` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `bomb_alchemy` · 폭탄 연금술 · GOLD | 전용 눈덩이 폭탄과 착탄 지점의 3초 불 지대 · 6 | `PaperOfficialCombat.cast` | 기계 · `proc/bomb_alchemy / fx/sig_bomb_alchemy` | 6키 · 30틱 · ignite · 투사체 비행·명중·폭발 반경과 제거 | FLAME · 전투 사건에서 공개 | `family_machine / ignite` |
| `bounty_hunter` · 현상금 사냥꾼 · GOLD | 적을 지정하여 피해 증가와 처치 시 회복·신속 · 0.25 | `PaperOfficialCombat.applyHitEffects` | 마법 · `proc/bounty_hunter` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_arcane` |
| `calamity_giant` · 재앙의 거인 · PRISMATIC | 크기·최대 체력·사거리 증가, 질주 밀치기 및 지면 강타 · 50 | `PaperOfficialCombat.cast` | 대지 · `proc/calamity_giant / fx/sig_calamity_giant` | 8키 · 56틱 · stone_break · 실제 지면 충격 반경과 달리기 발자국 | CRIT · 전투 사건에서 공개 | `family_earth / stone_break` |
| `center_of_universe` · 우주의 중심 · PRISMATIC | 주변을 도는 오브가 가까운 적에게 자동 공격 · 4 | `PaperOfficialCombat.orbit → syntheticHit → announceProc` | 공허 · `proc/center_of_universe` | 발동 모션 후 제거 | PORTAL · 확정 연쇄 피해 | `family_void` |
| `chain_impact` · 연쇄 충격 · SILVER | 근접 누적 타격으로 주변 적에게 감속 충격파 · 3 | `PaperOfficialCombat.meleeEffects → area → syntheticHit → announceProc` | 마법 · `proc/chain_impact` | 발동 모션 후 제거 | ELECTRIC_SPARK · 확정 연쇄 피해 | `family_arcane` |
| `clone_hand` · 복제의 손 · PRISMATIC | 근접 타격 누적으로 원래 공격의 복제 피해 · 1 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 그림자 · `proc/clone_hand` | 발동 모션 후 제거 | ELECTRIC_SPARK · 확정 연쇄 피해 | `family_shadow` |
| `counter_shield` · 역습의 방패 · SILVER | 방패로 피해 차단 시 회복하고 다음 근접 타격 강화 · 0.4 | `PaperOfficialCombat.damageTaken` | 금속 · `proc/counter_shield` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `defense` · 최후의 저항 · PRISMATIC | 강한 상대 근접 공격 · % 피해 감소 | `PaperAugments.combat → PaperAugments.afterCombat` | 금속 · `proc/defense` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `delayed_blast` · 지연 폭발 · GOLD | 직접 피해 누적 시 마지막 적중 위치에 폭발 예고 · 4 | `PaperOfficialCombat.attackReactions → visual` | 화염 · `proc/delayed_blast` | 발동 모션 후 제거 | FLAME · 전투 사건에서 공개 | `family_flame` |
| `draw_the_sword` · 검을 뽑아라 · PRISMATIC | 근접 검기 적중마다 영구 검기 피해 성장과 방패 사용 제한 · 0.02 | `PaperOfficialCombat.drawSword → grant` | 검술 · `proc/draw_the_sword` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_sword` |
| `dual_wielder` · 양손잡이 · PRISMATIC | 보조 손 무기로 추가 공격하고 방패 사용 제한 · 1 | `PaperOfficialCombat.offhand → syntheticHit → announceProc` | 검술 · `proc/dual_wielder` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_sword` |
| `duel_contract` · 결투의 계약 · GOLD | 지정 적과 결투하여 공격 피해와 받는 피해를 증가 · 0.3 | `PaperOfficialCombat.cast` | 마법 · `proc/duel_contract / fx/sig_duel_contract` | 6키 · 48틱 · seal · 상호 시야의 계약 연결과 상태 오라 | ELECTRIC_SPARK · 개인 발동 | `family_arcane / seal` |
| `earthquake_mage` · 지진술사 · PRISMATIC | 돌진한 경로를 1초 뒤 폭발 · 10 | `PaperOfficialCombat.cast` | 대지 · `proc/earthquake_mage / fx/sig_earthquake_mage` | 7키 · 52틱 · stone_break · 실제 출발점과 지연된 경로 타격 | CRIT · 전투 사건에서 공개 | `family_earth / stone_break` |
| `endless_evolution` · 끝없는 진화 · PRISMATIC | 적대 몹·플레이어 처치로 최대 체력과 피해 성장 · 2 | `PaperOfficialCombat.mobDeath → grant` | 성장 · `proc/endless_evolution` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `eureka` · 유레카 · GOLD | XP 레벨 5당 증강 재사용 대기시간 감소 · 0.05 | `PaperOfficialCombat.cooldown → PaperOfficialCombat.ready` | 시간 · `proc/eureka` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_time` |
| `explosive_arrow` · 폭발 화살 · SILVER | 화살 적중 누적으로 주변 적에게 폭발 피해 · 4 | `PaperOfficialCombat.applyHitEffects → area → syntheticHit → announceProc` | 화염 · `proc/explosive_arrow` | 발동 모션 후 제거 | FLAME · 확정 연쇄 피해 | `family_flame` |
| `fall` · 낙하 강타 · GOLD | 공중 낙하 중 근접 · % 추가 피해 | `PaperAugments.combat → PaperAugments.afterCombat` | 대지 · `proc/fall` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `falling_shockwave` · 낙하 충격파 · SILVER | 높은 곳에서 낙하 시 피해 감소 및 주변 적에게 충격파 · 5 | `PaperOfficialCombat.evadeOrResist → area → syntheticHit → announceProc` | 대지 · `proc/falling_shockwave` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_earth` |
| `final_judgment` · 최후의 심판 · PRISMATIC | 저체력에서 5초 예고 후 잃은 체력에 비례하는 심판 · 30 | `PaperOfficialCombat.reactAtLowHealth` | 검술 · `proc/final_judgment` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_sword` |
| `forbidden_deal` · 금단의 거래 · PRISMATIC | 최대 체력을 줄이고 골드 추가 선택 및 공격 피해 증가 · 0.2 | `PaperAugments.chooseExchange → PaperOfficialCombat.exchangeCommitted` | 공허 · `proc/forbidden_deal` | 발동 모션 후 제거 | PORTAL · 개인 발동 | `family_void` |
| `forbidden_reconstruction` · 금단의 재구성 · PRISMATIC | 실버 교환, 프리즘 추가 선택, 체력 감소 및 자연 회복 제한 · 0.5 | `PaperOfficialCombat.exchangeCommitted` | 공허 · `proc/forbidden_reconstruction` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_void` |
| `frenzy_rhythm` · 광기의 리듬 · SILVER | 같은 적을 연속 타격할 때 피해 증가 · 0.07 | `PaperOfficialCombat.applyHitEffects → announceProc` | 이동 · `proc/frenzy_rhythm` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_motion` |
| `frost_bind` · 서리 속박 · GOLD | 같은 적에게 직접 공격을 누적하면 추가 피해와 감속 · 2 | `PaperOfficialCombat.attackReactions → visual` | 마법 · `proc/frost_bind` | 발동 모션 후 제거 | SNOWFLAKE · 개인 발동 | `family_arcane` |
| `giant_slayer` · 거인 학살자 · GOLD | 상대 최대 체력이 높을수록 피해 증가 · 1.2 | `PaperOfficialCombat.confirmedHit` | 대지 · `proc/giant_slayer` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `gigantification` · 거인화 · PRISMATIC | 최대 체력·크기·근접 피해 증가 및 이동 속도 감소 · 20 | `PaperOfficialCombat.sync` | 대지 · `proc/gigantification` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `heal` · 응급 회복 · SILVER | 피격 후 저체력 · 체력/초 | `PaperAugments.afterCombat → trigger` | 생명 · `proc/heal` | 경기 시계 오라·만료 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `heart_harvester` · 심장 수확자 · GOLD | 적대 몹과 플레이어 처치로 체력 성장, 플레이어 처치 시 회복 · 2 | `PaperOfficialCombat.mobDeath → grant` | 생명 · `proc/heart_harvester` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_life` |
| `heavy_fist` · 육중한 주먹 · SILVER | 자신의 최대 체력 비례 근접 추가 피해 · 0.08 | `PaperOfficialCombat.confirmedHit → announceProc` | 대지 · `proc/heavy_fist` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `immortal_armor` · 불멸의 갑옷 · GOLD | 받는 피해 일부를 흡수 체력으로 전환 · 0.35 | `PaperOfficialCombat.evadeOrResist` | 금속 · `proc/immortal_armor` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `immortal_berserker` · 불멸의 광전사 · PRISMATIC | 유효 타격으로 광폭화, 흡혈 및 경기당 1회 치명 피해 방지 · 1.0 | `PaperOfficialCombat.revive → announceProc` | 생명 · `proc/immortal_berserker` | 경기 시계 오라·만료 | HAPPY_VILLAGER · 전투 사건에서 공개 | `family_life` |
| `infernal_conductor` · 지옥의 전도체 · GOLD | 타격 시 화상 중첩, 처치 시 전염 및 누적 화상 피해로 진화 · 0.6 | `PaperOfficialCombat.tickBurns → syntheticHit → announceProc` | 화염 · `proc/infernal_conductor` | 발동 모션 후 제거 | FLAME · 확정 연쇄 피해 | `family_flame` |
| `infinite_repeat` · 무한 반복 · PRISMATIC | 허용된 발동을 두 번, 매 세 번째에는 세 번 처리 · 2 | `PaperOfficialCombat.repetitions` | 마법 · `proc/infinite_repeat` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_arcane` |
| `iron_stomach` · 철의 위장 · SILVER | 종류가 다른 음식마다 최대 체력 성장과 음식 섭취 시 회복 · 2 | `PaperOfficialCombat.eat → grant` | 금속 · `proc/iron_stomach` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `jeweled_gauntlet` · 보석 건틀릿 · PRISMATIC | 합성 증강 피해에 치명타 적용 · 2.5 | `PaperOfficialCombat.syntheticHit` | 마법 · `proc/jeweled_gauntlet` | 발동 모션 후 제거 | ELECTRIC_SPARK · 확정 연쇄 피해 | `family_arcane` |
| `kinetic_reversal` · 반동 반격 · SILVER | 근접 피해를 견디면 다음 근접 공격으로 반격 · 2 | `PaperOfficialCombat.damageTaken → charge → visual` | 대지 · `proc/kinetic_reversal` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `last_bastion` · 최후의 요새 · PRISMATIC | 플레이어 직접 공격을 누적해서 방어 영역과 종료 충격파 발동 · 0.2 | `PaperOfficialCombat.damageTaken → charge → visual` | 금속 · `proc/last_bastion` | 발동 모션 후 제거 | ELECTRIC_SPARK · 전투 사건에서 공개 | `family_metal` |
| `last_demon` · 최후의 악마 · PRISMATIC | 낮은 체력에서 강화 피해·흡혈·신속 II·저항 I · 0.6 | `PaperOfficialCombat.reactAtLowHealth` | 화염 · `proc/last_demon` | 경기 시계 오라·만료 | FLAME · 개인 발동 | `family_flame` |
| `mad_scientist` · 미친 과학자 · PRISMATIC | 주기적으로 거대·소형 상태를 오가며 체력 비율 유지 · 40 | `PaperOfficialCombat.sync` | 마법 · `proc/mad_scientist` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_arcane` |
| `magic_missile` · 마법 미사일 · GOLD | 유효 타격마다 추적 미사일을 준비하고 누적 발사 수에 따라 진화 · 3 | `PaperOfficialCombat.advanceMissiles → syntheticHit → announceProc` | 마법 · `proc/magic_missile` | 발동 모션 후 제거 | ELECTRIC_SPARK · 전투 사건에서 공개 | `family_arcane` |
| `miner` · 광부의 기세 · SILVER | 드롭 허용 광석 채굴 · % 채굴 속도 | `PaperAugments.mine → trigger` | 대지 · `proc/miner` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `mineral_addiction` · 광물 중독 · SILVER | 자연 광석 채굴에 따라 공격 피해 증가 · 0.04 | `PaperOfficialCombat.breakOre → progress → grant` | 성장 · `proc/mineral_addiction` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_growth` |
| `mineral_devourer` · 광물 포식자 · GOLD | 자연 광석 누적으로 근접 피해, 목숨 제한 시작 때 황금사과 · 0.03 | `PaperOfficialCombat.breakOre → progress → grant` | 성장 · `proc/mineral_devourer` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `muscle_memory` · 근육 기억 · SILVER | 자연 적대 몬스터 처치로 근접 피해 증가 · 0.02 | `PaperOfficialCombat.mobDeath → progress → grant` | 대지 · `proc/muscle_memory` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_earth` |
| `ore_ward` · 광맥 방벽 · SILVER | 자연 광석을 채굴해 다음 공격 피해를 막는 방벽 충전 · 2 | `PaperOfficialCombat.breakOre` | 금속 · `proc/ore_ward` | 발동 모션 후 제거 | ELECTRIC_SPARK · 전투 사건에서 공개 | `family_metal` |
| `overgrowth` · 과잉 성장 · SILVER | 획득 및 경기 시간이 지나면 최대 체력 성장 · 16 | `PaperOfficialCombat.sync` | 성장 · `proc/overgrowth` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `phantom_weapon` · 환영 무기 · GOLD | 화살·검기 적중 시 허용된 근접 효과만 발동 · 1 | `PaperOfficialCombat.meleeEffects` | 그림자 · `proc/phantom_weapon` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_shadow` |
| `phoenix_heart` · 불사조 심장 · PRISMATIC | 경기당 한 번 치명 피해 방지 및 회복과 밀쳐내기 · 0.5 | `PaperOfficialCombat.revive → announceProc` | 생명 · `proc/phoenix_heart` | 발동 모션 후 제거 | FLAME · 전투 사건에서 공개 | `family_life` |
| `predator` · 포식자 · SILVER | 근접 유효 타격 누적으로 회복 · 3 | `PaperOfficialCombat.meleeEffects → gainHealth` | 생명 · `proc/predator` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_life` |
| `pursuit` · 연쇄 추격 · PRISMATIC | 상대 근접 명중 · % 이동 속도 | `PaperAugments.afterCombat → trigger` | 이동 · `proc/pursuit` | 경기 시계 오라·만료 | ELECTRIC_SPARK · 개인 발동 | `family_motion` |
| `rear` · 배후 공격 · GOLD | 후방 근접 · % 추가 피해 | `PaperAugments.combat → PaperAugments.afterCombat` | 그림자 · `proc/rear` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_shadow` |
| `reflex` · 반사 신경 · SILVER | 위협 공격 회피와 공격자 쪽 안전 이동 · 1 | `PaperOfficialCombat.evadeOrResist` | 이동 · `proc/reflex` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_motion` |
| `ricochet_arrow` · 도탄 화살 · GOLD | 직접 발사한 화살 명중 시 다른 적에게 도탄 · 0.35 | `PaperOfficialCombat.attackReactions → visual` | 마법 · `proc/ricochet_arrow` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_arcane` |
| `russian_doll` · 러시아 인형 · PRISMATIC | 치명 피해를 제한적으로 막고 체력 감소와 이동 가속 · 0.25 | `PaperOfficialCombat.revive → announceProc` | 생명 · `proc/russian_doll` | 발동 모션 후 제거 | ELECTRIC_SPARK · 전투 사건에서 공개 | `family_life` |
| `shadow_dash` · 그림자 도약 · GOLD | 바라보는 방향으로 돌진하고 3초 내 첫 타격 증가 · 0.5 | `PaperOfficialCombat.cast` | 그림자 · `proc/shadow_dash / fx/sig_shadow_dash` | 7키 · 28틱 · rift · 성공한 이동의 출발·도착·경로와 질주 오라 | ELECTRIC_SPARK · 전투 사건에서 공개 | `family_shadow / rift` |
| `speed` · 기동 훈련 · SILVER | 생존 중 상시 · % 이동 속도 | `PaperAugments.syncEffects → PaperAugments.setModifier` | 이동 · `proc/speed` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_motion` |
| `split_attack` · 분열하는 공격 · GOLD | 유효 공격 네 번 후 다음 공격을 세 번으로 분할 · 0.75 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 검술 · `proc/split_attack` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_sword` |
| `split_bow` · 분열의 활 · GOLD | 발사 3회마다 본 화살 피해를 높이고 두 갈래 추가 투사체 발사 · 1.25 | `PaperOfficialCombat.fireBow` | 마법 · `proc/split_bow` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_arcane` |
| `stack_dinosaur` · 중첩 공룡 · SILVER | 다른 영구 스택 획득 배율 · 2 | `PaperOfficialCombat.grant` | 성장 · `proc/stack_dinosaur` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `steel_heart` · 강철심장 · GOLD | 근접 유효 타격으로 영구 최대 체력 증가 · 2 | `PaperOfficialCombat.meleeEffects → grant` | 금속 · `proc/steel_heart` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_metal` |
| `storm_blade` · 폭풍의 검 · GOLD | 근접 네 번째 타격으로 대상과 주변에 낙뢰 피해 · 6 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 검술 · `proc/storm_blade` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_sword` |
| `storm_chaser` · 폭풍 추적자 · GOLD | 유효 이동 후 다음 타격에 낙뢰 · 12 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 이동 · `proc/storm_chaser` | 발동 모션 후 제거 | ELECTRIC_SPARK · 확정 연쇄 피해 | `family_motion` |
| `survival_instinct` · 생존 본능 · SILVER | 저체력에서 체력 회복과 신속 II · 6 | `PaperOfficialCombat.reactAtLowHealth → gainHealth` | 생명 · `proc/survival_instinct` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_life` |
| `sword_wind` · 검풍 · SILVER | 근접 타격 누적으로 벽을 관통하지 않는 전방 검기 · 8 | `PaperOfficialCombat.sword → syntheticHit → announceProc` | 검술 · `proc/sword_wind` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_sword` |
| `tank_engine` · 탱크 엔진 · GOLD | 수평 이동 거리마다 크기와 최대 체력 성장 · 4 | `PaperOfficialCombat.move → grant` | 기계 · `proc/tank_engine` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_machine` |
| `time_rewind` · 시간 역행 · PRISMATIC | 4초 전 안전 위치로 복귀하고 당시 체력까지 제한 회복 · 14 | `PaperOfficialCombat.cast` | 시간 · `proc/time_rewind / fx/sig_time_rewind` | 7키 · 56틱 · rewind · 복귀한 위치와 저장된 경로 표본 | SNOWFLAKE · 전투 사건에서 공개 | `family_time / rewind` |
| `time_thief` · 시간 도둑 · GOLD | 타격한 적의 이로운 효과를 줄이고 감속과 신속을 부여 · 1 | `PaperOfficialCombat.stealTime` | 시간 · `proc/time_thief` | 발동 모션 후 제거 | SNOWFLAKE · 개인 발동 | `family_time` |
| `triad_reactor` · 삼중 반응로 · PRISMATIC | 근접·원거리·증강 또는 방어 반응 후 다음 공격이 폭발 · 6 | `PaperOfficialCombat.triad → charge → visual` | 기계 · `proc/triad_reactor` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_machine` |
| `ultimate_hydra` · 궁극의 히드라 · GOLD | 근접 피해를 주변 적에게 전파하고 네 번째 타격마다 넓은 충격파 · 0.5 | `PaperOfficialCombat.hydra → syntheticHit → announceProc` | 검술 · `proc/ultimate_hydra` | 발동 모션 후 제거 | CRIT · 확정 연쇄 피해 | `family_sword` |
| `vein_overload` · 광맥 과부하 · SILVER | 자연 광석 채굴로 이동 속도 성장, PvP 시작 시 흡수 체력 · 0.02 | `PaperOfficialCombat.sync` | 대지 · `proc/vein_overload` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_earth` |
| `venom_swarm` · 맹독 군락 · GOLD | 근접 중독과 중독시킨 적 처치 시 주변 맹독 · 4 | `PaperOfficialCombat.applyVenom` | 마법 · `proc/venom_swarm` | 발동 모션 후 제거 | SNOWFLAKE · 개인 발동 | `family_arcane` |
| `void_lord` · 공허 군주 · PRISMATIC | 주변을 당기고 2초간 공허 피해 · 8 | `PaperOfficialCombat.cast` | 공허 · `proc/void_lord / fx/sig_void_lord` | 6키 · 50틱 · focus · 실제 장판의 범위·맥동·종료 수축 | PORTAL · 전투 사건에서 공개 | `family_void / focus` |
| `world_erosion` · 세계 침식 · PRISMATIC | 주변 적의 최대 체력을 낮추고 일부를 흡수 체력으로 획득 · 2 | `PaperOfficialCombat.worldErosion` | 공허 · `proc/world_erosion` | 발동 모션 후 제거 | CRIT · 개인 발동 | `family_void` |
| `wound_record` · 상처의 기록 · SILVER | 받은 피해 누적으로 체력 성장 및 저체력 진입 시 저항 · 2 | `PaperOfficialCombat.damageTaken → grant` | 성장 · `proc/wound_record` | 발동 모션 후 제거 | HAPPY_VILLAGER · 개인 발동 | `family_growth` |
| `xp_gold` · 경험 가속 · GOLD | 양의 경험치 이벤트 · % 추가 | `AugmentCatalog.load → PaperAugments.experience` | 성장 · `proc/xp_gold` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_growth` |
| `xp_prism` · 경험 폭발 · PRISMATIC | 양의 경험치 이벤트 · % 추가 | `AugmentCatalog.load → PaperAugments.experience` | 성장 · `proc/xp_prism` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_growth` |
| `xp_silver` · 경험 축적 · SILVER | 양의 경험치 이벤트 · % 추가 | `AugmentCatalog.load → PaperAugments.experience` | 성장 · `proc/xp_silver` | 발동 모션 후 제거 | ELECTRIC_SPARK · 개인 발동 | `family_growth` |
