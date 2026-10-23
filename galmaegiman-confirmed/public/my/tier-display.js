(() => {
  const labels = Object.freeze({COMMON: '커먼', UNCOMMON: '레어', RARE: '에픽'});
  const legacy = Object.freeze({'흔함': '커먼', '안흔함': '레어', '희귀': '에픽'});
  globalThis.gmmTierName = (unit) => {
    if (!unit) return '';
    const tier = unit.tier || unit.rarity || '';
    const original = unit.tierName || '';
    return Object.hasOwn(labels, tier) ? labels[tier]
      : !tier && Object.hasOwn(legacy, original) ? legacy[original] : original;
  };
})();
