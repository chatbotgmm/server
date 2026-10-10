# 할 일 37~44 세부 (GPT가 읽는 문서)

사용자는 「chatbotgmm/server 저장소의 galmaegiman-confirmed/docs/GPT-TODO.md 다음 할 일 해 줘」 한 줄과 **카드 그림 1장 첨부**만 보내요. GPT가 이 문서를 읽고 해요.

## GPT가 하는 순서
1. 아래 1~18번 중, `gpt-art` 브랜치 `galmaegiman-confirmed/art/gpt/`에 **올릴 파일이 아직 없는 첫 번째 번호**를 골라요. 예전 방식의 8~9마리 통짜 시트(`sheet_D_front.png` 등)가 이미 있으면 그 등급·방향 번호는 건너뛰어요.
2. 그 번호의 「첨부」 그림이 대화에 없으면, 그리지 말고 **"N번에는 `<파일 이름>` 첨부가 필요해요"** 한 줄만 답해요. (저장소 그림은 그림으로 보지 못해서 첨부가 필요해요.)
3. 첨부가 있으면 「그리기」대로 **그림 1장**을 그려요. 한 번에 이 번호 하나만 해요(다음 번호까지 이어서 하지 않기).
4. 다 그리면 바로 「올릴 파일」 이름으로 원본 해상도 그대로 올리고 **"[N번] 완료 · 파일 이름"**만 답해요.
5. 올리는 중 끊겼다면, 사용자가 "올려 줘"라고만 보내면 방금 그림을 그 이름으로 올려요(다시 그리지 않기).

