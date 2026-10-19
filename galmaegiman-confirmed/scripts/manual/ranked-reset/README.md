# 전체 점수 0 초기화: 별도 수동 유지보수 도구

상태: 코드·테스트를 작성한 초안입니다. 실제 DB, 프로젝트, 테스트, 백업, 리셋을 실행하지 않았습니다. v1 파일은 변경하지 않습니다. 이 도구를 CI, 배포, 서버 시작, Prisma migration에 연결하지 마세요.

필요 환경: Python 3.10 이상, Python 표준 sqlite3 모듈, Unix 파일 시스템. 외부 패키지를 설치하지 않습니다. 백업 SHA-256은 스트리밍으로 계산하며 Python 3.11 전용 API를 사용하지 않습니다.

## 바꾸는 값

- 모든 Player.rankPoint → 0
- 모든 기존 RankedProfile.tierIndex → 0 (Bronze), lp → 0
- RankedProfile.promotionActive → false, promotionWins / promotionLosses → 0
- 해당 RankedProfile.version → 기존 값 + 1, updatedAt → 초기화 시각

랭크 프로필이 없는 Player에게 새 프로필을 만들지는 않습니다. 나중에 생성되는 새 프로필은 기존 정책대로 Bronze 0에서 시작합니다.

## 그대로 두는 값

- Player의 rankWins/rankLosses 및 모든 재화·계정·유닛 정보
- RankedProfile의 wins/losses/games/draws/rankedMmr/casualPriorOffset
- CasualHumanRating의 MMR 및 일반전 기록
- RankedMatch, RankedSettlement, CasualHumanSettlement, RealtimeRoom, 요청·경기 이력과 과거 영수증

따라서 이 작업은 전적/시즌/숨김 MMR 초기화가 아닙니다. 과거 영수증에는 초기화 이전 점수가 그대로 남습니다. 과거 경기 재조회는 영수증을 다시 보여줄 수 있지만 동일 경기의 점수를 또 지급하지 않습니다. 보존되는 MMR 때문에 초기화 직후에도 매칭 실력값은 모든 사람에게 동일하지 않습니다.

## 적용 전 필수 조건

1. 모든 웹/API 서버, 매칭 worker, scheduled job, 관리 도구, SQLite를 쓰는 기타 프로세스를 유지보수 상태로 중단합니다. 여러 인스턴스가 있으면 모두 중단합니다. HTTP 요청 유입만 막고 worker를 계속 실행하면 안 됩니다.
2. 경기와 대기 상태를 정상 종료하고 다음 상태가 없는지 확인합니다. 이 스크립트는 자동 종료/삭제/청소하지 않습니다.
   - RealtimeRoom.state가 ACTIVE 또는 PREPARING
   - RankedMatch.state가 ACTIVE
   - RealtimeActivity의 모든 행 (만료돼 보여도 남아 있으면 거절)
   - 아직 유효한 WAITING 요청 또는 만료 판단이 불가능한 WAITING 요청
3. 배포에 사용 중인 실제 SQLite 파일 경로를 확인합니다. 환경변수나 기본 상대 경로는 자동으로 읽지 않습니다.
4. 백업을 저장할 기존 디렉터리를 준비하고 충분한 여유 공간을 확인합니다. 원본과 별도 디스크/오프사이트 보관은 추가 운영 절차로 수행합니다. 백업에는 계정 데이터가 포함되므로 공개 경로에 두지 않습니다.
5. 기존 스키마 및 이 도구의 필드 목록을 검토합니다. 필수 테이블/열이 없거나, Player/RankedProfile에 예상하지 못한 UPDATE 트리거가 있거나, version이 Prisma Int 증가 범위를 벗어나면 멈춥니다. WAITING leaseUntil의 정수 시간은 Unix ms로 해석하며 Unix 초처럼 보이는 값은 추측하지 않고 거절합니다. 스키마 생성·마이그레이션·트리거 해제는 하지 않습니다.

## 수동 명령 예시

아래 경로는 예시입니다. 운영 DB 경로를 확인한 사람이 직접 치환해야 합니다.

점검만 하기 (기본값, 백업도 생성하지 않음):

```sh
python3 reset_all_points.py --db /absolute/path/to/production.db
```

