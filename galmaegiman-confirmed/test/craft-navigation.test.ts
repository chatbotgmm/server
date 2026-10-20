import {readFileSync} from 'node:fs';
import {describe, expect, it} from 'vitest';

// Android 실행 없이 가능한 화면 경로 계약 검사. 실기기 왕복 검증은 인계 항목이다.
const source = (name: string) => readFileSync(`app/android/app/src/main/java/org/dpdns/gmm/${name}.java`,'utf8');
const activity = source('MainActivity');
const economy = source('NativeEconomy');
const flow = source('NativeAcquisitionFlow');
const routeBody = (route: string) => activity.match(new RegExp(`case "${route}":([\\s\\S]*?)break;`))?.[1];

describe('조합 진입과 복원 경로 계약', () => {
  it('홈 기본 조합은 계속 재료로 찾기로 진입한다', () => {
    expect(source('LobbyView')).toContain('"collection", "craft", "shop"');
    expect(routeBody('craft')).toContain('craft();');
    expect(activity).toMatch(/void craft\(\)\s*\{\s*menus\.economy\.craft\(true\);/);
  });
  it('결과 탭 화면 ID와 조합 결과 origin이 동일한 전용 경로를 사용한다', () => {
    const screen = economy.match(/h\.base\("조합", mix \? "mix" : "([^"]+)"\)/)?.[1];
    const origin = economy.match(/String origin = endpoint\.equals\("mix"\) \? "mix" : "([^"]+)"/)?.[1];
    expect(screen).toBe('craftRecipes');
    expect(origin).toBe(screen);
    expect(routeBody(screen!)).toContain('menus.economy.craft(false);');
    expect(routeBody('mix')).toContain('mix();');
  });
  it('결과 닫기·뒤로가기·획득 흐름·통신 후 새로고침이 복원 경로를 유지한다', () => {
    expect(economy).toContain('() -> h.load(flow.origin)');
    expect(flow).toContain('h.load(origin);');
    expect(activity).toContain('screen.equals("craftResult")) load(menus.economy.acquisitionOrigin())');
    expect(economy).toContain('acquisitionFlow.origin');
    expect(activity).toMatch(/void refreshProgress\(String current\)[\s\S]*?load\(current\);/);
    expect(source('AppSkin')).toContain('id.equals("craftRecipes")');
    expect(source('NativeTutorialController')).toContain('h.screen.equals("craftRecipes") ? "craft" : h.screen');
    expect(activity).toMatch(/boolean mapPage = [^;]*id\.equals\("craftRecipes"\)/);
  });
  it('API 조합 주소와 기존 특정 조합식 바로가기는 바꾸지 않는다', () => {
    expect(economy).toContain('m.fetch("craft",');
    expect(economy).toMatch(/performCraft\(\s*"craft",/);
    expect(source('NativeLibrary')).toContain('m.economy.craft(false);');
    expect(flow).toContain('h.menus.economy.craft(false);');
  });
});
