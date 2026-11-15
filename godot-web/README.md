# 고도 판 웹 빌드 배포 묶음(manual/godot-web)

갈매미맨 랜덤 디펜스 고도 판(저장소 `chatbotgmm/grd`)의 웹 빌드를 운영 서버 `public/defense-godot`에 설치하는 묶음이에요.
주소 `/defense-godot/`는 디펜스 묶음(`manual/home-assembly-20261019`)의 `site.js`가 내요.

- `defense-godot.tar`: 웹 빌드(index.html·index.js(+.gz)·index.wasm.gz·index.pck·아이콘). index.wasm은 설치할 때 .gz를 풀어 만듦
- `files.sha256`: 설치될 파일 지문, `defense-godot.tar.sha256`: 묶음 지문
- `godot-web-update.sh`: 설치(새 폴더에 풀어 확인 → 바꿔 끼움 → 예전 것은 /home/ubuntu/gmm-deploy-backups/godot-web-<시각>, 확인 실패면 되돌림)

```bash
sudo bash -c 'git -C /home/ubuntu/gmm fetch -q origin manual/godot-web && git -C /home/ubuntu/gmm show FETCH_HEAD:godot-web/godot-web-update.sh > /tmp/gw.sh && bash /tmp/gw.sh'
```
서버 실행 폴더(gmm-releases/…)가 새로 바뀌면 다시 설치해야 해요.
