/* Lightweight Android-only controls for the v67 website. */
(function () {
  "use strict";
  if (window.__morkovkiAndroidUiV187) {
    window.__morkovkiAndroidUiV187.refresh();
    return;
  }
  var api = window.MorkovkiAndroid;
  if (!api || typeof api.checkForUpdates !== "function") return;
  var style = document.createElement("style");
  style.id = "morkovki-android-v187";
  style.textContent = [
    "html{overscroll-behavior-y:none}",
    "button,a,input,textarea{touch-action:manipulation}",
    "@media(max-width:720px){",
    " .workspace-nav-btn{min-height:44px}",
    " .friends-page-heading{display:flex;align-items:center;gap:10px;justify-content:space-between;min-width:0}",
    " .friends-page-heading h2{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
    " #friendPageAddBtn{width:44px!important;min-width:44px;min-height:44px;padding:0!important;flex-shrink:0}",
    " #friendPageAddBtn .friend-add-label{display:none}",
    " .friend-card-actions button{min-height:44px}",
    " .friend-search-dialog button{min-height:44px}",
    "}",
    "@media(prefers-reduced-motion:reduce){.workspace-nav-btn,.friend-add-button{transition:none!important}}"
  ].join("\n");
  if (document.head) document.head.appendChild(style);
  function refresh() {
    var nav = document.querySelector(".workspace-nav");
    if (!nav || document.getElementById("morkovki-android-updates")) return;
    var btn = document.createElement("button");
    btn.type = "button";
    btn.id = "morkovki-android-updates";
    btn.className = "workspace-nav-btn";
    btn.setAttribute("aria-label", "Проверить обновления Android-приложения");
    btn.innerHTML = '<span aria-hidden="true" style="font-size:22px;line-height:1">↻</span><span>Проверить обновления</span>';
    btn.addEventListener("click", function () { api.checkForUpdates(); });
    nav.appendChild(btn);
  }
  var observer = new MutationObserver(function () {
    if (!document.getElementById("morkovki-android-updates")) refresh();
  });
  observer.observe(document.body, {childList:true,subtree:true});
  window.__morkovkiAndroidUiV187 = {refresh:refresh};
  refresh();
})();