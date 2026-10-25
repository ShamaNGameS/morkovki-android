/* Lightweight Android-only controls for the v67 website and its same-origin iframe. */
(function () {
  "use strict";
  if (window.__morkovkiAndroidUiV187) {
    window.__morkovkiAndroidUiV187.refresh();
    return;
  }
  var api = window.MorkovkiAndroid;
  if (!api || typeof api.checkForUpdates !== "function") return;
  var observed = new WeakSet();
  var frameListeners = new WeakSet();
  var pending = false;
  var css = [
    "html{overscroll-behavior-y:none}",
    "button,a,input,textarea{touch-action:manipulation}",
    "@media(max-width:720px){",
    " .workspace-nav-btn{min-height:44px}",
    " #morkovki-android-updates{display:flex;align-items:center;gap:11px;width:auto;min-height:46px;flex:none;margin:auto 14px 8px;padding:0 11px;border:1px solid var(--line);border-radius:12px;background:var(--soft);color:var(--ink);font-size:12px;font-weight:650;text-align:left}",
    " #profileTabBtn{margin-top:0!important}",
    " .friends-page-heading{display:flex;align-items:center;gap:10px;justify-content:space-between;min-width:0}",
    " .friends-page-heading h2{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
    " #friendPageAddBtn{width:44px!important;min-width:44px;min-height:44px;padding:0!important;flex-shrink:0}",
    " #friendPageAddBtn .friend-add-label{display:none}",
    " .friend-card-actions button,.friend-search-dialog button{min-height:44px}",
    "}",
    "@media(prefers-reduced-motion:reduce){.workspace-nav-btn,.friend-add-button{transition:none!important}}"
  ].join("\n");
  function observe(doc) {
    if (!doc || !doc.body || observed.has(doc)) return;
    observed.add(doc);
    new MutationObserver(schedule).observe(doc.body, {subtree:true,childList:true});
  }
  function addTo(doc) {
    if (!doc || !doc.body) return;
    if (doc.head && !doc.getElementById("morkovki-android-v187")) {
      var style = doc.createElement("style");
      style.id = "morkovki-android-v187";
      style.textContent = css;
      doc.head.appendChild(style);
    }
    var nav = doc.querySelector(".workspace-nav");
    var profile = doc.getElementById("profileTabBtn");
    var btn = doc.getElementById("morkovki-android-updates");
    if ((profile || nav) && !btn) {
      btn = doc.createElement("button");
      btn.type = "button";
      btn.id = "morkovki-android-updates";
      btn.className = "workspace-nav-btn";
      btn.setAttribute("aria-label", "Проверить обновления Android-приложения");
      btn.innerHTML = '<span aria-hidden="true" style="font-size:22px;line-height:1">↻</span><span>Проверить обновления</span>';
      btn.addEventListener("click", function () { api.checkForUpdates(); });
    }
    // Place directly above the bottom profile tile; legacy site can fall back to nav.
    if (profile && btn && (btn.parentNode !== profile.parentNode || btn.nextSibling !== profile)) {
      profile.parentNode.insertBefore(btn, profile);
    } else if (!profile && nav && btn && btn.parentNode !== nav) {
      nav.appendChild(btn);
    }
    observe(doc);
  }
  function refresh() {
    pending = false;
    addTo(document);
    var frames = document.querySelectorAll("iframe");
    for (var i = 0; i < frames.length; i++) {
      var frame = frames[i];
      if (!frameListeners.has(frame)) {
        frameListeners.add(frame);
        frame.addEventListener("load", schedule);
      }
      try {
        if (frame.contentDocument && frame.contentWindow.location.origin === location.origin) {
          addTo(frame.contentDocument);
        }
      } catch (ignore) { /* Cross-origin frames never receive Android controls. */ }
    }
  }
  function schedule() {
    if (pending) return;
    pending = true;
    requestAnimationFrame(refresh);
  }
  window.__morkovkiAndroidUiV187 = {refresh:schedule};
  refresh();
})();