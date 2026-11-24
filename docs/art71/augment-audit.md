# 증강 발동 연출 대조표

각 ID는 고유 아이콘 `augment/<ID>`와 전투용 `proc/<ID>`를 가진다. 전투 모델은 해당 Blender 형태와 고유 문양을 결합한다. 아래 경로는 해당 증강의 실제 효과를 대표하는 발동 또는 능력 적용 지점이다. 충전·스택 증가·교환은 각각 해당 상태가 변경될 때 표시한다.

PHASE 7.6에서 84개 아이콘 프레임과 명암 재질을 갱신했다. 액티브 7종에는 다른 실루엣과 초기·접촉·수축을 정의한 위치·회전·비균일 크기 키프레임을 넣었다. 진화 여섯 종과 주요 시너지의 전용 모션은 성장 이벤트에서만 재생된다. 나머지 일반 발동은 역할과 소재가 같은 모션을 공유한다. 모델·음원은 개인 설정과 표시 예산으로 제어한다.

공개 경로 열은 이 표에서 추적한 대표 능력의 추가 공유 경로다. 모든 증강의 일반 타격은 확인된 난전 피드백을 공유한다. 발동자의 개인 모델과 비공개 정보는 본인에게만 남는다.

**검사 범위**  소스 검사는 카탈로그의 전체 ID, 선언된 실제 메서드, 호출 간선, 시각 출력 지점, 모델의 고유 텍스처 참조를 확인한다. Paper 연출 프로브는 별도로 합성 뷰어에게 84개를 재생해 실제 엔티티의 모델 ID와 수명을 검사한다. 개별 실전 발동의 시청각 일치는 클라이언트 검증 열에 기록한다.

