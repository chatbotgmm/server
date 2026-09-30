# 아트 디렉션 v2 (컨펌 대기)

상태: **사용자 컨펌 전 초안**. 이미지 파일은 아직 교체하지 않았습니다. 이 문서의 프롬프트로 이미지 생성 도구에서 만든 뒤, 같은 파일명(`public/images/C001M.png` 등)으로 교체합니다.

## 목적

- 흔함·안흔함: 첫 유입. 가볍고 귀엽고 친숙하게.
- 특별·희귀: 조합으로 올라가는 중간 단계. 같은 귀여움 위에 배경·연출·화려함을 더해 "모으는 재미".
- 전설: 멋지고 귀여운 목표 캐릭터. (콘텐츠 미확정)
- 히든: 병맛. 봐야 웃겨서 괜히 모으고 싶은 캐릭터. (콘텐츠 미확정)
- 상위 뽑기가 추후 생기므로 특별·희귀를 최종 단계처럼 과하게 만들지 않는다.

## 전 등급 공통: 갈매기 얼굴 교정 (필수)

현재 이미지는 흰머리독수리처럼 보인다. 아래로 통일한다.

| 부위 | 이렇게 | 금지 |
|---|---|---|
| 머리 | 둥근 흰 머리 | 납작한 맹금류 두상, 찌푸린 눈썹뼈 |
| 부리 | 곧고 가는 노란 부리, 아래 부리 끝에 작은 빨간 점 | 갈고리처럼 휜 부리 |
| 눈 | 크고 둥근 눈, 하이라이트 | 날카로운 맹금 눈 |
| 날개 | 연회색 날개, 검은 날개 끝에 흰 점 | 독수리 갈색·거친 깃털 |
| 발 | 짧은 분홍~주황 **물갈퀴 발** | 맹금 발톱 |

석상 수호자 표식: 가슴에 **물결 무늬를 새긴 작은 조약돌 배지**를 모든 캐릭터에 공통으로 넣는다. 등급이 오를수록 배지만 조금씩 화려해진다(흔함 맨돌 → 희귀 금테 등). 곤충 매미·Mommy 해석은 쓰지 않는다.

## 이미지 안 글자

생성 이미지 속 한글은 오탈자가 잦고, 채팅 문구와 어긋난다(기존 문제). **이미지에는 글자를 넣지 않는다.** 팻말이 필요하면 빈 팻말로 두고, 개그 문구는 카카오 텍스트가 담당한다.

## 등급별 스타일

| 등급 | 체형 | 배경 | 연출 |
|---|---|---|---|
| 흔함 | 2등신, 동글동글 | 단순한 파스텔 배경 + 항구 힌트 하나 | 부드러운 낮 조명, 소품 1개가 주인공 |
| 안흔함 | 2~2.5등신 | 밝은 항구 장면(부두·시장·골목), 배경 흐림 | 소품 2~3개, 작은 동작 |
| 특별 | 2.5등신 | 콘셉트 장소가 또렷한 전체 배경 | 따뜻한 영화 조명, 반짝이 약간, 의상 디테일 |
| 희귀 | 2.5~3등신 | 콘셉트 장소 + 깊이감 있는 풍경(야경·노을·실내 무대 등) | 극적 조명, 파티클, 은은한 외곽 발광, 장식 많은 의상 |
| 전설 (방향만) | 3~4등신 영웅 포즈 | 캐릭터별 서사 장소 | 망토·발광 소품, 멋있지만 얼굴은 귀엽게 |
| 히든 (방향만) | 일부러 어긋난 비율 | 대충 그린 듯한 배경 | 낙서체·무표정·엉뚱한 합성 느낌의 병맛 |

공통 규격: 정사각형 PNG(현재 1254×1254), 전신, 중앙 배치, **가장자리 투명·원형 잘림 없음**(현재 C003F 오류).

## 공통 프롬프트 (흔함·안흔함)

**BASE**
```
cute chibi seagull guardian mascot, round chubby body, soft 3D vinyl toy style,
round white head, straight slim yellow beak with a small red spot near the tip,
big round glossy dark eyes, light gray wings with black wingtips and white spots,
short pink-orange webbed feet, small pebble badge with a carved wave pattern on the chest,
soft pastel colors, gentle daylight, full body, centered, square 1:1, no text
```

**NEGATIVE**
```
eagle, hawk, hooked beak, raptor talons, muscular, realistic dirty feathers, gritty, dark,
scary, horror, insect, cicada, text, letters, watermark, cropped, transparent edges
```

## 캐릭터별 프롬프트: 흔함 (2등신 · 단순 배경)

| ID | 이름 | 장면 (BASE 뒤에 붙임) |
|---|---|---|
| C001M | 새우깡봉지 | wearing an empty shrimp-snack bag as a crown, peeking inside it with a puzzled face, a few crumbs falling, pastel sky-blue background with a tiny lighthouse |
| C001F | 짝짝이슬리퍼 | wearing one blue slipper and one pink slipper, wobbling mid-step, one slipper sliding ahead, proud smile, pastel mint background with a pier plank floor |
| C002M | 도시락 | hugging a small lunch box with both wings, opening the lid happily, a sausage visible, pastel yellow background with a harbor bench |
| C002F | 우산 | holding a small yellow umbrella under a bright sunny sky, sitting in its shade, content face, pastel peach background with a small cloudless sky |
| C003M | 상자 | sitting inside a cardboard box that fits perfectly, only head and wing tips showing, sleepy relaxed face, pastel beige background with a harbor crate |
| C003F | 비닐봉지 | a clear plastic bag worn like a cape flapping in the wind, hopping in place with a determined face, pastel lavender background with wind swirls |

