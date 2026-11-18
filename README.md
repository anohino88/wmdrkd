# 증강 야생 (AugmentWild)

Paper 26.3과 Java 25를 대상으로 하는 FFA 미니게임입니다. 전체 요구 사항은 [통합 명세](docs/specification-ko.md)에 있습니다.

Java 25 환경에서 `./gradlew clean build --stacktrace`로 빌드합니다. 빌드에 성공하면 `build/libs/`의 플러그인 JAR을 Paper 서버의 `plugins/`에 넣습니다. GitHub Actions에서도 빌드에 성공하면 JAR 아티팩트를 제공합니다. 실제 Paper 서버 실행은 별도로 검증합니다.

- `/aw status`는 경기 단계, 참가자 수, 경기 시간과 일시정지 상태를 표시합니다.
- `/aw join`과 `/aw leave`는 참가와 탈퇴를 처리합니다.
- `/aw admin start`와 `/aw admin stop`은 `augmentwild.admin` 권한으로 경기 상태와 시간표를 시작하고 종료합니다.

PHASE 2A의 시작 명령은 상태와 시간표를 구동합니다. 실제 경기장, 안전 스폰, PvP 제한, 인벤토리 보존 및 증강 효과는 후속 단계입니다. 리스폰은 기본적으로 Paper의 위치를 사용합니다. 리스폰 버튼을 누른 뒤에도 경기 시간으로 계산한 대기가 끝날 때까지 이동과 상호작용이 제한됩니다. 개발용 복귀점은 config.yml의 `respawn.test-return`에서 같은 월드의 검증된 위치에 한해 설정할 수 있습니다. 증강 선택 시점에는 설정된 선택 시간이 지나면 경기 진행을 재개합니다.

Java 25에서 `scripts/test-domain.sh`로 Paper 없이 도메인 회귀 검사를 실행할 수 있습니다. [진행 및 검증 기록](PROGRESS.md)
