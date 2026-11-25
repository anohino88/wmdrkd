# PHASE 8.2.1 PART 1 · 증강 아이콘 감사

16px 중앙 10×10의 명암 RMS로 인접 아이콘을 찾았다. 색을 제외한 정적 비교이며, 간격이 좁으면 인게임 식별 여부를 우선 확인해야 한다.
원본 생성기는 128px에서 프레임과 재질을 제작하고 32px 아이템 텍스처를 생성한다. 16종 중심 실루엣을 새로 그렸다. 다른 중심 심볼은 기존 출처를 유지하고 넓은 면적과 새로운 명암을 적용했다.
인게임 식별성과 레퍼런스 대비 품질은 전체 84종에서 미검증이다.

| 증강 | 계열 | 희귀도 | 중심 실루엣 | 최근접 · RMS | 중심 명암 범위 |
| --- | --- | --- | --- | --- | ---: |
| 검풍 · `sword_wind` | 검술 | SILVER | 기존 심볼 가공 | `overgrowth` · 56.7 | 201 |
| 경험 축적 · `xp_silver` | 성장 | SILVER | 기존 심볼 가공 | `survival_instinct` · 59.0 | 238 |
| 과잉 성장 · `overgrowth` | 성장 | SILVER | 기존 심볼 가공 | `counter_shield` · 53.3 | 233 |
| 광기의 리듬 · `frenzy_rhythm` | 이동 | SILVER | 기존 심볼 가공 | `immortal_berserker` · 57.4 | 249 |
| 광맥 과부하 · `vein_overload` | 대지 | SILVER | 기존 심볼 가공 | `mineral_addiction` · 57.3 | 234 |
| 광맥 방벽 · `ore_ward` | 금속 | SILVER | 기존 심볼 가공 | `defense` · 58.6 | 227 |
| 광물 중독 · `mineral_addiction` | 성장 | SILVER | 기존 심볼 가공 | `vein_overload` · 57.3 | 249 |
| 광부의 기세 · `miner` | 대지 | SILVER | 기존 심볼 가공 | `final_judgment` · 69.7 | 196 |
| 근육 기억 · `muscle_memory` | 대지 | SILVER | 기존 심볼 가공 | `phoenix_heart` · 63.2 | 238 |
| 기동 훈련 · `speed` | 이동 | SILVER | 기존 심볼 가공 | `jeweled_gauntlet` · 59.8 | 234 |
| 낙하 충격파 · `falling_shockwave` | 대지 | SILVER | 기존 심볼 가공 | `sword_wind` · 59.8 | 205 |
| 반동 반격 · `kinetic_reversal` | 대지 | SILVER | 기존 심볼 가공 | `explosive_arrow` · 57.5 | 245 |
| 반사 신경 · `reflex` | 이동 | SILVER | 기존 심볼 가공 | `immortal_armor` · 65.7 | 252 |
| 상처의 기록 · `wound_record` | 성장 | SILVER | 재제작 | `immortal_berserker` · 57.7 | 234 |
| 생존 본능 · `survival_instinct` | 생명 | SILVER | 재제작 | `xp_silver` · 59.0 | 233 |
| 역습의 방패 · `counter_shield` | 금속 | SILVER | 기존 심볼 가공 | `overgrowth` · 53.3 | 222 |
| 연쇄 충격 · `chain_impact` | 마법 | SILVER | 기존 심볼 가공 | `russian_doll` · 61.8 | 235 |
| 육중한 주먹 · `heavy_fist` | 대지 | SILVER | 기존 심볼 가공 | `immortal_berserker` · 57.6 | 220 |
| 응급 회복 · `heal` | 생명 | SILVER | 기존 심볼 가공 | `mineral_devourer` · 56.9 | 234 |
| 잔상 질주 · `afterimage_stride` | 그림자 | SILVER | 기존 심볼 가공 | `iron_stomach` · 58.9 | 228 |
| 중첩 공룡 · `stack_dinosaur` | 성장 | SILVER | 기존 심볼 가공 | `delayed_blast` · 72.6 | 250 |
| 철의 위장 · `iron_stomach` | 금속 | SILVER | 기존 심볼 가공 | `afterimage_stride` · 58.9 | 232 |
| 포식자 · `predator` | 생명 | SILVER | 기존 심볼 가공 | `blood_trace` · 52.6 | 237 |
| 폭발 화살 · `explosive_arrow` | 화염 | SILVER | 기존 심볼 가공 | `kinetic_reversal` · 57.5 | 215 |
| 폭주 기관 · `berserk_engine` | 기계 | SILVER | 기존 심볼 가공 | `immortal_berserker` · 69.7 | 247 |
| 피의 기록 · `blood_record` | 성장 | SILVER | 기존 심볼 가공 | `immortal_armor` · 66.3 | 218 |
| 혈흔 추적 · `blood_trace` | 생명 | SILVER | 기존 심볼 가공 | `predator` · 52.6 | 214 |
| 강철심장 · `steel_heart` | 금속 | GOLD | 기존 심볼 가공 | `duel_contract` · 50.6 | 233 |
| 거인 학살자 · `giant_slayer` | 대지 | GOLD | 기존 심볼 가공 | `bomb_alchemy` · 59.0 | 223 |
| 결투의 계약 · `duel_contract` | 마법 | GOLD | 기존 심볼 가공 | `steel_heart` · 50.6 | 245 |
| 경험 가속 · `xp_gold` | 성장 | GOLD | 기존 심볼 가공 | `armor` · 54.0 | 233 |
| 광물 포식자 · `mineral_devourer` | 성장 | GOLD | 기존 심볼 가공 | `immortal_berserker` · 49.5 | 246 |
| 궁극의 히드라 · `ultimate_hydra` | 검술 | GOLD | 기존 심볼 가공 | `frost_bind` · 71.8 | 249 |
| 그림자 도약 · `shadow_dash` | 그림자 | GOLD | 재제작 | `rear` · 62.9 | 238 |
| 낙하 강타 · `fall` | 대지 | GOLD | 기존 심볼 가공 | `bomb_alchemy` · 66.7 | 244 |
| 도탄 화살 · `ricochet_arrow` | 마법 | GOLD | 기존 심볼 가공 | `gigantification` · 71.2 | 252 |
| 마법 미사일 · `magic_missile` | 마법 | GOLD | 기존 심볼 가공 | `bounty_hunter` · 54.0 | 222 |
| 맹독 군락 · `venom_swarm` | 마법 | GOLD | 기존 심볼 가공 | `blood_crown` · 57.0 | 204 |
| 배후 공격 · `rear` | 그림자 | GOLD | 기존 심볼 가공 | `bomb_alchemy` · 60.2 | 201 |
| 분열의 활 · `split_bow` | 마법 | GOLD | 기존 심볼 가공 | `time_thief` · 58.4 | 194 |
| 분열하는 공격 · `split_attack` | 검술 | GOLD | 기존 심볼 가공 | `time_thief` · 55.6 | 250 |
| 불멸의 갑옷 · `immortal_armor` | 금속 | GOLD | 기존 심볼 가공 | `last_demon` · 55.4 | 225 |
| 서리 속박 · `frost_bind` | 마법 | GOLD | 기존 심볼 가공 | `delayed_blast` · 45.4 | 223 |
| 시간 도둑 · `time_thief` | 시간 | GOLD | 기존 심볼 가공 | `russian_doll` · 53.9 | 222 |
| 심장 수확자 · `heart_harvester` | 생명 | GOLD | 재제작 | `defense` · 62.6 | 226 |
| 유레카 · `eureka` | 시간 | GOLD | 기존 심볼 가공 | `armor` · 49.3 | 249 |
| 전투 적응 · `armor` | 금속 | GOLD | 기존 심볼 가공 | `bomb_alchemy` · 48.6 | 214 |
| 지연 폭발 · `delayed_blast` | 화염 | GOLD | 재제작 | `frost_bind` · 45.4 | 228 |
| 지옥의 전도체 · `infernal_conductor` | 화염 | GOLD | 기존 심볼 가공 | `venom_swarm` · 61.3 | 211 |
| 탱크 엔진 · `tank_engine` | 기계 | GOLD | 재제작 | `blood_crown` · 54.4 | 249 |
| 폭탄 연금술 · `bomb_alchemy` | 기계 | GOLD | 기존 심볼 가공 | `armor` · 48.6 | 210 |
| 폭풍 추적자 · `storm_chaser` | 이동 | GOLD | 기존 심볼 가공 | `steel_heart` · 59.6 | 237 |
| 폭풍의 검 · `storm_blade` | 검술 | GOLD | 재제작 | `phoenix_heart` · 62.6 | 248 |
| 피의 왕관 · `blood_crown` | 생명 | GOLD | 재제작 | `tank_engine` · 54.4 | 226 |
| 현상금 사냥꾼 · `bounty_hunter` | 마법 | GOLD | 기존 심볼 가공 | `magic_missile` · 54.0 | 239 |
| 환영 무기 · `phantom_weapon` | 그림자 | GOLD | 기존 심볼 가공 | `bomb_alchemy` · 59.1 | 235 |
| 거인화 · `gigantification` | 대지 | PRISMATIC | 기존 심볼 가공 | `jeweled_gauntlet` · 59.6 | 238 |
| 검을 뽑아라 · `draw_the_sword` | 검술 | PRISMATIC | 기존 심볼 가공 | `immortal_berserker` · 53.1 | 201 |
| 경험 폭발 · `xp_prism` | 성장 | PRISMATIC | 기존 심볼 가공 | `blood_god` · 49.6 | 248 |
| 공허 군주 · `void_lord` | 공허 | PRISMATIC | 재제작 | `blood_god` · 53.4 | 252 |
| 광전사의 심장 · `berserk` | 생명 | PRISMATIC | 재제작 | `immortal_berserker` · 58.3 | 219 |
| 금단의 거래 · `forbidden_deal` | 공허 | PRISMATIC | 기존 심볼 가공 | `infinite_repeat` · 63.3 | 251 |
| 금단의 재구성 · `forbidden_reconstruction` | 공허 | PRISMATIC | 기존 심볼 가공 | `last_demon` · 67.2 | 252 |
| 끝없는 진화 · `endless_evolution` | 성장 | PRISMATIC | 기존 심볼 가공 | `time_thief` · 58.8 | 206 |
| 러시아 인형 · `russian_doll` | 생명 | PRISMATIC | 기존 심볼 가공 | `time_thief` · 53.9 | 201 |
| 무한 반복 · `infinite_repeat` | 마법 | PRISMATIC | 기존 심볼 가공 | `forbidden_deal` · 63.3 | 250 |
| 미친 과학자 · `mad_scientist` | 마법 | PRISMATIC | 기존 심볼 가공 | `bomb_alchemy` · 53.1 | 181 |
| 보석 건틀릿 · `jeweled_gauntlet` | 마법 | PRISMATIC | 기존 심볼 가공 | `calamity_giant` · 56.3 | 244 |
| 복제의 손 · `clone_hand` | 그림자 | PRISMATIC | 기존 심볼 가공 | `armor` · 65.0 | 244 |
| 불멸의 광전사 · `immortal_berserker` | 생명 | PRISMATIC | 기존 심볼 가공 | `frost_bind` · 49.0 | 178 |
| 불사조 심장 · `phoenix_heart` | 생명 | PRISMATIC | 재제작 | `last_demon` · 53.4 | 221 |
| 삼중 반응로 · `triad_reactor` | 기계 | PRISMATIC | 기존 심볼 가공 | `heart_harvester` · 65.7 | 248 |
| 세계 침식 · `world_erosion` | 공허 | PRISMATIC | 재제작 | `last_bastion` · 75.2 | 246 |
| 시간 역행 · `time_rewind` | 시간 | PRISMATIC | 기존 심볼 가공 | `final_judgment` · 61.1 | 243 |
| 양손잡이 · `dual_wielder` | 검술 | PRISMATIC | 재제작 | `immortal_armor` · 55.8 | 246 |
| 연쇄 추격 · `pursuit` | 이동 | PRISMATIC | 기존 심볼 가공 | `russian_doll` · 54.5 | 191 |
| 우주의 중심 · `center_of_universe` | 공허 | PRISMATIC | 재제작 | `defense` · 59.7 | 255 |
| 재앙의 거인 · `calamity_giant` | 대지 | PRISMATIC | 기존 심볼 가공 | `jeweled_gauntlet` · 56.3 | 246 |
| 지진술사 · `earthquake_mage` | 대지 | PRISMATIC | 기존 심볼 가공 | `last_demon` · 54.4 | 225 |
| 최후의 심판 · `final_judgment` | 검술 | PRISMATIC | 기존 심볼 가공 | `time_rewind` · 61.1 | 209 |
| 최후의 악마 · `last_demon` | 화염 | PRISMATIC | 기존 심볼 가공 | `phoenix_heart` · 53.4 | 221 |
| 최후의 요새 · `last_bastion` | 금속 | PRISMATIC | 기존 심볼 가공 | `immortal_berserker` · 56.1 | 234 |
| 최후의 저항 · `defense` | 금속 | PRISMATIC | 재제작 | `frost_bind` · 48.8 | 240 |
| 피의 신 · `blood_god` | 생명 | PRISMATIC | 재제작 | `xp_prism` · 49.6 | 219 |
