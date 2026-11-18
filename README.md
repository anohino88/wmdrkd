# 증강 야생 (AugmentWild)

Paper 26.3과 Java 25를 대상으로 하는 FFA 미니게임입니다. 전체 요구 사항은 [통합 명세](docs/specification-ko.md)에 있습니다.

Java 25 환경에서 `./gradlew clean build --stacktrace`로 빌드합니다. 빌드에 성공하면 `build/libs/`의 플러그인 JAR을 Paper 서버의 `plugins/`에 넣습니다. GitHub Actions에서도 빌드에 성공하면 JAR 아티팩트를 제공합니다. 실제 Paper 서버 실행은 별도로 검증합니다.

- `/aw status`는 경기 단계, 참가자 수, 경기 시간과 일시정지 상태를 표시합니다.
- `/aw join`과 `/aw leave`는 참가와 탈퇴를 처리합니다.
- `/aw admin arena preview`는 지정한 기존 월드에서 안전 스폰 후보를 포함한 경기장을 검색합니다. 표면 바이옴은 설정된 간격으로 검사합니다.
- `/aw admin start`와 `/aw admin stop`은 `augmentwild.admin` 권한으로 경기 상태와 시간표를 시작하고 종료합니다.

현재 시작 명령은 개발용 경기의 상태와 시간표를 구동합니다. 파밍 중 플레이어 공격을 플러그인이 차단하지 않으며, 기본 30:20부터 목숨 제한이 적용됩니다. 서버 자체 PvP 설정은 유지됩니다. 네이티브 리스폰과 경기 시계의 대기를 모두 완료해야 이동 제한이 해제됩니다. 현재는 서버의 기본 리스폰 위치가 사용되며, 실제 경기장 선점과 무작위 안전 리스폰은 후속 단계입니다.

Java 25에서 `scripts/test-domain.sh`로 Paper 없이 도메인 회귀 검사를 실행할 수 있습니다. [진행 및 검증 기록](PROGRESS.md)
