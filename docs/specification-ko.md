> 최신 규칙과 개발 순서는 [최종 통합 명세](final-specification-ko.md)가 우선합니다. 이 문서는 증강 64종을 포함한 세부 명세를 보존합니다.

# AUGMENTWILD — 최종 통합 개발 명세서

당신은 숙련된 Minecraft Paper 플러그인 엔지니어, 게임 서버 아키텍트, 테스트 엔지니어다.

연결된 새 빈 GitHub 저장소에 Minecraft Java Edition용 미니게임 플러그인
`AugmentWild / 증강 야생`을 실제로 빌드, 테스트, 실행 가능한 상태까지 개발하라.

이 문서는 이 저장소의 최우선 개발 명세다.
이전 프롬프트, 이전 작업 메모, 이전 대화 내용보다 이 문서를 우선한다.

단순히 파일이나 클래스를 만들어 놓는 것이 목표가 아니다.
실제 Paper 서버에서 친구들과 플레이 가능한 수준의 완성된 미니게임을 만드는 것이 목표다.

==================================================
A. 최우선 원칙
==================================================

1. 작업을 시작하기 전에 반드시 현재 공식 지원 상태를 검증한다.

확인할 항목:
- Minecraft Java Edition 26.3이 공식 출시 버전인지
- Paper 26.3이 현재 사용 가능한지
- `paper-api`의 실제 Maven 좌표
- Paper 26.3이 요구하는 Java/JDK 버전
- `plugin.yml` 또는 적절한 Paper 플러그인 메타데이터의 api-version 표기
- 사용하려는 Paper API가 26.3에서 실제 존재하는지

기본 예상 환경:
- Minecraft Java Edition 26.3
- Paper 26.3
- Java 25
- Gradle Kotlin DSL

그러나 공식 문서 또는 Maven 저장소와 충돌하면 추측하지 말고 공식 정보를 우선한다.

존재하지 않는 의존성 버전 문자열을 만들지 마라.

공식 버전 확인에 실패하면:
1. 코드 작성을 무작정 진행하지 말고
2. PROGRESS.md에 BLOCKED 사유를 기록하고
3. 검증 가능한 최신 지원 버전을 기록하되
4. 사용자 요구인 26.3을 임의로 다른 버전으로 바꾸지 마라.

2. 빌드 실패를 무시하지 마라.

각 단계 종료 전에 최소한:
- `./gradlew clean build`
- 테스트
- `git diff --check`

를 수행한다.

빌드가 실패하면 그 단계는 완료된 것이 아니다.

3. 구현되지 않은 기능을 구현 완료라고 표시하지 마라.

미완성 증강은:
- `implemented: false`
- 선택 풀 제외
- 관리자 목록에는 미완성으로 표시

하도록 처리한다.

4. 브랜치를 남발하지 마라.

권장:
- `main`
- 필요할 경우 단일 작업 브랜치 `codex/augmentwild`

기능 하나마다 브랜치를 만들지 않는다.

Codex Cloud 또는 저장소 정책상 별도 브랜치가 강제된다면 그 제약을 따르되,
추가 기능 브랜치를 임의로 생성하지 않는다.

한 작업을 계속 수정할 때는 가능한 한 같은 Codex Cloud task를 이어간다.

5. 단계별로 의미 있는 커밋을 만든다.

예:
- build: initialize Paper 26.3 project
- feat: add match state machine
- feat: add arena region finder
- feat: add respawn system
- feat: add augment engine
- feat: implement core augments
- test: add match integration tests

6. 항상 PROGRESS.md를 갱신한다.

내용:
- 현재 단계
- 완료 기능
- 미완료 기능
- 현재 빌드 상태
- 자동 테스트 결과
- 실제 서버 테스트 결과
- 알려진 버그
- 성능 문제
- 다음 작업
- BLOCKED 항목

7. 저장소 루트에 AGENTS.md도 작성한다.

AGENTS.md에는:
- 이 프로젝트의 핵심 규칙
- Java/Paper 버전
- 빌드 명령
- 테스트 명령
- 브랜치 정책
- 하드코딩 금지
- 미완성 기능 비활성화 원칙
- 게임 상태 머신 불변식
- 설정 snapshot 원칙
- 공유 월드 보호 원칙

을 간결하게 기록해서 이후 Codex 작업도 동일한 기준을 따르게 한다.

==================================================
B. 프로젝트 기술 스택
==================================================

프로젝트명:
AugmentWild

패키지명 예:
`io.github.<owner>.augmentwild`

환경:
- Minecraft Java Edition 26.3
- Paper 26.3
- Java 25
- Gradle Kotlin DSL
- Gradle Wrapper 포함
- Paper API
- Adventure API
- JUnit 5
- 적합한 경우 MockBukkit 또는 대체 테스트 프레임워크
- GitHub Actions

원칙:
- NMS 직접 접근 금지
- CraftBukkit 내부 클래스 의존 금지
- reflection 기반 버전 해킹 금지
- 공식 Paper/Bukkit API 우선
- 정말 공식 API로 불가능한 기능만 별도 abstraction 뒤에 격리
- 그 경우 README에 한계 기록

README:
- 한국어
- 설치
- 빌드
- 서버 준비
- 월드 준비
- 게임 실행
- 관리자 명령어
- 설정 파일
- 증강 목록
- 문제 해결
- 성능 관련 주의사항
- 알려진 제한사항

==================================================
C. 게임 핵심 컨셉
==================================================

게임명:
증강 야생 / AugmentWild

기본 모드:
FFA 개인전

향후 팀전을 넣을 수 있도록 Team abstraction은 설계하지만
첫 출시에서는 FFA가 기본이다.

핵심 흐름:
- 기존 야생 월드에서 랜덤 경기장을 찾는다.
- 30분 동안 야생 파밍한다.
- 5분마다 강력한 증강을 하나 선택한다.
- 증강은 누적되고 서로 시너지를 만든다.
- 30분 마지막 증강은 프리즘 확정이다.
- 파밍 시작부터 PvP가 가능하며 30분 20초부터 목숨이 제한된다.
- 자기장이 1000×1000에서 60×60까지 줄어든다.
- 최후 생존자가 승리한다.

증강들은 단순 +5% 능력치 수준이 아니라
플레이 스타일 자체가 변할 정도로 강력해야 한다.

==================================================
D. 경기 타임라인
==================================================

기본값:

00:00
- 경기 시작
- 플레이어 무작위 안전 좌표 배치
- 파밍 시작
- PvP 허용, 사망 시 목숨 무제한

05:00
- 증강 선택 1

10:00
- 증강 선택 2

15:00
- 증강 선택 3

20:00
- 증강 선택 4

25:00
- 증강 선택 5

보급은 일반 6~10분과 14~20분, 고급 23~28분에서 무작위 초를 정한다.
각 이벤트는 상자 1개이며 등장 2분 전 예고, 1분 전 좌표를 공개한다.

30:00
- 증강 선택 6
- 프리즘 3개 중 1개 확정 선택

30:20
- 목숨 제한 시작
- 자기장 축소 시작

40:20
- 자기장 60×60 도달
- SUDDEN_DEATH 진입

이후:
- 최후 생존자 결정

모든 시간은 설정 가능하다.

==================================================
E. 게임 시간 설계
==================================================

단순 Bukkit scheduler task 여러 개를 따로 만들어 시간을 관리하지 마라.

중앙 `MatchClock`을 만든다.

시간 개념을 최소 다음처럼 분리한다.

- wall-clock
- match elapsed time
- phase elapsed time
- paused time

증강 선택 중 시간 처리 기본값:

`pause-match-clock-during-selection: true`

즉:
- 증강 GUI 선택 중 경기 시간 정지
- 월드 자체 서버 tick은 계속 진행될 수 있음
- 목숨 제한 시작 시각
- 보급 등장
- 자기장 시작
- 다음 증강 선택

등 모든 경기 이벤트는 `MatchClock` 기준으로 밀린다.

동일한 timestamp에 여러 이벤트가 있으면 순서를 명시한다.

예:
15:00
1. 게임 이벤트 진입
2. 증강 선택
3. 선택 종료
4. 보급 상자 실제 spawn

