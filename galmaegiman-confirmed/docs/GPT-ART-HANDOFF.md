# ChatGPT → 저장소 그림 전달 규칙

ChatGPT가 GitHub에 그림을 직접 올릴 때 지킬 규칙이에요. 이 문서를 ChatGPT에게 그대로 읽게 해 주세요.

## 올리는 곳
- 저장소: `chatbotgmm/server`
- 브랜치: **`gpt-art`** (없으면 새로 만들기). 다른 브랜치(main, main-oz3tvo 등)에는 올리지 않아요.
- 폴더: `galmaegiman-confirmed/art/gpt/`
- 그림 파일 말고 다른 파일(코드, 문서, 설정)은 고치지 않아요.

## 파일 이름
| 받는 형식 | 이름 | 예 |
|---|---|---|
| 유닛 하나씩 | `<번호>_front.png`, `<번호>_back.png` | `D1_front.png`, `T6_back.png` |
| 등급별 시트 | `sheet_<등급>_front.png`, `sheet_<등급>_back.png` | `sheet_D_back.png` |
| 진화 단계 | `<번호>_s<단계>_front.png` | `D1_s1_front.png` |
| 빌런 세력별 시트 | `villains_jju.png`, `villains_lob.png`, `villains_gull.png` | (할 일 목록: docs/GPT-TODO.md) |
| 배경 9곳 시트 | `bg_all.png` | |
| 아이콘 시트 | `icons_<1~5>.png` | `icons_3.png` |
| 전투 배경 | `B<번호>.png` | `B2.png` |
| 진화 설계표 | `evolution-design.md` | |

## 그림 규칙
- PNG, **진짜 투명 배경**(알파 채널). 회색·흰색 체크무늬를 그려 넣은 가짜 투명은 안 돼요.
  - 투명이 안 되면 완전 단색 자홍색(#FF00FF) 배경. 검은 배경·빛 번짐·후광은 안 돼요.
- 등급별 시트: D·T·E는 8종(4×2), I는 9종. 캐릭터끼리 **서로 닿지 않게** 넉넉히 띄우고, 순서는 **왼쪽 위부터 오른쪽, 다음 줄**(D1, D2, … D8 / I는 I1~I9).
- 방향: 앞모습은 **보는 사람 기준 화면 왼쪽**을 봐요(부리가 그림의 왼쪽). 뒷모습은 화면 오른쪽 위를 향해요. 반대로 그렸으면 Claude가 좌우반전해요.
- 시트 안에 글자(번호·이름)를 넣지 않아요. 번호는 파일 이름과 순서로 구분해요.
- 원본 해상도 그대로(줄이지 않기). 한 파일 100MB 미만(GitHub 제한).

## 그다음 (Claude가 하는 일)
1. `gpt-art` 브랜치의 `art/gpt/`를 내려받아요(작업 브랜치에는 원본을 올리지 않음).
2. 등급별 시트는 `scripts/split-grade-sheet.py`로 유닛별로 잘라요.
3. `scripts/process-sprites.py`로 앞·뒤 크기·위치를 맞춰 `public/battle/sprites/`에 넣어요.
4. 확인용 모음 그림을 만들어 사용자에게 보여 줘요.
