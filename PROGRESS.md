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