이벤트 순서를 deterministic하게 만들어라.

서버 lag가 발생해도:
- 이벤트 중복 발생 금지
- 이벤트 누락 금지
- 같은 증강 라운드 두 번 열림 금지

각 scheduled event는 고유 ID와 executed flag를 가진다.

==================================================
F. 경기 상태 머신
==================================================

필수 상태:

WAITING
PREPARING
GATHERING
AUGMENT_SELECTION
PVP
SUDDEN_DEATH
ENDING
CLEANUP

상태 전환은 단일 `MatchStateMachine`을 통해 수행한다.

플레이어 상태도 별도로 관리한다.

PLAYER_ACTIVE
PLAYER_RESPAWNING
PLAYER_ELIMINATED
PLAYER_SPECTATING
PLAYER_DISCONNECTED

상태 변경은 명시적인 method를 통해서만 수행한다.

예:
- enterRespawn()
- eliminate()
- reviveByAugment()
- reconnect()
- leaveMatch()

동시 이벤트에 강하도록 만든다.

특히 다음 race condition을 막아라.

- 같은 tick에 lethal damage 두 번
- lethal damage와 quit 동시에 발생
- lethal damage와 revive augment 동시에 발생
- border damage와 melee damage 동시 발생
- 죽은 순간 마지막 상대도 사망
- respawn countdown 0과 sudden death 진입 동시 발생
- 관리자의 `/aw admin stop`과 death event 동시 발생

승리 판정은 Bukkit 이벤트마다 임의로 호출하지 말고
상태 변경 후 중앙 VictoryManager에 요청한다.

==================================================
G. 로비와 경기 참가 전 상태 보존
==================================================

경기 참가자의 기존 Minecraft 상태를 반드시 보존한다.

`PlayerPreMatchSnapshot` 생성.

저장 항목:
- inventory
- armor
- offhand
- XP level
- XP progress
- total XP
- health
- max health 관련 기본값
- hunger
- saturation
- potion effects
- game mode
- location
- world
- flight state
- fire ticks
- active item state
- scoreboard 관련 AugmentWild 변경분
- player border
- plugin이 변경한 attributes

경기 입장 시 snapshot 저장.

경기 종료, leave, kick, admin stop 시:
- snapshot 복원

복원은 idempotent해야 한다.

두 번 호출해도:
- 아이템 복제 금지
- attribute 중복 제거
- potion 중복 금지

기본 로비:
`arenas.yml`에 lobby world/location 설정.

로비가 없으면 관리자에게 명확한 오류.

==================================================
H. 기존 월드 기반 랜덤 경기장
==================================================

절대 하지 말 것:
- 매 경기 새 월드 생성
- 기존 월드 삭제
- 월드 seed 변경
- 경기 종료 후 rollback
- 경기 구역 블록 원상복구
- WorldEdit 의존 강제
- 경기 때문에 원본 월드를 복제하거나 교체

매 경기 기존 지정 월드에서 새로운 영역을 선택한다.

기본 경기장:
- 1000 × 1000
- 정사각형
- 랜덤 중심
- 바다 바이옴 0%
- 이전 경기와 겹치지 않음

==================================================
I. 바다 없는 1000×1000 탐색
==================================================

중요:

"중심점 몇 개만 검사해서 바다가 없다"고 판단하면 안 된다.

1000×1000은 매우 넓으므로
정확성과 TPS를 모두 고려한 단계적 검증 시스템을 구현한다.

`ArenaSearchService`

단계:

PHASE 1
Random candidate center 생성.

PHASE 2
중심점 / 모서리 / 가장자리 / 내부 coarse grid 검사.

하나라도 ocean 계열이면 즉시 후보 거부.

PHASE 3
통과한 후보에 대해 더 촘촘한 biome 검증.

PHASE 4
설정이 `ocean-policy: ZERO`라면
최종적으로 경기 구역의 지표면 biome 셀을 설정된 검증 해상도로 전수 검사한다.

바이옴은 3D이므로
경기 지표면 기준 biome 판정을 명확히 정의한다.

추천:
- x/z 검증 셀마다 MOTION_BLOCKING 또는 적절한 heightmap의 지표면 Y 계산
- 해당 surface Y의 biome 확인

`ocean-policy`:

ZERO
- 검사된 모든 surface biome cell에서 ocean 계열 0개 요구

RATIO
- max-ocean-ratio 이하 허용

CENTER_AND_SPAWNS
- 개발 및 비상 fallback용
- 기본 OFF

기본:
ZERO

Ocean 판정은 문자열 contains("OCEAN") 하나에만 의존하지 말고
26.3 registry/tag/key 기반으로 구현할 수 있는 가장 안전한 방식을 사용한다.

설정에서 추가 forbidden biome key를 넣을 수 있게 한다.

예:
forbidden-biomes:
  - minecraft:ocean
  - minecraft:deep_ocean
  ...

==================================================
J. 경기장 탐색 성능
==================================================

메인 스레드에서 수천 개 chunk를 한 번에 강제 로딩하지 마라.

다음 구조를 사용한다.

- 비동기 chunk request가 가능한 경우 Paper API 활용
- Bukkit/Paper API의 thread-safety 준수
- 실제 world/block/biome access가 main thread를 요구하면 main thread task budget으로 분할
- tick 당 최대 작업량 설정
- candidate별 timeout
- 전체 search timeout
- 취소 token
- admin stop 시 즉시 취소

기본 설정 예:
arena-search:
  max-candidate-attempts: 80
  max-search-seconds: 120
  chunk-requests-per-tick: 4
  biome-samples-per-tick: 2048

위 값은 모두 조정 가능.

성능 목표:
- 경기장 탐색 동안 평균 TPS가 눈에 띄게 장시간 멈추지 않음
- 단일 tick에서 대량 synchronous chunk load 금지
- 메인 thread stall을 로그로 감지할 수 있게 debug timing 제공

`/aw admin arena searchdebug`

에서:
- 후보 개수
- 로드 chunk 수
- 검사 biome cell 수
- 실패 사유
- 걸린 시간

을 확인할 수 있게 한다.

==================================================
K. 경기장 검색 실패 정책
==================================================

바다 없는 1000×1000 영역을 못 찾았다고
몰래 바다 지역을 선택하면 안 된다.

검색 실패 시:
- PREPARING 중단
- 경기 시작 안 함
- 참가자는 로비로 복귀
- 관리자에게 실패 이유 출력
- 어떤 조건이 가장 많이 실패했는지 출력

예:
"No valid 1000x1000 zero-ocean region found after 80 candidates."

선택 가능한 관리자 조치:
- search radius 증가
- candidate attempts 증가
- ocean policy 변경
- 사용 지역 기록 초기화
- 다른 world 지정

==================================================
L. 사용 경기장 기록
==================================================

`used-regions.yml`

저장:
- unique match id
- world key
- centerX
- centerZ
- width
- height
- startedAt
- endedAt
- search policy

기본 최소 중심 거리:
1500 blocks

단순 center distance만으로 겹침 판정을 하지 말고
AABB intersection + configurable buffer 사용.

설정:
used-region-buffer-blocks: 500

관리자:
`/aw admin arena history`
`/aw admin arena clearhistory`
`/aw admin arena reroll`
`/aw admin arena preview`

==================================================
M. 안전 스폰
==================================================

플레이어는 경기 영역 내부 랜덤 좌표에 배치한다.

금지:
- ocean biome
- 물 위
- 용암 위
- 불
- 선인장
- powder snow
- void
- 동굴 내부
- suffocation
- 잎 위
- 높은 낙하 위험
- border 바로 옆

검증:
- solid safe floor
- feet/head air
- 2 block vertical clearance
- 주변 최소 3×3 위험 블록 검사
- surface skylight 또는 surface 기준 확인

기본:
player-min-spawn-distance: 80
border-spawn-margin: 50

여러 플레이어 위치는 Poisson-like rejection 방식으로
서로 충분히 떨어지게 선택한다.

스폰 탐색 실패 시
점진적으로 최소 거리만 완화할 수 있으나
바다/용암/질식 조건은 절대 완화하지 않는다.

