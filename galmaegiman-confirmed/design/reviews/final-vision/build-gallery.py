"""Assemble the read-only review site from native Skia exports. No image retouching."""
from pathlib import Path
import json
import shutil

ROOT = Path(__file__).resolve().parents[3]
ART = Path('/workspace/review-artifacts/pr-2/final-vision')
DEST = ROOT / 'public/design-review'
SCENES = [
 ('title','시작','여정','title-out','항구 접수대와 원본 수호자, 또렷한 첫 행동'),
 ('login','로그인','여정','login','카카오·코드 인증의 위계와 간결한 입력'),
 ('home','메인','여정','home','갈매미와 항구를 살리고 성장·출근·주요 메뉴를 고정'),
 ('chapters','챕터 선택','여정','stages','순찰부터 보스까지 근무 경로를 한눈에'),
 ('dialogue','이야기 대화','여정','dialogue','배경을 크게, 늘어나지 않는 독립 종이 대화면'),
 ('recruit','뽑기','수집·조합','draw','하급·중급·고급 비용과 확률을 동시에 비교'),
 ('recruit-result','획득 결과','수집·조합','acquisition','새 갈매미에서 다음 조합으로 이어지는 흐름'),
 ('collection','수비대·수집','수집·조합','collection','카드 서가와 개체 인사기록, 성장·편성의 구분'),
 ('craft','조합','수집·조합','craft','재료 → 완성될 갈매미 → 다음 행동을 명확하게'),
 ('expedition','탐험대','일상·운영','expedition','항로·편성·시너지·귀환 보상의 한 흐름'),
 ('shop','상점','일상·운영','shop','재화와 교환 품목, 가격을 분리한 보급 창구'),
 ('team','전투 편성','전략·전투','team','출전 3자리와 후보, 시너지·상성을 함께 확인'),
 ('battle','전투 명령','전략·전투','battle-menu','전장·색상 기록·큰 명령 버튼의 역할 분리'),
 ('skills','전투 기술','전략·전투','battle-skills','기술명·위력·속성 배율·효과를 같은 순서로'),
 ('augment','증강체 선택','전략·전투','battle-augment-selected','30초와 세 선택지를 큰 카드로 비교'),
 ('reward','전투 보상','전략·전투','reward','성장 결과와 확인된 전투 사실, 다음 준비'),
 ('growth','성장·승진','성장','promotion','개체 능력치의 전후 변화와 승진 조건'),
 ('combo','합동기','전략·전투','battle-skills','함께 쓰는 동료·효과·전투당 1회를 강조'),
 ('types','상성표','전략·전투','types','공격 속성부터 고르는 읽기 쉬운 상성 안내'),
 ('missions','출석·임무','일상·운영','daily','출근 도장과 자동 지급 임무를 분리'),
 ('settings','설정','일상·운영','settings','소리·화면·알림, 볼륨 복원과 고정 UI'),
 ('items','아이템','일상·운영','items','보유 보급품과 회복할 갈매미를 나란히'),
]
ACTUAL = [
 ('title-out','시작 · 로그아웃'),('title-in','시작 · 로그인'),('login','로그인'),('home','메인'),
 ('modes','게임 모드'),('story','스토리 목록'),('stages','스테이지 선택'),('dialogue','이야기 대화'),
 ('collection','수비대'),('unit','갈매미 상세'),('team','전투 편성'),('draw','뽑기'),('acquisition','획득 결과'),
 ('craft','조합 목록'),('mix','직접 조합'),('shop','상점'),('items','아이템'),('expedition','탐험대'),
 ('daily','출석·임무'),('pvp','대전'),('pvp-lineup','대전 편성'),('practice','연습'),('types','속성 상성'),
 ('settings','설정'),('sound-popup','소리 설정'),('account','계정'),('tutorial','게임 안내'),
 ('battle-choose','선봉 선택'),('battle-menu','전투 명령'),('battle-skills','전투 기술'),
 ('battle-augment-selected','증강체'),('reward','전투 보상'),('promotion','승진 비교'),
]
SPECIAL={'acquisition':'ux72-acquisition-915.webp','reward':'ux72-reward-915-expanded.webp','promotion':'growth-915-font130-promotion-comparison-top.webp'}

def build():
    ART.mkdir(parents=True,exist_ok=True)
    hot={}
    for item in json.loads((Path(__file__).parent/'hotspots.json').read_text()):
        hot[item['scene']]=item['hotspots']
    concepts=[]
    for i,(scene,title,group,baseline,description) in enumerate(SCENES,1):
        stem=f'{i:02d}-{scene}'
        assert (ART/f'images/{stem}.webp').is_file(),stem
        spots=hot.get(scene,[])
        concepts.append(dict(id=scene,title=title,group=group,description=description,image=f'images/{stem}.webp',thumb=f'thumbs/{stem}.webp',actual=baseline,hotspots=spots))
    actual=[]
    for scene,title in ACTUAL:
        file=SPECIAL.get(scene,f'phone-{scene}.webp')
        if all((ART/version/file).is_file() for version in ['before','after']):
            actual.append(dict(id=scene,title=title,before=f'before/{file}',after=f'after/{file}'))
    ids={x['id'] for x in concepts}
    actual_ids={x['id'] for x in actual}
    for item in concepts:
        for h in item['hotspots']:
            target=h.get('target',item['id'])
            assert (target[7:] in actual_ids if target.startswith('actual:') else target in ids),(item['id'],target)
            assert 0<=h['x']<1280 and 0<=h['y']<524
            assert h['x']+h['w']<=1280 and h['y']+h['h']<=524,(item['id'],h)
    manifest=dict(title='수비조장의 모험 도감',date='2026-10-15',concepts=concepts,actual=actual,
        note='최종 시안은 설계 제안입니다. 현재 구현 화면과 구분해 표시하며 계정·재화는 변경하지 않습니다.')
    (ART/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    for name in ['index.html','app.js','style.css']:
        shutil.copyfile(Path(__file__).parent/name,ART/name)
    fonts=ART/'assets/fonts';fonts.mkdir(parents=True,exist_ok=True)
    for font in ['Jua-Regular.ttf','GowunDodum-Regular.ttf']:
        shutil.copyfile(ROOT/'app/android/app/src/main/assets/native'/font,fonts/font)
    DEST.mkdir(parents=True,exist_ok=True)
    for name in ['index.html','app.js','style.css','manifest.json']:
        shutil.copyfile(ART/name,DEST/name)
    for name in ['images','thumbs','before','after','assets']:
        shutil.copytree(ART/name,DEST/name,dirs_exist_ok=True)
    print(json.dumps(dict(concepts=len(concepts),actual=len(actual),hotspots=sum(len(x['hotspots']) for x in concepts),public=str(DEST)),ensure_ascii=False))
if __name__=='__main__':build()
