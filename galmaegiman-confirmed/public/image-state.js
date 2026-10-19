// 늦게 도착하는 카드 그림은 자리를 유지하고, 실패하면 빈 카드 대신 기존 갈매미 그림을 표시한다.
(() => {
  const isCard = img => img instanceof HTMLImageElement && /\/(thumbs|images|sprites)\//.test(img.getAttribute('src') || '');
  document.addEventListener('load', event => {
    if (event.target instanceof HTMLImageElement) event.target.classList.add('art-ready');
  }, true);
  document.addEventListener('error', event => {
    const img = event.target;
    if (!(img instanceof HTMLImageElement) || img.hasAttribute('onerror')) return;
    if (img.dataset.artPlaceholder === '1') return;
    if (!isCard(img) && !img.classList.contains('art-fallback')) return;
    if (img.classList.contains('art-fallback')) { img.dataset.artPlaceholder = '1'; img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="128" height="128"%3E%3Crect width="128" height="128" rx="12" fill="%23eee4ce"/%3E%3Ctext x="64" y="70" text-anchor="middle" font-size="32"%3E?%3C/text%3E%3C/svg%3E'; return; }
    img.classList.add('art-fallback'); img.alt = '그림을 불러오지 못했어요';
    img.src = window.gmmApp ? 'gull.png' : `${window.GMM_BASE || ''}/gull.png`;
  }, true);
})();