==================================================
N. 전용 월드와 공유 월드 안전
==================================================

두 모드를 지원한다.

DEDICATED_WORLD

- 경기 전용 기존 world 사용
- 실제 WorldBorder 사용 가능

SHARED_WORLD

- 같은 world에 비참가자가 존재할 수 있음
- global WorldBorder 변경 금지
- 플레이어별 virtual WorldBorder 표시 사용
- 실제 border damage / movement 제한은 plugin이 직접 처리
- 비참가자에게 border, bossbar, effect, damage 적용 금지

공유 월드 모드에서는
경기 지역 밖으로 나가는 참가자만 제어한다.

비참가자에 절대 영향 주지 않는다.

==================================================
O. 파밍 단계
==================================================

기본 30분.

PvP:
허용

목숨:
무제한

사망 이유:
- mob
- fall
- lava
- fire
- suffocation
- explosion
- environment

등은 정상.

사망 시:
- 아이템 드롭 없음
- XP 드롭 없음
- inventory 유지
- armor 유지
- offhand 유지
- XP 유지
- augment 유지
- permanent stacks 유지
- quest/evolution progress 유지

리스폰:
20초

리스폰 중:
PLAYER_RESPAWNING 상태.

리스폰 대기자는:
- spectator
- 공격 불가
- 아이템 사용 불가
- block interaction 불가
- locate 대상 제외
- 승리 후보에서는 아직 탈락 아님

리스폰 완료:
- 경기장 내부 안전한 육지
- border 안쪽
- 기본 3초 spawn protection

파밍 단계에서 100번 죽어도 탈락하지 않는다.

==================================================
P. PvP 단계 목숨
==================================================

30:20부터 목숨 제한 시작.

기본 PvP lives:
2

의미:
- PvP 단계에서 총 2회의 일반 사망 허용
- 첫 일반 사망 -> remaining lives 1 -> 20초 후 복귀
- 두 번째 일반 사망 -> remaining lives 0 -> 최종 탈락

UI에는 혼동 없게:
`남은 목숨: 2`
부터 시작.

부활 증강으로 막힌 사망은
일반 목숨을 차감하지 않는다.

==================================================
Q. 서든데스
==================================================

자기장이 최종 크기 도달 시:

SUDDEN_DEATH

기본:
- 이후 새로운 일반 respawn 없음
- 죽으면 최종 탈락

경계 진입 직전에 이미 respawn countdown 중인 경우 기본 정책:
`allow-existing-respawn: true`

즉:
- 서든데스 이전에 이미 죽고 countdown 중이었다면 한 번 복귀 가능
- 그 이후 죽으면 탈락

설정 가능하게 한다.

==================================================
R. 부활 우선순위
==================================================

부활과 일반 목숨 시스템을 반드시 하나의 `DeathResolutionService`로 통합한다.

lethal event 처리 순서:

1. lethal damage 감지
2. 이미 death resolution 중이면 중복 처리 차단
3. death-prevention augment 검사
4. immediate revive augment 검사
5. 성공 시 일반 death 취소
6. 실패 시 일반 death 처리
7. phase 확인
8. GATHERING이면 unlimited respawn
9. PVP이면 life 차감
10. SUDDEN_DEATH이면 설정에 따라 elimination
11. 상태 변경
12. victory check queue

부활 우선순위 기본:
- 불사조 심장
- 불멸의 광전사
- 러시아 인형
- 기타 향후 부활

관리자가 priority 수정 가능.

동일 lethal event에 여러 부활 효과를 동시에 소비하면 안 된다.

==================================================
S. 자기장
==================================================

기본:
start-size: 1000
final-size: 60
start-time: 30:20
duration: 600 sec

DEDICATED_WORLD:
가능하면 native WorldBorder 사용.

SHARED_WORLD:
virtual player border + plugin damage.

기본 border damage:
configurable.

추가:
- warning distance
- warning time
- damage interval
- damage per second

순간이동형 증강:
- border 밖 목적지 거부
- border 경계 clamp 옵션
- 위험한 위치면 cast 실패

respawn:
- border 내부만

==================================================
T. 다이아몬드 플레이어 탐지
==================================================

명령어:
`/aw locate`
alias:
`/aw track`

기본 비용:
DIAMOND ×1

기본 쿨타임:
60초

동작:
1. 명령 실행자 경기 참가 확인
2. phase 허용 확인
3. 비용 확인
4. 유효 대상 snapshot 확보
5. 가장 가까운 대상 계산
6. 성공 가능 여부 재검증
7. 비용 원자적 차감
8. 결과 표시

대상:
- 같은 경기
- ACTIVE 상태
- 다른 플레이어

제외:
- self
- spectator
- eliminated
- respawning
- disconnected
- 다른 world
- 팀전이면 teammate

가장 가까운 플레이어 판정:
3D Euclidean distance.

표시:
- actionbar의 8방향
- 선택적으로 이름 공개, 기본 비공개
- 위치·Y좌표·거리는 비공개

성공 후 경기 시간 10초간 방향을 갱신한다.
대상이 사망·탈락·오프라인이 되거나 현재 안전 구역을 벗어나면 종료한다.

유효 대상 없음:
비용 차감 금지.

두 명이 완전히 동일 거리:
UUID stable ordering으로 deterministic tie-break.

==================================================
U. 보급 상자
==================================================

일반은 6~10분, 14~20분에 각각 상자 1개.
고급은 23~28분에 상자 1개.

일반 보급은 전투 장비, 화살, 황금사과, 물약 및 경험치 병을 포함한다.
고급 보급은 희귀·인챈트 장비, 인챈트 책과 강한 소비품을 포함한다.
등장 시에만 보상 세트를 추첨하며 장비와 경험치 레벨은 추첨에 사용하지 않는다.

기본:
2분 전 예고
1분 전 좌표 공개

상자는:
- arena 안
- border 안
- 안전한 육지
- ocean 제외
- lava 제외

동시 open race:
상자 claim을 atomic하게 처리.

유효 거리에서 3초 개봉을 먼저 완료한 참가자만 획득한다. 이동 및 피격 시 개봉을 중단한다.

두 명이 같은 tick에 열어도
보상 duplication 금지.

==================================================
V. 증강 선택
==================================================

5 / 10 / 15 / 20 / 25 / 30분

총 6회.

매번:
3개 중 1개.

기본 선택 시간:
15초

기본 reroll:
1회 / player / match

기본 duplicate:
금지

등급:
SILVER
GOLD
PRISMATIC

일반 선택의 각 slot은 독립 추첨:

SILVER 80
GOLD 18
PRISMATIC 2

즉:
3칸의 rarity roll을 각각 수행한다.

30분:
PRISMATIC 3개 확정
1개 선택.

프리즘 기본 최대:
3개

선택 풀이 부족하면:
- 같은 증강 중복을 조용히 허용하지 말 것
- rarity fallback 정책 적용
- fallback log 남김

==================================================
W. 선택 GUI
==================================================

각 카드 표시:

- 이름
- 등급
- 설명
- 현재 기본 수치
- current stacks
- max stacks
- cooldown
- proc condition
- evolution progress
- synergy names
- late-pick compensation 여부

rarity 시각:
SILVER
GOLD
PRISMATIC

Prismatic 획득:
- title
- sound
- particle
- server announcement 설정 가능

==================================================
X. 증강 엔진 아키텍처
==================================================

필수 core:

AugmentRegistry
AugmentDefinition
AugmentId
AugmentRarity
AugmentInstance
AugmentTrigger
AugmentEffect
AugmentContext
AugmentProcContext
DamageContext
HealContext
AugmentSelectionService
AugmentCooldownManager
AugmentStackManager
AugmentEvolutionManager
AugmentSynergyManager
AugmentProcDispatcher
ReviveResolver
ProjectileService
TemporaryAttributeService
PlayerAugmentState

증강마다 하드코딩 class 1개씩 늘리는 구조만 사용하지 마라.

가능한 효과는 reusable component로 만든다.

