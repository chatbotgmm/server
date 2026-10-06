# 백업을 서버 밖에도 두기

지금 백업(`backups/`)은 게임 서버(EC2)와 **같은 디스크**에 있습니다. 디스크나 서버가 망가지면 백업도 함께 사라집니다.
아래 둘 중 하나로 서버 밖에도 사본을 두세요.

## 0. 서버 안 자동 백업이 켜져 있는지 먼저 확인
```bash
systemctl list-timers gmm-backup.timer --no-pager
```
목록에 안 나오면 켜기(매일 한국 시간 04:30, 최근 7개 보관):
```bash
sudo bash ~/gmm/galmaegiman-confirmed/scripts/install-backup-timer.sh
```
(`update.sh`도 끝에 타이머가 꺼져 있으면 알려 줍니다.)

## 1. 추천: EBS 스냅샷 자동 만들기 (AWS 콘솔, 한 번만 설정)
EC2 디스크(EBS) 전체를 AWS가 별도 저장소에 매일 찍어 둡니다. 서버가 통째로 망가져도 그날 상태로 되살릴 수 있습니다.

1. AWS 콘솔 → **EC2** → 왼쪽 메뉴 **Elastic Block Store → 수명 주기 관리자(Lifecycle Manager)**
2. **수명 주기 정책 생성** → **EBS 스냅샷 정책**
3. 대상: **인스턴스** 선택 → 대상 태그로 게임 서버 인스턴스 지정
   (인스턴스에 태그가 없으면 먼저 EC2 → 인스턴스 → 태그 → 예: `Name = gmm` 추가)
4. 일정: **매일**, 시간은 한국 04:30 백업 뒤쯤(UTC 기준으로 입력하는 화면이면 20:00 UTC = 한국 05:00)
5. 보관: 예) **7개**
6. 생성 → 다음 날 EC2 → **스냅샷** 목록에 생겼는지 확인

- 비용: 스냅샷 저장 용량만큼 매달 요금이 붙습니다. 금액은 https://aws.amazon.com/ebs/pricing/ 에서 확인하세요(여기서는 확인하지 못했습니다).
- 화면 이름은 AWS가 바꿀 수 있습니다. 메뉴가 다르면 "Data Lifecycle Manager"로 검색하세요.

## 2. 가끔 손으로: 백업 파일을 내 컴퓨터로
```bash
ls -1dt ~/gmm/galmaegiman-confirmed/backups/current-* | head -1
```
나온 폴더의 `storage/confirmed.db`를 Session Manager 파일 받기 등으로 내 컴퓨터에 보관하세요.
⚠ 백업 폴더의 `settings.env`에는 비밀키가 들어 있어 **남에게 보내거나 공개하지 마세요.**

## 되살리기 (요약)
서버를 멈추고 → `storage/confirmed.db`를 백업본으로 바꾸고 → 서버를 켭니다.
```bash
sudo systemctl stop gmm-server
```
이전 DB의 보조 파일(-wal, -shm)을 먼저 치워야 백업본과 섞이지 않습니다(지우지 않고 옮겨 둠):
```bash
cd ~/gmm/galmaegiman-confirmed && mkdir -p backups/before-restore && mv storage/confirmed.db* backups/before-restore/
```
```bash
cp 백업폴더/storage/confirmed.db storage/confirmed.db
```
```bash
sudo systemctl start gmm-server
```
