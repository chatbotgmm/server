# 제품 모집·도감 연결 / EC2 읽기 전용 조사

기준 e3d7500. 원본 워크북/approved:false 보존. 새 테스트 profile만 사용한다.

## 실제 연결
- MainActivity.collection/draw → NativeRoster60Library → 인증 native/roster60/collection, recruit GET/POST.
- 도감 한 장 정면 이미지, 등급/보유 필터, 이전/다음, 누르는 동안 설명과 해제/취소 처리. 화면 전체 스크롤 대신 정보 칸만 스크롤. 60종 이름·5스탯 Lv1·적합도(공격)·방어·습득 기술 표시. 로컬 승인 manifest 파생 아트만 사용; 개별 디자인의 미확정 여부는 기존 art index에 유지한다.
- 모집 LOW 기존 지원서2장/커먼70%/언커먼30%, 등급 내 균등. 기존 지원서 claim API 재사용. 현재 UI는 1회씩, API1~50회.
- 모집 차감·Roster60Owned 영구 생성·영수증 같은 SQLite transaction. 같은 requestId 재요청 결과 고정, 다른 payload 거절. 실패시 rollback. Android 서버+계정별 요청을 먼저 저장하여 재진입/통신 실패시 재전송. 취소는 요청하지 않음.
- 기존 관리자 무제한 모드는 isAdmin&&adminMode일 때 비용면제 유지.
- 새 시작 계정은 기존 web/chat 모집 private draw에서도 거절. 옛 개체를 새 개체 대신 지급하지 않음.
- additive Roster60RecruitReceipt migration 작성. 격리 테스트DB에만 적용, 운영 미적용.

## 부모에게 필요한 최소 결정
|항목|기존 제품|현재 새 흐름 / 필요한 결정|
|하급|2장 COMMON70 UNCOMMON30|유지 가능, 연결|
|중급|3장 COMMON20 UNCOMMON50 SPECIAL30|SPECIAL 없음, 비활성. SPECIAL→신규 RARE 매핑 승인 필요|
|고급|5장 COMMON10 UNCOMMON15 SPECIAL54 RARE20 LEGEND1, LEGEND100회 천장|SPECIAL→신규 RARE, 옛RARE→신규EPIC 매핑 제안. 가격/비율/천장 유지 여부 결정 필요|
|아이템|HP35/75 회복, 해제,50% 부활; 전투당3; 공격보다 먼저 처리|가격/효과/행동순서 유지 여부, MP아이템 신설하지 않음|
|교체|살아있는 벤치로 행동소비, 공격전 처리; 구증강 예외 있음|1대1 예비 편성 수·순서 결정, 구증강 예외 자동승계 안 함|
|도망/기권|기존 story leave 즉시패배 XP0, PvP surrender멱등|새 전투도 즉시기권/XP0 유지할지 결정. 확률 도주 발명 안 함|

## APK 자산
제품테스트 org.gmm.producttest에서만 옛 upper33-v1 패키징 제외. Git 원본/기존 제품 variant 및 공유 효과 보존. e3d7500 APK 압축 기준 upper33 30.6MiB. 나머지 game-art/duel24.1MiB, thumbs11.8MiB, registration28.4MiB는 옛 BattleView/NativeProgression/등록증 경로 참조가 남아 있으므로 무조건 삭제하지 않는다. journal32.0MiB와 release-v1 33.9MiB는 공통 홈/화면 테마가 참조한다.
100MiB 목표는 아직 미달. 다음은 기존 편성/획득 등록증/이전 전투 진입을 모두 새 로스터로 대체한 뒤 해당 legacy asset groups를 test variant에서 제외하고 공유 배경 중복을 분석해야 한다. 전송분할/재포장 우회 없음.

## 기존 EC2 구조와 분리안 (실행하지 않음)
README: gmm-server(systemd), gmm-named-tunnel(Cloudflare→gmm.dpdns.org), gmm-backup.timer. scripts/named-tunnel.sh는 PORT 또는3000을 사용. 실제 EC2 상태/현재 .env/인증값을 읽거나 접속하지 않았다.
scripts/update.sh는 fetch/pull→setup --prepare→gmm-server stop→setup --apply(DB백업/반영)→start 흐름. 테스트 분리를 위해 그대로 실행하면 운영에 영향을 준다.
같은 EC2의 별도 checkout, 별도 systemd서비스, 별도 PORT·SQLite·환경파일·세션secret·인증 redirect 설정·로그/백업 대상이 최소 분리안. 기존 서비스 교체 또는 분리 서비스 선택은 사용자 답변 대기. 외부 URL/터널라우팅 또한 승인·확인 전 정하지 않는다. APK URL은 기존 loopback 유지; 휴대폰에서 바로 접속 가능한 테스트 서버는 아직 없다.

실기기/에뮬레이터 렌더·실제 EC2 연결 검증은 하지 않았다. 신규 UI PNG Library403을 재시도하지 않았다. 외부 업로드/push/배포 없음.

## 검증 결과
- 서버 전체 회귀 55파일/591테스트 통과. 이후 동시요청·실패롤백/기존 모집 차단 시험을 보강한 관련 API2파일/6테스트 모두 통과.
- 실제 buildApp 인증 E2E에 collection/recruit 경로 추가; 기존 시작→튜토리얼→선택권→전투→조합→환생 검증 유지. 가속 성장·추가 조합재료는 격리DB fixture이며 실제 플레이로 모든 성장을 획득했다는 의미 아님.
- Android12테스트 통과. 640×320 headless 자동 레이아웃/상태 검증, 실기기 화면 확인 아님. TypeScript/web build 및 clean debug APK build 통과.
- APK219,303,600bytes (209.14MiB), org.gmm.producttest /1001/2.2.4-native. 이전251,390,785bytes 대비32,087,185bytes 감소. 100MiB 목표 미달.
- SHA256 3ff05cd5d2027d794e4143124fe52a692387caccfc4a22e6ea140b72bce691b1. debug서명 verify 통과, 인증서 SHA256 5c4a018046c682d7ff6d300cdce9ecb2ba87f1e3e5cd66f69e63f4814eacddd0.
- APK upper33 항목0, roster60-art121개(120뷰+index) 확인. incremental packaging 빈영역을 제거하려고 공식 Gradle clean assembleDebug로 재생성했다(재포장 아님).
- URL http://127.0.0.1:18765 유지. 별도 테스트 서버 연결 전 휴대폰 단독 제품플레이 불가. 기존 오프라인 검토판과 다르다. 실제org.gmm앱 덮어쓰기/삭제 없음.
