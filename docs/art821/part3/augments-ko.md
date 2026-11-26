# 증강 84종 발동 연결

고유 문양을 계열 모델 또는 7종 액티브 모델에 결합했다. 조건과 호출 경로는 소스 검사로 대조했다.
일반 발동은 종료 시 제거되며 간소 모드는 바닐라 파티클을 사용한다. 클라이언트 검증은 84종 모두 NOT_RUN이다.

| ID | 발동 조건 | 실제 코드 | 계열 | 모델 · 음원 |
| --- | --- | --- | --- | --- |
| `afterimage_stride` | 수평 이동을 누적해 다음 근접 공격 강화 및 신속 | `PaperOfficialCombat.move → charge → visual` | 그림자 | `proc/afterimage_stride` · `family_shadow` |
| `armor` | 상대 근접 피해 | `PaperAugments.afterCombat → trigger` | 금속 | `proc/armor` · `family_metal` |
| `berserk_engine` | 유효 타격마다 이동 속도 증가 | `PaperOfficialCombat.applyHitEffects` | 기계 | `proc/berserk_engine` · `family_machine` |
| `berserk` | 공격자 저체력 근접 | `PaperAugments.afterCombat → trigger` | 생명 | `proc/berserk` · `family_life` |
| `blood_crown` | 근접 피해 흡혈, 플레이어 처치 시 흡수 체력 | `PaperOfficialCombat.applyHitEffects → gainHealth` | 생명 | `proc/blood_crown` · `family_life` |
| `blood_god` | 근접 흡혈과 플레이어 타격·처치에 따른 영구 성장 | `PaperOfficialCombat.applyHitEffects → grant` | 생명 | `proc/blood_god` · `family_life` |
| `blood_record` | 자연 적대 몬스터 처치마다 영구 최대 체력 성장 | `PaperOfficialCombat.mobDeath → progress → grant` | 성장 | `proc/blood_record` · `family_growth` |
| `blood_trace` | 공격한 대상을 발광시킨 뒤 다음 타격 피해 증가 | `PaperOfficialCombat.applyHitEffects` | 생명 | `proc/blood_trace` · `family_life` |
| `bomb_alchemy` | 전용 눈덩이 폭탄과 착탄 지점의 3초 불 지대 | `PaperOfficialCombat.cast` | 기계 | `proc/bomb_alchemy` · `family_machine` |
| `bounty_hunter` | 적을 지정하여 피해 증가와 처치 시 회복·신속 | `PaperOfficialCombat.applyHitEffects` | 마법 | `proc/bounty_hunter` · `family_arcane` |
| `calamity_giant` | 크기·최대 체력·사거리 증가, 질주 밀치기 및 지면 강타 | `PaperOfficialCombat.cast` | 대지 | `proc/calamity_giant` · `family_earth` |
| `center_of_universe` | 주변을 도는 오브가 가까운 적에게 자동 공격 | `PaperOfficialCombat.orbit → syntheticHit → announceProc` | 공허 | `proc/center_of_universe` · `family_void` |
| `chain_impact` | 근접 누적 타격으로 주변 적에게 감속 충격파 | `PaperOfficialCombat.meleeEffects → area → syntheticHit → announceProc` | 마법 | `proc/chain_impact` · `family_arcane` |
| `clone_hand` | 근접 타격 누적으로 원래 공격의 복제 피해 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 그림자 | `proc/clone_hand` · `family_shadow` |
| `counter_shield` | 방패로 피해 차단 시 회복하고 다음 근접 타격 강화 | `PaperOfficialCombat.damageTaken` | 금속 | `proc/counter_shield` · `family_metal` |
| `defense` | 강한 상대 근접 공격 | `PaperAugments.combat → PaperAugments.afterCombat` | 금속 | `proc/defense` · `family_metal` |
| `delayed_blast` | 직접 피해 누적 시 마지막 적중 위치에 폭발 예고 | `PaperOfficialCombat.attackReactions → visual` | 화염 | `proc/delayed_blast` · `family_flame` |
| `draw_the_sword` | 근접 검기 적중마다 영구 검기 피해 성장과 방패 사용 제한 | `PaperOfficialCombat.drawSword → grant` | 검술 | `proc/draw_the_sword` · `family_sword` |
| `dual_wielder` | 보조 손 무기로 추가 공격하고 방패 사용 제한 | `PaperOfficialCombat.offhand → syntheticHit → announceProc` | 검술 | `proc/dual_wielder` · `family_sword` |
| `duel_contract` | 지정 적과 결투하여 공격 피해와 받는 피해를 증가 | `PaperOfficialCombat.cast` | 마법 | `proc/duel_contract` · `family_arcane` |
| `earthquake_mage` | 돌진한 경로를 1초 뒤 폭발 | `PaperOfficialCombat.cast` | 대지 | `proc/earthquake_mage` · `family_earth` |
| `endless_evolution` | 적대 몹·플레이어 처치로 최대 체력과 피해 성장 | `PaperOfficialCombat.mobDeath → grant` | 성장 | `proc/endless_evolution` · `family_growth` |
| `eureka` | XP 레벨 5당 증강 재사용 대기시간 감소 | `PaperOfficialCombat.cooldown → PaperOfficialCombat.ready` | 시간 | `proc/eureka` · `family_time` |
| `explosive_arrow` | 화살 적중 누적으로 주변 적에게 폭발 피해 | `PaperOfficialCombat.applyHitEffects → area → syntheticHit → announceProc` | 화염 | `proc/explosive_arrow` · `family_flame` |
| `fall` | 공중 낙하 중 근접 | `PaperAugments.combat → PaperAugments.afterCombat` | 대지 | `proc/fall` · `family_earth` |
| `falling_shockwave` | 높은 곳에서 낙하 시 피해 감소 및 주변 적에게 충격파 | `PaperOfficialCombat.evadeOrResist → area → syntheticHit → announceProc` | 대지 | `proc/falling_shockwave` · `family_earth` |
| `final_judgment` | 저체력에서 5초 예고 후 잃은 체력에 비례하는 심판 | `PaperOfficialCombat.reactAtLowHealth` | 검술 | `proc/final_judgment` · `family_sword` |
| `forbidden_deal` | 최대 체력을 줄이고 골드 추가 선택 및 공격 피해 증가 | `PaperAugments.chooseExchange → PaperOfficialCombat.exchangeCommitted` | 공허 | `proc/forbidden_deal` · `family_void` |
| `forbidden_reconstruction` | 실버 교환, 프리즘 추가 선택, 체력 감소 및 자연 회복 제한 | `PaperOfficialCombat.exchangeCommitted` | 공허 | `proc/forbidden_reconstruction` · `family_void` |
| `frenzy_rhythm` | 같은 적을 연속 타격할 때 피해 증가 | `PaperOfficialCombat.applyHitEffects → announceProc` | 이동 | `proc/frenzy_rhythm` · `family_motion` |
| `frost_bind` | 같은 적에게 직접 공격을 누적하면 추가 피해와 감속 | `PaperOfficialCombat.attackReactions → visual` | 마법 | `proc/frost_bind` · `family_arcane` |
| `giant_slayer` | 상대 최대 체력이 높을수록 피해 증가 | `PaperOfficialCombat.confirmedHit` | 대지 | `proc/giant_slayer` · `family_earth` |
| `gigantification` | 최대 체력·크기·근접 피해 증가 및 이동 속도 감소 | `PaperOfficialCombat.sync` | 대지 | `proc/gigantification` · `family_earth` |
| `heal` | 피격 후 저체력 | `PaperAugments.afterCombat → trigger` | 생명 | `proc/heal` · `family_life` |
| `heart_harvester` | 적대 몹과 플레이어 처치로 체력 성장, 플레이어 처치 시 회복 | `PaperOfficialCombat.mobDeath → grant` | 생명 | `proc/heart_harvester` · `family_life` |
| `heavy_fist` | 자신의 최대 체력 비례 근접 추가 피해 | `PaperOfficialCombat.confirmedHit → announceProc` | 대지 | `proc/heavy_fist` · `family_earth` |
| `immortal_armor` | 받는 피해 일부를 흡수 체력으로 전환 | `PaperOfficialCombat.evadeOrResist` | 금속 | `proc/immortal_armor` · `family_metal` |
| `immortal_berserker` | 유효 타격으로 광폭화, 흡혈 및 경기당 1회 치명 피해 방지 | `PaperOfficialCombat.revive → announceProc` | 생명 | `proc/immortal_berserker` · `family_life` |
| `infernal_conductor` | 타격 시 화상 중첩, 처치 시 전염 및 누적 화상 피해로 진화 | `PaperOfficialCombat.tickBurns → syntheticHit → announceProc` | 화염 | `proc/infernal_conductor` · `family_flame` |
| `infinite_repeat` | 허용된 발동을 두 번, 매 세 번째에는 세 번 처리 | `PaperOfficialCombat.repetitions` | 마법 | `proc/infinite_repeat` · `family_arcane` |
| `iron_stomach` | 종류가 다른 음식마다 최대 체력 성장과 음식 섭취 시 회복 | `PaperOfficialCombat.eat → grant` | 금속 | `proc/iron_stomach` · `family_metal` |
| `jeweled_gauntlet` | 합성 증강 피해에 치명타 적용 | `PaperOfficialCombat.syntheticHit` | 마법 | `proc/jeweled_gauntlet` · `family_arcane` |
| `kinetic_reversal` | 근접 피해를 견디면 다음 근접 공격으로 반격 | `PaperOfficialCombat.damageTaken → charge → visual` | 대지 | `proc/kinetic_reversal` · `family_earth` |
| `last_bastion` | 플레이어 직접 공격을 누적해서 방어 영역과 종료 충격파 발동 | `PaperOfficialCombat.damageTaken → charge → visual` | 금속 | `proc/last_bastion` · `family_metal` |
| `last_demon` | 낮은 체력에서 강화 피해·흡혈·신속 II·저항 I | `PaperOfficialCombat.reactAtLowHealth` | 화염 | `proc/last_demon` · `family_flame` |
| `mad_scientist` | 주기적으로 거대·소형 상태를 오가며 체력 비율 유지 | `PaperOfficialCombat.sync` | 마법 | `proc/mad_scientist` · `family_arcane` |
| `magic_missile` | 유효 타격마다 추적 미사일을 준비하고 누적 발사 수에 따라 진화 | `PaperOfficialCombat.advanceMissiles → syntheticHit → announceProc` | 마법 | `proc/magic_missile` · `family_arcane` |
| `miner` | 드롭 허용 광석 채굴 | `PaperAugments.mine → trigger` | 대지 | `proc/miner` · `family_earth` |
| `mineral_addiction` | 자연 광석 채굴에 따라 공격 피해 증가 | `PaperOfficialCombat.breakOre → progress → grant` | 성장 | `proc/mineral_addiction` · `family_growth` |
| `mineral_devourer` | 자연 광석 누적으로 근접 피해, 목숨 제한 시작 때 황금사과 | `PaperOfficialCombat.breakOre → progress → grant` | 성장 | `proc/mineral_devourer` · `family_growth` |
| `muscle_memory` | 자연 적대 몬스터 처치로 근접 피해 증가 | `PaperOfficialCombat.mobDeath → progress → grant` | 대지 | `proc/muscle_memory` · `family_earth` |
| `ore_ward` | 자연 광석을 채굴해 다음 공격 피해를 막는 방벽 충전 | `PaperOfficialCombat.breakOre` | 금속 | `proc/ore_ward` · `family_metal` |
| `overgrowth` | 획득 및 경기 시간이 지나면 최대 체력 성장 | `PaperOfficialCombat.sync` | 성장 | `proc/overgrowth` · `family_growth` |
| `phantom_weapon` | 화살·검기 적중 시 허용된 근접 효과만 발동 | `PaperOfficialCombat.meleeEffects` | 그림자 | `proc/phantom_weapon` · `family_shadow` |
| `phoenix_heart` | 경기당 한 번 치명 피해 방지 및 회복과 밀쳐내기 | `PaperOfficialCombat.revive → announceProc` | 생명 | `proc/phoenix_heart` · `family_life` |
| `predator` | 근접 유효 타격 누적으로 회복 | `PaperOfficialCombat.meleeEffects → gainHealth` | 생명 | `proc/predator` · `family_life` |
| `pursuit` | 상대 근접 명중 | `PaperAugments.afterCombat → trigger` | 이동 | `proc/pursuit` · `family_motion` |
| `rear` | 후방 근접 | `PaperAugments.combat → PaperAugments.afterCombat` | 그림자 | `proc/rear` · `family_shadow` |
| `reflex` | 위협 공격 회피와 공격자 쪽 안전 이동 | `PaperOfficialCombat.evadeOrResist` | 이동 | `proc/reflex` · `family_motion` |
| `ricochet_arrow` | 직접 발사한 화살 명중 시 다른 적에게 도탄 | `PaperOfficialCombat.attackReactions → visual` | 마법 | `proc/ricochet_arrow` · `family_arcane` |
| `russian_doll` | 치명 피해를 제한적으로 막고 체력 감소와 이동 가속 | `PaperOfficialCombat.revive → announceProc` | 생명 | `proc/russian_doll` · `family_life` |
| `shadow_dash` | 바라보는 방향으로 돌진하고 3초 내 첫 타격 증가 | `PaperOfficialCombat.cast` | 그림자 | `proc/shadow_dash` · `family_shadow` |
| `speed` | 생존 중 상시 | `PaperAugments.syncEffects → PaperAugments.setModifier` | 이동 | `proc/speed` · `family_motion` |
| `split_attack` | 유효 공격 네 번 후 다음 공격을 세 번으로 분할 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 검술 | `proc/split_attack` · `family_sword` |
| `split_bow` | 발사 3회마다 본 화살 피해를 높이고 두 갈래 추가 투사체 발사 | `PaperOfficialCombat.fireBow` | 마법 | `proc/split_bow` · `family_arcane` |
| `stack_dinosaur` | 다른 영구 스택 획득 배율 | `PaperOfficialCombat.grant` | 성장 | `proc/stack_dinosaur` · `family_growth` |
| `steel_heart` | 근접 유효 타격으로 영구 최대 체력 증가 | `PaperOfficialCombat.meleeEffects → grant` | 금속 | `proc/steel_heart` · `family_metal` |
| `storm_blade` | 근접 네 번째 타격으로 대상과 주변에 낙뢰 피해 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 검술 | `proc/storm_blade` · `family_sword` |
| `storm_chaser` | 유효 이동 후 다음 타격에 낙뢰 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | 이동 | `proc/storm_chaser` · `family_motion` |
| `survival_instinct` | 저체력에서 체력 회복과 신속 II | `PaperOfficialCombat.reactAtLowHealth → gainHealth` | 생명 | `proc/survival_instinct` · `family_life` |
| `sword_wind` | 근접 타격 누적으로 벽을 관통하지 않는 전방 검기 | `PaperOfficialCombat.sword → syntheticHit → announceProc` | 검술 | `proc/sword_wind` · `family_sword` |
| `tank_engine` | 수평 이동 거리마다 크기와 최대 체력 성장 | `PaperOfficialCombat.move → grant` | 기계 | `proc/tank_engine` · `family_machine` |
| `time_rewind` | 4초 전 안전 위치로 복귀하고 당시 체력까지 제한 회복 | `PaperOfficialCombat.cast` | 시간 | `proc/time_rewind` · `family_time` |
| `time_thief` | 타격한 적의 이로운 효과를 줄이고 감속과 신속을 부여 | `PaperOfficialCombat.stealTime` | 시간 | `proc/time_thief` · `family_time` |
| `triad_reactor` | 근접·원거리·증강 또는 방어 반응 후 다음 공격이 폭발 | `PaperOfficialCombat.triad → charge → visual` | 기계 | `proc/triad_reactor` · `family_machine` |
| `ultimate_hydra` | 근접 피해를 주변 적에게 전파하고 네 번째 타격마다 넓은 충격파 | `PaperOfficialCombat.hydra → syntheticHit → announceProc` | 검술 | `proc/ultimate_hydra` · `family_sword` |
| `vein_overload` | 자연 광석 채굴로 이동 속도 성장, PvP 시작 시 흡수 체력 | `PaperOfficialCombat.sync` | 대지 | `proc/vein_overload` · `family_earth` |
| `venom_swarm` | 근접 중독과 중독시킨 적 처치 시 주변 맹독 | `PaperOfficialCombat.applyVenom` | 마법 | `proc/venom_swarm` · `family_arcane` |
| `void_lord` | 주변을 당기고 2초간 공허 피해 | `PaperOfficialCombat.cast` | 공허 | `proc/void_lord` · `family_void` |
| `world_erosion` | 주변 적의 최대 체력을 낮추고 일부를 흡수 체력으로 획득 | `PaperOfficialCombat.worldErosion` | 공허 | `proc/world_erosion` · `family_void` |
| `wound_record` | 받은 피해 누적으로 체력 성장 및 저체력 진입 시 저항 | `PaperOfficialCombat.damageTaken → grant` | 성장 | `proc/wound_record` · `family_growth` |
| `xp_gold` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | 성장 | `proc/xp_gold` · `family_growth` |
| `xp_prism` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | 성장 | `proc/xp_prism` · `family_growth` |
| `xp_silver` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | 성장 | `proc/xp_silver` · `family_growth` |
