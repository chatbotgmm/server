# GameMaker 전환 인계 (2026-11-07)

사용자 결정(2026-11-07): Android 앱 전체를 GameMaker Studio 2로 대체한다(대상: Android·Windows·HTML5). 웹게임(/game60) 작업은 2단계까지에서 멈춘다.
GameMaker 프로젝트는 저장소 `gms2/`(브랜치 claude/charming-bardeen-sct1b0)에 둔다. 사용법은 `gms2/README.md`.

## 서버 연결(확인한 것)
- 로그인: `POST /my/api/login {code}` + 머리글 `x-gmm: 1`, `x-gmm-app: 1` → `{ok, token}`(91자). 이후 `x-gmm-s: <token>`(쿠키 대신). 열쇠 없으면 401.
- 현재 증강 전투: `x-gmm-roster60-augment-capability: current-v3`, `x-gmm-roster60-augment-model: roster60-current-v1`(없으면 전투 생성 409 ROSTER60_AUGMENT_CLIENT_UPDATE_REQUIRED).
- 조합 발견 이름·그림: `x-gmm-craft-discovery: 1`.

## 실시간 대전(3단계용, 서버 복제본에서 확인)
- `GET pvp/options`(편성 후보, rankEligibility: 방범대장 Lv.60·출전 Lv.60), `POST pvp/party {ownedIds, mode}`, `POST pvp/join {requestId, mode}` → `status: waiting`, `aiAvailableAt`(일반전은 30초 뒤 AI 상대).
- `GET pvp/current` → `{status:'matched', battle:{status:'ACCEPTING', deadline, opponentKind:'AI'|..., state}}`. `POST pvp/accept {requestId, battleId, revision, accept:true}`.
- 전투 상태는 스토리 전투와 같은 모양(`state.fighters`, `reserves`, `augmentChoice`, `opponentIntel`). 행동 `POST pvp/action {requestId, battleId, revision, action}`(스토리와 같은 action 종류), 상대를 기다리면 `waitingForOpponent`. 턴 제한 약 30초(deadline), 놓치면 시간 초과 패배(DISCONNECTED).
- `POST pvp/settle {requestId, battleId, revision}` → `settlement {winner, reason, outcome, xpAwarded, rating, commanderReward}`.
- **요청 제한**: pvp/* 는 사람마다 1분 60번. 상태 확인은 2초 간격(앱과 같음)으로 해야 한다(빠르게 하면 429로 막혀 시간 초과 패배가 날 수 있음).

## 웹게임에서 받은 요청(아직 안 함)
- 조합 목표: 도감·조합에서 버튼으로 목표를 정하면, 조합 화면·편성에서 재료 갈매미에 같은 색 표시(필요 레벨까지 키우게 도움). GameMaker 2단계에 넣는다.
