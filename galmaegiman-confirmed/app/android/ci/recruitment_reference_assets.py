"""Exact R3 additions; original three tray locks remain unchanged."""
import json
from pathlib import Path
from verify_registration_ci import check, sha256, png_dimensions
EXPECTED = [{'path': 'native/release-v1/recruitment/tray-low-reference-v3.png', 'kind': 'LOW', 'bytes': 1764662, 'sha256': 'bd17d0791ad410e00de0343900bcfea6e3be3e82ad99a5fd29ff2011d9132d22', 'dimensions': [1663, 946], 'sourceRect': [59, 84, 1639, 937]}, {'path': 'native/release-v1/recruitment/tray-mid-reference-v3.png', 'kind': 'MID', 'bytes': 1744944, 'sha256': '9e83838ef5fc8caf0b0a77d1323991d99f01785ea513940869070bd56cfd9a3e', 'dimensions': [1663, 946], 'sourceRect': [59, 83, 1637, 938]}, {'path': 'native/release-v1/recruitment/tray-high-reference-v3.png', 'kind': 'HIGH', 'bytes': 1892045, 'sha256': '6c319981a800974133746817e5d29403cd1c3edff0e0c8005a954b4c1150f5d0', 'dimensions': [1663, 946], 'sourceRect': [60, 86, 1640, 937]}, {'path': 'native/recruitment/fonts/NotoSansKR-Regular.ttf', 'bytes': 6224868, 'sha256': 'a516a09bb7351dc6254e0920b2ab6d21131a8f10213d45e5cf990af3b777695b'}, {'path': 'native/recruitment/fonts/NotoSansKR-Bold.ttf', 'bytes': 6222712, 'sha256': '8440245279bc3819589f99dc8ddee6372d3481816d2ccdb052f2f1d1eb26b86c'}, {'path': 'native/recruitment/fonts/OFL-NotoSansKR.txt', 'bytes': 4388, 'sha256': '1c05c68c34f9708415aada51f17e1b0092d2cea709bf4a94cd38114f9e73d7d9'}]
REFERENCE = {e['kind']: e for e in EXPECTED if 'kind' in e}
ASSET_PREFIX = 'galmaegiman-confirmed/app/android/app/src/main/assets/'

def reference_lock():
    entries = json.loads((Path(__file__).parent/'recruitment-assets-lock.json').read_text()).get('referenceV3')
    check(entries == EXPECTED, 'R3 finite asset identity/rectangle lock changed')
    return entries

def verify_reference_assets(read):
    entries = reference_lock()
    for e in entries:
        data = read(e['path'])
        check(len(data) == e['bytes'] and sha256(data) == e['sha256'], 'R3 asset byte/hash mismatch: '+e['path'])
        if 'dimensions' in e:
            check(list(png_dimensions(data)) == e['dimensions'] and data[25] == 6, 'R3 RGBA PNG dimensions mismatch')
            x,y,r,b = e['sourceRect']; w,h = e['dimensions']
            check(0 <= x < r <= w and 0 <= y < b <= h, 'R3 source rectangle invalid')
    return {'files':len(entries), 'sha256':{e['path']:e['sha256'] for e in entries}}