예:
- ADD_MAX_HEALTH
- DAMAGE_MULTIPLIER
- BONUS_DAMAGE
- PROJECTILE
- DASH
- AOE
- HEAL
- LIFESTEAL
- STACK_GAIN
- COOLDOWN_REDUCTION
- REVIVE
- DODGE
- ATTRIBUTE_SCALE
- REPEAT_PROC

복잡한 증강만 custom handler 허용.

==================================================
Y. 피해 처리 규칙
==================================================

`DamagePipeline`을 만든다.

모든 augment damage는 가능한 한 이 pipeline을 통과한다.

DamageContext:

- source player
- target entity
- base damage
- damage type
- melee
- ranged
- projectile
- augment
- environmental
- critical
- proc depth
- originating augment
- chain id
- hit id
- bypass armor flag
- lifesteal allowed
- can trigger on-hit
- can trigger on-damage
- duplicated
- synthetic

기본 정책:
augment bonus damage는 별도 명시 없으면 armor 적용.

YAML:
damage-mode:
  armor: NORMAL
  enchantments: NORMAL
  resistance: NORMAL

같은 hit가:
- split attack
- phantom weapon
- infinite repeat
- hydra
- magic missile

를 통해 반복 처리되더라도
무한 loop를 만들지 않는다.

==================================================
Z. 유효 타격 정의
==================================================

`ValidHitPolicy`

기본 유효 타격:

- 공격자 ACTIVE
- 대상 valid hostile mob 또는 active enemy player
- 공격자가 자기 자신 아님
- 팀원 아님
- target invulnerable test dummy 아님
- cancelled event 아님
- 실제 damage > 0
- synthetic proc가 stack을 쌓을 수 있는지는 augment별 설정

다음 악용 금지:
- 같은 무적 대상 반복 hit
- armor stand
- summon dummy
- 자신의 projectile self-hit
- teammate farming

==================================================
AA. 자연 광석 판정
==================================================

광물 중독 등의 증강 때문에
플레이어가 설치한 광석을 다시 캐는 악용을 막는다.

`NaturalOreTracker`

방식:
- 경기 시작 이후 플레이어가 설치한 ore block 위치 기록
- 설치된 ore는 natural ore credit 제외
- piston 이동 / explosion / silk touch 재배치 케이스 고려
- chunk unload 이후에도 경기 동안 상태 유지

광석 대상 Material 목록은 config 가능.

동일 좌표는 한 경기 중 기본 한 번만 progression credit.

==================================================
AB. 몹 악용 차단
==================================================

성장용 mob kill은 `EligibleMobPolicy` 적용.

기본 제외:
- player-spawned custom dummy
- plugin test entity
- armor stand
- tamed own entity
- 명확히 반복 생성기 악용으로 표시된 대상

기본 vanilla spawner mob 처리 정책은 config:
ALLOW
REDUCED
DENY

기본 추천:
REDUCED

mob UUID 중복 credit 금지.

==================================================
AC. 이동 중첩 악용 차단
==================================================

탱크 엔진:

유효 movement:
- normal walking
- sprinting
- swimming optionally

제외:
- teleport
- respawn teleport
- admin teleport
- augment dash
- vehicle
- elytra configurable
- knockback
- piston
- same-block jitter

horizontal distance 기반.

single update maximum credit clamp 적용.

==================================================
AD. 증강 연쇄 안전장치
==================================================

기본:
max-proc-depth: 2

각 proc chain:
UUID chainId.

추적:
- fired augment IDs
- target UUID
- depth
- originating hit
- synthetic flag

기본:
synthetic augment hit는 동일 증강을 다시 발동시키지 않음.

예:
magic missile가 magic missile를 다시 만드는 것 금지.

infinite repeat는 dispatcher에서 반복 수만 늘리고
동일 effect가 다시 infinite repeat를 proc하지 않는다.

==================================================
AE. 쿨타임
==================================================

강한 대신 긴 쿨타임 일변도를 피한다.

기본 가이드:
- frequent proc: 3~10초
- gold active: 8~18초
- prismatic active: 12~35초
- revive: match-limited

단,
각 증강 기본 수치는 아래 정의 사용.

유레카 최대:
70% cooldown reduction.

global minimum cooldown:
1초 기본.

관리자 변경 가능.

==================================================
AF. 영구 중첩 안전
==================================================

`max-stacks: -1`은 디자인상 무제한.

그러나 내부:
- long 사용
- max numeric safety clamp
- attribute upper bound
- finite check
- NaN / Infinity 거부

config:
safety:
  max-health-hearts-hard-cap: 500
  max-damage-multiplier-hard-cap: 100
  max-movement-speed-multiplier-hard-cap: 10
  max-runtime-stacks-hard-cap: 1000000

기본 게임 밸런스 상한과
서버 안전 hard cap을 구분한다.

==================================================
AG. 최신 핵심 증강 30종
==================================================

이 30종을 최우선 구현한다.

모든 수치는 augments.yml에서 변경 가능.

1하트 = Minecraft health 2.

----------------
SILVER 8
----------------

stack_dinosaur
중첩 공룡
- 다른 영구 중첩 증강의 stack gain ×2
- 자기 자신의 stack은 없음
- 배율 config 가능

heavy_fist
육중한 주먹
- 근접 공격에 자신의 최대 체력의 8% 추가 피해
- bonus damage
- armor 적용 기본
- multi-hit proc 중복 여부 config 가능

blood_record
피의 기록
- 적대적 유효 몹 5마리 처치마다 최대 체력 +1하트
- 기본 max-stacks -1
- 사망 후 유지

mineral_addiction
광물 중독
- 자연 광석 12개마다 모든 공격 피해 +4%
- max-stacks -1
- 설치 광석 제외

sword_wind
검풍
- 근접 공격 3회마다
- 7블록 전방 검기
- 피해 4하트
- line projectile / ray 기반
- 벽 관통 기본 false

berserk_engine
폭주 기관
- 유효 타격마다 이동속도 +6%
- 최대 +90%
- 5초간 유효 타격 없으면 모두 초기화

reflex
반사 신경
- 12초마다 다음 유효 incoming attack 완전 회피
- 공격자 방향으로 최대 5블록 안전 순간이동
- border 밖 금지
- safe destination 없으면 피해만 회피하고 이동 생략 가능하도록 config

overgrowth
과잉 성장
- 획득 즉시 최대 체력 +8하트
- 이후 5분마다 +4하트
- 경기 30분 이후 획득 시 +12하트 즉시 적용
- late compensation 시간/sum config

----------------
GOLD 10
----------------

steel_heart
강철심장
- 유효 근접 타격
- 내부 쿨타임 10초
- proc마다 최대 체력 +1하트 영구
- 20중첩 이후 stack gain ×2
- 진화와 연계

ultimate_hydra
궁극의 히드라
- 근접 공격을 주변 5블록 적에게 전달
- secondary targets는 configurable damage ratio
- 4번째 유효 공격마다 반경 8블록 충격파

infernal_conductor
지옥의 전도체
- 유효 공격마다 대상에게 화상 stack
- stack당 초당 0.3하트 피해
- stack 지속 정책 config
- 대상 처치 시 주변 적에게 stack 전염

eureka
유레카
- XP level 5당 증강 cooldown -5%
- 최대 -70%
- XP 소비 안 함

magic_missile
마법 미사일
- 유효 hit 3회마다
- 추적 미사일 5개
- 각 1.5하트
- 동일 missile은 동일 target 1회만 hit

phantom_weapon
환영 무기
- 화살 및 검기 hit이 허용된 melee on-hit effect 발동
- 모든 effect를 무조건 복사하지 않음
- allowlist 기반
- proc recursion 제한

tank_engine
탱크 엔진
- 유효 horizontal movement 250블록마다
- 크기 +5%
- 최대 체력 +2하트
- 20중첩
- teleport 이동 제외

giant_slayer
거인 학살자
- 대상 max health가 자신보다 높을수록 bonus damage
- 최대 +120%
- 정확한 scaling formula config

split_attack
분열하는 공격
- 유효 공격 4회마다
- 다음 공격을 총 3회 처리
- 각 75% damage
- 추가타의 on-hit proc 정책 명시

