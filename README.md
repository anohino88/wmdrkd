# 증강 야생 (AugmentWild)

Paper 26.3과 Java 25를 대상으로 하는 FFA 미니게임입니다. 전체 요구 사항은 [통합 명세](docs/specification-ko.md)에 있습니다.

Java 25 환경에서 `./gradlew clean build --stacktrace`로 빌드합니다. 빌드에 성공하면 `build/libs/`의 플러그인 JAR을 Paper 서버의 `plugins/`에 넣습니다. GitHub Actions에서도 빌드에 성공하면 JAR 아티팩트를 제공합니다. 실제 Paper 서버 실행은 별도로 검증합니다.

- `/aw status`는 경기 단계, 참가자 수, 경기 시간과 일시정지 상태를 표시합니다.
- `/aw join`과 `/aw leave`는 참가와 탈퇴를 처리합니다.
- `/aw admin arena preview`는 지정한 기존 월드에서 안전 스폰 후보를 포함한 경기장을 검색합니다. 표면 바이옴은 설정된 간격으로 검사합니다.
- `/aw admin start`와 `/aw admin stop`은 `augmentwild.admin` 권한으로 경기 상태와 시간표를 시작하고 종료합니다.

경기 전에 `config.yml`의 `arena.world`를 PvP가 허용된 기존 오버월드로 지정합니다. 기본 확정 검사는 1블록 간격이며, `generate-missing-chunks`의 기본값은 false입니다. 넓은 경기장을 사용하려면 기존 청크가 준비돼 있어야 합니다. 참가자가 두 명 이상 모이면 `/aw admin start`가 경기장을 검색하고 스냅샷 저장과 안전 배치 후 파밍을 시작합니다.

리스폰은 경기 시간 기준 대기와 Paper의 기본 리스폰이 모두 완료된 뒤, 현재 안전 영역의 무작위 지표면으로 이동하는 방식입니다. 이동이 거부되거나 안전 지점을 찾지 못하면 예약을 유지합니다. 경기 전 인벤토리와 상태는 `plugins/AugmentWild/recovery/`에 저장하며 경기 종료 또는 다음 접속 시 복구합니다. 사용 확정 지역은 `used-regions.yml`에 기록합니다.

Java 25에서 `scripts/test-domain.sh`로 Paper 없이 도메인 회귀 검사를 실행할 수 있습니다. [진행 및 검증 기록](PROGRESS.md)

기본 자기장은 경기 시간 30:20부터 600초 동안 1000×1000에서 60×60까지 줄어듭니다. `border.damage-hearts`와 `border.damage-interval-seconds`로 영역 밖 피해를 설정하며, `/aw status`로 현재 폭과 진행 상태를 확인합니다. 가상 보더의 모양은 정사각형이므로 `arena.width`, `arena.height`, `border.start-size`는 같은 값으로 설정합니다.

[Paper 및 2인 플레이 검증 절차](docs/phase4-playtest-ko.md) · [후속 희귀도와 보급 규칙](docs/post-phase4-design-ko.md)

증강 선택은 기본 5분 간격으로 열리며 `/aw augment`로 다시 열 수 있습니다. 경험치 레벨로 희귀도를 정하며 경험치는 소비하지 않습니다. [초기 증강 12종·수치·검증 절차](docs/phase5-augments-ko.md)
