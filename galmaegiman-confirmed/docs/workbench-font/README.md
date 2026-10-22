# 조합 작업대 시안 글꼴

## 배포 파일
- `workbench-noto-regular.otf`: 시안의 **실제** `NotoSansCJK-Regular.ttc` 기본 face 0 (`Noto Sans CJK JP`, v2.004)에서 추출한 정적 OpenType/CFF 서브셋.
- `OFL.txt`: 원본 Adobe 저작권·Google 상표 및 Debian 배포 저작권 고지, SIL Open Font License 1.1 전문. 앱에 글꼴과 함께 포함해야 한다. 독립 글꼴 판매는 허용되지 않는다.
- 글꼴 내부 패밀리는 `GMM Workbench`로 변경했다. 원본을 개작한 서브셋이므로 이름 사용에 관한 혼동을 피하고, 원본 고지와 라이선스 메타데이터는 유지했다.

## 정확성 검증
시안 렌더러는 `GlobalFonts.registerFromPath(TTC, 'Noto')`만 호출한다. 한국어 시안이라는 이유로 KR face 1이라고 추정하면 안 된다. JP/KR의 모양이 다른 한자 30자를 실제 `@napi-rs/canvas`로 비교해, 시안 별칭이 JP face 0과 **동일한 PNG SHA-256**을 내고 KR과 다름을 확인했다 (`render-face-verification.json`). 시안에서 쓰인 한국어·기호의 JP/KR 모양과 폭 자체는 동일하다.

배포 서브셋과 원본 시안 글꼴을 17개 실제 표시 문자열 × 6개 크기 × 2개 요청 굵기(400/700)로 렌더해 **204개 PNG가 바이트 단위 동일**함을 확인했다 (`mock-raster-verification.json`). 13,620개 포함 Unicode 모두 원본 face 0의 윤곽과 advance가 같고, 한글 완성형 11,172자가 전부 들어 있다 (`font-verification.json`). 원본 카드 이미지에 인쇄된 글꼴은 변경하지 않는다.

시안은 Regular 한 가지 face만 등록하므로 700 요청은 렌더러의 합성 굵기다. Android에서도 이 정적 Regular를 `Typeface.createFromAsset`으로 읽고 굵은 제목은 합성 Bold를 사용한다. 네이티브 Android와 canvas의 래스터라이저 차이까지 픽셀 동일하다는 뜻은 아니다. Android API 24가 읽을 수 있는 정적 OTF 구조이며 가변 폰트나 API 26 전용 font resource/Builder에 의존하지 않지만, 실제 API 24 디바이스 실행은 아직 검증하지 않았다.

## 범위 및 fallback
일반 Latin/Greek/Cyrillic, 한글 자모·호환 자모·확장 자모·완성형, 일반 구두점·통화·화살표·수학·여러 기호, CJK 구두점·호환/전각 문자를 보존한다. 범위 전체 중 원본이 가진 글리프를 보존하며 누락 글자를 새로 만든 것이 아니다. 한자·일본어·이모지 등 범위 밖 문자는 Android 시스템 fallback 대상이다. 사용자 이름을 특정 샘플 문자열로 한정한 서브셋이 아니다.

## 출처 및 재현
- 로컬 원본 `/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc`
- 원본 SHA-256 `b76b0433203017ca80401b2ee0dd69350349871c4b19d504c34dbdd80541690a`
- 원본 프로젝트 https://github.com/notofonts/noto-cjk
- 라이선스 원문 로컬 `/usr/share/doc/fonts-noto-cjk/copyright`의 SIL-1.1 항목. Debian 도구 파일에 해당하는 GPL-3+는 이 폰트 파일에 적용되지 않는다.
- `prepare-font.py`는 fontTools로 추출·서브셋·정적 구조·전체 윤곽 검증을 수행한다.
- 결과 파일 2,172,064 bytes, SHA-256 `615c5dde6d447d0d04833177702a09a93d120fa71eb940acc921bc4b2dfa5d35`.

재현 스크립트는 원본 head timestamp를 유지한다. 연속 두 번 생성해 동일 SHA-256임을 확인했다.