storm_chaser
폭풍 추적자
- 마지막 proc 이후 유효 이동 6블록 이상
- 다음 공격에 낙뢰
- 피해 6하트
- 쿨타임 8초

----------------
PRISMATIC 12
----------------

calamity_giant
재앙의 거인
- 최대 체력 +25하트
- 크기 ×1.8
- 근접 reach +2블록
- sprint 중 적 접촉 knockback
- 8초마다 ground slam 사용 가능
- slam 피해 6하트
- 이동속도 -25%

infinite_repeat
무한 반복
- proc형 증강 효과 2회 발동
- 매 3번째 repeatable proc는 3회 발동
- 자기 자신 재귀 금지

jeweled_gauntlet
보석 건틀릿
- augment damage에 crit
- crit chance 40%
- crit damage 250%
- 250%는 원래 피해의 2.5배 의미
- random source는 match/player deterministic RNG abstraction 사용

immortal_berserker
불멸의 광전사
- 적 유효 타격 10회 후 berserk
- 공격력 +100%
- 이동속도 +50%
- lifesteal 40%
- 경기당 1회 lethal damage 방지
- 방지 후 berserk 8초 연장

dual_wielder
양손잡이
- offhand 무기도 동시에 공격
- offhand weapon damage/effect 반영
- shield 사용 불가
- durability 정상 감소
- duplicate hit exploit 금지

earthquake_mage
지진술사
- 전용 dash active 제공
- dash path가 1초 뒤 폭발
- 폭발 피해 5하트
- 한 dash에서 같은 target 반복 hit cap

center_of_universe
우주의 중심
- 플레이어 주변 회전 orb 6개
- 근접 적 자동 공격
- orb당 피해 2하트
- target selection 및 attack interval config
- entity를 수십 개 실제 spawn해 TPS 악화시키지 말고 lightweight representation 우선

russian_doll
러시아 인형
- 최대 3회 즉시 부활
- 부활마다 최대 체력 -25%
- 부활마다 이동속도 +25%
- 감소는 원래/current 기준을 명확히 정의
- 기본은 현재 base augmented max health에 multiplicative 적용

mad_scientist
미친 과학자
- 12초마다 giant / small 상태 전환
- giant: 최대 체력 +20하트
- small: 이동속도 +80%, 회피 35%
- transform 시 현재 체력 비율 보존

draw_the_sword
검을 뽑아라
- shield 사용 불가
- 근접 공격마다 10블록 검기
- 검기 유효 hit마다 검기 피해 +2% 영구
- max-stacks -1
- base sword damage가 아닌 sword-wave damage scaling

final_judgment
최후의 심판
- 체력 30% 이하 진입
- 반경 12블록 심판 영역
- 5초 명확한 telegraph
- 최대 피해 15하트
- 잃은 체력 비율 기반
- 쿨타임 35초

forbidden_reconstruction
금단의 재구성
- 실버 증강 2개 제거
- 프리즘 증강 2개 추가 선택
- transaction 방식
- 성공 시 현재 체력 50% 감소
- 10초 자연 회복 금지
- eligible silver가 2개 미만이면 선택 불가

==================================================
AH. 추가 증강 34종
==================================================

최신 30종이 안정적으로 구현된 다음 추가한다.

----------------
SILVER 12
----------------

muscle_memory
근육 기억
- 적대적 몹 8마리 처치마다 근접 피해 +2%
- 최대 +20%

iron_stomach
철의 위장
- 서로 다른 음식 2종을 먹을 때마다 최대 체력 +1하트
- 최대 +4하트
- 음식 섭취 시 체력 +1하트
- 회복 내부 쿨타임 12초

wound_record
상처의 기록
- 받은 피해 누적 10하트마다 최대 체력 +1하트
- 최대 +4하트
- 체력 35% 이하 진입 시 저항 I 4초
- 쿨타임 22초

vein_overload
광맥 과부하
- 자연 광석 10개당 이동속도 +2%
- 최대 +16%
- 목숨 제한 시작 시 흡수 체력 +3하트

chain_impact
연쇄 충격
- 근접 3타마다
- 반경 4블록
- 피해 1.5하트
- 감속 I 2초
- 쿨타임 6초

blood_trace
혈흔 추적
- 공격한 대상 발광 6초
- 그 대상 다음 공격 피해 +30%
- 쿨타임 9초

frenzy_rhythm
광기의 리듬
- 동일 대상 연속 공격마다 +7%
- 최대 +28%
- 4초 무공격 시 reset

survival_instinct
생존 본능
- 체력 35% 이하
- 체력 3하트 회복
- 신속 II 4초
- 쿨타임 22초

predator
포식자
- 근접 3타마다 체력 1.5하트 회복
- 쿨타임 5초

falling_shockwave
낙하 충격파
- 4블록 이상 낙하
- 주변 적 피해 2.5하트
- knockback
- 낙하 피해 80% 감소
- 쿨타임 12초

explosive_arrow
폭발 화살
- 화살 3회 적중마다
- 반경 2블록
- 피해 2하트
- 쿨타임 8초

counter_shield
역습의 방패
- shield block 성공
- 다음 melee 피해 +40%
- 체력 1하트 회복
- 쿨타임 10초

----------------
GOLD 12
----------------

heart_harvester
심장 수확자
- 적대적 몹 10마리 처치마다 최대 체력 +1하트
- 최대 +8하트
- player kill 시 +2하트
- player kill 시 체력 5하트 회복

mineral_devourer
광물 포식자
- 자연 광석 10개마다 근접 피해 +3%
- 최대 +24%
- 목숨 제한 시작 시 황금사과 1개

blood_crown
피의 왕관
- 근접 피해 25% lifesteal
- heal internal cooldown 2초
- player kill 시 absorption 4하트

storm_blade
폭풍의 검
- 근접 4타마다
- main target +3하트
- nearby targets +2하트
- 쿨타임 8초

shadow_dash
그림자 도약
- 바라보는 방향 7블록 dash
- 이후 3초 안 첫 공격 +50%
- 쿨타임 15초

immortal_armor
불멸의 갑옷
- 받은 피해의 35%를 absorption으로 전환
- 최대 5하트
- 쿨타임 12초

venom_swarm
맹독 군락
- melee hit 시 Poison II 4초
- 대상 처치 시 주변 poison cloud
- 주변 피해 2하트
- 쿨타임 10초

bounty_hunter
현상금 사냥꾼
- target mark 6초
- marked target 피해 +25%
- 처치 시 체력 5하트 회복
- 신속 II
- 쿨타임 12초

split_bow
분열의 활
- 화살 3회마다
- main arrow 피해 +25%
- 좌우 secondary arrow 2개
- 각각 60% damage
- 쿨타임 6초

bomb_alchemy
폭탄 연금술
- 전용 snowball bomb
- 피해 3하트
- 3초 fire zone
- 쿨타임 18초

duel_contract
결투의 계약
- 지정 대상과 8초
- 대상에게 주는 피해 +30%
- 자신 받는 피해 +15%
- 양쪽 glowing
- 쿨타임 18초

time_thief
시간 도둑
- hit 시 상대 Slowness II 2초
- 자신 Speed II 4초
- 상대 positive potion remaining duration 감소
- 쿨타임 12초

----------------
PRISMATIC 10
----------------

endless_evolution
끝없는 진화
- 적대적 몹 8마리마다
  +1하트
  +3% damage
- 최대 8중첩
- player kill은 2중첩
- late acquisition compensation 지원

phoenix_heart
불사조 심장
- lethal damage 경기당 1회 차단
- 체력 50% 회복
- 주변 knockback
- 2초 invulnerability

time_rewind
시간 역행
- 4초 전 위치와 체력으로 복귀
- 최대 회복량 7하트
- cooldown 35초
- inventory rewind 없음
- augment stack rewind 없음
- border 밖 과거 위치면 safe clamped position 또는 cast fail 정책 config

void_lord
공허 군주
- 반경 6블록 적 pull
- 2초간 총 4하트 피해
- cooldown 25초

