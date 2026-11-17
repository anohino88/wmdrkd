# AugmentWild 작업 규칙

- 첨부된 최종 통합 개발 명세를 기준으로 `PROGRESS.md`를 먼저 읽고, 작업 결과와 차단 사유를 갱신한다.
- 목표는 Minecraft/Paper 26.3이다. 공식 출시 및 Paper 지원, Maven 좌표, Java 요구 버전, api-version과 API 존재를 검증하기 전 구현을 시작하거나 다른 버전으로 대체하지 않는다. Java 25는 예상일 뿐 검증되지 않았다.
- 버전 검증 후 Gradle Kotlin DSL과 wrapper를 사용한다. 단계마다 `./gradlew clean build`, 테스트, `git diff --check`를 실행하고 실패를 해결한다. 현재 wrapper는 없다.
- 기존 브랜치를 유지하고 불필요한 브랜치를 만들지 않는다. 검증된 단계마다 의미 있는 커밋을 만든다.
- 게임 수치를 하드코딩하지 않는다. 미구현 증강은 `implemented: false`로 표시하고 선택 풀에서 제외한다.
- 모든 경기 상태 전환은 단일 상태 머신을 거치며 중복 사망과 승리 판정 경합을 방지한다. 경기 이벤트는 중앙 MatchClock에서 결정적으로 처리한다.
- 경기 시작 시 불변 설정 snapshot을 만들고 해당 경기는 그것을 사용한다. reload는 검증 후 원자적으로 적용한다.
- 기존 월드를 생성, 삭제, 복제 또는 롤백하지 않는다. 공유 월드의 전역 border를 변경하거나 비참가자에게 효과를 적용하지 않는다. 참가 전 상태를 안전하고 멱등하게 복원한다.
- NMS, CraftBukkit 내부 클래스, reflection 기반 버전 해킹을 사용하지 않는다.
