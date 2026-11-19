# PHASE 6.3 증강 76종 소스 감사

각 행의 조건과 설정을 코드의 실제 호출 경로에 대조했다. 수치는 설정의 필드값이며 0은 해당 필드에서 별도 수치를 설정하지 않았음을 뜻한다. 여러 설정을 합산하는 효과의 나머지 수치는 각 YAML의 settings 섹션에 있다. 모든 행의 상태는 소스 대조 완료 및 참가자 단위 실플레이 **NOT_RUN**이다. 공용 엔진의 자동 검사 및 테스트 엔티티 프로브는 [검증 기록](phase63-validation-ko.md)에 기록한다.

- **P**는 영구 성장이다. 현재 경기의 참가자 상태에서 증강별 원장과 공명 잔량을 보존하고, 소유 해제나 경기 종료에서 제거한다. 피의 신은 피해와 처치 체력 원장을 분리한다.
- **R**은 치명 피해 방지다. 경기당 사용 횟수를 상태에 보존하며 한 이벤트에서 하나만 사용한다. 일반 리스폰, 자기장 피해, 공허 피해 및 서든데스는 사망 방지의 발동 경로를 타지 않는다.
- **T**는 시간·속성·이벤트 효과다. 활동 중에만 적용되며, Paper 속성 모디파이어는 정리하고 참가 전 상태는 스냅샷으로 복원한다. 쿨다운 및 일부 임시 상태가 초기화되는 시점은 개별 경로에 따라 다르다.
- **X**는 교환이다. 보상을 확정할 때 소유권·체력을 변경하고 제거되는 증강 상태를 해제한다. 특수 교환 2종의 선택 자격은 별도로 계산한다.

