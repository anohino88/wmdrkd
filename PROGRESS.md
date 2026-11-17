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
