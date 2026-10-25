# 승인된 게임 진행 초기화 (2026-10-25)

사용자 승인 범위: 로그인 식별·연결·심사용 인증·관리자 권한은 보존하고,
심사용 계정 포함 모든 게임 진행을 초기화한다. 새 u01 한 마리와 지원서200장으로
새 튜토리얼을 시작한다. 사용자는 실제 결제/유료 권리가 없다고 확인했다.
서버/앱 전체 교체, DB 스키마 폐기, 구 seed 실행, 인증키/비밀번호 생성은 범위가 아니다.

## 대상과 보존
- 실제 대상: /home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db
- 실행 릴리스: /home/ubuntu/gmm-releases/recruit-eb541ce-20261025T071013Z-265062/galmaegiman-confirmed
- Player ID/identity, 닉네임, 생성일, 초대 코드, isAdmin/adminMode, KakaoLink,
  ChatAppUser, WebLogin, AuditEvent, AppMeta/정적 캐릭터 정의를 보존한다.
- 댓글/좋아요는 별도 콘텐츠이므로 보존한다.
- reset.ts의 명시적42테이블 게임 진행을 정리한다(FK 없는 PvP 포함).
- 지원서: 기존 신규가입 설정 tickets.welcome 사용, 승인값200과 다르면 중단.
- 새우깡/땅콩/천장/승패0, rankPoint1000, 지급 타이머null, 배지/초대인null,
  편성/화면안내기록빈값. 구 임시스타터 비활성화.
- RosterOnboarding.start()를 동일 트랜잭션에서 호출하여 계정별u01/XP0/환생0/
  미완료 튜토리얼 생성. 선택권은 전투 완료 증명 전 지급하지 않는다.
- 쿠폰 사용기록도 초기화하므로 기존 게임 쿠폰을 다시 사용할 수 있다.
- 심사용 Player ID/identity와 인증 설정은 그대로다. 게임 상태만 새로 생성한다.
  따라서 Play Console 로그인 안내 변경이나 새 비밀번호 발급은 필요하지 않다.
- 활성 adminMode/AdminSnapshot 또는 결제 관련 AuditEvent, 미검토 테이블 발견 시
  중단한다. 사용자 결제 없음 확인과 충돌하는 실제 데이터는 자동 삭제하지 않는다.

## 도구
- cli.ts: 격리복사본용. 기본dry-run, 명시확인·fingerprint·백업 후 apply.
- production.ts: 기본plan. 실제고정DB, systemd inactive/PID0, 별도확인문,
  지정백업경로·무결성·동일fingerprint를 검사한 뒤 apply. verify는 읽기전용.
- db-ops.py: 일관 SQLite backup, 복원 리허설, 실패 시 같은DB에 backup API로 복원.
  DB/WAL 파일을 rm하지 않는다. 서비스정지와 다른DB열린프로세스 부재도 검사한다.
- deploy-ec2.sh: 아래 운영 절차를 묶는다. 사용자ssm-user가 sudo-n으로 실행한다.

## 운영 절차 및 자원
1. 기존 실행경로/DB환경/profile/소스 기준 일치, 최소512MiB 여유 확인.
2. 서버 소스 복사 총량128MiB 상한. 기존node_modules/public/app을 빌드용으로
   참조하되 설치/패키지생성/공유패키지 쓰기 없음. 전체archive/art복사/npm ci 금지.
3. 현재서비스를 유지한 채 TypeScript컴파일+관련격리34시험 실행. 최소256MiB 여유 확인.
4. gmm-named-tunnel.service 및 gmm-server.service 정지(일시접속중단).
5. 대상테이블/건수dry-run 출력→차단조건검사→DB일관백업+복원리허설.
6. 소스4개와dist모듈4개만 백업/교체. 인증설정/economy.yaml/systemd설정은 불변.
7. 모든게임초기화+새스타터+감사표식은 한 트랜잭션. 인증/보존데이터·초기값 검증.
8. read-only DB + 프로세스내 HTTP injection으로 새상태와모집비용2/6/10,
   리롤1/3/5, 구세션401 확인. 일시서명토큰은 메모리에서만 사용하며 출력/저장하지 않는다.
   심사용 설정ID 일치를 검사하며 비밀번호나 새 영구접근권한을 생성하지 않는다.
9. 서비스시작/health/ready 통과 후에만 터널을 재개한다.
10. 트래픽개방전 실패하면 서비스를 멈추고 DB+소스+dist를 복원해 검증한다.
    검증완료 후 터널시작만 실패하면 이미검증된DB를 다시 되돌리지 않고 정지상태보고.
    completed.txt가 있으면 초기화를 재실행하지 않는다.

백업/로그는 /home/ubuntu/gmm-deploy-backups/game-progress-reset-<시각>-<PID>에 남긴다.
원본빌드/DB/다른자산을 정리하지 않는다. disk부족이면 삭제하지 않고 중단한다.

## 세션과 이전 앱 요청
일반세션은 기존서명만료시각-고정30일로 발급시각을 판정한다. reset표식시각과
같은초까지401거절. 키/비밀번호는 불변이며 앱은 재로그인해야 한다.
심사세션/앱교환코드는 기존메모리전용이므로 필수정지/재시작으로 폐기된다.
현재Android는401에서 해당토큰만 비우고 대기모집·조합요청을 토큰해시로 구분한다.
새로그인에서 이전큐를 자동재시도하지 않는 코드계약을 검사했다. 설치APK실기기검증은 별도다.
구세션으로requestId만 바꿔도 handler전에401. 새정상인증으로 사용자가의도적으로
다시제출한 요청을 과거요청이라고 판별한다는 보장은 하지 않는다.
온라인초기화는 지원하지 않는다. 모든쓰기프로세스정지가 필수다.

## 검증 범위
전용시험은 /tmp에 가짜 일반/관리자/심사계정과 구·신게임진행을 생성해서만 실행한다.
로그인/심사authenticate 보존, 이전요청차단, 중복스타터방지, 미확인결제차단,
실패롤백, backup/restore, preserved필드비교를 포함한다.
운영EC2에서는 에이전트가 명령을 실행하지 않는다. 실제결과는 사용자 실행 후 확인한다.