blood_god
피의 신
- 근접 피해 35% lifesteal
- player 유효 공격 3회마다 damage +2% 영구
- 최대 +20%
- player kill 시 max health +2하트
- 최대 +6하트

clone_hand
복제의 손
- 근접 3타마다 clone attack
- 원래 공격 피해의 100%
- cooldown 3초
- clone attack이 다시 clone attack을 proc하지 않음

gigantification
거인화
- 최대 체력 +10하트
- melee damage +25%
- size ×1.4
- movement speed -12%

last_demon
최후의 악마
- 체력 40% 이하
- 7초간
  damage +60%
  Speed II
  Resistance I
  lifesteal
- cooldown 30초

forbidden_deal
금단의 거래
- 최대 체력 5하트 영구 감소
- Gold augment 2개 추가 선택
- 모든 damage +20%

world_erosion
세계 침식
- 반경 5블록 적 max health 감소
- target당 최대 -4하트
- 3초마다 적용
- 감소한 양에 비례해 자신 absorption 획득
- 범위 벗어나면 10초 동안 복구

==================================================
AI. 구형 증강 후보
==================================================

다음 증강들은 출시 기본 풀에 넣지 않는다.

`legacy-augments.yml`

에:
enabled: false
implemented: false 또는 적절한 상태

로 저장한다.

SILVER 후보:
강철 피부
가벼운 발걸음
채굴 전문가
행운의 광부
벌목꾼
풍요로운 수확
대장장이
경험의 달인
단단한 착지
화염 적응
날카로운 칼날
정밀 사격
재빠른 회복
경량 장비
신속한 사냥
강인한 육체
화살 수집가
가벼운 허기
물갈퀴
수중 호흡
야행성
정찰병
신속한 장전
견고한 방패
비상 식량
화살 보급
원석 감별사
행운의 사냥
황금 손길
불길 내성
기습 방어
충격 흡수
가시 피부
신중한 발걸음

GOLD 후보:
흡혈
처형인
최후의 저항
광전사
연속 공격
반격
사냥꾼의 표식
불꽃의 검
번개 발걸음
거인의 심장
철벽 수호자
연금술사
광맥 탐지
탐욕의 손
그림자 추격
마지막 숨결
선제 공격
결투가
방패 파괴자
집중 사격
독사의 이빨
균열 화살
강력한 밀치기
응급 치료
불굴의 의지
기습 전문가
전투 적응
침착한 궁수
돌진 공격
불타는 반격
화염 방호막
급속 도약
전장 감각
끈질긴 추격
방어 태세
약점 포착
비축된 화살
피의 계약
대지의 발걸음
폭풍 궁수
고통의 인내

PRISMATIC 후보:
유리 대포
중력 지배자
그림자 군주
운명의 재구성
거울의 가호
혼돈의 계약
공간 균열
환영 분신
반격의 기록
폭풍의 눈
천둥 낙인
균형의 계약
화살 폭풍
철의 결계
무중력
교환의 문
이중성
시간의 늪
피의 저장고
유성의 심판
운명의 주사위

추후 구현 전:
- 최신 64종과 비교
- 단순 상위/하위호환 제거
- 현재 파워레벨에 맞게 재설계

==================================================
AJ. 증강 진화
==================================================

진화 시스템 공통 제공.

steel_heart:
20중첩
-> 이후 stack 획득량 ×2

magic_missile:
누적 missile 100발 발사
-> 5발에서 12발

sword_wind:
검기 유효 적중 30회
-> X형 검기

infernal_conductor:
화상 누적 피해 100하트
-> burn stack/전염 강화

tank_engine:
15중첩
-> movement collision damage 해금

berserk_engine:
최고속도 상태 누적 30초
-> 최대 stack cap 증가

진화 수치 모두 YAML.

==================================================
AK. 시너지
==================================================

강철의 왕
필요:
- steel_heart
- stack_dinosaur
- heavy_fist

효과:
최대 체력 30하트 이상일 때 melee 공격 충격파.

무한 포격
필요:
- magic_missile
- eureka
- infinite_repeat

효과:
missile 발사 수 2배
missile이 대상 간 1회 bounce.

재앙의 기관차
필요:
- tank_engine
- calamity_giant
- berserk_engine

효과:
sprint 중 주변 적 collision damage.

지옥의 군주
필요:
- infernal_conductor
- center_of_universe
- jeweled_gauntlet

효과:
orb가 burn stack 부여
burn damage도 crit 가능.

천 개의 검
필요:
- sword_wind
- ultimate_hydra
- dual_wielder

효과:
melee마다 전방/좌/우 다중 검기.

시너지 완성 시:
server-wide announcement 기본 true.

==================================================
AL. 동급 증강 밸런스 검증
==================================================

증강 구현 후 자동 또는 분석 가능한 `BalanceAuditService` 작성.

각 augment metadata:
- rarity
- type
- offensive
- defensive
- mobility
- sustain
- scaling
- utility
- early-game
- late-game
- skill-dependency
- counterplay

관리자:
`/aw admin balance report`

출력:
같은 rarity 내 비교.

예:
- average burst damage
- sustained DPS contribution
- EHP contribution
- mobility
- cooldown uptime
- scaling potential
- estimated synergy count

이 계산을 절대 완전한 실제 승률이라고 주장하지 마라.

단순 정적 밸런스 진단 도구다.

또한 telemetry optional:
- pick count
- reroll count
- wins
- average stacks
- average proc count
- damage dealt
- healing
- deaths prevented

개인정보가 아닌 게임 내 통계만 로컬 파일에 저장.

기본 telemetry:
false

==================================================
AM. 설정 아키텍처
==================================================

파일:

plugins/AugmentWild/
  config.yml
  augments.yml
  loot.yml
  synergies.yml
  arenas.yml
  messages_ko.yml
  legacy-augments.yml
  used-regions.yml
  stats.yml optional

모든 gameplay 숫자는 config화.

하지만 아무 설정이나 runtime 변경하지 않는다.

설정 항목 분류:

LIVE_SAFE
- 메시지
- particle 제한 등

NEXT_MATCH
- augment damage
- rarity weights
- respawn duration
- lives
- border timings
- arena size

RESTART_REQUIRED
- 특별히 필요한 경우만

==================================================
AN. 설정 스키마와 migration
==================================================

모든 주요 파일:
`schema-version`

예:
schema-version: 1

ConfigLoader:
1. parse
2. validate
3. migrate
4. build immutable config object
5. only then swap active config

잘못된 YAML이면:
기존 정상 config 유지.

reload transactional.

절대 partial reload 하지 않는다.

migration:
ConfigMigration interface

예:
V1ToV2Migration

업데이트로 key 이름이 변경되어도 기존 서버 config를 가능한 한 보존.

==================================================
AO. 경기 설정 Snapshot
==================================================

경기가 시작될 때:

`MatchConfigSnapshot`

생성.

해당 경기는 snapshot만 사용.

경기 도중 관리자가:
augment damage
border size
lives
respawn
rarity

등을 수정해도 현재 경기에는 반영하지 않고
다음 경기부터 반영.

관리자에게:
"현재 경기에는 적용되지 않고 다음 경기부터 적용됩니다."

표시.

예외:
LIVE_SAFE 설정만 즉시 적용.

==================================================
AP. 기본 config.yml
==================================================

schema-version: 1

server:
  expected-minecraft-version: "26.3"

game:
  min-players: 2
  max-players: 16
  base-health-hearts: 20
  gathering-seconds: 1800
  selection-interval-seconds: 300
  selection-duration-seconds: 15
  pause-match-clock-during-selection: true
  pvp-grace-seconds: 20

lobby:
  world: "world"
  x: 0.5
  y: 100.0
  z: 0.5
  yaw: 0
  pitch: 0

gathering:
  pvp-enabled: true
  infinite-lives: true
  respawn-delay-seconds: 20
  keep-inventory: true
  keep-experience: true
  keep-augments: true
  keep-permanent-stacks: true
  respawn-protection-seconds: 3

combat:
  lives: 2
  respawn-delay-seconds: 20
  keep-inventory: true
  keep-experience: true

sudden-death:
  enabled: true
  disable-new-respawns: true
  allow-existing-respawn: true

