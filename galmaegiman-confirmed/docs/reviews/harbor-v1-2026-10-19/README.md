# galmaemiman-runtime-v1 홈(항구 광장) 조립 렌더 · 2026-10-19

- 렌더 방법: `R1ScreenReviewTest.homeLobby`(Robolectric, 서버 없이 고정 자료, 실제 게임 글꼴). 실기기 캡처가 아니다.
- 크기: 915×412(폰), 1006×411 + 노치 인셋(Z플립), 1280×720(태블릿 프로필).
- 숫자 검사(화면 밖·부모 잘림·48dp 미만·글자 세로 잘림·버튼 겹침): 세 크기 모두 0건.

## 계약(layout.json)과 다르게 둔 것
- 정보·설정 버튼: 계약 좌표(폰 787,280 / 태블릿 1152,660)가 「출석·미션」 버튼과 겹친다. 그래서 설정은 머리줄 오른쪽 끝에 두고, 정보 버튼은 없앴다. 대신 프로필(닉네임)을 누르면 계정 정보가 열린다.
- title-strip 9분할: 시작값(density 3)으로 48dp 높이에 그리면 모서리가 칸 전체를 차지해 글자가 테두리 위에 겹친다. 그래서 density 5(모서리 약 16dp)로 실측 조정했다.

## 미포함 자산(임의 완성하지 않음)
- `decor.podium`(받침대): 원본에 배경이 남아 묶음에서 제외됨. 투명 PNG가 필요하다. 예상 경로 `assets/decor/podium.png`.
- 홈 대표 캐릭터: 신규 그림 없음. 편성 첫 대원의 기존 정면 스프라이트(`/duel/sprites/<ID>_s0_front.webp`)를 런타임 레이어로 연결했다.
- 증강체 아이콘: 새 아이콘은 `augment-dual`, `augment-swap` 2종뿐이다. 나머지 증강체 ID(focus·first·lastone·peanut·sharp·overtime·drain·pierce·weakguard·counter·learn·bulwark·firstaid·thick·thorns·lunch·reverse·neutral·camo·freeswitch·strip·rustle)는 아이콘이 없다. 파일명은 `assets/icons/augment-<id>.png`가 필요하다.
- 원본 가장자리 경고(qaWarnings): button-primary(왼쪽), portrait-frame(오른쪽), tab(아래), harbor·explore·team·combine·shop·daily·settings·peanut·shrimp-snack·augment-dual·augment-swap. 화면에서 눈에 띄는 잘림은 보지 못했지만, 원본 잘림이 없다고 확정하지는 않는다.
