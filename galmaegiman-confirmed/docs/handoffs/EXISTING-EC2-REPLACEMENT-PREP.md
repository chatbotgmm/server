# 기존 EC2 교체 준비 — 실행 전 검토

결정: 기존 EC2의 gmm-server를 새 버전으로 교체하고 https://gmm.dpdns.org 유지. 별도 테스트 서비스/새 호스팅 없음. 이 문서는 배포 완료 기록이 아니다. 운영 중단·운영DB 반영·초기화·원격 push/merge·외부 업로드 미실행.

## 로컬 구성
- scripts/build-product-domain-debug.sh: 별도 org.gmm.producttest, versionCode1002, debug 서명, 고정 gmmServerUrl=https://gmm.dpdns.org. 공식 Gradle clean assembleDebug만 수행. release 키 환경값은 하위 프로세스에서 제거한다.
- 기존 org.gmm Play앱을 덮어쓰거나 제거하지 않는다. 제품기능/인증 API 연결판이며 오프라인 fixture APK가 아니다.
- APK의 URL 설정은 서버를 배포하거나 시험 profile을 운영에 활성화하지 않는다. 현재 도메인에서 신규 API 제공 여부와 인증이 확인되기 전 실제 플레이 가능하다고 하지 않는다.
- 새 서버 profile은 ROSTER60_PROFILE=five-stat-device-test-v1을 명시해야 한다. 이 승인된 시험 수치와 최종 운영 밸런스는 구분한다. 해당 환경변수는 이번에 운영에 설정하지 않았다.

## 저장소로 확인한 배포 구조
README.md: gmm-server, gmm-named-tunnel, gmm-backup.timer. Cloudflare 고정 터널이 기존 도메인을 로컬 PORT(기본3000)에 전달한다. 문서상 앱 폴더 ~/gmm/galmaegiman-confirmed. 실제 systemd WorkingDirectory/ExecStart/환경파일/현재 커밋/DB크기/활성 세션은 EC2에서 미확인.
update.sh: 현재 체크아웃 브랜치 fetch/pull → setup --prepare → 서비스 stop → setup --apply → start. prepare도 init-env/npm ci를 실행한다. apply는 백업→prisma db push→옛145종 seed를 실행하므로 이번 새 개편에는 그대로 실행하지 않는다. script 실패 복구는 준비 단계 이전 코드 reset, DB apply 실패시 서버 재시작 수준이며 전면적 자동 DB rollback이 아니다.

## DB 호환성 / 복구 리허설
scripts/rehearse-roster60-upgrade.py는 로컬 Git의 cde2152 스키마와 현재 스키마를 비교한다. 이것이 현재 운영 스키마와 같다고 가정하지 않는다.
- 임시 SQLite 생성, 계정/지원서73/땅콩4 fixture 저장, 독립 백업.
- 차이: Roster60State/Owned/CraftReceipt/RebirthAccount/RebirthReceipt/Battle/BattleReceipt/Home/RecruitReceipt 9개 테이블 및 인덱스 추가. 이전 테이블 삭제/컬럼삭제 없음.
- upgrade SQL 적용 후 기존 계정·재화 행 동일, integrity_check/foreign_key_check, 잘못된 소유자 FK 거절, 별도 DB 복구 확인.
- 실제DB/.env를 읽거나 변경하지 않음. 산출 SQL은 이전 스키마 가정의 검토 자료이고 운영 적용 명령이 아님.
- prisma/migrations에는 신규 일부만 있고 앞부분은 prisma/manual로 분산돼 있다. 기존 DB의 migration baseline 확인 없이 prisma migrate deploy로 전부 해결된다고 가정하지 않는다. 실제DB의 안전한 복사본에서 현재 스키마 diff를 다시 만들고 검토해야 한다.
- 기존계정/인증/결제/옛개체 데이터는 보존된다. 새 계정 성장 테이블과 별개이며 기존 데이터 초기화를 수행하지 않는다. 계정 전환 시작 정책은 따로 확인해야 한다.

