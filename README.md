# 증강 야생 (AugmentWild)

Paper 26.3 및 Java 25를 대상으로 하는 FFA 미니게임의 초기 개발 코드입니다. 아직 경기를 시작하거나 플레이할 수 없습니다. 전체 증강 목록과 게임 요구 사항은 [통합 명세](docs/specification-ko.md)를 참조하세요.

Gradle wrapper는 포함되어 있으나 이 환경에서는 Java 25 JDK와 Paper 의존성을 사용할 수 없어 빌드를 완료하지 못했습니다. 네트워크 및 JDK 준비 후 Paper API의 안정 빌드를 고정한 다음 `./gradlew clean build`로 빌드와 테스트를 진행해야 합니다. `build/libs/`의 jar를 Paper 서버의 `plugins/`에 설치하는 방식이 목표이나, 서버 실행은 아직 검증하지 않았습니다.

현재 명령은 `/aw status`뿐입니다. 경기장 탐색, 바다 제외, 리스폰, 관리자 설정, 증강 효과 및 기존 상태 복원은 미구현입니다. 현재 설정값은 설계 초안이며 게임에 적용되지 않습니다. 진행 및 차단 사항은 [PROGRESS.md](PROGRESS.md)를 참조하세요.
