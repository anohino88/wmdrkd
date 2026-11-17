# AugmentWild 진행 상황

기준일: 2026-11-17 (Asia/Seoul)

## 현재 단계: PHASE 0 — BLOCKED

명세에 따라 Minecraft Java Edition 26.3과 Paper 26.3의 공식 지원 여부를 확인한 뒤 구현을 시작해야 한다. 현재 실행 환경의 네트워크 프록시가 공식 출처에 대한 연결을 HTTP 403으로 거부하여 이를 확인하지 못했다. 403은 해당 버전이 존재하지 않는다는 증거가 아니다. 요구 버전을 임의로 변경하지 않는다.

## 검증 시도 및 근거

- Mojang 공식 버전 목록: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json — 연결 시 프록시 HTTP 403.
- Paper 공식 다운로드 API: https://fill.papermc.io/v3/projects/paper — CONNECT 프록시 HTTP 403.
- Paper 공식 Maven 메타데이터: https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml — CONNECT 프록시 HTTP 403.
- 따라서 26.3 출시 여부, Paper 빌드, 실제 Maven 좌표와 버전, 요구 JDK, api-version 및 사용할 API의 존재를 확인하지 못했다. 검증 가능한 최신 지원 버전도 확인하지 못했다.
- 저장소: 빈 Git 저장소, 현재 브랜치 `work`, 원격 `origin` 연결됨. GitHub API 접근도 Forbidden.

## 완료

- 저장소 및 브랜치 확인, 공식 출처 접근 시도, 작업 규칙 기록.

## 미완료

- PHASE 0의 공식 버전 확인과 빌드 전략 확정. PHASE 1–11 전체 및 게임 기능 전체.
- 플러그인 코드, Gradle wrapper, CI, 테스트와 서버 실행 파일 없음. 플레이할 수 없음.

## 검증 상태

- `./gradlew clean build` 및 테스트: 실행 불가 (wrapper와 프로젝트가 아직 없음).
- 실제 Paper 서버 테스트 및 GitHub Actions: 실행하지 않음.
- 알려진 게임 버그와 성능 문제: 구현 및 실행 전이므로 평가하지 못함.

## 다음 작업

1. 공식 Mojang, Paper API, Paper Maven 저장소에 접근 가능한 환경에서 위 출처를 다시 조회한다. 공식 문서에서 요구 Java, 플러그인 메타데이터와 필요한 API를 확인하고 근거를 기록한다.
2. 26.3 지원을 확인하지 못하면 최신 검증 버전과 근거를 기록하고 계속 BLOCKED로 둔다. 명세에 따라 임의로 대체하지 않는다.
3. 26.3이 검증되면 PHASE 0을 마친 후 명세의 순서대로 bootstrap, 빌드, 테스트 및 커밋을 진행한다.