실제 적용 (새 고유 백업 이름 사용):

```sh
python3 reset_all_points.py \
  --db /absolute/path/to/production.db \
  --backup /private/backups/production-before-points-zero-20261017T073000Z.sqlite3 \
  --apply --confirm RESET_ALL_POINTS_TO_ZERO
```

확인 문구는 수동 오작동 방지 장치입니다. 백업 파일이 이미 있으면 덮어쓰지 않고 실패합니다. 읽기 전용 점검을 통과했더라도 적용 시 BEGIN IMMEDIATE 안에서 상태를 다시 검사합니다.

## 백업과 트랜잭션

- BEGIN IMMEDIATE를 먼저 얻어 다른 SQLite writer를 막습니다.
- 잠금 안에서 모든 활성 상태, 스키마, 원본 integrity_check를 재확인합니다.
- 아직 UPDATE하기 전에 별도 읽기 연결로 SQLite backup API를 실행합니다. 단순 파일 복사가 아니므로 WAL의 커밋된 기록도 포함됩니다.
- 백업 파일은 O_EXCL로 생성하여 충돌/덮어쓰기를 방지하고 권한을 0600으로 제한합니다.
- 백업 integrity_check와 fsync, SHA-256 계산까지 성공한 뒤에만 원본의 점수 필드를 UPDATE합니다.
- 두 테이블 UPDATE, 결과 점검, COMMIT은 하나의 트랜잭션입니다. COMMIT 이전 오류는 양쪽 모두 rollback합니다.
- 실패한 백업 파일은 자동 삭제하지 않습니다. 백업 검증이 실패했다고 표시된 파일을 정상 복원본으로 사용하면 안 됩니다.
- 출력의 applied:true와 백업 경로/해시, 변경 전후 행 개수를 저장하세요. 갑작스러운 종료 또는 COMMIT 성공 여부가 불확실한 오류가 있으면 무작정 재실행하지 말고 DB 상태를 확인하세요.

## 적용 후

서비스를 재시작하기 전에 읽기 전용 점검을 다시 실행하여 모든 점수가 0인지 확인합니다. 보존 대상 전적/MMR/영수증과 백업 해시를 확인하고 운영자가 결과를 승인한 뒤 서비스를 재개합니다. 미완료 방이나 신규 경기가 존재하면 점검이 거절되는 것은 정상입니다.

## 복원 제한

전체 백업 복원은 리셋 이후 어떠한 다른 쓰기도 발생하지 않았고 모든 DB 사용 프로세스가 중단되어 있을 때만 가능합니다. 이후 재화 변화, 로그인 기록, 새 경기, 새 계정 등 어떤 쓰기라도 있었다면 전체 백업으로 교체하면 그 새 데이터를 잃습니다. 그 경우 전체 복원하지 말고 별도 보정 계획을 검토해야 합니다.

복원이 필요하면 먼저 현재 DB와 WAL/SHM을 별도 보존하고, 모든 연결 종료를 확인한 다음 검증된 백업을 별도 임시 경로에서 integrity_check로 재확인하세요. 활성 WAL/SHM을 남겨둔 채 DB 파일만 단순 덮어쓰기하지 마세요. 실제 복원은 운영자의 별도 승인·운영 절차로 수행합니다. 이 도구에는 자동 복원 기능이 없습니다.

## 별도 격리 테스트

작성된 테스트는 tempfile의 신규 SQLite fixture만 사용하며 운영 DB 환경변수/프로젝트 DB를 읽지 않습니다. 운영 스크립트를 CI에 자동 연결하지 않으며 아래 테스트도 검토 후 별도 환경에서 수동 실행합니다.

```sh
python3 -m unittest discover -s /path/to/reset -p 'test_reset_all_points.py' -v
```

포함 항목: 기본 무변경, 백업 보존·무결성·권한, WAL 포함, 백업명 충돌/TOCTOU, 활성/준비 중 경기·활동·WAITING 차단, 잠금 후 상태 재검사, 다른 writer 차단, 백업 실패, 두 번째 UPDATE 실패 시 전체 rollback, 전적/MMR/영수증 유지. 이 테스트는 아직 실행하지 않았습니다.
