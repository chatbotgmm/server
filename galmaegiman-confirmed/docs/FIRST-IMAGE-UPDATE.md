# 갈매미맨 0.2.6 — 처음 수집할 때만 획득 이미지

**0.2.5 설치본에 적용하는 패치**입니다.

## 변경

뽑기·조합·교환으로 캐릭터를 얻는 결과 화면에 다음 기준을 공통 적용합니다.

| 수집 상태 | 획득 결과 |
|---|---|
| 해당 사용자의 도감에 처음 등록 | 이미지 + 소개 + 보상 |
| 이전 수집 이력이 있음 | 소개 + 보상 |
| 조합에 소비한 종류를 다시 획득 | 소개 + 보상 |

흔함·안흔함·특별·희귀 모두 같은 기준입니다. 다른 사용자의 수집 이력은 영향을 주지 않습니다. 기존 도감 기록을 그대로 사용하므로 업데이트 후 이미 모은 종류는 처음 획득으로 처리하지 않습니다.

내갈매미·도감·조합식·교환 미리보기에서 직접 조회하는 이미지는 기존처럼 볼 수 있습니다. 이미 완료된 동일 확인 버튼의 재전송은 기존처럼 저장된 결과를 반환하며 추가 획득으로 처리하지 않습니다.

기능 코드는 `src/game.ts`의 획득 결과 세 곳에서 이미지 조건만 변경했습니다. 보상·확률·조합 재료·충전 설정·DB 스키마는 변경하지 않습니다.

## 갤탭 설치

1. `galmaegiman-update-v0.2.6.zip`을 갤탭의 Download 폴더에 저장합니다.
2. 게임 서버 창에서 `Ctrl+C`로 서버를 멈춥니다. Ubuntu 안이라면 `exit`로 Termux에 나옵니다. 터널 창은 켜 둡니다.
3. Termux에서 아래를 그대로 실행합니다. 압축을 푸는 **폴더 이름에는 `.zip`을 붙이지 않습니다.**

```bash
cd "$HOME"
unzip -o storage/downloads/galmaegiman-update-v0.2.6.zip -d galmaegiman-update-v0.2.6
proot-distro login ubuntu --termux-home
```

4. Ubuntu에서 적용합니다.

```bash
cd ~/galmaegiman-confirmed
node ../galmaegiman-update-v0.2.6/apply.mjs
```

5. **갈매미맨 0.2.6 적용 완료**가 나온 뒤 시작합니다.

```bash
npm start
```

URL과 스킬 헤더는 그대로 사용합니다. 적용 중 오류가 나오면 오류를 먼저 확인합니다. 설치기는 배포본과 다른 수정 파일을 덮어쓰지 않으며, 빌드 실패 시 이전 코드와 dist를 복구합니다. DB 초기화나 seed는 실행하지 않습니다.

## 검증

이번 변경과 관련된 테스트만 골라 **17개(획득 관련 14개 + 기존 게임 관련 3개)**를 실행했고 통과했습니다. TypeScript 빌드도 통과했습니다.

- 전 등급 첫 뽑기는 이미지, 같은 종류 두 번째 뽑기는 이미지 없음.
- 조합 가능한 세 등급의 첫 조합·재조합에도 같은 기준 적용.
- 선택 교환과 뽑기가 수집 이력을 공유함.
- 소비 후 재획득과 이전 도감 기록이 있는 경우 이미지 없음.
- 사용자별 이력 분리, 동시 획득 시 첫 결과에만 이미지.
- 상세 조회 이미지, 보상 본문, 동일 확인 요청 재전송 동작 유지.

실행 기록은 ZIP의 `VERIFICATION.txt`에 있습니다. 실제 갤탭·카카오 채널의 적용 결과는 원격으로 확인할 수 없습니다.

## 수정 파일

- `src/game.ts`: 획득 이미지 조건 세 곳.
- `test/acquisition.integration.test.ts`, `test/game.integration.test.ts`: 관련 검증.
- `src/app.ts`, `package.json`, `package-lock.json`: 버전 0.2.6.
- `README.md`, `docs/PLAY-GUIDE.md`, `docs/FIRST-IMAGE-UPDATE.md`, `docs/package-checksums.sha256`: 안내와 파일 해시.
