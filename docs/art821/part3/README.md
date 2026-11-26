# PART 3 · 전투 연출 검토

배포용 아이템 모델 JSON, 텍스처와 서버 키프레임으로 제작한 Blender 미리보기다. 게임 클라이언트 검증은 `REAL_CLIENT_VERIFIED=NOT_RUN`이다.

| 자료 | 확인할 내용 |
| --- | --- |
| [액티브 7종 전후](active-models.png) · [진화 6종 전후](evolution-models.png) · [시너지 5종·공명 2종 전후](synergy-models.png) | 같은 배율에서 중심 실루엣과 재질 비교 |
| [진화 키프레임](evolution-frames.png) · [시너지·공명 키프레임](synergy-frames.png) | 배포 시퀀스에서 뽑은 프레임과 틱 번호 |
| [그림자 질주](shadow_dash.gif) · [지진술사](earthquake_mage.gif) · [시간 역행](time_rewind.gif) · [공허 군주](void_lord.gif) · [폭탄 연금술](bomb_alchemy.gif) · [결투 계약](duel_contract.gif) · [재앙의 거인](calamity_giant.gif) | 2틱마다 표본을 잡고 20 TPS 기준 속도로 재생 |
| [거리·품질 비교](readability.png) · [계열 12종](families.png) | 70° 카메라에서 3·8·16·24블록 투영과 독립 부품을 제외한 근접 실루엣 |
| [성장 단계](growth.png) · [성장 원장 감사](growth-ko.md) | 기본 공용 모델과 금속·기계·검·생명·비전의 단계별 구조 |
| [일반·고급 보급 전후](supply.png) · [일반 개봉](supply.gif) · [고급 개봉](premium.gif) | 기본 3초 진행 중 실제 힌지 표본과 완료 위치. 중단하면 서버가 닫힌 자세로 복구한다. |
| [음원 A/B](sounds.ogg) | 검술→공허→그림자→금속→기계→대지→마법→생명→성장→시간→이동→화염. 각 계열은 기존 대표 큐 다음에 새 계열 음원 순서다. |
| [증강 84종 발동 감사](augments-ko.md) | 실제 소스 경로, 발동 조건, 아이콘과 모델, 계열 음원 연결 |
| [Blender 원본](visual-evolution.blend) · [Blockbench 원본 22개](blockbench/) | Blender에 텍스처와 키프레임을 포함했다. Blockbench에서는 코어와 보급의 모델 그룹·피벗을 편집하고 시간표는 서버 TSV에서 관리한다. |

거인의 다섯 손가락은 메시에 분절되어 있다. 전투에서는 손가락 묶음과 전완 두 트랙을 추가로 움직인다. 기관차에는 독립 차륜과 왕복 구동축, 천 개의 검에는 발사 시점이 서로 다른 두 검, 시간 역행에는 별도 축으로 움직이는 시곗바늘 두 개를 쓴다. 실제 그래픽 클라이언트의 투영, 타격 타이밍 체감과 음원 믹싱은 확인하지 못했다.

## 재현

```bash
mkdir -p /tmp/aw821-baseline /tmp/aw821-frames
git archive 70ab670 -- resource-pack/pack/assets/augmentwild | tar -x -C /tmp/aw821-baseline
python3 scripts/resource-pack.py --bake-visual --output distribution/augmentwild-26.3.zip
blender -b -t 3 --factory-startup --python-exit-code 1 \
  --python resource-pack/source/visual-studio.py -- "$PWD" /tmp/aw821-baseline /tmp/aw821-frames --phase821-part3
python3 scripts/phase821-visual-sheets.py /tmp/aw821-baseline /tmp/aw821-frames docs/art821/part3
```

공개 자료에서 픽셀·실루엣, 피벗, 다중 파트 및 효과 가독성 기법을 조사했다. 새 모델, 재질과 음원은 저장소의 생성 코드에서 제작했다. 기존 증강 문양의 출처는 기존 자산 문서를 따른다. [Blockbench 스타일](https://blockbench.net/wiki/guides/minecraft-style-guide/) · [ModelEngine 모델](https://wiki.mythiccraft.io/modelengine/Modeling/Creating-a-Model) · [ModelEngine 애니메이션](https://wiki.mythiccraft.io/modelengine/Modeling/Animating-a-Model) · [Riot 시각 효과 자료](https://www.riotgames.com/pl/artedu/visual-effects)