arena:
  mode: DEDICATED_WORLD
  world: "augment_world"
  width: 1000
  height: 1000

  search:
    radius: 30000
    max-candidate-attempts: 80
    max-search-seconds: 120
    used-region-buffer-blocks: 500
    chunk-requests-per-tick: 4
    biome-samples-per-tick: 2048

  biome:
    ocean-policy: ZERO
    max-ocean-ratio: 0.0
    forbidden-biomes: []

  spawn:
    min-player-distance: 80
    border-margin: 50
    max-attempts-per-player: 200

border:
  start-size: 1000
  final-size: 60
  shrink-start-seconds: 1820
  shrink-duration-seconds: 600
  damage-per-second: 1.0
  warning-distance: 10

augments:
  choices-per-round: 3
  rerolls-per-player: 1
  allow-duplicates: false
  final-round-guaranteed-prismatic: true
  max-prismatic-per-player: 3

  rarity:
    silver-weight: 80
    gold-weight: 18
    prismatic-weight: 2

  proc:
    max-depth: 2
    minimum-cooldown-seconds: 1

tracker:
  amount: 1
  maximum-distance: -1
  duration-seconds: 10
  cooldown-seconds: 60
  update-seconds: 1
  reveal-name: false

supply:
  windows:
    first-normal: {min-seconds: 360, max-seconds: 600}
    second-normal: {min-seconds: 840, max-seconds: 1200}
    premium: {min-seconds: 1380, max-seconds: 1680}
  announce-seconds: 120
  coordinates-seconds: 60
  opening-seconds: 3

performance:
  max-active-custom-projectiles-per-player: 40
  max-particles-per-player-per-second: 150
  max-orbs-per-player: 12

safety:
  max-health-hearts-hard-cap: 500
  max-damage-multiplier-hard-cap: 100
  max-movement-speed-multiplier-hard-cap: 10
  max-runtime-stacks-hard-cap: 1000000

==================================================
AQ. augments.yml 구조
==================================================

모든 증강은 typed config로 읽는다.

예:

schema-version: 1

augments:

  sword_wind:
    enabled: true
    implemented: true
    rarity: SILVER

    proc:
      trigger: MELEE_HIT
      hits-required: 3
      cooldown-seconds: 0

    projectile:
      range-blocks: 7.0
      damage-hearts: 4.0
      width: 0.8
      wall-piercing: false

  steel_heart:
    enabled: true
    implemented: true
    rarity: GOLD

    stack:
      trigger: VALID_MELEE_HIT
      internal-cooldown-seconds: 10
      health-hearts-per-stack: 1.0
      max-stacks: -1

    evolution:
      threshold: 20
      stack-gain-multiplier: 2

  magic_missile:
    enabled: true
    implemented: true
    rarity: GOLD

    trigger:
      valid-hits-required: 3

    missile:
      count: 5
      damage-hearts: 1.5
      speed: 1.4
      tracking-range: 20.0

    evolution:
      missiles-fired-required: 100
      evolved-count: 12

  infinite_repeat:
    enabled: true
    implemented: true
    rarity: PRISMATIC

    repeat:
      normal-count: 2
      empowered-every: 3
      empowered-count: 3
      max-proc-depth: 2

각 64종 모두 위 정도 수준으로 명시적인 YAML schema를 제공한다.

==================================================
AR. 관리자 명령어
==================================================

일반:

/aw join
/aw leave
/aw status
/aw augments
/aw locate
/aw track
/aw cast <augment>

/aw help

관리자:

/aw admin start
/aw admin stop
/aw admin status
/aw admin reload

/aw admin config get <path>
/aw admin config set <path> <value>

/aw admin augment list
/aw admin augment info <id>
/aw admin augment enable <id>
/aw admin augment disable <id>
/aw admin giveaugment <player> <id>
/aw admin removeaugment <player> <id>
/aw admin setstack <player> <id> <amount>
/aw admin resetcooldown <player> [augment]

/aw admin arena preview
/aw admin arena find
/aw admin arena reroll
/aw admin arena history
/aw admin arena clearhistory
/aw admin arena searchdebug

/aw admin supply spawn normal
/aw admin supply spawn premium

/aw admin test augment <id>
/aw admin test pvp
/aw admin test respawn
/aw admin test tracker
/aw admin test border

/aw admin balance report

Brigadier/Paper command API를 사용할 수 있으면
26.3 공식 지원 방식을 우선 검토한다.

탭 완성 제공.

permissions:

augmentwild.play
augmentwild.track
augmentwild.admin

==================================================
AS. 관리자 설정 검증
==================================================

잘못된 값 거부.

예:
negative border size
rarity weight total <= 0
respawn time < 0
NaN
Infinity
invalid Material
invalid biome key
negative augment damage
impossible player limits

`config set`은:
parse -> validate -> save temp -> atomic replace

순서로 처리.

파일 쓰기 실패 시 memory config 변경 금지.

==================================================
AT. 보급 Loot schema
==================================================

현재 보급 보상은 config.yml의 supply.rewards.normal과 supply.rewards.premium에 있다.
각 항목은 등확률로 한 번 추첨하는 완성 보상 세트다.
MATERIAL*수량, POTION:종류, MATERIAL:마법부여:레벨, BOOK:마법부여:레벨 형식을 지원한다.
설정은 활성화 시 검증한다. 자세한 설정과 운영 방법은 phase64-intel-supply-ko.md를 참고한다.

==================================================
AU. UI
==================================================

필수:

- augment selection GUI
- augment codex GUI
- owned augments GUI
- stack progress
- evolution progress
- synergy state
- cooldown
- match timer
- next event
- 목숨 제한 시작 카운트다운
- border bossbar
- respawn countdown
- remaining lives
- supply coordinates
- tracker result
- spectator state
- winner screen

UI 업데이트를 매 tick 하지 말고
필요한 주기만 사용.

==================================================
AV. 성능 목표
==================================================

목표 테스트 규모:
8 players 기본
16 players 지원 목표

일반 경기 중:
- 반복 O(players × all entities × all augments) scan 금지
- 매 tick 전체 월드 entity scan 금지
- 자동 공격 orb 등 spatial query frequency 제한
- projectile pool / lightweight simulation 고려

성능 예산 문서화.

`/aw admin debug performance`

선택적으로:
- active augment effects
- active projectiles
- scheduler tasks
- arena search work
- average plugin tick time

표시.

Paper timings 또는 profiler를 대체하려 하지 않는다.

플러그인 내부 디버그용이다.

==================================================
AW. 네트워크/재접속
==================================================

disconnect policy 설정.

기본:
GATHERING:
- 5분 grace
- 재접속 시 상태 복귀

PVP:
- disconnect를 악용한 회피 방지
- configurable grace
- 기본 60초 이내 재접속 가능
- 캐릭터 combat logout 처리 정책을 README에 명확히 작성

최소 구현에서는:
PLAYER_DISCONNECTED 상태 유지
grace 초과 시 elimination.

재접속 시:
- inventory/state
- augment
- stack
- lives
- phase

복원.

==================================================
AX. 경기 종료 Cleanup
==================================================

경기 종료 시 반드시 제거:

- augment temporary attributes
- size changes
- potion effects injected by plugin
- custom projectiles
- orbiting visual objects
- hologram/marker
- bossbars
- virtual border
- scoreboard additions
- actionbar loops
- scheduler tasks
- cooldowns
- stack state
- respawn tasks
- supply tasks
- tracked chest state

그리고 pre-match snapshot 복원.

Cleanup 실패:
각 단계 independently try/finally 처리.

한 플레이어 cleanup 실패가 전체 cleanup을 중단하지 않음.

==================================================
AY. 테스트 전략
==================================================

단위 테스트와 서버 통합 테스트를 분리.

UNIT:
- rarity roll
- config validation
- config migration
- cooldown calculation
- stack calculation
- eureka cooldown
- giant slayer scaling
- state machine
- death resolver
- victory calculation
- region overlap
- direction calculation
- loot weight
- proc depth

INTEGRATION:
- Paper boot
- plugin enable
- player join
- match start
- arena search
- death
- respawn
- augment event
- border
- supply
- cleanup

