# AugmentWild 진행 상황

기준일: 2026-11-17 (Asia/Seoul)

## 현재 단계

PHASE 0은 사용자 제공 공식 자료에 근거해 대상 플랫폼을 확정했다. PHASE 1은 기본 파일 작성 중이며 **빌드 및 서버 검증이 실패 또는 미실행 상태이므로 완료되지 않았다**. 현재 경기를 플레이할 수 없다.

## 외부 확인과 내부 검증의 구분

사용자는 외부 환경에서 다음 공식 자료를 확인했다고 제공했다. Minecraft/Paper 26.3, Java 25, `io.papermc.paper:paper-api:26.3.build.+`, `api-version: '26.3'`을 이 지시에 따라 적용했다.

- [Minecraft 26.3](https://www.minecraft.net/ko-kr/article/minecraft-java-edition-26-3)
- [Paper 26.3](https://papermc.io/news/26-3/)
- [Java 요구 사항](https://docs.papermc.io/paper/getting-started/)
- [프로젝트 설정](https://docs.papermc.io/paper/dev/project-setup/)
- [plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/)

Codex 내부에서 위 내용, 실제 Maven 아티팩트, 안정 빌드 번호, Paper 세부 API 및 plugin.yml 호환성은 독립적으로 검증하지 못했다. 동적 의존성은 접근 복구 후 실제 안정 빌드로 고정해야 한다.

## 네트워크

이전 검증의 HTTPS GET은 다음 호스트에 대한 프록시 CONNECT 단계에서 403, curl 종료 코드 56, 원본 HTTP 코드 000이었다. 버전 미지원의 증거가 아니다.

| 허용 필요 도메인 | 요청 |
| --- | --- |
| `piston-meta.mojang.com:443` | `/mc/game/version_manifest_v2.json` |
| `fill.papermc.io:443` | `/v3/projects/paper` |
| `repo.papermc.io:443` | `/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml` |
| `docs.papermc.io:443` | `/paper/getting-started/`, `/paper/dev/project-setup/`, `/paper/dev/plugin-yml/` |

이번 작업에서 Paper Maven 메타데이터 재요청도 CONNECT 403이었다. 공식 리디렉션 대상이나 추가 의존성 호스트에도 접근이 필요할 수 있다. `services.gradle.org` 배포본은 curl로 다운로드했으나 Java wrapper 자체의 DNS 조회는 실패했다. 환경별 프록시 설정도 확인해야 한다.

## 작성한 내용

- Java 25 Gradle Kotlin DSL 설정, Paper API 동적 의존성, 공식 Gradle 9.1.0 배포본으로 생성한 wrapper, plugin.yml, 진입점 및 `/aw status`.
- 경기 상태 전환 모델, 불변 설정 값 객체, 선택 가능 여부를 구분하는 빈 증강 레지스트리, 상태 전환 JUnit 테스트 초안.
- 1000×1000, ZERO 바다 정책, 20초 리스폰 등 설정 초안. 아직 게임 로직과 연결되지 않았다.
- 원본 전체 명세와 증강 목록을 `docs/specification-ko.md`에 보존. 증강은 구현되지 않았고 선택 풀은 비어 있다.
- GitHub Actions 빌드 설정과 초기 한국어 README.

## 실제 검증 결과

- `gradle wrapper --gradle-version 9.1.0 --no-validate-url`: 성공. 공식 배포본을 curl로 다운로드하여 실행했다. URL의 Gradle 자체 검증은 실패해 비활성화했다.
- `./gradlew clean build`: 실패. wrapper가 `services.gradle.org` DNS를 조회하지 못했다.
- 다운로드한 Gradle로 `clean build`: 실패. Java 25 JDK가 없고 toolchain 다운로드 저장소가 구성되지 않았다.
- `javac` 직접 컴파일: 실행 불가, javac 없음. JUnit 테스트, Paper 서버 기동, GitHub Actions는 실행하지 않았다.
- `git diff --check`: 커밋 전 별도 확인. 게임 버그와 성능은 아직 평가할 수 없다.

## 미완료 및 다음 작업

1. Java 25 JDK와 Gradle/의존성 네트워크 접근을 마련하고 wrapper 빌드, 테스트 및 실제 Paper 기동을 검증한다. Paper API 메서드, `api-version` 및 선택한 안정 빌드를 직접 확인한다.
2. PHASE 1 설정 로딩·검증, 명령, CI를 완성한다. 현재 설정 객체는 YAML에 연결되지 않았고 관리자 수정이나 transactional reload도 없다.
3. 상태 머신을 실제 경기 수명주기와 연결하고 clock, 플레이어 상태, snapshot 및 cleanup을 구현한다.
4. 바다 없는 경기장 검색, 안전 스폰, 공유 월드 보호, 20초 리스폰, 관리자 수치 설정, 증강 엔진 및 전체 명세의 게임 기능을 단계별로 구현하고 테스트한다. 현재 모두 미구현이다.

## 2026-11-17 빌드 경로 재검증

시작 HEAD `5988792`, 브랜치 `work`, 작업 트리 clean. 실제 저장소에 wrapper가 있어 AGENTS.md의 기존 'wrapper 없음' 문구를 수정했다. 이 절은 위의 이전 실행 기록보다 최신이다.

- 기본 `java --version`: OpenJDK 21.0.13; 기본 `javac --version`: 명령 없음. Oracle 공식 다운로드에서 Java 25 JDK를 임시 작업 공간에 확보했고 `javac 25.0.5.0.3` 확인. JDK는 저장소에 포함하지 않았다.
- 기본 `./gradlew --version` 및 `./gradlew clean build --stacktrace`: 기본 Gradle 홈의 lock 디렉터리를 만들 수 없어 실패. 쓰기 가능한 별도 Gradle 홈에서는 wrapper의 직접 다운로드가 `UnknownHostException`으로 실패했다.
- 공식 Gradle 9.1.0 배포본 SHA-256을 공식 `.sha256`에서 확인했고, 이전에 curl로 받은 배포본과 일치했다. 체크섬을 wrapper 설정에 추가했다. wrapper JAR의 SHA-256도 공식 `.sha256`과 일치했다. 배포본을 별도 캐시에 준비한 뒤 `./gradlew --version`은 성공했다.
- Java 25, 프록시 설정 및 캐시된 wrapper로 `./gradlew clean build --stacktrace` 재시도: `compileJava`의 의존성 해석 실패. Paper Maven이 403, Maven Central은 프록시 인증서에 대한 Java PKIX 오류. Paper API는 resolve되지 않았고 plugin 컴파일·JUnit·JAR 생성은 이루어지지 않았다. 버전 미지원이라는 증거는 아니다.
- Paper 비의존 도메인 클래스는 Java 25 `javac`로 직접 컴파일 성공. 새 MatchClock의 일시정지 및 역행 시간 거부를 별도의 임시 Java 실행 코드로 확인했다. JUnit은 실행하지 않았다. MatchClock은 아직 서버 이벤트와 연결되지 않았다.
- CI는 Java 25, 버전 출력, stacktrace 빌드, 성공 시 JAR 업로드로 구성했다. GitHub Actions 조회는 API Forbidden으로 실패했다. **CI configured, not verified**. JDK setup, wrapper, 의존성, 컴파일, 테스트 및 JAR의 CI 결과는 모두 미확인이다.

| Feature | Implemented | Compiled | Tested | Runtime Verified | Notes |
| --- | --- | --- | --- | --- | --- |
| Plugin bootstrap | yes | no | no | no | Paper 의존성 해석 실패 |
| /aw status | yes | no | no | no | 등록과 동작 미검증 |
| Match state machine | partial | yes | no | no | Java 25 직접 컴파일만; JUnit 미실행 |
| Match clock | partial | yes | partial | no | 독립 시계; 임시 실행 코드 확인, JUnit 및 서버 통합 미실행 |
| Config system | partial | yes | no | no | 값 객체만 직접 컴파일, YAML 연결 및 관리자 설정 없음 |
| Arena finder | no | no | no | no | 바다 검사 및 안전 스폰 포함 미구현 |
| Gathering respawn | no | no | no | no | 인벤토리 및 XP 보존 포함 미구현 |
| Player tracker | no | no | no | no | 미구현 |
| Augment engine | partial | yes | no | no | 빈 레지스트리만 직접 컴파일; 선택 풀 비어 있음 |
| Supply, border, victory | no | no | no | no | 미구현 |

PHASE 1은 미완료다. 다음에는 Paper Maven 접근과 Java 프록시 신뢰 설정을 해결하고 실제 `./gradlew clean build`, JUnit, JAR 및 Paper 26.3 runtime을 확인한다. 동적 Paper 버전은 공식 안정 빌드를 확인한 뒤 고정한다. 빌드 성공 전 PHASE 2의 Paper 연동을 시작하지 않는다.

## 추가 검증 및 순수 도메인 작업

시작 커밋 `0b3faaf`, `work`. Java 25.0.5.0.3와 javac, 캐시된 Gradle 9.1.0 실행을 확인했다. `clean build --stacktrace`는 Paper Maven CONNECT 403 및 Maven Central `SSLHandshakeException`/`PKIX path building failed`로 `compileJava` 의존성 해석에서 실패했다. curl의 Central 접근은 200이며 Java는 명시적인 프록시 설정을 사용했다. 운영체제와 Java의 신뢰 구성이 다를 가능성이 있으나 인증서 체인과 신뢰 근거를 충분히 확인하지 못해 원인을 확정하거나 인증서를 설치하지 않았다. 만료 및 호스트명 불일치도 독립적으로 배제하지 못했다. Paper DNS 조회는 결과가 없고 프록시 CONNECT가 403이다. `repo.papermc.io:443` 허용과 Central 프록시 CA 신뢰 경로의 관리자가 검증한 구성이 필요하다. 자격 증명이나 인증서 우회는 추가하지 않았다.

`LifeState`는 파밍 무한 목숨, PvP 목숨 차감, 서든데스 탈락, 대기 중 중복 사망 방지를 순수 Java로 작성했다. Java 25 직접 컴파일 및 임시 독립 실행 검증(파밍, 중복 사망, PvP 두 사망) 성공. 서버 연결, 부활 증강, 예약 및 동시성 보장은 미구현이다. IMPLEMENTED/COMPILED는 해당 순수 모델에만 적용되며 JUnit TESTED, BUILD_VERIFIED, RUNTIME_VERIFIED는 아니다.

GitHub Actions 조회는 Forbidden이고 원격 work 참조도 확인되지 않았다. CI_NOT_VERIFIED. 전체 JUnit과 서버 테스트 미실행, 플러그인 JAR 없음. PHASE 1 BLOCKED 및 미완료. 다음 작업은 프록시 허용과 신뢰 경로 확인 후 전체 빌드 및 테스트를 실행하고 실제 안정 Paper 빌드를 고정하는 것이다.

## PHASE 1B 순수 도메인 작업

시작 `5cb10e8`, work. 전체 빌드 차단 상태는 변경되지 않았으며 PHASE 1은 미완료다.

| 기능 | IMPLEMENTED | COMPILED | TESTED | BUILD / RUNTIME |
| --- | --- | --- | --- | --- |
| 경기 세션 및 목숨 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |
| 리스폰 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |
| 탐지 대상 선정 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |
| 경기장 기하 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |
| 불변 규칙 검증 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |
| 이벤트 순서 | 부분 | Java 25 직접 | 독립 실행 | 미검증 |

`scripts/test-domain.sh`를 Java 25로 실행해 331개 assertion 통과. 100회 파밍 사망 반복 검사를 포함하며 독립 테스트이지 JUnit이나 전체 Gradle 빌드가 아니다. 기존 JUnit, Paper 서버, Actions는 실행하지 않았다. JAR 없음. `git ls-remote --exit-code origin refs/heads/work`는 종료 코드 2, 출력 없음으로 원격 work를 확인하지 못했다. CI_NOT_VERIFIED.

`GameSession`은 기존 상태 머신과 LifeState를 사용한다. 재접속 중 만기 예약은 접속할 때까지 유지하고 종료·탈퇴 시 예약을 제거한다. 단일 스레드 호출을 전제로 하며 서버 스케줄러 및 동시성 제어는 미연결이다. `Timeline`은 시각과 순서로 한 번씩 반환하며 blocking 이벤트 뒤에는 호출자가 선택 완료까지 다음 poll을 보류해야 한다. 실제 기본 일정 구성, MatchClock 일시정지 연결, supply 및 세션 자동 단계 전환은 미구현이다. 승자 평가는 단일 생존자 조회만 제공하고 동시 사망 무승부 및 종료 큐는 미구현이다.

탐지는 유효 대상과 3차원 거리 및 결정적 동률만 처리하며 다이아몬드 차감은 없다. 경기장은 연속 좌표의 하한 포함·상한 제외 규약이며 실제 지형, 바다, 스폰 검색이나 자기장 축소는 없다. 설정 객체는 값 검증만 제공하고 YAML, 관리자 명령과 연결되지 않았다. 인벤토리·XP 복원, 증강·중첩, 부활 효과, 참가자 연결에 따른 전체 정책도 미구현이다. 기존 증강 명세는 유지했다. Paper 통합 및 검증은 네트워크 복구와 전체 빌드 성공 후 계속한다.

## PHASE 1C 진행

시작 `f26fa1f`, 브랜치 work. 순수 도메인 부분 구현: `GameSession.resolveTick`은 한 논리 틱의 사망을 모두 반영한 후 승자 또는 무승부를 확정한다. 리스폰 대기자는 승리 후보에 포함되며 종료 후 결과는 유지된다. `Arena.shrink`는 직사각형 선형 축소를 계산한다. `MatchSimulationClock`은 주입된 시간과 MatchClock/Timeline을 이용해 선택 중 경기 시간을 정지하고 보급과 단계 이벤트를 순서대로 제공한다.

Java 25 직접 컴파일 및 `scripts/test-domain.sh` 성공: 기존 331개 assertion(반복 포함), 추가 시뮬레이션 2개(4인 무승부 및 단일 우승). 이는 JUnit과 Gradle 전체 빌드가 아니다. 전체 빌드, JUnit, Paper 서버 및 CI는 이번에 실행하지 않았다. 이전 Paper 403/PKIX 차단이 유지되는 환경으로 보고 재요청하지 않았다. BUILD_VERIFIED와 RUNTIME_VERIFIED는 false이며 PHASE 1 미완료다.

PHASE 1C도 미완료다. 특히 스케줄러는 경계 시점에 tick이 호출되는 것을 전제로 하고 지연 tick 시 이벤트 시점으로 시간을 되돌려 처리하지 않는다. 선택 중 사망과 경기 시간 기반 리스폰 예약은 통합되지 않았다. 수동 pause/resume, 재시작 격리, 선택 제한시간 종료 후 잔여 wall time 처리, 시간표와 설정 전체 검증, Tracker와 참가자 상태 연결, 예약 ID/경기 ID, 이벤트 순서 역전 및 대기자 승리 판정의 추가 시나리오가 필요하다. 시뮬레이터는 Paper 서버가 아니며 실제 증강, 보급, 자기장 표시, 인벤토리 및 플레이어 탐지 비용은 구현하지 않는다. 다음 작업은 이 제약을 해결하고 독립 테스트를 확장한 뒤 Paper 의존성 및 서버 검증 경로를 복구하는 것이다.

## PHASE 1C 안정성 작업

시작 `f146a07`, work. Java 25 독립 컴파일 및 `scripts/test-domain.sh`: 기존 331개 반복 포함 검사와 시뮬레이션 4개 통과. 신규 시나리오는 지연 틱/중첩 관리자 일시정지 및 세션 참가자 상태 기반 탐지이다. JUnit, 전체 Gradle, Paper 서버, CI는 실행하지 않았다. BUILD_VERIFIED 및 RUNTIME_VERIFIED는 false다.

스케줄러는 선택 경계 이후 늦게 관측된 초과 wall time을 버리고 선택을 관측 시각에 시작한다. 선택 종료도 관측 시점에 재개하며 관측 지연은 버린다. 관리자의 별도 pause가 선택 종료 후에도 유지된다. 예약에는 경기 ID, 플레이어, 사망 순번, 예약 ID 및 예정 시각을 추가했다. 세션 탐지는 참가자 생존·접속 상태로 후보를 필터링한다.

PHASE 1C는 여전히 미완료: 경기 시간과 리스폰 예약의 API 연결 및 오래된 예약 ID 검증, 선택 결과/자동 선택, 수동 pause 이유, 시간표 snapshot과 선택 종료의 조기 처리, Tracker 비용 계약, 탈퇴 시 승리 판정, 완전한 지연 틱과 동시 이벤트 시나리오가 남았다. 스케줄러의 시간 진행은 tick 호출 시에만 관측되므로 외부 호출 순서를 강제하는 통합이 필요하다. 순수 Java 검사는 Paper 기능이나 전체 빌드의 증거가 아니다. 기존 네트워크 차단은 재검증하지 않았다.

## PHASE 1C 추가 통합

시작 커밋 `4f7e97c`, work. 순수 Java에서 예약 객체 일치 검사 및 만기 순서 처리, 경기 시계 기준 사망·만기 API, 미구현 증강 필터와 프리즘 선택 계약, 중복 탐지 요청 및 비용 처리 계약을 작성했다. `scripts/test-domain.sh`에서 Java 25 직접 컴파일 후 기존 331개 반복 포함 검사, 기존 시뮬레이션 4개, 신규 통합 시나리오 4개가 통과했다. JUnit, Gradle 전체 빌드, Paper 서버 및 Actions는 이번에 실행하지 않았다. JAR 없음. BUILD_VERIFIED/RUNTIME_VERIFIED false, PHASE 1 및 PHASE 1C 미완료.

선택 모델은 아직 경기 스케줄러와 결합되지 않았으며 조기 종료와 참가자별 자동 선택을 실제 경기 이벤트로 전달하지 않는다. 예약 상태 이력, 재접속·탈퇴 승리 판정, 동일 틱 예약 만료와 사망 순서, 선택 중 사망, 탐지 비용과 실제 인벤토리의 원자적 연동, 자기장과 경기 시계의 통합이 남았다. 비용 계약은 단일 스레드와 성공 여부를 원자적으로 반환하는 adapter를 전제로 하며 예외 후 재시도 보장은 없다. 기존 Paper Maven 403 및 Java PKIX 문제는 환경 변경 증거가 없어 재시도하지 않았다. 다음은 위 통합을 완성하고 시나리오 A–G를 별도로 검증하는 것이다.

## PHASE 1C 통합 후속

시작 `6feecd8`, work. 선택지 제공자를 설정한 스케줄러는 SelectionRound를 생성하고 참가자 선택, 15초 만료 자동 선택, 조기 완료 및 선택 후 재개를 처리한다. 같은 논리 틱은 유효 리스폰 만료 → 자발적 탈퇴 → 중복 제거된 사망 → 승리 판정 순서이며 단순 접속 종료는 참가자 자격을 유지한다. 선택지 공급자가 없는 기존 호출 경로는 이전처럼 타임아웃만 처리한다.

Java 25 독립 테스트: 기존 331개 반복 포함 검사, 기존 시뮬레이션 4개, 기존 통합 시나리오 4개, 신규 통합 시나리오 5개 통과. JUnit/Gradle 전체 빌드/JAR/Paper runtime은 별도 미검증이다.

PHASE_1C_CORE_COMPLETE는 아직 선언하지 않는다. 요청된 시나리오 A–G 각각의 전체 통합 검증이 아직 없고 실제 추적 비용 계약을 세션·시간표와 함께 검증한 전체 경기 시나리오가 없다. 특히 선택지 제공자와 재접속·탈퇴, 스케줄러의 경기 종료 및 같은 틱 border 처리 등 추가 회귀 검증이 필요하다. 기존 Paper Maven 403 및 PKIX에 변화가 확인되지 않아 로컬 다운로드를 반복하지 않았다.

`00fc7ab`을 origin/work에 푸시했다(원격 브랜치 신규 생성 확인). GitHub Actions API 조회는 Forbidden이므로 실행 및 JAR 결과는 CI_NOT_VERIFIED다. 워크플로는 Java 25, Gradle wrapper 및 Paper API 의존성을 사용하는 clean build와 성공 시 JAR 업로드를 설정한다.

## PHASE 1C A–G 검증 및 CI 상태

시작 `1718da8`, 원격 `refs/heads/work`도 동일 커밋임을 확인했다. GitHub Actions API 조회는 Forbidden이므로 CI_NOT_VERIFIED이며 실행의 성공 또는 실패를 판단하지 않는다. 워크플로는 Java 25, Gradle wrapper, clean build 및 성공 시 JAR 업로드를 설정한다. 실제 JAR과 내부 파일은 확인하지 못했다.

Java 25에서 `scripts/test-domain.sh` 실행: 기존 331개 반복 포함 검사, 기존 시뮬레이션 4개, 통합 4개, 완료 검사 5개와 별도 JVM에서 실행한 A–G 7개 통과. A는 4인 경기 및 단일 우승, B 동시 사망 무승부, C 지연 틱, D 선택 중 리스폰 정지, E 이전 예약 차단, F 중첩 일시정지 및 탈퇴, G 전체 시간표와 자기장 축소 및 서든데스 종료를 확인했다. 시나리오는 순수 Java 모델의 검증이며 실제 Minecraft 효과나 월드 작업을 검증하지 않는다.

요청된 범위의 A–G와 기존 회귀 검사에 한정하여 `PHASE_1C_CORE_COMPLETE = true`. `GRADLE_BUILD_VERIFIED = false`, `JUNIT_VERIFIED = false`, `JAR_CREATED = false` (이번 실행에서 미생성, CI 결과 미확인), `PAPER_RUNTIME_VERIFIED = false`. 전체 플러그인 PHASE 1은 미완료다. 기존 네트워크 차단이 변했다는 증거가 없어 로컬 Gradle 재시도는 하지 않았다. 빌드 검증 후 다음 우선 작업은 실제 Paper 로딩과 `/aw status` 및 서버 스케줄러 연동이다.

## PHASE 2 빌드 경로 확인

시작 HEAD `81b2a6d`, 기존 work를 강제 없이 origin/work에 푸시하고 원격 SHA `81b2a6d879066765e4041daaf8a86a1058344964` 일치를 확인했다. Actions API는 Forbidden으로 실행·로그 확인 불가: CI_NOT_VERIFIED. Paper Maven 메타데이터 요청은 프록시 CONNECT 403, 원본 HTTP 000이므로 이 환경의 Paper 의존성 해석도 차단 상태다. 버전 미지원이나 CI 실패로 해석하지 않는다.

Java 25 독립 도메인 회귀 실행은 기존 331개 검사, 시뮬레이션 4개, 통합 4개, 완료 검사 5개, A–G 7개 모두 통과했다. JUnit, 전체 Gradle 빌드, plugin JAR, Paper 런타임은 이번에 검증하지 않았다. 빌드 가능한 환경이 확보된 경우 Paper 연동을 시작하라는 이번 지시에 따라 검증 불가능한 Paper API 코드는 추가하지 않았다. `/aw join`, admin start/stop, 서버 스케줄러 연결 및 서버 테스트는 미구현이다. 다음은 CI 로그 접근 또는 Paper Maven 허용과 검증된 Java 신뢰 경로 확보 후 전체 빌드 및 서버 실행이다.

## PHASE 2A Paper 어댑터 (2026-11-18)

- `PHASE_1C_CORE_COMPLETE = true`, `PHASE_2A_IMPLEMENTED = true`.
- JavaPlugin 설정·명령·리스너 초기화와 종료 정리, status/join/leave/admin start/admin stop, 동기 Paper 스케줄러를 구현했다. GameSession과 MatchSimulationClock을 재사용한다.
- 참가 중 일시 접속 종료는 경기 자격을 유지한다. 경기 중 자발적 탈퇴와 사망은 다음 스케줄러 틱에서 함께 처리한다. 증강 선택 중 사망은 파밍 규칙을 따른다. 선택지 없는 회차는 기존 선택 제한시간 뒤 재개된다.
- 20초 대기는 도메인 상태에만 적용된다. 네이티브 리스폰 위치, 경기장, 인벤토리, PvP 제한, 실제 자기장·증강·보급 동작은 후속 단계다.
- Java 25 독립 테스트에서 기존 반복 포함 검사 331개, 시뮬레이션 4개, 통합 4개, 완료 검사 5개, A–G 7개가 통과했다. D에 선택 중 사망·중복·목숨 보존 회귀 검사를 포함했다.
- Java 25로 `./gradlew clean build --stacktrace --no-daemon`을 실행했다. `:compileJava`의 `compileClasspath` 해석에서 Paper Maven 403과 Maven Central PKIX 인증서 오류가 발생했다. `PHASE_2A_COMPILED = false`, `GRADLE_BUILD_VERIFIED = false`, `JUNIT_VERIFIED = false`, `JAR_CREATED = false`, `PAPER_RUNTIME_VERIFIED = false`.
- 시작 시 로컬은 `301c2d0`, 원격 work는 `81b2a6d`였다. GitHub Actions API가 Forbidden을 반환해 `CI_NOT_VERIFIED` 상태다.

## PHASE 2B Paper 리스폰 (2026-11-18)

기준 커밋 `841bcd1`의 공개 Actions #4에서 clean build 및 `augmentwild-jar` 업로드 성공을 확인했다. CLI Actions API는 Forbidden이며 공식 Paper 다운로드 API 접근은 CONNECT 403으로 로컬 서버 검증이 차단됐다.

Paper 사망 이벤트의 HIGHEST에서 표준 인벤토리·XP 보존과 드롭 제거를 적용하고 MONITOR에서 사망을 기록한다. 예약은 경기 시간 만료, 접속, 네이티브 리스폰이 모두 충족됐을 때 처리한다. 그 전과 탈락 후에는 이동·상호작용·피해·인벤토리 이벤트를 제한한다. 게임 모드, 비행, 체력·효과·인벤토리 값을 수동으로 변경하지 않는다. 종료·탈퇴 시 제한과 예약을 해제한다. 테스트 복귀점은 기본 비활성이고, 활성화하면 같은 월드의 로드된 청크에서 바닥·공간·경계를 확인한 설정 좌표만 사용한다. 설정이 없거나 부적합하면 Paper 기본 위치를 유지한다. 최종 경기장 내부 스폰은 미구현이다.

Java 25 독립 검사 331개와 시나리오 21개가 통과했다. 재접속, 중복 사망, 네이티브 리스폰 완료 조건 및 경기 종료 예약 정리를 검증했다. 실제 Paper 시나리오 A–F는 BUILD_BLOCKED인 로컬 환경에서 실행하지 않았으므로 RUNTIME_VERIFIED는 false다. 표준 사망 보존 API의 실제 XP 진행도 및 다른 플러그인과의 이벤트 상호작용은 서버에서 확인해야 한다.

PHASE 2B의 GitHub Actions #6에서 Java 버전 확인, `./gradlew clean build --stacktrace`(표준 test 태스크 포함), JAR 내부 검사 및 업로드 단계가 성공했다. 검사에서 루트 `plugin.yml`, 선언된 메인 클래스와 대응하는 클래스 파일을 확인했다. `GRADLE_BUILD_VERIFIED = true`, `JAR_CREATED = true`, `PAPER_RUNTIME_VERIFIED = false`. 개별 JUnit 로그는 공개 스텝 로그 URL이 404라 확인하지 못했다. 로컬 `BUILD_BLOCKED`와 Actions 성공은 별도다. [Actions #6](https://github.com/anohino88/wmdrkd/actions/runs/44218680457)

## 최종 통합 명세 진행 (2026-11-18)

- 시작 HEAD `27ec557`, 브랜치 `work`. 최종 통합 명세를 저장하고 기존 세부 명세의 PvP 충돌을 수정했다. 직업 시스템은 제외한다.
- Stage A 구현. 사망 의도를 경기 ID·플레이어·순번·당시 단계·경기 시각으로 기록한다. 배치에서 원래 단계로 판정하고, 종료가 확정되면 리스폰을 처리하지 않는다. Paper의 후속 리스폰 이벤트를 확인하고 개발용 고정 복귀점은 제거했다. 서버 기본 리스폰 대기는 정식 경기장 랜덤 복귀로 검증되지 않았다.
- Stage A 검증. Java 25 독립 테스트 331개 및 22개 시나리오 통과. 두 단계 경계, 예약 시각, 다른 경기 및 오래된 사망 의도 거부를 포함한다. 커밋 `d15c6ac`의 [Actions 실행](https://github.com/anohino88/wmdrkd/actions/runs/44262339823)은 전체 빌드, 독립 검사 및 JAR 검사를 통과했다.
- Stage B 부분 구현. `/aw admin arena preview`는 지정 기존 오버월드에서 랜덤 후보, 월드 경계, 기존 기록 AABB, 설정 간격의 표면 바다 바이옴, 안전 스폰 간격을 검사한다. 지정 간격 외 좌표에는 바다 없음이 보장되지 않는다. 생성되지 않은 청크는 후보 실패로 처리하고 동시에 요청하는 청크 수를 제한한다. 기록은 읽기만 하며 경기 시작·배치, 사용 기록 쓰기, 공유 월드 보호와 랜덤 리스폰은 후속 작업이다.
- 서버 실동작, 8~16인 검증 및 Stage C–J는 미완료다. 다음은 검색 결과를 게임 시작 컨텍스트에 연결하고 안전하게 배치한 뒤, 예약 활성화 전에 경기장·자기장 내부 랜덤 텔레포트 성공을 확인하는 것이다.
- Stage B 미리보기 커밋 `f68bb5f`의 [Actions 실행](https://github.com/anohino88/wmdrkd/actions/runs/44262787984/job/134659937514)에서 `COMPILED`, `BUILD_VERIFIED`, `JAR_VERIFIED` 확인. `clean build`, 독립 도메인 테스트, 메타데이터·클래스 검사 및 `augmentwild-jar` 업로드 단계가 성공했다. 실제 월드에서 미리보기와 플레이를 실행한 검증은 `NOT_RUN`이다.

## PHASE 3 실제 경기장 및 리스폰

시작 HEAD `1188ed6`, 브랜치 `work`. `4fa56d9`에서 경기 시작·복귀·저장 흐름을 연결하고 `a9078f7`에서 리스폰 거리 선호 및 서버 기동 CI를 추가했다.

- IMPLEMENTED: `PREPARING`에서 기존 월드 PvP 설정과 참가자를 확인하고 엄격한 ZERO 표면 검사 후 서로 떨어진 랜덤 스폰을 배정한다. 초기값은 확정용 간격 1, 미생성 청크 생성 비허용, 동시 청크 최대 8이다. 배치와 기록 저장을 완료한 뒤에만 경기 시계가 시작된다. 미리보기는 기록에 쓰지 않는다.
- IMPLEMENTED: 게임 ID·예약을 확인하고 네이티브 리스폰과 경기 시간 지연이 끝난 뒤, 현재 안전 영역에서 새 좌표를 추첨한다. 이동 직전 재검증하고 성공 후 예약을 소비한다. 실패 시 예약을 유지하며, 재시도 사이에 틱 지연을 둔다. 부활 직후 피해 보호를 적용한다.
- IMPLEMENTED: 플레이어 상태 스냅샷·지역 기록을 원자적으로 저장한다. 인벤토리·갑옷·보조 손, 위치·모드·XP·체력·허기·효과·비행 상태를 복구한다. 속성 자체는 변경하지 않는다. 실패하거나 오프라인이면 스냅샷을 남겨 다음 접속 때 재시도한다. 동시 외부 플러그인 및 서버 크래시 복구는 실서버에서 검증하지 않았다.
- TESTED: Java 25 독립 도메인 검사 331개와 시나리오 23개 통과. 최종 60×60 영역 랜덤 후보의 다양성·경계 여유 및 빈 영역을 검증했다. 서버의 실제 안전 지표면 판정은 독립 검사의 범위 밖이다.
- COMPILED / BUILD_VERIFIED / JAR_VERIFIED: `4fa56d9`의 [Actions 빌드](https://github.com/anohino88/wmdrkd/actions/runs/44266754367)는 컴파일, Gradle 테스트, 독립 도메인 및 JAR 업로드를 완료했다.
- 서버 검증: 로컬 Paper 다운로드는 프록시 CONNECT 403. CI에는 서버 부팅·`/aw status`·정상 종료를 확인하는 분리된 작업을 추가했다. 실제 2인 경기 시나리오 A–H는 NOT_RUN이다. 별도 대기실 대신 네이티브 리스폰 위치에서 행동을 제한하며 전역 WorldBorder에 손대지 않는다. 이 단계의 경계 처리는 원래 경기장 밖 이동 제한과 랜덤 리스폰 시 축소 안전 영역 검사다.

최종 코드 `711aab0`의 [Actions 실행](https://github.com/anohino88/wmdrkd/actions/runs/44268221316)이 성공했다. `COMPILED`, `BUILD_VERIFIED`, `JAR_VERIFIED` 및 Paper 26.3 기동·플러그인 활성화·콘솔 `/aw status`·종료 smoke 검증을 확인했다. `augmentwild-jar`와 `paper-smoke-log`가 Actions 아티팩트로 제공된다. 실제 2인 배치·전투·리스폰·복구에 대한 `RUNTIME_VERIFIED`는 미확인이다.

## PHASE 3.1 실전 안정화 (2026-11-18)

- 시작 HEAD `b0a016d`. 부활 보호 동안 받는 피해와 본인에게 귀속되는 공격·간접 피해를 모두 차단한다. 직접 공격, 발사체 발사·피해, 소유자가 확인되는 TNT·길든 몹, 투척 물약·잔류 구름, 발화 경로를 처리한다. 발신자가 없는 기존 지속 피해나 외부 플러그인의 이벤트 우회 체력 변경은 추적할 수 없다. 파밍 전역 PvP 설정은 변경하지 않았다.
- 복원 파일은 시작 시 인덱싱한다. 복원 도중 실패하면 파일을 보존하고 상호작용을 제한한다. 다음 접속 또는 재시도 시 인벤토리·효과를 추가 지급하지 않고 스냅샷으로 교체한다. 검증과 플레이어 데이터 저장이 끝난 뒤에만 파일을 삭제한다. 이동 예외·거부는 실패로 처리하며 활성 리스폰 예약을 유지한다.
- 경기장 검색은 표면 검사 수, 요청 수, 최대 동시 요청, 소요 시간, Paper의 최대 틱 길이와 50ms 초과분 및 실패 사유를 로그로 남긴다. 누락된 월드는 `arena.world` 수정 방법을 안내한다. 첫 재현에서는 청크 8개 요청 뒤 미준비 청크로 후보를 거부했다. 검사는 Paper 메인 스레드의 로드 완료 시점에 수행하도록 변경했다.
- Java 25 독립 검사 331개와 시나리오 23개 통과. 로컬 Gradle은 Paper Maven 403과 Java PKIX로 의존성 해석 단계에서 차단됐다. 기존 Paper 콘솔 smoke는 실제 클라이언트 테스트를 대체하지 않는다. 실제 2인 시나리오 A–F와 복원 상황별 실플레이 검증은 모두 `NOT_RUN`이다.
- 수정 코드 `b2b7d72`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44283554308)에서 Java 25 Gradle 빌드, JAR 검사·업로드, 실제 Paper 26.3 기동·콘솔 상태·종료 및 경기장 벤치마크가 통과했다.
- 실제 Paper의 사전 생성·로드된 평지에서 1,000×1,000 / 간격 1 검사: 후보 1개, 검사 좌표 1,000,000개, 청크 요청 4,096건, 최대 동시 요청 8건, 25.684초, 최대 틱 31.03ms, 50ms 초과 0ms, 거부 사유 없음. 이 수치는 미생성 청크나 디스크 로드가 필요한 실전 지형의 검색 시간 보장이 아니다.

## PHASE 4 자기장 (2026-11-18)

- 기준 `7c990f9`. `64e48a8`에서 경기 시계 기반 자기장과 Paper 검증용 프로브를 구현했다. `44246fd`에서 피해·리스폰 처리 중 경기가 종료되면 같은 틱의 나머지 자기장 처리를 차단했다.
- 기본 30:20~40:20 동안 1000×1000에서 60×60으로 선형 축소한다. 같은 경기 시간의 영역을 표시·범위 밖 피해·리스폰 검사에 사용한다. 기존 경기 스케줄러에서 판정하며 자기장 갱신은 경기 시계 1초마다, 피해 간격 기본값은 1초, 기본 피해는 2 체력 포인트다. 일시정지 중 중단하고 지연 틱의 누락 간격은 누적하지 않는다. 자기장 구현에 별도 리스너와 스케줄러는 추가하지 않았다.
- 가상 WorldBorder는 참가자에게 부착한다. 탈퇴·종료·접속 종료에서 제거하고 교체된 경우 기존 또는 다른 플러그인의 현재 표시를 보존한다. 재접속 시 현재 표시를 부착한다. 가상 보더는 정사각형이므로 경기장 가로·세로·시작 크기를 동일하게 설정한다.
- Java 25 독립 검증 PASS: 기존 331개와 시나리오 23개, 신규 자기장 검사 21개. 피해 예외·시간 경계·축소 내 리스폰 후보 등은 순수 Java 검사다. 로컬 Gradle BLOCKED: Paper Maven 403 및 Maven Central PKIX.
- [Paper 프로브](https://github.com/anohino88/wmdrkd/actions/runs/44287841509/job/134745473636) PASS: 실제 Paper 26.3 기동·가상 보더 생성, 전역 월드 보더 불변(B19), 돼지 대상 OUTSIDE_BORDER 피해 이벤트 및 체력 2포인트 감소, 종료. 플레이어별 표시·정리·재접속(B16/B17) 및 참가자 PvP·리스폰·복원은 2인 클라이언트 미실행으로 NOT_RUN. `docs/phase4-playtest-ko.md`에 실행 절차를, `docs/post-phase4-design-ko.md`에 후속 확정 규칙을 남겼다.
- 코드 HEAD `44246fd`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44288114740) PASS: Java 25 Gradle 빌드, 독립 검사, JAR 확인·업로드, Paper 프로브, 기존 경기장 벤치마크. [서버 JAR 아티팩트](https://github.com/anohino88/wmdrkd/actions/runs/44288114740/artifacts/14420042517).
- 동일 실행의 로드된 평지 검사에서 100만 좌표, 청크 요청 4096건, 동시 최대 8건, 25.686초, 최대 틱 88.35ms(50ms 초과 38.35ms)를 관찰했다. 실경기의 자기장 피해 처리로 인한 틱 비용이나 2인 클라이언트는 측정하지 않았다.

## PHASE 5 증강 선택 및 효과 (2026-11-18)

- 기준 `319940a`. `3b6781e`에서 GUI, 경험치 희귀도와 12종 이벤트 효과를 경기 시계에 연결했다. `66c27e0`에서 확률·선택·쿨다운·재귀 검사와 Paper 속성 프로브를 추가했다. `b943d1d`에서 발동 조건을 설정으로 옮겼다.
- 5분 간격, 현실 15초, 최종 프리즘 3종, 경기당 재추첨 1회, 마감 자동 선택을 구현했다. 재추첨은 같은 레벨 스냅샷을 사용하고 마감은 바꾸지 않는다. XP는 선택 비용으로 차감하지 않는다. 기존 고정 희귀도 가중치는 이 추첨에 사용하지 않는다.
- `AugmentCatalog`에 실버·골드·프리즘이 각 4종 있으며, 실제 XP 이벤트 조정, 근접 피해·방어 수정, 시간 기반 회복과 임시 채굴·이동·방어 속성으로 연결했다. 같은 ID는 중첩되지 않으며 XP는 가장 큰 보너스를 사용한다. 명칭·수치·설정 및 PHASE 6 목록은 `docs/phase5-augments-ko.md` 참고.
- 독립 Java 25 PASS: 기존 331개, 시나리오 23개, 자기장 검사 21개, 신규 증강 검사 127개. 로컬 Gradle BLOCKED: Paper Maven 403 및 Java PKIX.
- `66c27e0` [Actions](https://github.com/anohino88/wmdrkd/actions/runs/44299783974) PASS. Paper 26.3 실제 테스트 엔티티에서 이동 속도 modifier를 두 번 적용해 중복 없음과 원복을 확인했다. 실제 클라이언트 GUI, XP 획득 이벤트, 2인 PvP·재접속·종료 처리는 NOT_RUN이다. 따라서 코드 구현과 서버 프로브 통과를 기록하지만 `PHASE_5_CORE_COMPLETE` 및 `REAL_CLIENT_VERIFIED`는 선언하지 않는다.
- 최종 코드 `b943d1d`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44300111215) PASS: Java 25 Gradle/JUnit, 독립 회귀 검사, JAR 검사·업로드, Paper 26.3 속성·자기장 프로브, 경기장 벤치마크. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44300111215/artifacts/14425452103).

## PHASE 6 공식 증강 확장 (2026-11-18)

- 시작 기준 `f29958a`. 공식 AG 30종과 AH 34종의 효과 경로 및 진화 6종·시너지 5종을 연결했다. 공식 64개와 기존 PHASE 5 확장 12개를 별도 집계한다. 기본 설정에서 총 76개 ID가 등록·선택 가능하며, 교환형 2개에는 개별 자격을 적용한다. XP 확장 3종과 기존 선택 시계·GUI·스냅샷을 유지했다.
- 명세 대응, 설정, 활성 증강 명령, 두 선택을 확정한 뒤에 비용을 부과하는 교환, 실플레이 검증 절차는 [PHASE 6 문서](docs/phase6-augments-ko.md)에 기록했다.
- Java 25 독립 검사 통과. 331개 기존 검사, 시나리오 23개, 자기장 검사 21개, PHASE 5 증강 검사 127개, 신규 엔진 검사 49개와 `git diff --check`를 실행했다. 신규 검사는 64개 ID 대조, 연쇄 한도, 쿨다운·중첩, 광석·이동 기록, 부활 우선순위 및 교환의 원자적 완료를 포함한다.
- 로컬 Gradle은 Paper Maven 메타데이터 GET 403 및 Maven Central Java PKIX로 의존성 해석에서 차단됐다. [전체 카탈로그가 포함된 2f0cd0b CI](https://github.com/anohino88/wmdrkd/actions/runs/44306582864)에서 Gradle 빌드·JUnit, JAR 구조 검사와 업로드, Paper 26.3 기동·속성·자기장 프로브 및 경기장 벤치마크가 통과했다.
- 모든 증강의 실제 2인 FFA 효과 검증은 `NOT_RUN`이다. 현재 가동 중인 서버에 실제 26.3 클라이언트를 연결해 본 기록이 없으므로 실전 완료 상태는 미확정이다. 경기 이전에 설치된 광석의 자연 생성 여부는 판별할 수 없다.
- 최신 코드 `d9d1fab`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44307911766)에서 전체 빌드, JUnit, 독립 검사, JAR 구조 확인, Paper 26.3 기동·종료, 카탈로그 및 테스트 엔티티의 피해 한도·속성 원복 프로브와 경기장 벤치마크가 모두 통과했다. [서버 JAR 아티팩트](https://github.com/anohino88/wmdrkd/actions/runs/44307911766/artifacts/14429575691).

## PHASE 6.2 계열 공명 및 전투 안정화 (2026-11-19)

- 기준 `aec1e1944cd018b27736fd046e7b11bc0ca1048f`에서 작업했다. 누적의 공명 13종, 생명의 잔향 3종을 별도 설정으로 추가했다. 공식 증강 64종, 확장 12종, 기존 진화 6종 및 고유 시너지 5종의 등록을 유지했다.
- 누적의 공명은 신규 영구 보상의 소수 잔량을 합산한다. 생명의 잔향은 치명 피해 방지 성공 때만 별도 보호막을 생성하고 바닐라 흡수 체력과 분리한다. 강철심장 체력 환산, PvP 육중한 주먹 상한, 같은 희귀도 안에서의 상호 시너지 가중치, 연쇄 피해 계측을 적용했다. 피해 예산 강제 제한과 신규 후반 성장 보정은 기본 비활성화다.
- 로컬 Java 25 독립 회귀 및 신규 공명·밸런스 58개 검사 PASS. 고정 시드에서 가중치 적용 전후를 비교했고, 실제 PvP 결과와 분리하여 [PHASE 6.2 문서](docs/phase62-resonance-ko.md)에 기록했다. `git diff --check` PASS.
- 로컬 `./gradlew clean build --stacktrace` BLOCKED. Paper Maven 메타데이터 GET 403 및 Maven Central Java PKIX에서 중단됐다. [선행 코드 검증](https://github.com/anohino88/wmdrkd/actions/runs/44432253916)에서는 Java 25 빌드·JUnit, Paper 기동·종료, 실제 엔티티에서 별도 보호막과 기존 흡수 체력의 피해 처리 및 정리가 통과했다.
- `REAL_CLIENT_VERIFIED = NOT_RUN`. 실제 클라이언트 2·8·16인 경기, 강철의 왕과 누적의 공명의 실전 합산 피해, 참가자 단위 리스폰·재접속·정리 및 실제 전투 성능은 확인이 필요하다.
- 최종 코드 `637b0a1`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44432667326) PASS. Java 25 전체 빌드, JUnit, JAR 구성 검사, Paper 26.3 기동·종료, 속성·자기장·보호막 프로브 및 경기장 벤치마크가 완료됐다. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44432667326/artifacts/14485949938).

## PHASE 6.3 전투 감사 및 검증 (2026-11-19)

- 기준 `50d16b49d5c3af55195a430952d0f1e1ecf01750`에서 증강 76종의 설정·호출 경로를 [감사표](docs/phase63-audit-ko.md)에 정리했다. 선택 중 일반 위치 이동·공격이 허용되는 경로를 수정했으며 GUI와 관리 이동은 기존 검사 경로를 유지한다.
- 실제 Paper 재현에서 선행 보호막 적용 후 후속 리스너가 피해 BASE를 0으로 바꾸면 테스트 엔티티의 바닐라 흡수 체력이 2→4로 증가했다. 피해 보정치가 정상적으로 다시 계산되는 방식으로 변경해 같은 경우를 재검증했다. 취소·BASE 교체·갑옷·저항, 흡수 체력 및 보호막 정리를 프로브에 포함했다.
- 합성 타격은 원인·대상 일치 여부로 확인한다. 선택·원본·연쇄·피해·보호막·성장 등을 경기 로컬 별칭으로 기록하는 선택형 진단을 추가했다. 강철심장 성장 곡선, 육중한 주먹 PvP 상한, 부활 수치, 시너지 가중치와 피해 예산 기본 설정을 유지했다.
- Java 25 독립 회귀 PASS. 신규 PHASE 6.3 도메인 단언 126개에 13개 영구 원장, 진화 6종 경계, 고유 시너지 구성, 연쇄 ID 및 진단의 상한·정리를 포함했다. `git diff --check` PASS. 로컬 Gradle은 Paper Maven 403과 Java PKIX로 BLOCKED다.
- 6가지 선택 전략 × 재추첨 유무, 총 120만 경기의 시뮬레이션과 강철심장 후보 곡선을 비교했다. 실제 Paper의 2·8·16개 테스트 엔티티 부하에서 피해 이벤트 60·240·480건을 관찰했고 임시 엔티티 잔여는 모두 0이었다. 실제 FFA 성능과 증강별 실효과, 실제 GUI·재접속은 NOT_RUN이다. 결과와 재현 절차는 [검증 기록](docs/phase63-validation-ko.md)에 있다.
- 최종 코드 `411fc16`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44436414303) PASS. Java 25 빌드, JUnit, 독립 검사, JAR 구성, Paper 26.3 기동·종료, 자기장·보호막·합성 전투 프로브, 경기장 벤치마크가 통과했다. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44436414303/artifacts/14489227747).

## PHASE 6.4 정보전과 보급 (2026-11-19)

- 기준 `fe86f7b`. 기존 증강 76종과 진화·시너지·공명, 경기 시계·리스폰·자기장을 유지했다. `/aw locate` 및 `/aw track`에 다이아몬드 비용과 방향 전용 추적을 연결했다. 실제 바닐라 망원경 사용과 시야를 조건으로 스냅샷을 만들고 참가자별 읽기 전용 결과를 연다.
- Bukkit 최종 피격과 공식 전투 엔진의 출처 정보를 사망 분석에 연결했다. 체력 피해와 흡수를 분리하며 기록은 참가자별로 제한한다. 경기 종료 시 원장과 활성 추적·정찰을 정리하고 최종 사망 보고서만 전달 기한까지 남긴다.
- 경기 시간에서 일반 6~10분과 14~20분, 고급 23~28분에 상자 하나씩을 선택한다. 예고 2분, 좌표 공개 1분, 개봉 3초와 피격·이동 중단, 성공 시 단일 지급을 연결했다. 보상 세트는 장비나 XP를 읽지 않고 등장 시 추첨한다. 관리 종료 시 월드 마커와 청크 티켓을 정리한다.
- Java 25 독립 검사 PASS. 기존 회귀와 새 도메인 단언 27개를 실행했다. 2·8·16 가상 참가자 대상 최근접 검색 및 상한 기록 합성 검사도 실행했다. 로컬 전체 Gradle은 Paper Maven 403 및 Maven Central PKIX로 BLOCKED. `git diff --check` PASS.
- `REAL_CLIENT_VERIFIED = NOT_RUN`. 서버 프로브는 아이템 비용·공간, 실제 시야, 상자 마커 생성·정리의 범위를 검증한다. 실제 2·8·16인 플레이의 망원경 GUI, 사망 표시, 동시 개봉, 재접속은 미실행이다. [명령·설정·검증 절차](docs/phase64-intel-supply-ko.md)
- 최종 코드 `acc5473`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44458233133) PASS. Java 25 Gradle 전체 빌드·JUnit·JAR 구조 검사, Paper 26.3 기동·종료와 증강·자기장·보호막·정보전 프로브, 경기장 벤치마크가 통과했다. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44458233133/artifacts/14498053304).

## PHASE 6.5 보급 복구와 운영 안정화 (2026-11-19)

- 기준 `1de942d8ae1194c127b9e971222a8cb636ca8f48`. 생성 전 원장 쓰기, 시작 시 및 경기 직전 복구를 연결했다. 원장에는 경기·월드 UUID와 이름, 좌표, 원래 블록과 소유권 토큰 및 상태를 저장한다. 토큰 일치·빈 단일 상자만 복원하며 저장 후 원장을 제거한다. 충돌 또는 읽기·저장 실패는 보존하고 새 경기 시작을 차단한다.
- 보급 공개 위치와 생성 위치를 검증하고 소유권·내용물이 바뀐 상자의 지급을 중단한다. 다른 화면 열기, 이동·피격·접속 종료를 개봉 취소에 연결했다. 공간 부족이나 확실한 원복은 재시도할 수 있으며 원복 불확실은 재지급을 막는다.
- 정찰 진행률·스냅샷 시각, 추적 잔여 초와 표시 우선순위, 사망 분석의 바닐라 흡수·생명의 잔향 분리를 추가했다. 익명 총계에는 보급 생성·개봉·교전·지연, 추적, 정찰, 사망 조회를 포함한다. 실제 경기 운영 통계는 NOT_RUN이다.
- Java 25 독립 회귀와 신규 단언 14개 PASS. 합성 통계에서 정찰 시도 2·성공 2·유효 후속 교전 1을 검증했다. 기존 Paper 합성 전투는 계속 검증 범위에 있다. 로컬 전체 Gradle BLOCKED는 Paper Maven 403과 Maven Central PKIX다. `git diff --check` PASS.
- `CRASH_RECOVERY_REAL = PASS`. 일회용 Paper 프로세스에 SIGKILL 후 두 번 기동했다. 생성 전·완료·미기록 전이·청크 저장 이전 상태 및 지급 후 마커 제거 경계의 복구, 다른 토큰과 내용물이 있는 상자 보호를 확인했다. 실제 참가자의 개봉·지급 중 강제 종료와 2·8·16인 플레이, 망원경 GUI·사망 UI의 `REAL_CLIENT_VERIFIED`는 NOT_RUN이다. [운영·검증 문서](docs/phase65-recovery-ko.md)
- 최종 코드 `b28c8d5943af1b83b41f5bc8b411ca606c60431b`의 [GitHub Actions](https://github.com/anohino88/wmdrkd/actions/runs/44470056149) PASS. Java 25 Gradle/JUnit, 독립 회귀, JAR 구조 검사, Paper 26.3 프로브, 실제 프로세스 강제 종료 복구와 경기장 벤치마크가 통과했다. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44470056149/artifacts/14504102405) · [강제 종료 및 재시작 로그](https://github.com/anohino88/wmdrkd/actions/runs/44470056149/artifacts/14503797799).

## PHASE 6.6 경기 템포와 플레이테스트 (2026-11-19)

- 기준 원격 `ea131bcce5e40369b4fd717fc01b59ea9a581592`. 기존 경기 규칙은 유지했다. 익명 경기 집계에 첫 PvP, 5분별 교전, 참가자별 처치·사망, 선택·재추첨, 고유 시너지, 진화 시각, 축소 중 생존 인원, Paper 틱 분포를 연결했다. 실전 로그와 자동 합성 로그의 결과를 분리한다.
- Java 25 독립 검사 PASS. 2·4·8·16인 합성 경기가 모두 2,420 경기초·2,510 가상 현실초, 선택 6회와 보급 일정 3개를 거쳐 종료했다. 후보 비교는 12,000개 합성 표본에서 역할 분산과 최종 XP 후보 제외를 비교했다. 기본 XP 수치와 밸런스는 변경하지 않았다.
- 부하 프로브에서 콜백 30회가 Paper 한 틱에 몰린 사실을 확인했다. 고정 1틱 반복 작업으로 바꾸고 실제 표본 30개를 검증하도록 수정했다. 이전 단일 틱 표본은 p95 근거로 사용하지 않는다.
- 일회용 Paper 강제 종료 검사에 저장 경계를 추가했다. 분리된 Paper 인벤토리에 실제 보급 지급 함수를 적용해 저장한 보상 1개와 저장 전 중단된 보상 0개를 두 번의 재기동에서 확인했다. 실제 Minecraft playerdata는 이 검사의 대상이 아니다. 복제 위험을 줄이기 위해 기존 자동 재지급 금지를 유지했다.
- 실제 접속 가능한 클라이언트를 확보하지 못해 `REAL_CLIENT_VERIFIED = NOT_RUN`. 실전 전투 조합별 피해·성장, 실제 경기 템포, 실제 재접속과 플레이어 저장 중 강제 종료는 미실행이다. [서버 준비, 단계, 기록지](docs/phase66-playtest-ko.md)
- 로컬 전체 Gradle은 `:compileJava`의 Paper API 메타데이터 해석에서 Maven Central과 Paper Maven의 DNS 오류로 차단됐다. Java 25 독립 검사와 `git diff --check`는 통과했다.
- 검증한 코드 `42e3485e8f7e83238e2caa26aed69db18ba8288c`의 [Actions](https://github.com/anohino88/wmdrkd/actions/runs/44472291871) PASS. Java 25 Gradle/JUnit, JAR 구조, 기존 회귀 및 Paper 26.3 프로브, 1000×1000 경기장 벤치마크와 SIGKILL 복구가 통과했다. 클라이언트가 없는 Paper 합성 부하에서 2·4·8·16개 엔티티의 실제 틱 30개씩을 확인했다. 틱 p95/최대/50ms 초과는 각각 40.825/77.169ms/1회, 4.075/22.232ms/0회, 2.554/2.792ms/0회, 2.899/2.948ms/0회였다. [플러그인 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44472291871/artifacts/14504428818).

## PHASE 6.7 증강 선택과 전투 실험실 (2026-11-19)

- 기준 원격 `1e7e9e3e201b45fb898b095a329b700f549581fe`. 증강 76종의 주 역할과 최종 프리즘 보정, 순수 XP의 최종 회차 제외 및 후보 부족 시 복귀를 연결했다. 일반 회차 보정의 기본값은 비활성화다. XP 희귀도 공식, 시너지 기본 가중치, 선택 시간과 전체 경기 규칙을 유지했다.
- Java 25 독립 회귀 PASS. 고정 시드로 각각 120,000개 선택지 세트를 비교했다. 최종 단일 역할은 989→426/20,000, 순수 XP 포함은 2,325→0/20,000이었다. 기본 1–5회차 후보와 재추첨 결과, 각 회차 희귀도 계수는 일치했다. 시너지 5종 및 증강별 출현·선택률은 [실험실 문서](docs/phase67-combat-lab-ko.md)에 기록했다.
- 관리자 실험은 양쪽 권한, 전용 월드, 명시적 테스트 시작, 활성 참가자 및 경기 시계를 검사한다. 부여·제거·진행도·보고 명령은 기존 소유권·진화·공명 및 속성을 사용한다. 실험 경기 통계는 `mode=lab`로 표시한다. 클라이언트를 사용하는 명령의 실제 부여·제거, 공격, GUI 및 종료 복원은 `REAL_CLIENT_VERIFIED = NOT_RUN`이다.
- 로컬 전체 Gradle은 Paper API 메타데이터 해석 시 Maven Central·Paper Maven DNS 오류로 차단됐다. Java 25 독립 회귀와 `git diff --check`는 통과했다.
- 최종 코드 `9997982418a4e5e8e6c0a23709172768b87b8a94` [CI](https://github.com/anohino88/wmdrkd/actions/runs/44517290832) PASS. Java 25 빌드·JUnit·독립 회귀·JAR 검사, Paper 26.3 명령 권한·비활성·대기 중 변경 차단, 기존 프로브·보급 복구·경기장 벤치마크가 통과했다. 증가된 Paper 합성 부하는 2·4·8·16 엔티티별 50틱 워밍업 및 300틱 측정을 두 차례 수행했다. 전체 측정 2,400틱에서 50ms 초과는 0회, 최대는 4.640ms였다. [서버 JAR](https://github.com/anohino88/wmdrkd/actions/runs/44517290832/artifacts/14526058164) · [증강별 결과](https://github.com/anohino88/wmdrkd/actions/runs/44517290832/artifacts/14525748362) · [합성 부하 로그](https://github.com/anohino88/wmdrkd/actions/runs/44517290832/artifacts/14527085136).

## PHASE 6.8 최종 기능 구현 (2026-11-19)

- 기준 원격 `83e3cd24e50edfa8f77e5ec74b39274416e0cb95`. `/aw augments`의 개인 도감에 증강 76종, 고유 시너지 5종의 구성과 진행, 진화 6종 원장과 달성 여부, 두 계열 공명을 연결했다. 전투 발동과 선택·보호막·보급·정보전의 액션바는 한 곳에서 우선순위를 적용한다.
- 전투 실험실 프리셋 6종을 기존 권한 및 활성 테스트 경기 조건에 연결했다. 플러그인 GUI의 읽기 전용 클릭과 드래그 차단을 공유한다. 기본 경기 규칙과 초반 처치 보상 설정을 유지했다.
- `FEATURE_COMPLETE = PASS`, `REAL_CLIENT_VERIFIED = NOT_RUN`. 코드 `ec4cb4c7083b90ed974aa619e9479ed0f5355f19`의 [CI](https://github.com/anohino88/wmdrkd/actions/runs/44521254301)에서 Java 25 Gradle/JUnit, 독립 회귀, JAR 검사와 Paper 26.3 프로브 전체를 통과했다. 로컬 독립 회귀와 `git diff --check`도 통과했다. 로컬 Gradle은 Paper Maven·Maven Central DNS 오류로 차단됐다. [사용법과 검증 범위](docs/phase68-feature-complete-ko.md) · [JAR](https://github.com/anohino88/wmdrkd/actions/runs/44521254301/artifacts/14527364496).

## PHASE 6.8 전투 반응 확장

- 시작 원격 `fcd3cd97890a66c73649003b634d8b82cd5485ce`. 76종 유지 및 신규 8종, 기존 8종 후속 반응, 기존 발동형 7종의 지정과 단축키, 도감·HUD·연출을 연결했다.
- Java 25 독립 회귀와 A–G 통과. 고정 시드 XP 25에서 신규 증강의 일반 슬롯 점유율 5,970/60,000, 최종 프리즘 슬롯 점유율 4,640/60,000. [규칙과 실험 조건](docs/phase68-combat-reactions-ko.md)
- 로컬 Gradle은 Maven Central·Paper Maven 호스트 DNS 실패로 compileClasspath 해석이 차단됐다. `REAL_CLIENT_VERIFIED = NOT_RUN`.

## PHASE 6.9 전투 화면과 개인 설정

- 시작 원격 `1017dbf5d505f012cdd03e46c35316fcfa0c8965`. 보스바는 5틱마다 24블록 시선·장애물을 검사하고 마지막 관측을 최대 0.5초 보관한다. 피해 알림은 다음 관측 경계에서 확인한 체력·흡수 감소와 전투 원장의 보호막 차감을 구별한다. 같은 공격자·원본·대상은 0.2초 동안 집계하고 개인 액션바에 1.2초 표시한다. 다른 변동이 섞여 일치하지 않는 체력 감소는 제외한다.
- 경기 시계 사이드바, 발동형 스킬 및 자동 충전 상태, 후보의 시너지 마지막 구성원, 공개 보급 안내, 사망 확정 킬 피드, 종료 결과와 선수별 PDC 설정을 연결했다. 경기 복원이 성공한 뒤 결과를 연다. 모호한 증강별 피해 분배는 생략한다.
- Java 25 독립 회귀에서 331개 기본 검사와 A–G를 포함한 기존 검사 통과. 로컬 Gradle은 Paper Maven·Maven Central DNS 해석이 차단됐다. Paper 프로브는 실제 서버 API와 합성 뷰어를 사용하며 네트워크 클라이언트, 화면 렌더링과 실제 재접속은 `REAL_CLIENT_VERIFIED = NOT_RUN`.

## PHASE 7.0 전투 편의성

- 기준 원격 `d3d6332f9a5f88aeef446bcac70f28be79622c84`. 퀵슬롯 세 개가 기존 증강별 쿨다운과 발동 함수를 공유한다. 기존 지정 명령은 선택 슬롯에 등록하고, `next`는 다음 등록 슬롯으로 이동한다. 증강을 잃으면 연결된 슬롯을 비운다.
- 확인된 체력·흡수·보호막 차감에서 피격 방향과 개인 전투 기록을 생성한다. 직접 공격의 타격 위치와 발사 당시 기록한 투사체 원점만 방위 계산에 사용한다. 방향은 피격 당시 시점 기준 8방향이며 최근 피격 후 1초 동안 표시한다. 환경 피해는 방위를 표시하지 않는다. `/aw combatlog`는 현재 경기의 본인 기록을 최신순으로 최대 30건 보여준다.
- 기존 액션바에 상태 효과, 보호막 만료 시간, 스킬 슬롯·자동 충전, 경계 경고와 체력 25% 위기를 연결했다. 일반 상태 페이지는 2초 간격으로 교대한다. 도감 목록에서 항목을 클릭해 개인 즐겨찾기 8개를 관리한다. 진화 90% 근접 및 완료, 마지막 시너지 구성원, 연속 처치·탈락·우승 알림은 기존 진행 원장과 사망 확정 처리를 사용한다.
- 로컬 Java 25 독립 회귀 및 A–G 통과. 전체 로컬 Gradle은 Maven Central과 Paper Maven의 DNS 실패로 의존성 해석 단계에서 중단됐다. `REAL_CLIENT_VERIFIED = NOT_RUN`.

## PHASE 7.1 외부 에셋과 Blender 제작

- 기준 원격 `ce79fff1b13580e49a8e7976cce96b5d2be4940d`. Game-icons.net의 라이선스가 확인된 증강 84개·GUI 7개 심볼과 Kenney CC0 음원 3개를 가공했다. Blender에서 최적화한 공통 연출·일반 및 고급 보급 모델 14개를 만들고 Minecraft 26.3 형식 97.1로 내보냈다.
- 선택형 팩의 로드 상태를 확인하는 Paper 연동을 추가했다. 증강 선택·도감, 주요 GUI, 액티브 7종, 진화 6종, 고유 시너지 5종, 계열 공명 2종과 보급 표식에 시각 에셋을 연결했다. [설치·제작·출처](docs/phase71-resources-ko.md)
- Blender 4.3.2·Inkscape 1.4·FFmpeg 베이크, 입력 라이선스·SHA-256, ZIP 및 모델 참조 검증을 통과했다. Java 25에서 Paper 26.3 공식 소스 `d81ca50aeb956bccd912c124638f556b564ea8af`로 API를 컴파일해 로컬 Gradle `clean build paperProbeJar`를 완료했다. JUnit 42건과 독립 회귀·시나리오 A–G가 통과했다.
- Maven 기본 의존성 해석은 DNS 실패로 차단됐다. Paper 서버 다운로드 API는 HTTP 403을 반환해 서버 부팅과 프로브 실행이 중단됐다. 실제 네트워크 클라이언트 렌더와 다른 모델 엔진은 `NOT_RUN`. GitHub 푸시는 HTTPS 인증 실패로 차단됐다.

## PHASE 7.1-B 연출·화면·배포 감사

- 기준 로컬 `117142fee1b08a4229b719099d520a039f95c729`. 작업 시작 시 원격은 두 커밋 뒤인 `ce79fff`였고, 기존 Git HTTPS 경로로 일반 푸시 후 기준 SHA가 일치했다. gh 환경 토큰은 유효하지 않았으며 GitHub 연결 앱과 Git 원격 접근은 가능했다.
- 84종의 고유 아이콘과 Blender 원형을 실제 발동 모델로 결합했다. 능력 성장·속성 적용·교환 확정·방어·흡혈·확정 타격의 발동 경로를 [증강별 표](docs/art71/augment-audit.md)에 기록했다. 실험실은 연출만 재생한다. 원형 14개와 액티브 7종의 편집 파일·렌더, 패널 12종, 간소 연출 및 안전한 설치 도구를 배포한다. [한국어 안내서](docs/phase71-resources-ko.md)
- 로컬 Gradle은 공식 Paper 26.3 소스에서 컴파일한 API로 Java 25 빌드 및 JUnit 42건을 통과했다. 독립 회귀와 A–G, 84개 발동 경로·모델 정적 검사, Blender 베이크, 배포 ZIP 재생성 대조, 설치 도구 테스트 4건을 통과했다. 실제 클라이언트 전체 발동·렌더·프레임률 시험은 NOT_RUN이다.
- 첫 원격 실행에서 빌드와 리소스팩은 통과했고 Paper HUD 프로브가 이전 결과 슬롯 0·1을 참조해 실패했다. 변경한 4·9번 슬롯의 우승·개인 기록 문구와 정확한 카드 개수를 검사하도록 수정했다. 14개 원형에서 겹치는 동일 평면을 제거하고 전 모델의 면 검사를 빌드에 추가했다.

## PHASE 7.2 전투 조작 및 연출

- 기준 원격 43a1d3e. 웅크린 채 실제 핫바 1~3으로 이동하면 등록된 슬롯이 선택된다. 기존 핫바 이벤트를 취소하지 않는다. 자동 등록과 전환 설정은 UI 프리셋과 독립적으로 보존한다. 슬롯 교체는 기존 빠른 메뉴에서 수행한다.
- 스킬 전용 폰트 7자, 형태별 발동·소멸 보간, 실제 질주 경로·폭탄 명중·공허 장판의 개인 연출을 연결했다. 신규 장식 파티클에는 거리·품질별 예산과 서버 평균 틱 부하 제한을 적용했다.
- Java 25 공식 API 로컬 캐시로 Gradle clean build 및 Paper 프로브 컴파일 통과. REAL_CLIENT_VERIFIED = NOT_RUN.
- 1인 실험의 격리 월드에 정지·이동·고체력·흡수 표적을 연결했다. 발동 시험은 원래 쿨다운 및 조건을 사용한다. `advance`는 실험 경기에서만 다음 증강 선택 시각으로 진행한다.
- Blender 원형을 보존하고 초승달·파쇄 조각·나선을 추가했다. 빌드에서 84종 모델과 텍스처 참조를 확인한다. Paper 연출 프로브는 현장 이동·발동점의 시청각 체감과 별개로 84개 모델의 수명, 1,200틱 장판 갱신·정리, 2·4·8·16명 상당의 연출 호출을 확인한다. [PHASE 7.2 설치·조작·제작 자료](docs/phase72-combat-ko.md)

## PHASE 7.3 전투 연출과 부하 검증

- 기준 원격 `053669e273a3ae9759fe7e6cb03aefeee4acb72c`. 공개·개인·피격자 연출 분리, 게임 상태와 블록 시야 확인, 확인된 발동점에 공유 Display 적용, 경기 종료 및 일시정지에서 제거. 큐와 Display 예산을 제한하고 실제 피해·위험 예고를 먼저 처리한다.
- 지연 폭발은 실제 예약을 사용하며, 마법 미사일은 좌표 진행에 따라 흔적을 보낸다. 광맥 방벽과 치명 피해 방지, 지진·공허·폭탄 등 대표 능력에 공개 효과를 연결했다. 84종 소스 경로 전수 감사와 실제 발동 보장 범위는 별도로 기록한다.
- 스킬 전환은 핫바, 무기 유지 순환, 메뉴에서 선택한다. 경기장 검사는 기존 조건을 사용하며 완료 청크에 대한 검사 시간 예산과 최대 검사 구간 시간을 기록한다.
- Java 25 로컬 Gradle의 Paper API 준비에는 공식 소스에서 컴파일된 API를 사용했다. `REAL_CLIENT_VERIFIED = NOT_RUN`. [정책, 실험실, 조작 및 에셋](docs/phase73-cinematics-ko.md)

## PHASE 7.4 리소스팩 복구 및 실험실

- 기준 원격 `4c3caabc3f6f884280d4ef002075f45a86ef8c6b`. 모든 인벤토리에 기본 제목을 사용하고 큰 패널의 폰트 등록을 제거했다. 개인 안전 모드, 서버 HTTPS ZIP 진단, 접속 세션별 클라이언트 상태와 재요청을 구현했다.
- 검증된 실험 경기장으로 직접 배치하고, 테스트 월드의 사전 점검과 미생성 청크 설정을 운영 설정과 분리했다. 증강 선택과 도감을 구분하고 15개 도감 필터와 외형 미리보기를 연결했다.
- 희귀도별 프레임과 검기·파쇄·나선 Blender 내보내기를 갱신했다. Java 25, 배포 ZIP 재생성 및 Paper 프로브와 실제 클라이언트 렌더는 별도 검증 항목이다. [설치·복구·미리보기](docs/phase74-recovery-ko.md)
