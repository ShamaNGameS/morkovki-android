/* Android-compatible fallback; the site patch implements the same UX guard. */
(function(){
"use strict";
if(window.__morkovkiMobileUX188){window.__morkovkiMobileUX188.refresh();return;}
var shell=document.getElementById("appShell"),send=document.querySelector("#messageForm .send-btn"),add=document.getElementById("friendPageAddBtn"),head=document.querySelector("#friendsPage .friends-page-heading"),toolbar=document.querySelector("#friendsPage .friends-toolbar");
if(!shell||!send||!add||!toolbar)return;
var originalNext=add.nextSibling,style=document.createElement("style");
style.textContent='.morkovki-section-back{display:none}@media(max-width:720px){#friendsPage .friends-page-heading{display:none!important}#friendsPage .friends-toolbar{display:flex!important;align-items:center!important;flex-direction:row!important;flex-wrap:nowrap!important;gap:8px!important;margin-bottom:12px!important}#friendsPage .friends-tabs{width:auto!important;flex:1 1 0!important;min-width:0!important;overflow-x:auto!important;gap:2px!important}#friendsPage .friend-tab{flex-shrink:0!important;min-height:44px!important;padding:0 9px!important;font-size:12px!important}#friendPageAddBtn{display:flex!important;position:static!important;flex:0 0 44px!important;width:44px!important;min-width:44px!important;max-width:44px!important;height:44px!important;min-height:44px!important;margin:0!important;padding:0!important;align-items:center!important;justify-content:center!important;font-size:23px!important;overflow:hidden!important}#friendPageAddBtn .friend-add-label{display:none!important}.morkovki-section-back{display:flex;align-self:flex-start;align-items:center;min-height:44px;padding:0 12px;margin-bottom:9px;border:1px solid var(--line);border-radius:11px;background:var(--soft);color:var(--ink);font:600 12px system-ui}#messageForm .send-btn{touch-action:manipulation;user-select:none;-webkit-user-select:none}#messageForm .send-btn.morkovki-recording{background:#d14354!important}}.morkovki-voice-indicator{position:fixed;left:12px;right:12px;bottom:100px;z-index:99990;padding:14px;border-radius:12px;background:#412833;color:#fff;text-align:center}.morkovki-voice-layer{position:fixed;inset:0;z-index:99999;display:grid;place-items:center;background:#0009;padding:20px}.morkovki-voice-card{display:grid;gap:14px;width:min(100%,400px);padding:20px;border-radius:16px;background:#202b40;color:white}.morkovki-voice-card audio{width:100%}.morkovki-voice-actions{display:flex;gap:10px}.morkovki-voice-actions button{flex:1;min-height:44px;color:white;border:0;border-radius:10px;background:#43516b}.morkovki-voice-actions .send{background:#4976eb}';
document.head.appendChild(style);
function mobile(){return matchMedia("(max-width:720px)").matches}
function inChat(){var pane=document.getElementById("conversationPane");return !!(pane&&!pane.classList.contains("no-chat")&&shell.classList.contains("chat-open"))}
function notice(t){var n=document.getElementById("toast");if(n){n.textContent=t;n.classList.add("show");setTimeout(function(){n.classList.remove("show")},2500)}else alert(t)}
["friendsPage","messagesPage","groupsPage"].forEach(function(id){var page=document.getElementById(id);if(!page)return;var b=document.createElement("button");b.type="button";b.className="morkovki-section-back";b.textContent="‹  Назад к разделам";b.onclick=function(){shell.classList.remove("chat-open")};page.insertBefore(b,page.firstChild)});
function refresh(){if(mobile()){if(add.parentNode!==toolbar)toolbar.appendChild(add)}else if(head&&add.parentNode!==head){if(originalNext&&originalNext.parentNode===head)head.insertBefore(add,originalNext);else head.appendChild(add)}}
window.addEventListener("resize",refresh,{passive:true});window.__morkovkiMobileUX188={refresh:refresh};refresh();
var p=null,r=null,blocked=0,indicator=null;
function stopTracks(s){if(s)s.getTracks().forEach(function(t){t.stop()})}
function clearIndicator(){if(indicator)indicator.remove();indicator=null;send.classList.remove("morkovki-recording")}
function stop(discard){if(!r||r.stopping)return;var x=r;x.stopping=true;x.discard=!!discard;clearTimeout(x.timeout);try{if(x.rec.state!=="inactive")x.rec.stop()}catch(e){stopTracks(x.stream);r=null;clearIndicator()}}
function showPreview(blob,type){if(!blob.size||blob.size>10485760){notice("Запись больше 10 МБ");return}var url=URL.createObjectURL(blob),layer=document.createElement("div"),card=document.createElement("div"),title=document.createElement("strong"),audio=document.createElement("audio"),row=document.createElement("div"),cancel=document.createElement("button"),sendVoice=document.createElement("button");layer.className="morkovki-voice-layer";layer.setAttribute("role","dialog");layer.setAttribute("aria-modal","true");card.className="morkovki-voice-card";title.textContent="Прослушайте голосовое перед отправкой";audio.controls=true;audio.src=url;audio.preload="metadata";row.className="morkovki-voice-actions";cancel.type=sendVoice.type="button";cancel.textContent="Отмена";sendVoice.className="send";sendVoice.textContent="Отправить голосовое";row.append(cancel,sendVoice);card.append(title,audio,row);layer.appendChild(card);document.body.appendChild(layer);function close(){audio.pause();URL.revokeObjectURL(url);layer.remove()}cancel.onclick=close;layer.onclick=function(e){if(e.target===layer)close()};sendVoice.onclick=function(){var input=document.getElementById("fileInput");if(!input||!inChat()){notice("Откройте переписку");return}try{var ext=/mp4/.test(type)?"m4a":/ogg/.test(type)?"ogg":"webm",file=new File([blob],"Голосовое-"+Date.now()+"."+ext,{type:type}),dt=new DataTransfer();dt.items.add(file);input.files=dt.files;close();input.dispatchEvent(new Event("change",{bubbles:true}));notice("Отправляем голосовое…")}catch(e){notice("Не удалось прикрепить запись")}};cancel.focus()}
async function record(press){if(p!==press||!press.down)return;press.long=true;blocked=Date.now()+1300;if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia||!window.MediaRecorder||!window.DataTransfer){notice("Микрофон не поддерживается WebView");return}try{var stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true}});if(p!==press||!press.down||!inChat()){stopTracks(stream);return}var types=["audio/webm;codecs=opus","audio/webm","audio/mp4","audio/ogg;codecs=opus"],type="";for(var i=0;i<types.length;i++)if(MediaRecorder.isTypeSupported(types[i])){type=types[i];break}var rec=new MediaRecorder(stream,type?{mimeType:type}:undefined),x={rec:rec,stream:stream,parts:[],started:Date.now(),stopping:false,discard:false,timeout:null};r=x;rec.ondataavailable=function(e){if(e.data&&e.data.size)x.parts.push(e.data)};rec.onerror=function(){stop(true);notice("Ошибка записи")};rec.onstop=function(){clearTimeout(x.timeout);stopTracks(stream);if(r===x)r=null;clearIndicator();if(x.discard)return;if(Date.now()-x.started<400){notice("Запись слишком короткая");return}var format=(rec.mimeType||type||"audio/webm").split(";")[0];showPreview(new Blob(x.parts,{type:format}),format)};rec.start(250);indicator=document.createElement("div");indicator.className="morkovki-voice-indicator";indicator.textContent="🔴 Запись… Отпустите для завершения";document.body.appendChild(indicator);send.classList.add("morkovki-recording");x.timeout=setTimeout(function(){stop(false);notice("Запись остановлена через 90 секунд")},90000)}catch(e){if(press.down)notice("Разрешите доступ к микрофону")}}
send.addEventListener("pointerdown",function(e){if(!mobile()||e.button!==0||p||!inChat())return;var q={id:e.pointerId,down:true,long:false};p=q;q.timer=setTimeout(function(){record(q)},550)},true);
function finish(e,discard){var q=p;if(!q||q.id!==e.pointerId)return;clearTimeout(q.timer);q.down=false;p=null;if(q.long){blocked=Date.now()+1300;if(r)stop(discard)}}
send.addEventListener("pointerup",function(e){finish(e,false)},true);send.addEventListener("pointercancel",function(e){finish(e,true)},true);
send.addEventListener("contextmenu",function(e){if(mobile())e.preventDefault()});
send.addEventListener("click",function(e){if(Date.now()<blocked){e.preventDefault();e.stopImmediatePropagation()}},true);
document.getElementById("messageForm").addEventListener("submit",function(e){if(Date.now()<blocked){e.preventDefault();e.stopImmediatePropagation()}},true);
})();


