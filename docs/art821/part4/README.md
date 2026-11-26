# PART 4 · 경기 기록과 운영

[화면 비교](history-death-admin.png) · [전투 귀속 표](attribution-ko.md) · [데이터 형식과 복구](storage-ko.md) · [84종 기본값과 발동 감사](../../phase82-balance-audit-ko.md#part-4--기본-설정과-실제-발동-경로)

## 서버 명령

| 명령 | 결과 |
| --- | --- |
| `/aw history` | 본인 경기 목록과 이전·다음 페이지 |
| `/aw history last` | 본인의 가장 최근 저장 결과 |
| `/aw history <match-id>` | 본인이 참가한 경기의 결과 |
| `/aw death` | 실제 사망 확정 이후 유효기간 내에 열 수 있는 개인 보고서 |
| `/aw admin history <match-id>` | 관리자의 경기 요약 조회 |
| `/aw admin stats augment steel_heart players=16 days=30 round=2` | 기간·경기 인원·회차로 선택 통계를 조회한다. 나머지 비율은 선택한 경기 전체 기준이다. |
| `/aw admin stats group rarity GOLD` · `/aw admin stats group family 금속` | 희귀도·전투 계열별 조회 |
| `/aw admin stats export` | 익명 집계와 회차별 행을 백그라운드에서 CSV로 생성 |
| `/aw admin dashboard` | 운영 월드·배치 상태, 복원 및 기록 대기, 팩 검사, HUD·연출 집계와 최근 평균 틱 |
| `/aw admin recover history` · `/aw admin recover stats` | 원장을 재검사하고 이번 서버 프로세스의 미저장 기록을 재시도 |
| `/aw admin recover snapshots` | 대기 상태에서 온라인 플레이어의 기존 복원 기록으로 재시도 |

`version=8.2`로 이전 집계를 조회할 수 있다. 이전 형식에 없는 회차별 선택과 진화 대상 분모는 계산 불가로 표시한다. 결과 조회와 CSV에는 현재 진행 중인 경기 자료가 없다.

## 오프라인 화면과 예시

상단 이미지의 이름과 수치, 아래 행은 메뉴 배치와 스키마 설명을 위한 가상 데이터다. 운영 집계에 포함되지 않는다.

| 경기 | 유형 | 판정 | 내 순위 | 체력 손실 / 회복 |
| --- | --- | --- | --- | --- |
| 예시 A | NORMAL | WIN | 1 | 11.0 / 3.0 HP |
| 예시 B | PRACTICE | CANCELLED | — | 4.0 / 미확인 |

`REAL_CLIENT_VERIFIED=NOT_RUN`. 실제 Paper 프로토콜 검사에서 인벤토리의 데이터와 입력 잠금은 검증할 수 있다. 그래픽 렌더링과 메뉴 배율은 별개다.
