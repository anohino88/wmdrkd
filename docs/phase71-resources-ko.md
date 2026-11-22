# PHASE 7.1 AugmentWild 리소스팩

Java Edition 26.3의 리소스팩 형식은 97.1이다. [Mojang 26.3 릴리스 노트](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)

PHASE 7.4는 기본 인벤토리 제목, 안전 모드 및 서버 다운로드 진단을 사용한다. 최신 팩과 문제 해결은 [PHASE 7.4 안내](phase74-recovery-ko.md)를 참고한다.

## 설치

1. Actions의 `augmentwild-resource-pack` 아티팩트에서 `augmentwild-26.3.zip`을 꺼낸다. 로컬에서는 `python3 scripts/resource-pack.py`가 같은 ZIP을 만든다.
2. 해당 ZIP 자체를 플레이어가 접근할 수 있는 HTTPS 주소에 올린다. 서버 `plugins/AugmentWild/config.yml`에 그 직접 주소를 `art.url`, 스크립트 출력의 40자리 해시를 `art.sha1`으로 설정한다.
3. 서버를 다시 시작하고 접속한 뒤 팩을 수락한다. Paper가 이 팩 UUID의 로드 성공을 보고하면 이후 열리는 메뉴에서 아이콘을 사용한다. 빈 URL, 로드 실패 및 거절 시 기존 아이템 아이콘과 기본 연출을 사용한다.

디스플레이는 클라이언트별로 표시하고 경기 종료 시 제거한다. 액티브 스킬과 강화 효과는 최대 13틱 동안 3단계 크기·회전·이동을 보간한다. 보급은 도착 연출과 일반·고급 표식을 사용한다. 블록을 통한 상자 상호작용과 복구 원장은 그대로 유지된다.

## 소스 및 제작

- Game-icons.net의 84개 증강과 7개 GUI 심볼을 제작자별로 확보했다. 투명 마스크로 추출하고 64px 배지에 합성했다. 골드·프리즘은 등급별 테두리와 마커를 표시한다. [Game-icons.net 소스](https://github.com/game-icons/icons/tree/82d948812bfe3f269ef8f731dcdb07b08160edc4)
- Kenney Interface Sounds에서 발동·진화·보급 음원 세 개를 채택했다. 고정된 공개 미러와 원본 CC0 고지를 대조했다. 원본은 mono Vorbis로 변환하고 음량과 페이드를 적용했다. [Kenney 원본](https://kenney.nl/assets/interface-sounds)
- itch.io와 OpenGameArt의 CC0 파티클은 후보로 검토했다. Poly Haven/BlenderKit과 Sketchfab/Fab에서는 외부 메시와 배포 조건을 검토했다. Freesound는 음원별 라이선스 확인이 필요하다. 모델은 낮은 비용의 Minecraft 큐보이드 형태로 Blender에서 생성했다. [itch.io 후보](https://bobkingg.itch.io/magic-spell-particles-pack) · [OpenGameArt 후보](https://opengameart.org/content/rpg-vfx-pack) · [Poly Haven](https://polyhaven.com/license) · [BlenderKit](https://www.blenderkit.com/docs/licenses/) · [Sketchfab](https://www.sketchfab.com/blogs/community/refine-downloadable-model-searches-with-new-license-filters/) · [Fab](https://www.fab.com/eula) · [Freesound](https://freesound.org/help/faq/)
- 모든 외부 입력의 제작자, 용도, 파일 경로, 라이선스, 가공 내역, SHA-256은 `ASSET_MANIFEST.json`에 있다. `THIRD_PARTY_NOTICES.md`와 고지 원문은 ZIP에도 포함한다.

Blender 4.3.2, Inkscape 1.4, Python 3 + Pillow, FFmpeg를 설치한 환경에서는 다음 명령으로 오프라인 재생성한다.

```sh
python3 scripts/resource-pack.py --bake
```

Blender Python이 14개 메시를 만들고 Minecraft의 모델 요소로 내보낸다. 모델당 큐보이드 상한은 32개다. 런타임 플러그인은 Paper의 ItemDisplay를 사용한다. 다른 모델 엔진은 별도 검증하지 않았다.

## 배포 및 적용 확인

배포용 ZIP은 `distribution/augmentwild-26.3.zip`이다. 검증기는 내려받은 바이트 전체가 이 파일과 일치할 때만 설정을 출력한다. 기본 주소는 이 ZIP이 마지막으로 변경된 GitHub 커밋에 고정된다. 자체 호스팅 시 `--url`에 공개 HTTPS 정적 파일 주소를 지정한다. Actions 아티팩트의 바깥 ZIP과 GitHub 파일 보기 페이지는 직접 배포 주소로 사용할 수 없다.

```sh
python3 scripts/install-pack.py
python3 scripts/install-pack.py --apply /path/to/server/plugins/AugmentWild/config.yml
```

`--apply`에는 Python 3와 PyYAML이 필요하다. 서버를 정지한 상태에서 실행하고 다시 시작한다. 설치기는 기존 YAML의 다른 설정과 주석을 유지하고 같은 디렉터리의 임시 파일을 교체한다. URL 오류·TLS 오류·다른 바이트를 받으면 기존 설정을 유지한다. 플레이어는 빠른 메뉴의 **리소스팩** 버튼에서 상태를 확인하고 다운로드 실패 후 재요청할 수 있다. 팩을 수락하지 않아도 바닐라 아이템과 짧은 본인 전용 파티클을 사용한다.

## 화면 및 효과 검사

진행 중인 전용 실험 경기에서 관리자 참가자는 `/aw admin test preview`를 사용한다. 하단 분류로 이동하고 중앙 설정 버튼으로 원본 모델과 간소 연출을 비교한다. 모델·효과음 버튼은 보상·피해·블록 배치를 실행하지 않는다. 실제 도감에서는 좌클릭으로 즐겨찾기를 관리하고 우클릭으로 상세 카드를 연다.

- [모든 발동 경로와 검사 범위](art71/augment-audit.md)
- [84종 아이콘](art71/icons.png) · [14개 모델](art71/models.png) · [액티브 키프레임](art71/animation.png)
- [12종 화면 시안](art71/interfaces.png) · [보급 비교](art71/supply.png) · [등급 비교](art71/rarity.png)
- [편집용 Blender 파일](../resource-pack/studio/augmentwild-studio.blend) · [모델 생성기](../resource-pack/source/geometry.py) · [라이선스](../THIRD_PARTY_NOTICES.md)

Blender 렌더와 GUI 시안은 오프라인 제작 자료다. 실제 플레이어의 창 크기, 언어 설정, 조명과 프레임률은 클라이언트에서 확인해야 한다.

```sh
python3 scripts/resource-pack.py --bake --output distribution/augmentwild-26.3.zip
blender -b -t 4 --factory-startup --python-exit-code 1 --python resource-pack/source/studio.py -- "$PWD" build/art-studio
python3 scripts/art-sheets.py build/art-studio
python3 scripts/audit-art.py
```

시트 생성기는 Pillow와 Noto Sans CJK를 사용한다. Blender 스튜디오에는 Minecraft로 내보낸 큐보이드, 텍스처, 액티브 7종의 세 키프레임이 들어 있다.