## 캐릭터별 프롬프트: 안흔함 (2~2.5등신 · 밝은 항구 장면)

| ID | 이름 | 장면 (BASE 뒤에 붙임) |
|---|---|---|
| U001M | 브라자 | wearing an oversized pink polka-dot bra over a plain t-shirt, used purely as a silly two-pocket snack carrier, one cup stuffed with snacks and the other with a seashell, proud goofy face, comedic and innocent, non-suggestive, no body emphasis, sunny harbor dock background slightly blurred |
| U001F | 라면 | holding a steaming cup of instant noodles with both wings, counting on its wingtips while waiting, happy blushing face, cozy harbor food stall background |
| U002M | 물고기 | tiny fishmonger in a small apron, proudly lifting a palm-sized fish, crate stall with ice and small fish, morning fish market background |
| U002F | 셀카봉 | wearing little sunglasses, holding a selfie stick too high, only the sky in frame, cheerful pose, harbor promenade at sunset background |
| U003M | 교통콘반장 | wearing a traffic cone as a hat, blowing a whistle, a neat row of tiny traffic cones lined up in front, harbor construction corner background |
| U003F | 구명조끼 | wearing three stacked life vests, very round and puffy, holding a checklist clipboard, standing on a breakwater, calm blue sea background |
| U004M | 빨대 | carrying a bendy straw as tall as itself toward an empty clear cup, eager face, harbor cafe terrace background |
| U004F | 선풍기 | several mini handheld fans clipped to both wings, feathers fluttering, slightly sweaty but happy face, sunny harbor alley background |
| U005M | 새우깡 신도 | wearing a cape made of shrimp-snack bags, sweeping up snack crumbs with a small broom, tiny gull friends waiting nearby, harbor square background |

## 특별·희귀 프롬프트 템플릿

체형과 얼굴은 BASE를 따르고, 배경·연출을 키운다. `{concept}`에는 `data/catalog.yaml` 4번째 열(이미지 콘셉트)을, `{costume}`에는 이름의 직업·칭호를 넣는다.

**특별**
```
[BASE, 2.5 heads tall], {costume}, {concept},
detailed themed location filling the whole background, warm cinematic lighting,
light sparkles, richer costume details, pebble badge with a thin silver rim
```

**희귀**
```
[BASE, 2.5 to 3 heads tall], {costume}, {concept},
grand themed scene with depth (night lights / sunset / stage lighting as fits),
dramatic lighting, floating particles, subtle glowing outline, ornate costume,
pebble badge with a gold rim
```

예시: R001M 우주배달총책임자 → `{costume}`=space delivery chief in a cute spacesuit, `{concept}`=우주 배달 상자(space delivery box), 배경은 별이 보이는 항구 발사대.

## ChatGPT 생성 절차 (2026-09 기준)

- 엔진: ChatGPT 이미지 2.5. ChatGPT, ChatGPT Work, Codex 전 요금제에서 사용 가능하다. 투명 배경, 참고 이미지 대상 보존, 여러 차례 편집 일관성, 이미지 댓글 편집, `@Sketch`를 지원한다.
- 모델·추론 단계(Work 입력창 아래 모델·추론 컨트롤 → Advanced):
  - 마스터 디자인 확정: GPT-6 Astra Medium, 없으면 GPT-6 Sol High.
  - 캐릭터 개별 생성: GPT-6 Sol Medium.
  - 한 곳만 고치기: 이미지 댓글 편집. 추론 단계는 그대로 둔다.
  - Max·Ultra는 쓰지 않는다.
- 추론 단계는 그림 품질 자체보다 지시 이해, 조건 확인, 여러 장 사이의 일관성에 영향을 준다(운영 판단).

1. 마스터 시트: 새 대화에서 BASE로 기본 갈매미 설정화(정면·측면·표정 3종)를 만들고, 컨펌 후 PNG로 저장한다.
2. 개별 생성: 한 캐릭터당 한 번 요청한다. 마스터 시트와 기존 이미지(소품 참고용)를 첨부하고 아래 요청 틀을 사용한다.
3. 수정: 부리·발·글자 같은 한 부분은 이미지 댓글로 고친다. 그림체가 흐트러지면 새 대화에서 마스터 시트부터 다시 첨부한다.
4. 저장: PNG로 받아 `ID.png`(예: `C001M.png`)로 이름을 바꾼다.

요청 틀:
```
첨부 1은 갈매미 마스터 설정화, 첨부 2는 기존 {ID} 이미지(소품·콘셉트 참고용, 그림체는 따르지 말 것)야.
마스터 설정화의 얼굴·체형·배지를 그대로 유지해서 아래 캐릭터 1장을 그려 줘.
정사각형 1:1, 불투명 배경, 전신, 중앙, 이미지 안에 글자 없음.

[BASE]
[캐릭터별 장면]
Avoid: [NEGATIVE]

생성 후 체크: 곧은 노란 부리+빨간 점 / 물갈퀴 발 / 조약돌 배지 / 글자 없음 / 가장자리 잘림 없음. 하나라도 틀리면 고쳐서 다시 그려 줘.
```

## 코드·데이터 영향

- 이미지는 같은 파일명으로 교체하므로 코드 수정이 필요 없다. `docs/asset-manifest.json`, `docs/package-checksums.sha256`의 해시는 교체 후 갱신한다.
- 이미지 응답에 1시간 캐시가 있어 교체 직후 예전 그림이 보일 수 있다.
- `data/catalog.yaml`(개그 문구·이미지 콘셉트 열)은 DB 콘텐츠 해시 대상이라 수정하지 않는다. 톤 변경은 `data/flavor.yaml`과 이미지로만 한다.
