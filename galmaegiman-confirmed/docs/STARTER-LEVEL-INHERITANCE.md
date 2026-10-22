# 임시 갈매미 레벨 보관·같은 종류 계승 — 추가 로컬 delta

사용자 확정: 임시 갈매미 레벨을 유지하고 다음 **같은 종류** 갈매미에 계승. 기존 15일 뒤 레벨 소멸 설명은 이 정책으로 대체한다.

- StarterLoanLevel: receipt ID, 소유자, 실제 종 ID, 임시 owned ID, 최고 서버확정 레벨, 마지막 전투 session, 첫 영구 recipient ID/연결시각을 StorySave와 별도로 보존.
- 임시 지급 transaction에서만 archive를 생성. 실제 native 전투 정산에서 캡처한 fighter에 XP를 계산한 뒤 별도 `{ownedId,level}` checkpoint로 기록. 일반 `/story` 데이터나 collection grant, 클라이언트 XP에서 archive를 추출하지 않는다. 기존 battle progression receipt 내부여서 재시도 중복 정산 없음.
- 캡처된 활성 전투가 만료 이후 끝나도 정상 checkpoint. 만료 정리/reset은 archive를 삭제하지 않는다. 관리자 체험 모드의 임시 성장은 archive에도 StorySave에도 남기지 않아 정상 모드로 돌아와 세탁할 수 없다.
- 임시 지급 이후 **처음 새로 획득하는 동일 characterId 영구 개체 1마리**에 한 번 연결한다. 이미 보유 중이던 영구 개체를 임의로 선택/교체하지 않는다. 임시가 아직 유효할 때 영구본을 얻어도 연결하며, 이후 임시 훈련의 서버확정 레벨 증가도 동일 recipient에 반영한다.
- recipient가 소모되어도 두 번째 복사본으로 재연결하지 않는다. 종이 다른 갈매미/다른 소유자/다른 복사본/환생의 inheritedFrom만 같은 개체에는 적용하지 않는다.
- 신규 roster를 만들 때 `max(자신의 레벨, 보관 레벨)`을 적용. 레벨만 이전하고, 증가한 경우 XP는 0부터 시작한다. 영구본이 더 높으면 레벨과 자기 XP를 그대로 유지한다. 승진 단계/배운 스킬/증강/소모품/재화는 이전하지 않는다. 현재 성장 상한 60을 적용한다.
- 영구 개체의 저장 성장 전체를 덮어쓰지 않는다. 원래 캡처된 활성 전투도 중간에 바꾸지 않는다. 다음 상태/새 전투 roster에 level floor를 반영한다. 랭크/레거시 고정레벨 정규화는 기존대로 유지하고, 일반 PvP·스토리에 실제 성장레벨 반영.
- 일반 grant(모집/조합/탐험/교환/튜토리얼)와 progression transform 신규 permanent 생성 경로에 binding hook을 넣었다. 실데이터 후보는 상위 D/T이고 transform 결과는 현재 하위등급이므로 대부분 hook은 no-op이다. admin snapshot restore는 새획득으로 취급하지 않는다.

## DTO/UI
GET starter-loans에 `levelArchives:[{sourceOwnedId,characterId,level,recipientOwnedId:number|null}]` 추가. 내부 `webStoryUnits` 결과에 `loanLevelFloor` 추가 (native `state.units` 별도 필드는 아님); native 상세/일반 PvP는 계산된 level을 사용한다.
안내: “기간이 끝나도 레벨은 보관되며, 다음에 얻는 같은 종류의 영구 갈매미 1마리에 이어집니다.”

## 검증
- 이전25개 + 신규7개 = 32개 집중검사. 실제 production webNativeCommit/saveStory 경로를 실행하는 회귀검사 포함.
- 만료 후 획득, 만료 전 연결+추가훈련, 반복획득/재시도, 높은 영구레벨, 다른종/다른소유자, 레벨상한, reset/클라이언트 위조/만료정리, 관리자 체험 세탁 차단, 캡처된 활성전투 불변 확인.
- 집중 Node 32 PASS, 별도 disposable SQLite transaction/계정삭제 8 PASS, 통합 Prisma generate/전체 tsc PASS. 전체 DB suite 533/533 PASS이며 Android 전체 타입검사·기기 통합검증 및 운영 배포 승인은 별도다.
- 이 delta는 /workspace/shared/integrated-v2-review/source/galmaegiman-confirmed 읽기 snapshot 위에 포팅한 추가 패치. V2/소식 기존 변경을 덮어쓰지 않는다. 게시·실지급·DB 실행·APK 없음.