실제 Paper 서버가 필요한 테스트는
Gradle runServer 또는 적절한 테스트 task 구성 검토.

==================================================
AZ. 필수 시나리오 테스트
==================================================

1. Paper 26.3 boot
2. plugin enable
3. config load
4. invalid config rollback
5. schema migration
6. 1000x1000 region selection
7. ocean candidate reject
8. used region overlap reject
9. search timeout
10. 8 player safe spawn
11. gathering PvP allowed without life loss
12. gathering infinite death
13. inventory retained
14. XP retained
15. 20 sec respawn
16. duplicate inventory 없음
17. locate consumes exactly one diamond
18. locate picks nearest player
19. invalid target consumes zero
20. respawning player excluded
21. 5min augment event
22. rarity independent slot rolls
23. reroll
24. duplicate prevention
25. 30min prismatic guaranteed
26. limited lives start after final selection
27. PvP life decrement once
28. double lethal event does not remove 2 lives
29. first PvP death respawn
30. second PvP death elimination
31. revive augment prevents life consumption
32. multiple revive augments consume only one
33. sudden death
34. pending respawn sudden death transition
35. border shrink
36. border outside damage
37. shared world nonparticipant unaffected
38. supply drop spawn on land
39. simultaneous chest open gives one winner
40. natural ore exploit blocked
41. teleport movement exploit blocked
42. proc recursion blocked
43. infinite repeat safe
44. phantom weapon safe
45. missile recursion safe
46. augment attribute removed after match
47. original player inventory restored
48. admin stop mid-match
49. server disable cleanup
50. winner calculated correctly

그리고 64종 각각 최소:
- acquisition test
- primary trigger test
- cleanup test

==================================================
BA. GitHub Actions
==================================================

`.github/workflows/build.yml`

- correct JDK
- Gradle cache
- `./gradlew clean build`
- tests
- test reports artifact optional

PR/build failure:
red status.

release jar:
`build/libs/`

가능하면:
manual workflow dispatch로 integration server smoke test.

==================================================
BB. 개발 단계
==================================================

PHASE 0 — VERIFY

- 공식 26.3 지원 확인
- Paper dependency 확인
- Java 확인
- 저장소 상태 확인
- AGENTS.md
- PROGRESS.md

Acceptance:
- 버전 근거 기록
- 빌드 전략 확정

COMMIT

PHASE 1 — BOOTSTRAP

- Gradle
- wrapper
- plugin.yml
- main plugin
- commands
- config schema
- GitHub Actions

Acceptance:
- clean build 성공
- plugin jar 생성

COMMIT

PHASE 2 — MATCH CORE

- match state machine
- match clock
- player state
- snapshots
- lobby
- join / leave
- cleanup

Acceptance:
- start/stop 정상
- snapshot 복원
- state unit tests

COMMIT

PHASE 3 — ARENA

- region finder
- ocean validation
- used region
- safe spawn
- dedicated/shared border abstraction

Acceptance:
- ocean candidate 거부
- 탐색 실패 안전 종료
- 8 players safe spawn
- no global border side effect in shared mode

COMMIT

PHASE 4 — DEATH / RESPAWN

- unlimited gathering lives
- inventory preservation
- 20sec respawn
- PvP lives
- sudden death
- revive resolver

Acceptance:
- double death race 안전
- item duplication 없음
- first/second PvP death correct

COMMIT

PHASE 5 — TRACKER / SUPPLY

- diamond tracker
- nearest target
- supply drops
- atomic claim

Acceptance:
- cost exactly once
- invalid target no cost
- chest race safe

COMMIT

PHASE 6 — AUGMENT ENGINE

- registry
- selection
- rarity
- cooldown
- stack
- damage
- proc chain
- GUI

Acceptance:
- 3 choices
- reroll
- independent rarity
- recursion tests pass

COMMIT

PHASE 7 — CORE 30

- latest Silver 8
- Gold 10
- Prismatic 12

Acceptance:
각 증강:
- trigger
- effect
- config
- cleanup
- test

COMMIT

PHASE 8 — EVOLUTION / SYNERGY

- evolution system
- 6 evolutions
- 5 synergy sets

Acceptance:
- activation
- announcement
- cleanup
- config

COMMIT

PHASE 9 — EXTRA 34

추가 34종.

Acceptance:
총 64종 implemented true
또는 실제 미완성은 false 상태로 명확히 남김.

COMMIT

PHASE 10 — HARDENING

- config migration
- reload
- balance audit
- performance
- reconnect
- full integration test

COMMIT

PHASE 11 — DOCUMENTATION

- Korean README
- admin guide
- config guide
- augment reference
- known limitations
- performance notes

FINAL BUILD.

==================================================
BC. 완료 정의
==================================================

"완료"라고 말하려면:

- 공식 Paper 26.3 기준 프로젝트
- Java 요구 버전 정확
- `./gradlew clean build` 성공
- GitHub Actions 성공
- plugin enable 성공
- 기존 world 사용
- world 생성/삭제/rollback 없음
- 1000x1000 arena
- zero-ocean 기본 검사
- used region 방지
- safe spawn
- 30분 gathering
- unlimited gathering lives
- inventory/XP 유지
- 20초 respawn
- diamond tracker
- supply drops
- six augment rounds
- final prismatic choice
- limited lives at 30:20
- 2 combat lives
- 1000 -> 60 border
- sudden death
- augment engine
- core 30 작동
- extra 34 상태 정확
- evolution
- synergy
- admin config
- transactional reload
- config migrations
- match config snapshot
- lobby snapshot restore
- shared world protection
- cleanup
- race condition protection
- Korean README
- PROGRESS.md 최신

이어야 한다.

==================================================
BD. 절대 하지 말 것
==================================================

- 빌드 안 되는 코드 대량 생성
- TODO만 남기고 구현했다고 표시
- mock/stub을 실제 기능처럼 보고
- 존재하지 않는 Paper API 사용
- 버전 문자열 추측
- 게임 숫자 하드코딩
- 월드 삭제
- 경기 후 블록 rollback
- shared world global border 변경
- infinite proc recursion
- death event 중복 life 감소
- respawn item duplication
- 같은 loot chest 이중 claim
- config reload 중 일부만 적용
- 경기 도중 next-match 설정 즉시 적용
- 불필요한 branch 수십 개 생성
- 미구현 증강을 selection pool에 넣기

==================================================
BE. 최종 Codex 작업 방식
==================================================

지금부터 다음 순서로 실제 작업하라.

1. 저장소와 현재 branch 확인.
2. 공식 Paper 26.3 / Java / Maven dependency 검증.
3. PROGRESS.md 생성.
4. AGENTS.md 생성.
5. PHASE 0 수행.
6. 빌드 가능한 최소 플러그인 생성.
7. 테스트.
8. 커밋.
9. PHASE 1부터 순서대로 진행.
10. 단계마다 빌드와 테스트 결과를 PROGRESS.md에 기록.
11. 실패하면 실패 원인을 해결하기 전 다음 단계로 넘어가지 않는다.
12. 한 번의 작업에서 모두 끝내기 어려우면 현재 단계의 작동 가능한 상태에서 멈추고 정확한 다음 작업을 PROGRESS.md에 남긴다.
13. 기존 구현을 깨뜨리는 대규모 재작성은 테스트 없이 하지 않는다.
14. 새 작업을 시작해야 하는 경우 반드시 저장소의 PROGRESS.md와 AGENTS.md를 먼저 읽는다.
15. 브랜치는 환경이 허용하는 범위에서 최소화한다.

첫 번째 목표는 "증강 64개를 빨리 만들어 놓는 것"이 아니다.

첫 번째 목표는:

Paper 26.3 서버에서 실제로 실행되고,
기존 월드를 안전하게 사용하며,
바다 없는 랜덤 경기장을 찾고,
파밍 → 증강 → 리스폰 → PvP → 자기장 → 승리까지
한 판이 끝까지 정상적으로 돌아가는 안정적인 게임 코어다.

그 코어가 통과된 뒤 증강을 단계적으로 확장하라.