## 모든 번호 공통
- 그림체는 첨부한 카드와 같게(부드러운 채색, 갈색 외곽선, 귀엽지만 얄미운 느낌). 얼굴·포인트 색·소품은 카드 그대로.
- 같은 종족 표시(새 부리, 볏, 작은 새 발, 귀여운 눈)는 지키고, 각 번호에 적힌 **체형·자세·상징물**로 서로 확실히 다르게. 상징물은 몸만큼 크게.
- 한 장에 적힌 마리 수를 **가로 한 줄**로, 적힌 순서대로 왼쪽부터. 칸끼리 떨어지게, 모두 같은 크기로.
- 배경은 진짜 투명 PNG(안 되면 완전 단색 자홍색 #FF00FF). 글자·번호·이름·그림자·빛 번짐 없이.
- 걷기·공격 같은 동작 그림 말고 1마리에 서 있는 대기 자세 1개, 발끝까지 전신.

| 번호 | 마리 | 첨부 | 올릴 파일 |
|---|---|---|---|
| 1 | D1 D2 D3 D4 앞 | `cards_D_a.jpg` | `sheet_D_front_a.png` |
| 2 | D5 D6 D7 D8 앞 | `cards_D_b.jpg` | `sheet_D_front_b.png` |
| 3 | T1 T2 T3 T4 앞 | `cards_T_a.jpg` | `sheet_T_front_a.png` |
| 4 | T5 T6 T7 T8 앞 | `cards_T_b.jpg` | `sheet_T_front_b.png` |
| 5 | E1 E2 E3 E4 앞 | `cards_E_a.jpg` | `sheet_E_front_a.png` |
| 6 | E5 E6 E7 E8 앞 | `cards_E_b.jpg` | `sheet_E_front_b.png` |
| 7 | I1 I2 I3 앞 | `cards_I_a.jpg` | `sheet_I_front_a.png` |
| 8 | I4 I5 I6 앞 | `cards_I_b.jpg` | `sheet_I_front_b.png` |
| 9 | I7 I8 I9 앞 | `cards_I_c.jpg` | `sheet_I_front_c.png` |
| 10 | D1 D2 D3 D4 뒤 | `sheet_D_front_a.png`(앞모습 결과) | `sheet_D_back_a.png` |
| 11 | D5 D6 D7 D8 뒤 | `sheet_D_front_b.png`(앞모습 결과) | `sheet_D_back_b.png` |
| 12 | T1 T2 T3 T4 뒤 | `sheet_T_front_a.png`(앞모습 결과) | `sheet_T_back_a.png` |
| 13 | T5 T6 T7 T8 뒤 | `sheet_T_front_b.png`(앞모습 결과) | `sheet_T_back_b.png` |
| 14 | E1 E2 E3 E4 뒤 | `sheet_E_front_a.png`(앞모습 결과) | `sheet_E_back_a.png` |
| 15 | E5 E6 E7 E8 뒤 | `sheet_E_front_b.png`(앞모습 결과) | `sheet_E_back_b.png` |
| 16 | I1 I2 I3 뒤 | `sheet_I_front_a.png`(앞모습 결과) | `sheet_I_back_a.png` |
| 17 | I4 I5 I6 뒤 | `sheet_I_front_b.png`(앞모습 결과) | `sheet_I_back_b.png` |
| 18 | I7 I8 I9 뒤 | `sheet_I_front_c.png`(앞모습 결과) | `sheet_I_back_c.png` |

## 1번 · D1 태초의 갈매미맨, D2 원금 갈매미맨, D3 첫밤의 갈매미맨, D4 원해 갈매미맨 (앞모습)
- 첨부: `cards_D_a.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_D_front_a.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 D1 D2 D3 D4.
  - D1 태초의 갈매미맨: 가장 크고 넓적, 낮게 버틴 자세. 상징물: 등 뒤로 날개처럼 펼친 대방패 두 장(몸보다 큼).
  - D2 원금 갈매미맨: 가슴 내민 당당한 자세, 날개 활짝. 상징물: 금괴 주먹.
  - D3 첫밤의 갈매미맨: 졸린 듯 몸 기울이고 눈 감은 둥근 자세. 상징물: 머리 위 거울 원반(일식) + 잠옷 모자.
  - D4 원해 갈매미맨: 머리 위에 물 고리가 뜬 키 큰 실루엣. 상징물: 머리 위 뒤집힌 바다(물 고리) + 가슴 바다 구슬.

## 2번 · D5 시원화 갈매미맨, D6 첫별의 갈매미맨, D7 갈크탑, D8 갈리번 (앞모습)
- 첨부: `cards_D_b.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_D_front_b.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 D5 D6 D7 D8.
  - D5 시원화 갈매미맨: 가늘고 우아한, 한 다리 든 자세. 상징물: 품에 안은 얼어붙은 꽃봉오리.
  - D6 첫별의 갈매미맨: 작은 몸 위에 **크게 떠 있는 별**. 상징물: 머리 위 커다란 별(아직 꺼져 있음).
  - D7 갈크탑: 배를 불룩 내밀고 뒤로 젖힌 작달막한 자세. 상징물: 배만 드러난 황금 조개 흉갑.
  - D8 갈리번: **날개 대신 리본**, 몸을 감은 거대 리본. 상징물: 거대한 별빛 리본.

## 3번 · T1 만상의 수호자, T2 금율 갈매미맨, T3 영야 갈매미맨, T4 대양 갈매미맨 (앞모습)
- 첨부: `cards_T_a.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_T_front_a.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 T1 T2 T3 T4.
  - T1 만상의 수호자: 사방을 방패로 두른 동그란 요새형. 상징물: 몸 둘레를 도는 방패 고리(머리 위만 비어 빗방울).
  - T2 금율 갈매미맨: 대장장이 자세, 앞치마. 상징물: 녹은 금이 흐르는 국자·망치.
  - T3 영야 갈매미맨: 그림자에 잠겨 **흰 배만 튀어나온** 낮은 웅크림. 상징물: 발밑 그림자 웅덩이, 검은 꽃잎.
  - T4 대양 갈매미맨: 날개를 넓게 뻗어 구슬을 띄운 자세. 상징물: 날개 사이 바다를 압축한 소용돌이 구슬.

## 4번 · T5 만개 갈매미맨, T6 천체 갈매미맨, T7 갈깁스, T8 갈쫄쫄이 (앞모습)
- 첨부: `cards_T_b.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_T_front_b.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 T5 T6 T7 T8.
  - T5 만개 갈매미맨: 빗자루 든 부지런한 자세. 상징물: 빗자루 + 등에서 쏟아지는 꽃.
  - T6 천체 갈매미맨: 주위를 도는 궤도 고리, 날개를 반쯤 접은 자세. 상징물: 궤도 링과 운석.
  - T7 갈깁스: **한쪽 팔만 거대한 비대칭**. 상징물: 거대한 얼음 깁스 팔.
  - T8 갈쫄쫄이: 꽉 낀 쫄쫄이로 날씬한 영웅 자세. 상징물: 몸에 빛나는 수호 무늬.

## 5번 · E1 불퇴의 수호자, E2 황금향 갈매미맨, E3 끝없는 밤, E4 돌아오는 바다 (앞모습)
- 첨부: `cards_E_a.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_E_front_a.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 E1 E2 E3 E4.
  - E1 불퇴의 수호자: 앞으로 쏠린 돌진 자세. 상징물: 실로 꿰맨 깨진 방패 조각들.
  - E2 황금향 갈매미맨: 키가 크고 머리 위가 무거운 자세. 상징물: 머리 위 작은 황금 마을.
  - E3 끝없는 밤: 바닥까지 끌리는 긴 망토. 상징물: 밤 커튼 망토 + 초승달.
  - E4 돌아오는 바다: 몸을 도는 물줄기에 감싸인 형태. 상징물: 몸을 계속 도는 물 고리 + 양동이.

## 6번 · E5 지지 않는 벚꽃, E6 별의 기록자, E7 갈발, E8 갈내복 (앞모습)
- 첨부: `cards_E_b.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_E_front_b.png`
- 그리기: 첨부한 카드 4장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 E5 E6 E7 E8.
  - E5 지지 않는 벚꽃: 나무줄기 같은 굵은 다리, 큰 꽃 머리. 상징물: 지지 않는 벚꽃 우산.
  - E6 별의 기록자: 안경 쓰고 커다란 두루마리를 든 학자형. 상징물: 별 지도 두루마리 + 공룡 뼈 갑옷.
  - E7 갈발: **작은 몸 위에 몸보다 큰 가발이 따로 떠 있음**. 상징물: 혼자 날아다니는 깃털 가발.
  - E8 갈내복: 통통한 몸에 꽉 낀 내복. 상징물: 한쪽 불꽃·한쪽 냉기가 돌아가는 김.

## 7번 · I1 앙~ 갈매미맨이야, I2 녹슬지 않는 황금, I3 꺼지지 않는 그림자 (앞모습)
- 첨부: `cards_I_a.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_front_a.png`
- 그리기: 첨부한 카드 3장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 I1 I2 I3.
  - I1 앙~ 갈매미맨이야: 부리를 크게 벌린 외침 자세. 상징물: 입에서 퍼지는 목소리 파동 고리 + 조개 흉갑.
  - I2 녹슬지 않는 황금: 반쯤 녹아 흘러내리다 굳는 몸. 상징물: 몸을 감싼 주형 틀.
  - I3 꺼지지 않는 그림자: 몸보다 큰 그림자가 뒤에서 일어섬. 상징물: 뒤에 선 그림자 분신.

## 8번 · I4 마르지 않는 바다, I5 다시 피는 벚꽃, I6 돌아오는 별 (앞모습)
- 첨부: `cards_I_b.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_front_b.png`
- 그리기: 첨부한 카드 3장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 I4 I5 I6.
  - I4 마르지 않는 바다: 몸이 물로 된 반투명. 상징물: 몸속에 뜬 흑진주.
  - I5 다시 피는 벚꽃: 작고 어린 새싹형. 상징물: 진주 봉오리에서 뻗은 실 뿌리.
  - I6 돌아오는 별: 몸 조각들이 궤도로 떠 있는 형태. 상징물: 몸 둘레를 도는 깃털·조각.

## 9번 · I7 갈터벨트, I8 갈압스타킹, I9 갈슬리퍼 (앞모습)
- 첨부: `cards_I_c.jpg` (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_front_c.png`
- 그리기: 첨부한 카드 3장과 같은 캐릭터의 **앞모습**. 보는 사람 기준 화면 왼쪽을 향해 살짝 튼 자세. 왼쪽부터 I7 I8 I9.
  - I7 갈터벨트: 다리가 길쭉하고 꼿꼿한 자세. 상징물: 몸을 고정한 별빛 띠 벨트.
  - I8 갈압스타킹: 검은 스타킹 신은 **긴 돌다리**. 상징물: 발밑의 작은 블랙홀.
  - I9 갈슬리퍼: 거대한 슬리퍼를 끄는 앞으로 기운 자세. 상징물: 바다가 담긴 거대한 조개 슬리퍼.

## 10번 · D1 태초의 갈매미맨, D2 원금 갈매미맨, D3 첫밤의 갈매미맨, D4 원해 갈매미맨 (뒷모습)
- 첨부: `sheet_D_front_a.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_D_back_a.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 D1 D2 D3 D4(앞모습과 같은 순서).

## 11번 · D5 시원화 갈매미맨, D6 첫별의 갈매미맨, D7 갈크탑, D8 갈리번 (뒷모습)
- 첨부: `sheet_D_front_b.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_D_back_b.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 D5 D6 D7 D8(앞모습과 같은 순서).

## 12번 · T1 만상의 수호자, T2 금율 갈매미맨, T3 영야 갈매미맨, T4 대양 갈매미맨 (뒷모습)
- 첨부: `sheet_T_front_a.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_T_back_a.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 T1 T2 T3 T4(앞모습과 같은 순서).

## 13번 · T5 만개 갈매미맨, T6 천체 갈매미맨, T7 갈깁스, T8 갈쫄쫄이 (뒷모습)
- 첨부: `sheet_T_front_b.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_T_back_b.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 T5 T6 T7 T8(앞모습과 같은 순서).

## 14번 · E1 불퇴의 수호자, E2 황금향 갈매미맨, E3 끝없는 밤, E4 돌아오는 바다 (뒷모습)
- 첨부: `sheet_E_front_a.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_E_back_a.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 E1 E2 E3 E4(앞모습과 같은 순서).

## 15번 · E5 지지 않는 벚꽃, E6 별의 기록자, E7 갈발, E8 갈내복 (뒷모습)
- 첨부: `sheet_E_front_b.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_E_back_b.png`
- 그리기: 첨부한 앞모습 그림 속 4마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 E5 E6 E7 E8(앞모습과 같은 순서).

## 16번 · I1 앙~ 갈매미맨이야, I2 녹슬지 않는 황금, I3 꺼지지 않는 그림자 (뒷모습)
- 첨부: `sheet_I_front_a.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_back_a.png`
- 그리기: 첨부한 앞모습 그림 속 3마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 I1 I2 I3(앞모습과 같은 순서).

## 17번 · I4 마르지 않는 바다, I5 다시 피는 벚꽃, I6 돌아오는 별 (뒷모습)
- 첨부: `sheet_I_front_b.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_back_b.png`
- 그리기: 첨부한 앞모습 그림 속 3마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 I4 I5 I6(앞모습과 같은 순서).

## 18번 · I7 갈터벨트, I8 갈압스타킹, I9 갈슬리퍼 (뒷모습)
- 첨부: `sheet_I_front_c.png`(앞모습 결과) (저장소 `galmaegiman-confirmed/art/ref/` 또는 `art/gpt/`)
- 올릴 파일: `galmaegiman-confirmed/art/gpt/sheet_I_back_c.png`
- 그리기: 첨부한 앞모습 그림 속 3마리의 **뒷모습**. 같은 색·같은 상징물, 등을 보이며 화면 오른쪽 위를 보는 자세. 왼쪽부터 I7 I8 I9(앞모습과 같은 순서).
