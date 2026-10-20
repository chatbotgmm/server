import type { Recipe } from './content.js';

// 결과 식별자·개수·전체 재료 목록을 응답에 싣지 않는 재료별 최소 힌트.
export function hiddenMaterialHint(recipes: readonly Recipe[], known: ReadonlySet<string>, material: string): boolean {
  return recipes.some(r => r.hidden && !known.has(r.resultId) && r.materials.includes(material));
}

export function mixMaterialLimit(recipes: readonly Recipe[]): number {
  return Math.max(2, ...recipes.map(r => r.materials.length));
}
