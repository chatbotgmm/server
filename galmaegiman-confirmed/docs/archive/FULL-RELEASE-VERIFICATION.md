# v0.2.6 전체본 검증

검증일: 2026-09-29. 실행 환경: Linux, Node.js v24.19.0.

## 실행하여 확인한 사항

- `npm run build`: 성공.
- 별도 `/tmp/galmaegiman-tests-3dZL0K/test.db`에 Prisma 스키마 적용과 75종 seed 완료.
- 다음 명령으로 전체 테스트 실행: 10개 파일, 114개 테스트 모두 통과, 건너뛴 테스트 없음.

```bash
TEST_DB_URL=file:/tmp/galmaegiman-tests-3dZL0K/test.db DATABASE_URL=file:/tmp/galmaegiman-tests-3dZL0K/test.db node node_modules/vitest/vitest.mjs run --maxWorkers=1 --reporter=verbose
```

위 경로는 검증 당시 생성한 임시 DB 경로다. 사용자는 그대로 복사하지 말고 `npm run test:db`로 자신의 임시 DB를 생성한다. 원본 출력은 FULL-RELEASE-TESTS.txt에 있다.

- `npm run data:check`: 캐릭터75 / 조합69 / v3 이미지25 / v2 이미지50 / 보완 표시명11 확인.
- Pillow로 PNG75개 전체 열기/파일 검증 통과.
- src/test 및 package.json/package-lock.json이 기존 정식 v0.2.6 패치의 목표 해시와 일치함을 확인.
- 이번 전체본 구성 작업에서 게임 기능 코드는 바꾸지 않음. README 설치 절차와 전체본 설치/인수인계/검증 문서를 추가·정리.

## 범위와 한계

이 결과는 작업 환경에서의 검증이다. 사용자의 Galaxy Tab S9 ARM64에서 npm 의존성을 새로 다운로드하는 과정, 실제 전체 재설치 명령 실행, 카카오 운영 채널과 현재 터널 접속은 원격으로 검증하지 못했다. 갤탭 설치 완료 전 실제 적용 성공으로 간주하지 않는다.

실제 사용자 DB·인증키·터널·충전 설정은 ZIP에 없다. INSTALL-FULL.md의 A 절차로 기존 갤탭에서 보존한다. 이미지75개는 기존 제작물을 포함한 것이며 새로 생성한 이미지가 아니다. 전설/히든은 미구현이다.

## 패키지 무결성

`docs/package-checksums.sha256`는 배포 직전에 생성하며 이 체크섬 파일 자체를 제외한 배포 파일들을 포함한다. 압축 해제한 프로젝트 루트에서 `sha256sum -c docs/package-checksums.sha256 --quiet`로 검사한다. 소스/문서를 수정하면 그 파일의 체크섬은 달라지는 것이 정상이다.