| ID | 발동 조건 | 양 / 문턱 / 지속초 / 재사용초 / 상한 | 실행 경로 | 상태 / 진화 / 고유 시너지 | 실플레이 |
|---|---|---|---|---|---|
| `stack_dinosaur` | 다른 영구 스택 획득 배율 | 2 / 0 / 0 / 0 / 0 | grant / 소수점 영구지급 | T · steel_king | NOT_RUN |
| `heavy_fist` | 자신의 최대 체력 비례 근접 추가 피해 | 0.08 / 0 / 0 / 0 / 0 | increaseDamage / 독립 원본 피해 | T · steel_king | NOT_RUN |
| `blood_record` | 자연 적대 몬스터 처치마다 영구 최대 체력 성장 | 2 / 5 / 0 / 0 / 200 | mobDeath → progress → applyAttributes | P | NOT_RUN |
| `mineral_addiction` | 자연 광석 채굴에 따라 공격 피해 증가 | 0.04 / 12 / 0 / 0 / 200 | breakOre → progress → increaseDamage | P | NOT_RUN |
| `sword_wind` | 근접 타격 누적으로 벽을 관통하지 않는 전방 검기 | 8 / 3 / 0 / 0 / 0 | applyHitEffects → sword → ray | T · 검기 적중 30회 · thousand_swords | NOT_RUN |
| `berserk_engine` | 유효 타격마다 이동 속도 증가 | 0.06 / 0 / 5 / 0 / 15 | applyHitEffects → recordTopSpeed → applyAttributes | T · 최고 속도 30초 · calamity_train | NOT_RUN |
| `reflex` | 위협 공격 회피와 공격자 쪽 안전 이동 | 1 / 0 / 0 / 12 / 0 | evadeOrResist → 안전 이동 | T | NOT_RUN |
| `overgrowth` | 획득 및 경기 시간이 지나면 최대 체력 성장 | 16 / 300 / 0 / 0 / 0 | acquired → applyAttributes | T | NOT_RUN |
| `steel_heart` | 근접 유효 타격으로 영구 최대 체력 증가 | 2 / 0 / 0 / 10 / 200 | meleeEffects → grant → applyAttributes | P · 20중첩 · steel_king | NOT_RUN |
| `ultimate_hydra` | 근접 피해를 주변 적에게 전파하고 네 번째 타격마다 넓은 충격파 | 0.5 / 4 / 0 / 0 / 0 | applyHitEffects → hydra → syntheticHit | T · thousand_swords | NOT_RUN |
| `infernal_conductor` | 타격 시 화상 중첩, 처치 시 전염 및 누적 화상 피해로 진화 | 0.6 / 0 / 6 / 0 / 6 | applyHitEffects → ignite → tickBurns / mobDeath 전이 | T · 화상 피해 200포인트 · hell_lord | NOT_RUN |
| `eureka` | XP 레벨 5당 증강 재사용 대기시간 감소 | 0.05 / 5 / 0 / 0 / 14 | cooldown / 레벨 기반 | T · infinite_barrage | NOT_RUN |
| `magic_missile` | 유효 타격마다 추적 미사일을 준비하고 누적 발사 수에 따라 진화 | 3 / 3 / 0 / 0 / 0 | applyHitEffects → launchMissiles → advanceMissiles | T · 100발 · infinite_barrage | NOT_RUN |
| `phantom_weapon` | 화살·검기 적중 시 허용된 근접 효과만 발동 | 1 / 0 / 0 / 0 / 0 | phantom → meleeEffects / 허용 효과만 | T | NOT_RUN |
| `tank_engine` | 수평 이동 거리마다 크기와 최대 체력 성장 | 4 / 250 / 0 / 0 / 20 | move → grant → applyAttributes / 충돌 | P · 15중첩 · calamity_train | NOT_RUN |
| `giant_slayer` | 상대 최대 체력이 높을수록 피해 증가 | 1.2 / 0 / 0 / 0 / 0 | increaseDamage / 상대 최대 체력 비율 | T | NOT_RUN |
| `split_attack` | 유효 공격 네 번 후 다음 공격을 세 번으로 분할 | 0.75 / 4 / 0 / 0 / 0 | increaseDamage → applyHitEffects → afterRecovery | T | NOT_RUN |
| `storm_chaser` | 유효 이동 후 다음 타격에 낙뢰 | 12 / 6 / 0 / 8 / 0 | move → applyHitEffects / 번개 피해 | T | NOT_RUN |
| `calamity_giant` | 크기·최대 체력·사거리 증가, 질주 밀치기 및 지면 강타 | 50 / 0 / 0 / 8 / 0 | applyAttributes / move / cast 지면 강타 | T · calamity_train | NOT_RUN |
| `infinite_repeat` | 허용된 발동을 두 번, 매 세 번째에는 세 번 처리 | 2 / 3 / 0 / 0 / 0 | repetitions → hydra / launchMissiles | T · infinite_barrage | NOT_RUN |
| `jeweled_gauntlet` | 합성 증강 피해에 치명타 적용 | 2.5 / 0 / 0 / 0 / 0 | syntheticHit / 합성 치명타 | T · hell_lord | NOT_RUN |
| `immortal_berserker` | 유효 타격으로 광폭화, 흡혈 및 경기당 1회 치명 피해 방지 | 1.0 / 10 / 8 / 0 / 1 | applyHitEffects → increaseDamage / revive | R | NOT_RUN |
| `dual_wielder` | 보조 손 무기로 추가 공격하고 방패 사용 제한 | 1 / 0 / 0 / 0 / 0 | applyHitEffects → afterRecovery → offhand | T · thousand_swords | NOT_RUN |
| `earthquake_mage` | 돌진한 경로를 1초 뒤 폭발 | 10 / 0 / 1 / 12 / 0 | cast → tickZones → eruptPath | T | NOT_RUN |
| `center_of_universe` | 주변을 도는 오브가 가까운 적에게 자동 공격 | 4 / 6 / 0 / 1 / 0 | sync → orbit → syntheticHit | T · hell_lord | NOT_RUN |
| `russian_doll` | 치명 피해를 제한적으로 막고 체력 감소와 이동 가속 | 0.25 / 3 / 0 / 0 / 0 | revive → applyAttributes | R | NOT_RUN |
| `mad_scientist` | 주기적으로 거대·소형 상태를 오가며 체력 비율 유지 | 40 / 0 / 12 / 0 / 0 | acquired → sync / evadeOrResist | T | NOT_RUN |
| `draw_the_sword` | 근접 검기 적중마다 영구 검기 피해 성장과 방패 사용 제한 | 0.02 / 0 / 0 / 0 / 200 | applyHitEffects → drawSword / 방패 제한 | P | NOT_RUN |
| `final_judgment` | 저체력에서 5초 예고 후 잃은 체력에 비례하는 심판 | 30 / 0 / 5 / 35 / 0 | damageTaken → reactAtLowHealth → tickZones | T | NOT_RUN |
| `forbidden_reconstruction` | 실버 교환, 프리즘 추가 선택, 체력 감소 및 자연 회복 제한 | 0.5 / 2 / 10 / 0 / 0 | canOffer → startExchange → chooseExchange → exchangeCommitted | X | NOT_RUN |
| `muscle_memory` | 자연 적대 몬스터 처치로 근접 피해 증가 | 0.02 / 8 / 0 / 0 / 10 | mobDeath → progress → increaseDamage | P | NOT_RUN |
| `iron_stomach` | 종류가 다른 음식마다 최대 체력 성장과 음식 섭취 시 회복 | 2 / 2 / 0 / 12 / 4 | eat → grant → applyAttributes | P | NOT_RUN |
| `wound_record` | 받은 피해 누적으로 체력 성장 및 저체력 진입 시 저항 | 2 / 20 / 4 / 22 / 4 | damageTaken → grant → applyAttributes | P | NOT_RUN |
| `vein_overload` | 자연 광석 채굴로 이동 속도 성장, PvP 시작 시 흡수 체력 | 0.02 / 10 / 0 / 0 / 8 | breakOre → grant → applyAttributes / PvP 흡수 | P | NOT_RUN |
| `chain_impact` | 근접 누적 타격으로 주변 적에게 감속 충격파 | 3 / 3 / 2 / 6 / 0 | meleeEffects → area | T | NOT_RUN |
| `blood_trace` | 공격한 대상을 발광시킨 뒤 다음 타격 피해 증가 | 0.3 / 0 / 6 / 9 / 0 | applyHitEffects → increaseDamage | T | NOT_RUN |
| `frenzy_rhythm` | 같은 적을 연속 타격할 때 피해 증가 | 0.07 / 0 / 4 / 0 / 4 | applyHitEffects → increaseDamage / 만료 | T | NOT_RUN |
| `survival_instinct` | 저체력에서 체력 회복과 신속 II | 6 / 0 / 4 / 22 / 0 | damageTaken → reactAtLowHealth | T | NOT_RUN |
| `predator` | 근접 유효 타격 누적으로 회복 | 3 / 3 / 0 / 5 / 0 | meleeEffects / 근접 회복 | T | NOT_RUN |
| `falling_shockwave` | 높은 곳에서 낙하 시 피해 감소 및 주변 적에게 충격파 | 5 / 4 / 0 / 12 / 0 | evadeOrResist → area | T | NOT_RUN |
| `explosive_arrow` | 화살 적중 누적으로 주변 적에게 폭발 피해 | 4 / 3 / 0 / 8 / 0 | applyHitEffects → area | T | NOT_RUN |
| `counter_shield` | 방패로 피해 차단 시 회복하고 다음 근접 타격 강화 | 0.4 / 0 / 0 / 10 / 0 | damageTaken → increaseDamage | T | NOT_RUN |
| `heart_harvester` | 적대 몹과 플레이어 처치로 체력 성장, 플레이어 처치 시 회복 | 2 / 10 / 0 / 0 / 8 | mobDeath → grant → applyAttributes | P | NOT_RUN |
| `mineral_devourer` | 자연 광석 누적으로 근접 피해, 목숨 제한 시작 때 황금사과 | 0.03 / 10 / 0 / 0 / 8 | breakOre → increaseDamage / PvP 황금사과 | P | NOT_RUN |
| `blood_crown` | 근접 피해 흡혈, 플레이어 처치 시 흡수 체력 | 0.25 / 0 / 0 / 2 / 0 | applyHitEffects / mobDeath | T | NOT_RUN |
| `storm_blade` | 근접 네 번째 타격으로 대상과 주변에 낙뢰 피해 | 6 / 4 / 0 / 8 / 0 | applyHitEffects → syntheticHit | T | NOT_RUN |
| `shadow_dash` | 바라보는 방향으로 돌진하고 3초 내 첫 타격 증가 | 0.5 / 0 / 3 / 15 / 0 | cast → increaseDamage / 안전 이동 | T | NOT_RUN |
| `immortal_armor` | 받는 피해 일부를 흡수 체력으로 전환 | 0.35 / 0 / 0 / 12 / 0 | evadeOrResist → addAbsorption | T | NOT_RUN |
| `venom_swarm` | 근접 중독과 중독시킨 적 처치 시 주변 맹독 | 4 / 0 / 4 / 10 / 0 | applyHitEffects → onMarkedKill → tickPoisonClouds | T | NOT_RUN |
| `bounty_hunter` | 적을 지정하여 피해 증가와 처치 시 회복·신속 | 0.25 / 0 / 6 / 12 / 0 | applyHitEffects / increaseDamage / onMarkedKill | T | NOT_RUN |
| `split_bow` | 발사 3회마다 본 화살 피해를 높이고 두 갈래 추가 투사체 발사 | 1.25 / 3 / 0 / 6 / 0 | fireBow → advanceSplitArrows / increaseDamage | T | NOT_RUN |
| `bomb_alchemy` | 전용 눈덩이 폭탄과 착탄 지점의 3초 불 지대 | 6 / 0 / 3 / 18 / 0 | cast → bombHit → tickZones | T | NOT_RUN |
| `duel_contract` | 지정 적과 결투하여 공격 피해와 받는 피해를 증가 | 0.3 / 0 / 8 / 18 / 0 | cast → increaseDamage / evadeOrResist | T | NOT_RUN |
| `time_thief` | 타격한 적의 이로운 효과를 줄이고 감속과 신속을 부여 | 1 / 0 / 4 / 12 / 0 | applyHitEffects → stealTime | T | NOT_RUN |
| `endless_evolution` | 적대 몹·플레이어 처치로 최대 체력과 피해 성장 | 2 / 8 / 0 / 0 / 8 | mobDeath → grant → applyAttributes / increaseDamage | P | NOT_RUN |
| `phoenix_heart` | 경기당 한 번 치명 피해 방지 및 회복과 밀쳐내기 | 0.5 / 1 / 2 / 0 / 0 | revive / 이동 보호 및 밀치기 | R | NOT_RUN |
| `time_rewind` | 4초 전 안전 위치로 복귀하고 당시 체력까지 제한 회복 | 14 / 4 / 0 / 35 / 0 | cast → remember / rewind | T | NOT_RUN |
| `void_lord` | 주변을 당기고 2초간 공허 피해 | 8 / 0 / 2 / 25 / 0 | cast → tickZones → pulseVoid | T | NOT_RUN |
| `blood_god` | 근접 흡혈과 플레이어 타격·처치에 따른 영구 성장 | 0.35 / 3 / 0 / 0 / 10 | applyHitEffects / mobDeath → grant → applyAttributes | P | NOT_RUN |
| `clone_hand` | 근접 타격 누적으로 원래 공격의 복제 피해 | 1 / 3 / 0 / 3 / 0 | applyHitEffects → afterRecovery → syntheticHit | T | NOT_RUN |
| `gigantification` | 최대 체력·크기·근접 피해 증가 및 이동 속도 감소 | 20 / 0 / 0 / 0 / 0 | applyAttributes / increaseDamage | T | NOT_RUN |
| `last_demon` | 낮은 체력에서 강화 피해·흡혈·신속 II·저항 I | 0.6 / 0 / 7 / 30 / 0 | reactAtLowHealth → increaseDamage / applyHitEffects | T | NOT_RUN |
| `forbidden_deal` | 최대 체력을 줄이고 골드 추가 선택 및 공격 피해 증가 | 0.2 / 2 / 0 / 0 / 0 | canOffer → chooseExchange → applyAttributes / increaseDamage | X | NOT_RUN |
| `world_erosion` | 주변 적의 최대 체력을 낮추고 일부를 흡수 체력으로 획득 | 2 / 0 / 10 / 3 / 0 | sync → worldErosion → PaperErosion | T | NOT_RUN |
| `xp_silver` | 양의 경험치 이벤트 | 0.5 / 0 / 0 / 0 / 0 | PaperAugments.experience / 최대 XP 배율 | T | NOT_RUN |
| `miner` | 드롭 허용 광석 채굴 | 0.35 / 0 / 6 / 12 / 0 | PaperAugments.mine → activate → syncEffects | T | NOT_RUN |
| `speed` | 생존 중 상시 | 0.08 / 0 / 0 / 0 / 0 | PaperAugments.syncEffects / 이동 속성 | T | NOT_RUN |
| `heal` | 피격 후 저체력 | 1.0 / 0 / 4 / 30 / 0 | PaperAugments.afterCombat → activate → syncEffects | T | NOT_RUN |
| `xp_gold` | 양의 경험치 이벤트 | 1.0 / 0 / 0 / 0 / 0 | PaperAugments.experience / 최대 XP 배율 | T | NOT_RUN |
| `rear` | 후방 근접 | 0.2 / 0 / 0 / 0 / 0 | PaperAugments.combat / 후방 내적 검증 | T | NOT_RUN |
| `fall` | 공중 낙하 중 근접 | 0.25 / 0 / 0 / 0 / 0 | PaperAugments.combat / 낙하 조건 | T | NOT_RUN |
| `armor` | 상대 근접 피해 | 3.0 / 0 / 5 / 12 / 0 | PaperAugments.afterCombat → activate → syncEffects | T | NOT_RUN |
| `xp_prism` | 양의 경험치 이벤트 | 2.0 / 0 / 0 / 0 / 0 | PaperAugments.experience / 최대 XP 배율 | T | NOT_RUN |
| `berserk` | 공격자 저체력 근접 | 0.25 / 0 / 5 / 20 / 0 | PaperAugments.afterCombat → combat | T | NOT_RUN |
| `defense` | 강한 상대 근접 공격 | 0.35 / 0 / 0 / 25 / 0 | PaperAugments.combat / 원본 피해 감소 | T | NOT_RUN |
| `pursuit` | 상대 근접 명중 | 0.2 / 0 / 4 / 12 / 0 | PaperAugments.afterCombat → activate → syncEffects | T | NOT_RUN |

