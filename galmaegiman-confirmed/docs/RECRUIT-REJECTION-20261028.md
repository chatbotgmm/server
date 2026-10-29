# 모집 불합격 환급: 서버 패치 07

기준: `085f204b13429889c631994961433a0a1f0ffb25`. 앱 캐러셀 변경과 별도 서버 패치다. 운영 배포 및 APK 빌드는 하지 않았다. Prisma 스키마 변경은 없다.

## 기존 소모 규칙

`RecruitSessions.start()`는 `offer.cost * times`를 `charge()`로 먼저 차감하고 같은 트랜잭션 안에서 후보를 생성한다. 후보 생성 실패 시 차감도 롤백된다. 현재 정책의 LOW/MID/HIGH 모집 비용은 후보당 지원서 2/6/10장이다. `action('reroll')`은 한 후보를 같은 등급에서 한 번 교체하며 기존 반값 비용 1/3/5장을 별도로 차감한다. `claim`과 `claimAll`은 이미 생성된 후보를 보유 개체로 지급하며 추가 모집비용을 차감하지 않는다. 이 시점과 금액을 변경하지 않았다.

관리자 무제한 모드는 실제 차감 없이 후보를 만든다. 과거 snapshot의 `baseCost`만으로는 실제 차감했는지 증명할 수 없다. 기존 start 영수증은 sessionId/batchId와 생성 후 credits는 남기지만 차감 전 잔액·실제 차감액·당시 관리자 무료 여부가 없다. `charge()`가 별도 감사 이벤트나 차감 원장을 만들지도 않는다. 따라서 기존 영수증을 후보 ID와 연결해도 실제 지출을 검증할 수 없다. 새 snapshot의 `paidBaseCost`는 생성 트랜잭션에서 실제 지불한 후보당 비용만 기록한다. 무료 생성에는 0을 기록한다. 기존 snapshot에 이 증거가 없으면 소급 추정하지 않는다.

## 불합격 처리

기존 `POST native/roster60/recruit/session/action`의 `action`에 `reject`를 추가한다. 요청은 `{requestId,sessionId,revision,action:'reject'}`이며 요청 본문에 소유 여부나 환급액을 추가할 수 없다.

서버는 같은 트랜잭션에서 다음 조건을 확인한다.

- 후보가 요청 계정 소유이며 현재 `PENDING`이고 revision이 일치한다.
- 후보와 같은 종족의 `CONSUMED`가 아닌 개체를 요청 계정이 현재 보유한다.
- 후보 snapshot에 실제 생성 차감 증거가 있으며 차감액이 최소 2장이다.

성공하면 `REJECTED`로 한 번만 전환하고 지원서 1장만 환급한다. 보유 개체를 삭제하지 않고 후보 개체를 지급하지 않는다. DRAW 미션 보상도 올리지 않는다. 전체 합격은 기존 `claim-all`로 남은 후보만 지급한다. 개별 claim API는 구버전 호환을 위해 유지한다. 모든 상태 전환·잔액 변경·영수증 기록은 하나의 트랜잭션에 포함된다. 잔액이 32비트 정수 상한이면 환급과 상태 전환을 모두 롤백한다.

종족 중복을 클라이언트가 표시했더라도 제출 시 다시 검사한다. 재료로 소비된 개체만 남았다면 환급할 수 없다. 다른 계정의 후보는 조회와 처리 모두 404다. 다른 requestId의 동시 불합격이나 전체 합격 경합은 후보 revision/status 조건으로 한 작업만 확정한다. 같은 requestId의 재전송은 저장된 영수증을 돌려주며 다시 지급하지 않는다.

## 응답 계약

info/read/start/action/claim-all의 정상 응답에는 현재 서버 상태가 함께 포함된다. 클라이언트는 정상 응답 후 추가 GET 없이 `pendingSessions`와 `credits`로 화면을 갱신할 수 있다.

- `pendingSessions`: 현재 미처리 후보 목록. 결과 영수증의 `sessions`와 구분한다.
- `credits`, `balances.applicationTickets`: 현재 실제 잔액.
- 각 후보의 `ownedSpecies`, `canReject`, `rejectRefund`(1 또는 0), `rejectReason`.
- `rejectReason`: 가능하면 빈 문자열. 그 외 `NOT_PENDING`, `NOT_OWNED_SPECIES`, `PAYMENT_PROOF_UNAVAILABLE`, `FREE_GENERATION`, `REFUND_COST_TOO_LOW`.
- 새 거래의 `receipt`: requestId, action, chargedTickets, refundedTickets, grantedOwnedIds, sessionIds.
- 불합격 성공의 행: `status:'REJECTED'`, `refundedTickets:1`, 일치하는 sessionId.
- 불합격 receipt의 `replayed`: 최초 commit 응답은 false, 저장된 영수증 재전달은 true.

재전송 응답에서도 경제적 영수증은 유지하고 현재 잔액·미처리 목록을 다시 읽어 전달한다. 과거 start 영수증의 `sessions`는 당시 결과이므로 화면 목록으로 사용하지 않는다. 예전 영수증에 새 receipt 필드가 없으면 임의로 채우지 않는다.

파쇄와 +1 연출은 앱이 현재 화면에서 새로 보낸 요청의 성공을 확인했을 때만 실행한다. `replayed:true`, 이전에 불확실했던 요청의 재시도, 실패·통신 오류에는 연출하지 않는다. 서버 receipt만 보고 cold reconnect에서 연출하면 안 된다.

50개 후보의 기존 전체 합격 요청은 기본 2048바이트 제한을 넘으므로 그 경로의 bodyLimit만 8192바이트로 맞췄다. 배열 최대 50개, UUID와 revision, strict 필드 검사는 유지한다. 나머지 경로 제한은 그대로다.

## 검증

신규 `roster60-recruit-reject.integration.test.ts` 18개와 기존 모집 관련 4파일 44개, 합계 5파일 62개가 새 임시 SQLite에서 통과했다. 이 중 3개는 각 등급의 전체 허용 개수 1~50개를 실제 생성·차감·불합격·환급해 150조합과 3,825회 환급 트랜잭션을 검사한다. TypeScript `tsc --noEmit`도 통과했다. 원본 DB와 운영 서버는 사용하지 않았다. 원시 실행 로그와 JUnit을 동봉한다.

검증에는 같은 후보 재처리·동일/상이 requestId 동시 요청·계정 병렬과 외부 후보 차단·재접속 영수증·전체 합격 경합·보유 종족 변화·소비된 개체·관리자 무료 생성·과거 지급 증거 누락·0장 정책 거부·환급 안전 하한·재뽑기 후 환급 1회·정수 상한 롤백·영수증 기록 장애 롤백·실제 3등급 비용 유지·불합격 반복의 잔액 비증가·인증 API strict 검증·49개 남은 후보 전체 합격이 포함된다.

불합격만 반복하면 후보 N개 생성 시 기존 비용 2N/6N/10N장을 내고 최대 N장을 돌려받으므로 지원서가 늘지 않는다. 무료/legacy 후보는 환급을 차단한다. 새 생성 기록 없이 기존 후보를 환급 가능하게 만드는 데이터 이관은 포함하지 않는다. 기존 합격에 따른 일일 미션 보상 정책은 변경하지 않았다.

남은 검증: 앱 캐러셀과 실제 연결, 네트워크 불확실 후 복구 UI, 파쇄 연출의 실기기 프레임 비용. 서버 반영이 필요한 기능이며 앱만 배포해서 완료할 수 없다.
