/* Android WebView friends header fix. Binds only to an existing add-friend action. */
(function () {
  "use strict";
  if (window.__morkovkiFriendsMobileFixV1) {
    window.__morkovkiFriendsMobileFixV1.refresh();
    return;
  }

  var helper = document.createElement("button");
  helper.type = "button";
  helper.id = "morkovki-add-friend-mobile";
  helper.setAttribute("aria-label", "Добавить в друзья");
  helper.setAttribute("title", "Добавить в друзья");
  helper.textContent = "+";
  helper.style.cssText = [
    "position:fixed", "z-index:2147483000", "width:42px", "height:42px",
    "display:none", "align-items:center", "justify-content:center",
    "border:1px solid rgba(148,163,184,.25)", "border-radius:12px",
    "background:#26354f", "color:#f7fafc", "font-size:28px",
    "font-weight:400", "line-height:1", "box-shadow:0 2px 12px rgba(0,0,0,.12)",
    "touch-action:manipulation", "cursor:pointer"
  ].join(";");
  document.body.appendChild(helper);

  var state = { action: null, pending: false };
  function visible(el) {
    if (!el || !el.isConnected) return false;
    var box = el.getBoundingClientRect();
    var s = window.getComputedStyle(el);
    return box.height > 0 && box.width > 0 &&
      s.display !== "none" && s.visibility !== "hidden";
  }
  function label(el) {
    return ([
      el.getAttribute("aria-label"), el.getAttribute("title"),
      el.getAttribute("data-testid"), el.textContent
    ].filter(Boolean).join(" ")).toLocaleLowerCase("ru");
  }
  function friendsHeading() {
    var nodes = document.querySelectorAll("h1,h2,h3,[role=heading],div,span");
    for (var i = 0; i < nodes.length; i++) {
      var e = nodes[i];
      if (e === helper || !e.childNodes) continue;
      var direct = "";
      for (var k = 0; k < e.childNodes.length; k++) {
        if (e.childNodes[k].nodeType === Node.TEXT_NODE)
          direct += e.childNodes[k].textContent;
      }
      if (direct.trim() !== "Мои друзья") continue;
      if (!visible(e)) continue;
      var r = e.getBoundingClientRect();
      if (r.top >= 0 && r.top < Math.min(300, window.innerHeight * .35)) return e;
    }
    return null;
  }
  function inHeader(el, heading) {
    if (!heading || !el) return false;
    var h = heading.closest("header,[role=banner]") ||
      heading.parentElement.parentElement;
    if (h && h.contains(el)) return true;
    var a = el.getBoundingClientRect(), b = heading.getBoundingClientRect();
    return Math.abs(a.top - b.top) < 65 && a.left > b.left;
  }
  function locateAction(heading) {
    var controls = document.querySelectorAll("button,a,[role=button]");
    var nearbyIcon = null;
    for (var i = 0; i < controls.length; i++) {
      var el = controls[i];
      if (el === helper || el.closest("#morkovki-add-friend-mobile")) continue;
      var text = label(el);
      // Prefer explicit actionable labels wherever a responsive layout hides them.
      if (/(добав|приглас|add|invite)/i.test(text) &&
          /(друг|друз|контакт|friend|contact)/i.test(text)) return el;
      // Generic add icon is only safe if it belongs to the Friends header.
      if (inHeader(el, heading)) {
        var svg = el.querySelector("svg");
        var icon = (svg && (svg.getAttribute("data-lucide") ||
          svg.getAttribute("class") || svg.getAttribute("aria-label"))) || "";
        if (/user.plus|plus|add/i.test(icon) || /^(добавить|add|\+)$/i.test(text.trim()))
          nearbyIcon = nearbyIcon || el;
      }
    }
    return nearbyIcon;
  }
  function refresh() {
    state.pending = false;
    if (Math.min(window.innerWidth, document.documentElement.clientWidth) > 640) {
      helper.style.display = "none";
      return;
    }
    var heading = friendsHeading();
    if (!heading) {
      state.action = null;
      helper.style.display = "none";
      return;
    }
    var action = locateAction(heading);
    if (!action) {
      state.action = null;
      helper.style.display = "none";
      return;
    }
    state.action = action;
    // Let the website's original button remain visible when possible.
    if (visible(action)) {
      helper.style.display = "none";
      return;
    }
    var rect = heading.getBoundingClientRect();
    var top = Math.max(8, rect.top + rect.height / 2 - 21);
    helper.style.top = Math.round(top) + "px";
    helper.style.right = "16px";
    helper.style.display = "flex";
  }
  function schedule() {
    if (state.pending) return;
    state.pending = true;
    window.requestAnimationFrame(refresh);
  }
  helper.addEventListener("click", function () {
    var action = state.action;
    if (action && action.isConnected) action.click();
    else schedule();
  });
  var observer = new MutationObserver(schedule);
  observer.observe(document.body, {subtree:true,childList:true});
  window.addEventListener("resize", schedule);
  window.addEventListener("scroll", schedule, {passive:true});
  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", schedule);
    window.visualViewport.addEventListener("scroll", schedule);
  }
  window.__morkovkiFriendsMobileFixV1 = {refresh:schedule};
  refresh();
})();