## 불일치와 검증 경계

- 증강 선택 화면 동안 도메인 시계와 증강 엔진만 멈추고 일반 공격과 위치 이동은 허용됐다. 선택 중 동작 제한을 별도로 연결하여 GUI 선택 경로를 유지했다.
- 보호막을 MAGIC 보정치에 덧붙였을 때 이후 이벤트 리스너가 BASE를 재설정해도 음수 보정이 남을 수 있었다. Paper 네이티브 피해 계산을 다시 거치는 BASE 값으로 처리하고 후속 BASE 교체는 보호막 차감에 반영한다.
- 기존 설정의 정적 희귀도 가중치 키는 실제 선택의 XP 확률을 설명하지 않는다. 현재 선택은 AugmentRarity의 레벨 공식과 결정된 희귀도 내부의 연계 가중치를 사용한다.
- 전투 전에 이미 설치된 광석은 자연 광석과 구분할 수 없다. 경기 중 설치·이동 기록과 좌표 중복 제거는 유지한다. 생성 유형을 확인할 수 있는 자연 적대 몹만 성장에 포함하는 경로를 확인했다.
- 시간 역행과 돌진 위치는 현재 안전 영역 및 로드된 지표면을 통과해야 한다. 안전 지점이 없거나 기록 간격이 어긋나면 발동이 거절될 수 있다. 경기장 내부이면서 축소된 자기장 바깥인 참가자에게 모든 능력을 일괄 차단하는 규칙은 없다.
- 25분 XP 증강은 남은 마지막 고정 프리즘 회차의 희귀도를 개선하지 않는다. 25분과 30분 XP 보너스의 일반 경험치·장비 활용 가치, 후반 성장 효과, 설치 광석 우회, 넉백을 이용한 이동 파밍, 흡혈과 보호막 조합은 실전 검증이 필요하다.