| ID · 아이콘 · 효과 모델 | 발동 조건 | 코드 경로 | Blender 원형 | 발동·종료 및 지속 단계 | 7.6 변경 | 간소 파티클 | 대표 공개 경로 | 소스·모델 검사 | 클라이언트 |
|---|---|---|---|---|---|---|---|---|---|
| `afterimage_stride` | 수평 이동을 누적해 다음 근접 공격 강화 및 신속 | `PaperOfficialCombat.move → charge → visual` | `rush` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `armor` | 상대 근접 피해 | `PaperAugments.afterCombat → trigger` | `ward` | 형성·잔류 5+10틱 · 경기 시계 오라·만료 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `berserk_engine` | 유효 타격마다 이동 속도 증가 | `PaperOfficialCombat.applyHitEffects` | `sig_berserk_engine` | 6키 · 54틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `berserk` | 공격자 저체력 근접 | `PaperAugments.afterCombat → trigger` | `blood` | 형성·잔류 5+10틱 · 경기 시계 오라·만료 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `blood_crown` | 근접 피해 흡혈, 플레이어 처치 시 흡수 체력 | `PaperOfficialCombat.applyHitEffects → gainHealth` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `blood_god` | 근접 흡혈과 플레이어 타격·처치에 따른 영구 성장 | `PaperOfficialCombat.applyHitEffects → grant` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `blood_record` | 자연 적대 몬스터 처치마다 영구 최대 체력 성장 | `PaperOfficialCombat.mobDeath → progress → grant` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `blood_trace` | 공격한 대상을 발광시킨 뒤 다음 타격 피해 증가 | `PaperOfficialCombat.applyHitEffects` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `bomb_alchemy` | 전용 눈덩이 폭탄과 착탄 지점의 3초 불 지대 | `PaperOfficialCombat.cast` | `sig_bomb_alchemy` | 6키 · 30틱 · ignite · 투사체 비행·명중·폭발 반경과 제거 | 아이콘·재질·액티브 | FLAME | 전투 사건에서 공개 | PASS | NOT_RUN |
| `bounty_hunter` | 적을 지정하여 피해 증가와 처치 시 회복·신속 | `PaperOfficialCombat.applyHitEffects` | `bolt` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `calamity_giant` | 크기·최대 체력·사거리 증가, 질주 밀치기 및 지면 강타 | `PaperOfficialCombat.cast` | `sig_calamity_giant` | 8키 · 56틱 · stone_break · 실제 지면 충격 반경과 달리기 발자국 | 아이콘·재질·액티브 | CRIT | 전투 사건에서 공개 | PASS | NOT_RUN |
| `center_of_universe` | 주변을 도는 오브가 가까운 적에게 자동 공격 | `PaperOfficialCombat.orbit → syntheticHit → announceProc` | `void` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | PORTAL | 확정 연쇄 피해 | PASS | NOT_RUN |
| `chain_impact` | 근접 누적 타격으로 주변 적에게 감속 충격파 | `PaperOfficialCombat.meleeEffects → area → syntheticHit → announceProc` | `bolt` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 확정 연쇄 피해 | PASS | NOT_RUN |
| `clone_hand` | 근접 타격 누적으로 원래 공격의 복제 피해 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 확정 연쇄 피해 | PASS | NOT_RUN |
| `counter_shield` | 방패로 피해 차단 시 회복하고 다음 근접 타격 강화 | `PaperOfficialCombat.damageTaken` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `defense` | 강한 상대 근접 공격 | `PaperAugments.combat → PaperAugments.afterCombat` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `delayed_blast` | 직접 피해 누적 시 마지막 적중 위치에 폭발 예고 | `PaperOfficialCombat.attackReactions → visual` | `fire` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | FLAME | 전투 사건에서 공개 | PASS | NOT_RUN |
| `draw_the_sword` | 근접 검기 적중마다 영구 검기 피해 성장과 방패 사용 제한 | `PaperOfficialCombat.drawSword → grant` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `dual_wielder` | 보조 손 무기로 추가 공격하고 방패 사용 제한 | `PaperOfficialCombat.offhand → syntheticHit → announceProc` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `duel_contract` | 지정 적과 결투하여 공격 피해와 받는 피해를 증가 | `PaperOfficialCombat.cast` | `sig_duel_contract` | 6키 · 48틱 · seal · 상호 시야의 계약 연결과 상태 오라 | 아이콘·재질·액티브 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `earthquake_mage` | 돌진한 경로를 1초 뒤 폭발 | `PaperOfficialCombat.cast` | `sig_earthquake_mage` | 7키 · 52틱 · stone_break · 실제 출발점과 지연된 경로 타격 | 아이콘·재질·액티브 | CRIT | 전투 사건에서 공개 | PASS | NOT_RUN |
| `endless_evolution` | 적대 몹·플레이어 처치로 최대 체력과 피해 성장 | `PaperOfficialCombat.mobDeath → grant` | `growth` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `eureka` | XP 레벨 5당 증강 재사용 대기시간 감소 | `PaperOfficialCombat.cooldown → PaperOfficialCombat.ready` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `explosive_arrow` | 화살 적중 누적으로 주변 적에게 폭발 피해 | `PaperOfficialCombat.applyHitEffects → area → syntheticHit → announceProc` | `fire` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | FLAME | 확정 연쇄 피해 | PASS | NOT_RUN |
| `fall` | 공중 낙하 중 근접 | `PaperAugments.combat → PaperAugments.afterCombat` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `falling_shockwave` | 높은 곳에서 낙하 시 피해 감소 및 주변 적에게 충격파 | `PaperOfficialCombat.evadeOrResist → area → syntheticHit → announceProc` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `final_judgment` | 저체력에서 5초 예고 후 잃은 체력에 비례하는 심판 | `PaperOfficialCombat.reactAtLowHealth` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `forbidden_deal` | 최대 체력을 줄이고 골드 추가 선택 및 공격 피해 증가 | `PaperAugments.chooseExchange → PaperOfficialCombat.exchangeCommitted` | `void` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | PORTAL | 개인 발동 | PASS | NOT_RUN |
| `forbidden_reconstruction` | 실버 교환, 프리즘 추가 선택, 체력 감소 및 자연 회복 제한 | `PaperOfficialCombat.exchangeCommitted` | `growth` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `frenzy_rhythm` | 같은 적을 연속 타격할 때 피해 증가 | `PaperOfficialCombat.applyHitEffects → announceProc` | `rush` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `frost_bind` | 같은 적에게 직접 공격을 누적하면 추가 피해와 감속 | `PaperOfficialCombat.attackReactions → visual` | `frost` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | SNOWFLAKE | 개인 발동 | PASS | NOT_RUN |
| `giant_slayer` | 상대 최대 체력이 높을수록 피해 증가 | `PaperOfficialCombat.confirmedHit` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `gigantification` | 최대 체력·크기·근접 피해 증가 및 이동 속도 감소 | `PaperOfficialCombat.sync` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `heal` | 피격 후 저체력 | `PaperAugments.afterCombat → trigger` | `growth` | 확산·수렴 4+7틱 · 경기 시계 오라·만료 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `heart_harvester` | 적대 몹과 플레이어 처치로 체력 성장, 플레이어 처치 시 회복 | `PaperOfficialCombat.mobDeath → grant` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `heavy_fist` | 자신의 최대 체력 비례 근접 추가 피해 | `PaperOfficialCombat.confirmedHit → announceProc` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `immortal_armor` | 받는 피해 일부를 흡수 체력으로 전환 | `PaperOfficialCombat.evadeOrResist` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `immortal_berserker` | 유효 타격으로 광폭화, 흡혈 및 경기당 1회 치명 피해 방지 | `PaperOfficialCombat.revive → announceProc` | `blood` | 형성·잔류 5+10틱 · 경기 시계 오라·만료 | 아이콘·재질 | HAPPY_VILLAGER | 전투 사건에서 공개 | PASS | NOT_RUN |
| `infernal_conductor` | 타격 시 화상 중첩, 처치 시 전염 및 누적 화상 피해로 진화 | `PaperOfficialCombat.tickBurns → syntheticHit → announceProc` | `sig_infernal_conductor` | 6키 · 64틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | FLAME | 확정 연쇄 피해 | PASS | NOT_RUN |
| `infinite_repeat` | 허용된 발동을 두 번, 매 세 번째에는 세 번 처리 | `PaperOfficialCombat.repetitions` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `iron_stomach` | 종류가 다른 음식마다 최대 체력 성장과 음식 섭취 시 회복 | `PaperOfficialCombat.eat → grant` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `jeweled_gauntlet` | 합성 증강 피해에 치명타 적용 | `PaperOfficialCombat.syntheticHit` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 확정 연쇄 피해 | PASS | NOT_RUN |
| `kinetic_reversal` | 근접 피해를 견디면 다음 근접 공격으로 반격 | `PaperOfficialCombat.damageTaken → charge → visual` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `last_bastion` | 플레이어 직접 공격을 누적해서 방어 영역과 종료 충격파 발동 | `PaperOfficialCombat.damageTaken → charge → visual` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 전투 사건에서 공개 | PASS | NOT_RUN |
| `last_demon` | 낮은 체력에서 강화 피해·흡혈·신속 II·저항 I | `PaperOfficialCombat.reactAtLowHealth` | `fire` | 확산·수렴 4+7틱 · 경기 시계 오라·만료 | 아이콘·재질 | FLAME | 개인 발동 | PASS | NOT_RUN |
| `mad_scientist` | 주기적으로 거대·소형 상태를 오가며 체력 비율 유지 | `PaperOfficialCombat.sync` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `magic_missile` | 유효 타격마다 추적 미사일을 준비하고 누적 발사 수에 따라 진화 | `PaperOfficialCombat.advanceMissiles → syntheticHit → announceProc` | `sig_magic_missile` | 6키 · 55틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | ELECTRIC_SPARK | 전투 사건에서 공개 | PASS | NOT_RUN |
| `miner` | 드롭 허용 광석 채굴 | `PaperAugments.mine → trigger` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `mineral_addiction` | 자연 광석 채굴에 따라 공격 피해 증가 | `PaperOfficialCombat.breakOre → progress → grant` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `mineral_devourer` | 자연 광석 누적으로 근접 피해, 목숨 제한 시작 때 황금사과 | `PaperOfficialCombat.breakOre → progress → grant` | `growth` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `muscle_memory` | 자연 적대 몬스터 처치로 근접 피해 증가 | `PaperOfficialCombat.mobDeath → progress → grant` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `ore_ward` | 자연 광석을 채굴해 다음 공격 피해를 막는 방벽 충전 | `PaperOfficialCombat.breakOre` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 전투 사건에서 공개 | PASS | NOT_RUN |
| `overgrowth` | 획득 및 경기 시간이 지나면 최대 체력 성장 | `PaperOfficialCombat.sync` | `growth` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `phantom_weapon` | 화살·검기 적중 시 허용된 근접 효과만 발동 | `PaperOfficialCombat.meleeEffects` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `phoenix_heart` | 경기당 한 번 치명 피해 방지 및 회복과 밀쳐내기 | `PaperOfficialCombat.revive → announceProc` | `fire` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | FLAME | 전투 사건에서 공개 | PASS | NOT_RUN |
| `predator` | 근접 유효 타격 누적으로 회복 | `PaperOfficialCombat.meleeEffects → gainHealth` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `pursuit` | 상대 근접 명중 | `PaperAugments.afterCombat → trigger` | `rush` | 검격·이동 3+5틱 · 경기 시계 오라·만료 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `rear` | 후방 근접 | `PaperAugments.combat → PaperAugments.afterCombat` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `reflex` | 위협 공격 회피와 공격자 쪽 안전 이동 | `PaperOfficialCombat.evadeOrResist` | `rush` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `ricochet_arrow` | 직접 발사한 화살 명중 시 다른 적에게 도탄 | `PaperOfficialCombat.attackReactions → visual` | `bolt` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `russian_doll` | 치명 피해를 제한적으로 막고 체력 감소와 이동 가속 | `PaperOfficialCombat.revive → announceProc` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 전투 사건에서 공개 | PASS | NOT_RUN |
| `shadow_dash` | 바라보는 방향으로 돌진하고 3초 내 첫 타격 증가 | `PaperOfficialCombat.cast` | `sig_shadow_dash` | 7키 · 28틱 · rift · 성공한 이동의 출발·도착·경로와 질주 오라 | 아이콘·재질·액티브 | ELECTRIC_SPARK | 전투 사건에서 공개 | PASS | NOT_RUN |
| `speed` | 생존 중 상시 | `PaperAugments.syncEffects → PaperAugments.setModifier` | `rush` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `split_attack` | 유효 공격 네 번 후 다음 공격을 세 번으로 분할 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `split_bow` | 발사 3회마다 본 화살 피해를 높이고 두 갈래 추가 투사체 발사 | `PaperOfficialCombat.fireBow` | `bolt` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `stack_dinosaur` | 다른 영구 스택 획득 배율 | `PaperOfficialCombat.grant` | `growth` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `steel_heart` | 근접 유효 타격으로 영구 최대 체력 증가 | `PaperOfficialCombat.meleeEffects → grant` | `sig_steel_heart` | 6키 · 62틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `storm_blade` | 근접 네 번째 타격으로 대상과 주변에 낙뢰 피해 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `storm_chaser` | 유효 이동 후 다음 타격에 낙뢰 | `PaperOfficialCombat.applyHitEffects → syntheticHit → announceProc` | `rush` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 확정 연쇄 피해 | PASS | NOT_RUN |
| `survival_instinct` | 저체력에서 체력 회복과 신속 II | `PaperOfficialCombat.reactAtLowHealth → gainHealth` | `ward` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `sword_wind` | 근접 타격 누적으로 벽을 관통하지 않는 전방 검기 | `PaperOfficialCombat.sword → syntheticHit → announceProc` | `sig_sword_wind` | 6키 · 52틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `tank_engine` | 수평 이동 거리마다 크기와 최대 체력 성장 | `PaperOfficialCombat.move → grant` | `sig_tank_engine` | 6키 · 60틱 · evolution · 발동 모션 후 제거 | 아이콘·재질·진화 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `time_rewind` | 4초 전 안전 위치로 복귀하고 당시 체력까지 제한 회복 | `PaperOfficialCombat.cast` | `sig_time_rewind` | 7키 · 56틱 · rewind · 복귀한 위치와 저장된 경로 표본 | 아이콘·재질·액티브 | SNOWFLAKE | 전투 사건에서 공개 | PASS | NOT_RUN |
| `time_thief` | 타격한 적의 이로운 효과를 줄이고 감속과 신속을 부여 | `PaperOfficialCombat.stealTime` | `time` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | SNOWFLAKE | 개인 발동 | PASS | NOT_RUN |
| `triad_reactor` | 근접·원거리·증강 또는 방어 반응 후 다음 공격이 폭발 | `PaperOfficialCombat.triad → charge → visual` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `ultimate_hydra` | 근접 피해를 주변 적에게 전파하고 네 번째 타격마다 넓은 충격파 | `PaperOfficialCombat.hydra → syntheticHit → announceProc` | `blade` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 확정 연쇄 피해 | PASS | NOT_RUN |
| `vein_overload` | 자연 광석 채굴로 이동 속도 성장, PvP 시작 시 흡수 체력 | `PaperOfficialCombat.sync` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `venom_swarm` | 근접 중독과 중독시킨 적 처치 시 주변 맹독 | `PaperOfficialCombat.applyVenom` | `frost` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | SNOWFLAKE | 개인 발동 | PASS | NOT_RUN |
| `void_lord` | 주변을 당기고 2초간 공허 피해 | `PaperOfficialCombat.cast` | `sig_void_lord` | 6키 · 50틱 · focus · 실제 장판의 범위·맥동·종료 수축 | 아이콘·재질·액티브 | PORTAL | 전투 사건에서 공개 | PASS | NOT_RUN |
| `world_erosion` | 주변 적의 최대 체력을 낮추고 일부를 흡수 체력으로 획득 | `PaperOfficialCombat.worldErosion` | `impact` | 검격·이동 3+5틱 · 발동 모션 후 제거 | 아이콘·재질 | CRIT | 개인 발동 | PASS | NOT_RUN |
| `wound_record` | 받은 피해 누적으로 체력 성장 및 저체력 진입 시 저항 | `PaperOfficialCombat.damageTaken → grant` | `blood` | 형성·잔류 5+10틱 · 발동 모션 후 제거 | 아이콘·재질 | HAPPY_VILLAGER | 개인 발동 | PASS | NOT_RUN |
| `xp_gold` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `xp_prism` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
| `xp_silver` | 양의 경험치 이벤트 | `AugmentCatalog.load → PaperAugments.experience` | `rune` | 확산·수렴 4+7틱 · 발동 모션 후 제거 | 아이콘·재질 | ELECTRIC_SPARK | 개인 발동 | PASS | NOT_RUN |