## 실행 전 필수 확인 순서 (아직 실행하지 않음)
1. 승인된 기존 EC2 접속 경로/인스턴스 확인, 서비스 정의·실제 체크아웃 커밋·현재 DB 스키마/버전·디스크 여유·백업 정책 확인. 비밀은 출력/산출물에 넣지 않음.
2. 배포할 정확한 로컬 커밋/서버 dist/lockfile/카탈로그/정적자산 manifest 해시 고정. 현재 변경은 원격에 게시하지 않았으므로 update.sh의 fetch/pull만으로 받을 수 없음.
3. EC2의 기존 환경/세션키/로그인 redirect와 기존 도메인 보존. 시험 profile 운영 활성화 여부와 불완전 기능의 공개 범위 확인.
4. 서비스 중단 시각/예상 영향 승인: gmm-server 중지 동안 API/로그인/전투 요청 실패 가능. 진행중 옛 전투/새 세션 정리 방침 및 클라이언트 점검 안내 필요.
5. 중단 후 scripts/backup.sh의 VACUUM INTO + integrity_check 방식으로 일관된 DB 백업, 설정/배포 코드·dist/정적파일 보존. settings.env는 제한 권한으로 보관하고 전달 ZIP에 넣지 않음. 백업경로는 자동 current-* 정리 대상과 구분해 보존할 것.
6. 복사본에서 검증한 추가 SQL만 transaction 반영, 원본 DB 삭제/초기화 및 옛 seed 실행 없음. dependencies/Prisma client/dist를 같은 커밋으로 맞춤.
7. 기존 gmm-server 재시작. loopback health, 기존 HTTPS health, 실제 로그인, roster API, 시작→튜토→우편→모집→전투/정산→조합/환생을 지정된 검증계정으로 확인. 현재는 실행 미승인.
8. 장애시 서비스 중지→새 DB/로그 먼저 보존→이전 코드/환경 복원. DB복구가 필요하면 검증된 snapshot을 사용하되 교체 후 발생한 정상 이용자 변경을 잃을 수 있으므로 자동 덮어쓰기 금지. WAL/SHM은 중지·checkpoint 상태를 확인해 함께 다루며 임의 삭제하지 않음.

## 배포 차단/남은 결정
- 중급/고급 모집 SPECIAL 매핑 미정, 아이템/교체/기권 비활성. 지원서 가격/확률/천장을 임의 변경하지 않는다.
- 새 증강체·종족시너지 미정. 기존 편성/연습/PvP/상점 일부와 웹/채팅은 옛145종 흐름이 남아 있다. 구기능 전체를 새60종으로 교체한 완성판이 아니다. 공개 전 접근 제한 또는 통합 범위를 결정/구현해야 한다.
- 기존 계정은 새 튜토리얼 start 이전 Roster60State가 없다. 전체 계정 전환/옛 데이터 초기화를 임의 실행하지 않는다.
- review-auth.ts의 심사용 로그인 route allowlist는 신규 roster60 API가 없어 해당 로그인 경로는 거절된다. 일반 세션 인증 E2E와 구분. 실제 검증 로그인 경로를 정해야 한다.
- 레벨상한 초과XP/조합 환생계승 정책 미정은 명시 거절. 시험안 승인과 이 정책 승인은 별개.
- APK 약209MiB,100MiB 목표 미달. 새PNG 아이콘403 차단은 미해결, 기존 벡터 유지. 다른 화면이 참조하는 레거시 자산 제거는 후속 작업.

## 접근 상태
현재 도구에 EC2/SSM 전용 커넥터 없음. 로컬 ssh 실행파일 있음, aws CLI 없음. /workspace/.aws 디렉터리 존재만 확인했으며 내용/비밀은 읽지 않음. EC2에 연결 시도하지 않았으므로 인증 성공/거절/네트워크 차단을 단정할 수 없다. 실제 접속 경로의 유효성은 미검증이며 거절된 작업은 없다. 신규 권한/토큰/키 생성 없음.

## 이번 준비 검증 결과
- 서버 전체55파일/593테스트 통과 (격리 SQLite, 운영 미접속).
- HTTPS build configuration으로 Android 관련12테스트 통과. 실기기/에뮬레이터 실행 미검증.
- 공식 Gradle clean assembleDebug 통과; 생성 BuildConfig.SERVER_URL=https://gmm.dpdns.org 확인.
- APK219,303,528bytes, org.gmm.producttest,1002/2.2.4-native. SHA256 3d1731bbd0b6f89063d35ee35c77493bf37c0420010482d8215d5a6902bdb5d5.
- apksigner verify 통과, Android Debug 인증서 SHA256 5c4a018046c682d7ff6d300cdce9ecb2ba87f1e3e5cd66f69e63f4814eacddd0.
- 로컬 캐릭터121항목(120뷰+index),옛 upper33 0개 유지.100MiB 이하 경량화는 여전히 미완료.
- 리허설9테이블추가/계정·재화보존/FK/백업복구 assertions 모두 통과. 운영 스키마와 실제배포 E2E는 미검증.
