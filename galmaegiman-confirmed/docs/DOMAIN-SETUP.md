# 무료 고정 도메인 연결 (DigitalPlat + Cloudflare 고정 터널)

목적: 지금의 Quick Tunnel(`*.trycloudflare.com`, 재시작마다 주소가 바뀜)을 **고정 주소**로 바꿉니다.
바꾸면 웹페이지(`/`, `/world`, `/dex`) 링크가 고정되고, 카카오 스킬 URL도 다시 넣을 일이 없어집니다.

- 비용: 도메인(DigitalPlat FreeDomain) 무료, Cloudflare 무료 플랜, Cloudflare Tunnel 무료(2026년 기준).
- 주의: DigitalPlat 심사 기간·요건은 공식 안내에 적혀 있지 않습니다. 화면 이름은 바뀌었을 수 있습니다.
- 아래 `내도메인`은 받은 도메인(예: `galmaemi.dpdns.org`)으로 바꿔 읽으세요.

## 1. 도메인 신청 (휴대폰 브라우저)
1. https://dash.domain.digitalplat.org/ 접속 → 가입·로그인
2. 원하는 이름 신청(예: `galmaemi` + `.dpdns.org`). 영문 소문자·숫자 추천.
3. 네임서버를 넣으라는 단계가 나오면 **잠시 그대로 두고 2번(Cloudflare)으로** 가서 네임서버 2개를 받아 옵니다.

## 2. Cloudflare에 도메인 추가
1. https://dash.cloudflare.com 가입·로그인
2. 도메인(사이트) 추가 → `내도메인` 입력 → **Free** 플랜
3. Cloudflare가 보여 주는 **네임서버 2개**(예: `xxx.ns.cloudflare.com`)를 복사
4. DigitalPlat 대시보드의 그 도메인 → 네임서버 설정에 2개를 붙여 넣고 저장
5. Cloudflare에서 도메인 상태가 **Active**가 될 때까지 기다림(몇 시간~하루 걸릴 수 있음, 메일로 알려 줌)

## 3. 고정 터널 만들기 (Cloudflare 화면)
1. Cloudflare 대시보드 → Zero Trust(또는 Networks) → **Tunnels** → Create a tunnel → `cloudflared` 선택
2. 이름: `gmm` → 저장
3. 설치 화면에서 운영체제 **Debian/Ubuntu (64-bit)** 선택 → 아래쪽 명령 중
   `sudo cloudflared service install 긴토큰` 한 줄을 복사(토큰은 비밀번호처럼 남에게 보이지 않게)
4. **Public hostname** 추가: Subdomain 비움(또는 `www`), Domain `내도메인`, Service `HTTP` / `localhost:3000` → 저장

## 3-B. 대시보드가 결제 수단을 요구하면: 서버에서 직접 만들기 (Zero Trust 불필요)
3번 대신 EC2에서(한 줄씩):
```bash
sudo -iu ubuntu
```
```bash
cloudflared tunnel login
```
→ 나오는 `https://dash.cloudflare.com/argotunnel?...` 주소를 휴대폰 브라우저로 열고 `gmm.dpdns.org` 선택 → Authorize. 서버 화면에 성공 메시지가 나오면:
```bash
cd ~/gmm/galmaegiman-confirmed && bash scripts/named-tunnel.sh gmm.dpdns.org
```
→ 터널 만들기·DNS 연결·서비스(`gmm-named-tunnel`) 설치·확인까지 자동. `✅ 고정 주소 동작`이 나오면 4번의 `npm run url` 부터 이어서 하면 됩니다(`cloudflared service install` 줄은 건너뜀). 되돌릴 때는 `sudo systemctl disable --now gmm-named-tunnel`.

## 4. EC2에서 (Session Manager, 한 줄씩)
```bash
sudo -iu ubuntu
```
기존 Quick Tunnel 끄기(주소가 바뀌던 것):
```bash
sudo systemctl disable --now gmm-tunnel
```
3-3에서 복사한 명령 붙여 넣기:
```bash
sudo cloudflared service install 긴토큰
```
게임 서버가 그림 주소를 새 도메인으로 쓰게:
```bash
cd ~/gmm/galmaegiman-confirmed && npm run url -- https://내도메인
```
```bash
sudo systemctl restart gmm-server
```
확인(둘 다 응답이 와야 함):
```bash
curl -fsS https://내도메인/health; echo
```

## 5. 카카오 i 오픈빌더
스킬 URL을 `https://내도메인/kakao/skill` 로 바꾸고 저장·배포. (헤더의 스킬 비밀키는 그대로)

## 6. 되돌리기 (문제 생기면)
```bash
sudo systemctl stop cloudflared
```
```bash
sudo systemctl enable --now gmm-tunnel
```
그다음 예전처럼 Quick Tunnel 주소를 확인해 `npm run url`과 카카오 스킬 URL을 다시 넣으면 됩니다.

## 웹페이지 주소
- 홈: `https://내도메인/`
- 세계관: `https://내도메인/world` (글은 `data/world.md`를 고치면 반영)
- 도감: `https://내도메인/dex` (유닛 데이터 `data/units.json`에서 자동 생성, 히든·최상위는 가림)
- 랭킹: 다음 단계
