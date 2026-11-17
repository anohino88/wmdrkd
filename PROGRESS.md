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