/* Android 1.9.0: compatibility with server prealpha v0.0.2.
 * The website owns account state and attachments; Android only adds secure,
 * same-origin audio playback and removes redundant section back buttons.
 */
(function () {
  "use strict";
  if (window.__morkovkiServerV002Compat) return;
  if (!document.getElementById("appShell")) return;
  window.__morkovkiServerV002Compat = true;

  var style = document.createElement("style");
  style.id = "morkovki-v002-android-audio-style";
  style.textContent = ".morkovki-android-inline-audio{display:block;max-width:100%;width:min(290px,100%);height:48px;margin:8px 0 3px}" +
    "@media(max-width:720px){.morkovki-section-back{display:none!important}}";
  document.head.appendChild(style);

  var queued = false;
  function hasNativeBack() {
    return ["friendsBackBtn", "messagesBackBtn", "groupsBackBtn"].some(function (id) {
      return !!document.getElementById(id);
    });
  }

  function enhanceAudio() {
    queued = false;
    // v0.0.2 has native back controls. Remove fallback duplicates only if present.
    if (hasNativeBack()) {
      document.querySelectorAll(".morkovki-section-back").forEach(function (button) {
        button.remove();
      });
    }
    document.querySelectorAll("a.message-attachment").forEach(function (anchor) {
      if (anchor.dataset.morkovkiAndroidAudio === "checked") return;
      anchor.dataset.morkovkiAndroidAudio = "checked";
      var label = anchor.querySelector(".attachment-card-name");
      var fileName = ((label ? label.textContent : anchor.textContent) || "").trim();
      if (!/\.(?:webm|ogg|opus|m4a|mp3|wav|aac|mp4)$/i.test(fileName)) return;
      var uri;
      try { uri = new URL(anchor.href, document.location.href); } catch (_) { return; }
      if (uri.origin !== document.location.origin ||
          !/^\/api\/messages\/attachments\/[A-Za-z0-9._-]+$/.test(uri.pathname)) return;
      var parent = anchor.parentElement;
      if (!parent || parent.querySelector("audio")) return;
      var audio = document.createElement("audio");
      audio.className = "morkovki-android-inline-audio";
      audio.controls = true;
      audio.preload = "none";
      audio.src = uri.href;
      audio.setAttribute("aria-label", "Воспроизвести " + fileName);
      anchor.insertAdjacentElement("afterend", audio);
    });
  }

  function schedule() {
    if (queued) return;
    queued = true;
    window.requestAnimationFrame(enhanceAudio);
  }
  var observer = new MutationObserver(schedule);
  observer.observe(document.body, { childList: true, subtree: true });
  schedule();
})();
