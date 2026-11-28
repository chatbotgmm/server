/* Manual preview state only. No clock, storage, cookies, or network. */
(function(global){
  const phases=['morning','noon','evening','predawn'];
  function normalize(value){return phases.includes(value)?value:'noon'}
  function read(search){return normalize(new URLSearchParams(search).get('phase'))}
  function href(base,phase){return base+'?phase='+normalize(phase)}
  function updateLinks(phase){document.querySelectorAll('a[data-phase-link]').forEach(a=>a.setAttribute('href',href(a.dataset.phaseLink,phase)))}
  global.GmmPreviewPhase=Object.freeze({normalize,read,href,updateLinks});
})(window);
