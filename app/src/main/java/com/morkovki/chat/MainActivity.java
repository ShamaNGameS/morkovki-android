package ru.morkovki.chat;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.webkit.WebViewAssetLoader;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.security.SecureRandom;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private static final Uri SITE_URI = Uri.parse(BuildConfig.SITE_URL);
    private static final String SITE_HOST = SITE_URI.getHost();
    private static final boolean IS_HTTPS_SITE = "https".equalsIgnoreCase(SITE_URI.getScheme());
    private static final String START_URL = IS_HTTPS_SITE
            ? BuildConfig.SITE_URL + "chat" : BuildConfig.SITE_URL;
    private static final int FILE_CHOOSER_REQUEST = 4207;
    private static final int MEDIA_PERMISSION_REQUEST = 4208;
    private static final int DOWNLOAD_PERMISSION_REQUEST = 4209;

    private WebView webView;
    private Button googleLoginButton;
    private boolean nativeGoogleBridgeAvailable;
    private FrameLayout contentRoot;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> pendingFileChooser;
    private boolean googleSignInInProgress;
    private CredentialManager credentialManager;
    private PermissionRequest pendingWebPermissionRequest;
    private DownloadManager.Request pendingDownloadRequest;
    private boolean backActionPending;
    private View fullscreenCustomView;
    private WebChromeClient.CustomViewCallback fullscreenCustomViewCallback;
    private String pendingSharedText;
    private int safeTopInset;
    private int safeRightInset;
    private int keyboardHeight;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        // Draw edge-to-edge while explicitly reserving room for IME and system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.rgb(18, 26, 41));
        window.setNavigationBarColor(Color.rgb(18, 26, 41));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        try {
            credentialManager = CredentialManager.create(this);
            assetLoader = new WebViewAssetLoader.Builder()
                    .setDomain(LOCAL_HOST)
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                    .build();

            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(18, 26, 41));
            webView.setFitsSystemWindows(false);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // Native Google auth and clipboard bridge are unavailable over insecure HTTP.
            if (IS_HTTPS_SITE) {
                webView.addJavascriptInterface(new MorkovkiAndroidBridge(), "MorkovkiAndroid");
            }
            contentRoot = new FrameLayout(this);
            contentRoot.setBackgroundColor(Color.rgb(18, 26, 41));
            contentRoot.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (IS_HTTPS_SITE) {
                googleLoginButton = new Button(this);
                googleLoginButton.setAllCaps(false);
                googleLoginButton.setText("Войти Google");
                googleLoginButton.setTextSize(12);
                googleLoginButton.setContentDescription("Выбрать аккаунт Google с телефона");
                googleLoginButton.setOnClickListener(v -> beginNativeGoogleFromUi());
                int margin = (int) (12 * getResources().getDisplayMetrics().density);
                FrameLayout.LayoutParams googleParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                googleParams.topMargin = margin;
                googleParams.rightMargin = margin;
                contentRoot.addView(googleLoginButton, googleParams);
                googleLoginButton.setVisibility(View.GONE);
            }

            setContentView(contentRoot);
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (view, windowInsets) -> {
                Insets safe = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                boolean showingKeyboard = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
                int bottom = showingKeyboard ? Math.max(safe.bottom, ime.bottom) : safe.bottom;
                keyboardHeight = showingKeyboard ? Math.max(0, ime.bottom - safe.bottom) : 0;
                safeTopInset = safe.top;
                safeRightInset = safe.right;

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webView.getLayoutParams();
                if (params.leftMargin != safe.left || params.topMargin != safe.top
                        || params.rightMargin != safe.right || params.bottomMargin != bottom) {
                    params.setMargins(safe.left, safe.top, safe.right, bottom);
                    webView.setLayoutParams(params);
                }
                if (googleLoginButton != null) {
                    FrameLayout.LayoutParams loginParams =
                            (FrameLayout.LayoutParams) googleLoginButton.getLayoutParams();
                    int margin = (int) (12 * getResources().getDisplayMetrics().density);
                    loginParams.topMargin = safe.top + margin;
                    loginParams.rightMargin = safe.right + margin;
                    googleLoginButton.setLayoutParams(loginParams);
                }
                updateResponsivePage();
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(contentRoot);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDefaultTextEncodingName("UTF-8");
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(false);
            settings.setSupportZoom(false);
            settings.setTextZoom(100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Allow Android password managers to offer saved email/password credentials.
                webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            }
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptCookie(true);
            webView.clearCache(true);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    showFullscreenCustomView(view, callback);
                }

                @Override
                public void onHideCustomView() {
                    hideFullscreenCustomView();
                }

                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    runOnUiThread(() -> handleWebPermissionRequest(request));
                }

                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> upload, FileChooserParams params) {
                    if (pendingFileChooser != null) {
                        pendingFileChooser.onReceiveValue(null);
                    }
                    pendingFileChooser = upload;
                    try {
                        startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (RuntimeException exception) {
                        pendingFileChooser.onReceiveValue(null);
                        pendingFileChooser = null;
                        return false;
                    }
                }
            });
            webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                    downloadFile(url, userAgent, contentDisposition, mimeType));
            handleIncomingShare(getIntent());

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    WebResourceResponse local = assetLoader.shouldInterceptRequest(uri);
                    if (local != null) {
                        return local;
                    }
                    return ("https".equalsIgnoreCase(uri.getScheme())
                            || (!IS_HTTPS_SITE && isTrustedAppUri(uri))) ? null : blockedResponse();
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    if (isTrustedAppUri(uri)) {
                        return false;
                    }
                    if (request.isForMainFrame() && isGoogleAuthUrl(uri)) {
                        // Use the on-device picker only when the site's HTTPS bridge is ready.
                        // Otherwise preserve its existing browser-based OAuth flow.
                        if (nativeGoogleBridgeAvailable) beginNativeGoogleFromUi();
                        else openExternalUri(uri);
                        return true;
                    }
                    // Open other origins externally rather than giving them WebView access.
                    openExternalUri(uri);
                    return true;
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                    if (request.isForMainFrame()) {
                        showLaunchError();
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    updateResponsivePage();
                    installFriendsMobileFix();
                    updateLoginButtonVisibility();
                    CookieManager.getInstance().flush();
                    if (pendingSharedText != null && isSiteLoaded()) {
                        deliverPendingSharedText();
                    }
                }
            });

            webView.loadUrl(START_URL);
        } catch (RuntimeException exception) {
            showLaunchError();
        }
    }

    private void updateResponsivePage() {
        if (webView == null || !isSiteLoaded()) return;
        // Safe, idempotent CSS scoped to Android WebView and small portrait screens.
        webView.evaluateJavascript("(function(){try{\n var doc=document,root=doc.documentElement;\n if(!root||!doc.head)return;\n var s=doc.getElementById('morkovki-android-mobile-css');\n if(!s){s=doc.createElement('style');s.id='morkovki-android-mobile-css';s.textContent=\"/* Mobile WebView-only adjustments. Do not modify the server's page. */\\nhtml,body{max-width:100%;overflow-x:hidden}\\ninput,textarea,[contenteditable=\\\"true\\\"]{font-size:16px}\\n@media(max-width:600px){\\n  html,body{width:100%;min-width:0}\\n  .app-shell{height:100dvh;min-height:0;max-height:100dvh}\\n  .conversation,.messages,.list-pane{min-height:0}\\n  .composer-wrap{flex-shrink:0}\\n  header,[class*=\\\"header\\\"],[class*=\\\"Header\\\"],[class*=\\\"toolbar\\\"],[class*=\\\"Toolbar\\\"]{box-sizing:border-box;max-width:100%;min-width:0}\\n  header > *,[class*=\\\"header\\\"] > *,[class*=\\\"Header\\\"] > *{min-width:0}\\n  header button,header a,[class*=\\\"header\\\"] button,[class*=\\\"Header\\\"] button{flex-shrink:0}\\n  [class*=\\\"header\\\"] [class*=\\\"title\\\"],[class*=\\\"Header\\\"] [class*=\\\"Title\\\"]{min-width:0;overflow:hidden;text-overflow:ellipsis}\\n  [class*=\\\"actions\\\"],[class*=\\\"Actions\\\"]{flex-shrink:0}\\n}\";doc.head.appendChild(s);}\n function sync(){\n   var viewport=window.visualViewport;\n   var height=viewport?viewport.height:window.innerHeight;\n   root.style.setProperty('--morkovki-viewport-height',Math.round(height)+'px');\n }\n sync();\n if(!window.__morkovkiViewportListeners){\n   window.__morkovkiViewportListeners=true;\n   if(window.visualViewport)window.visualViewport.addEventListener('resize',sync);\n   window.addEventListener('resize',sync);\n   doc.addEventListener('focusin',function(e){\n     var t=e.target;\n     if(t&&(/^(INPUT|TEXTAREA)$/.test(t.tagName)||t.isContentEditable)){\n       window.setTimeout(function(){try{t.scrollIntoView({block:'nearest',inline:'nearest'});}catch(_e){}},210);\n     }\n   },true);\n }\n }catch(_e){}})()", null);
    }

    private void installFriendsMobileFix() {
        if (webView == null || !isSiteLoaded()) return;
        // Uses only existing website add-friend actions; no guessed API endpoints.
        try (InputStream input = getAssets().open("friends-mobile-fix.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) {
                output.write(chunk, 0, count);
            }
            webView.evaluateJavascript(new String(output.toByteArray(), StandardCharsets.UTF_8), null);
        } catch (IOException error) {
            Log.w("MorkovkiUI", "Friends mobile fix asset is unavailable", error);
        }
    }

    private void updateLoginButtonVisibility() {
        if (googleLoginButton == null || webView == null || !isSiteLoaded()) return;
        // Do not cover friends, chats or other controls with the floating button.
        // A hidden sign-in form still exists in many single-page apps.
        String js = "(function(){"
                + "function ready(w){return typeof w.__morkovkiNativeGoogleStart==='function'"
                + "||typeof w.__morkovkiNativeGoogleComplete==='function'}"
                + "var bridge=ready(window),f=document.querySelector('iframe');"
                + "try{if(f&&f.contentWindow&&f.contentWindow.location.origin===location.origin)"
                + "bridge=bridge||ready(f.contentWindow)}catch(e){}"
                + "if(!bridge)return false;"
                + "var items=document.querySelectorAll('input[type=password],input[type=email]');"
                + "for(var i=0;i<items.length;i++){var el=items[i];"
                + "if(el.getClientRects().length&&getComputedStyle(el).visibility!=='hidden')"
                + "return true;}"
                + "return false;})()";
        webView.evaluateJavascript(js, result -> {
            nativeGoogleBridgeAvailable = "true".equals(result);
            googleLoginButton.setVisibility(nativeGoogleBridgeAvailable
                    ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            // Persist the site's secure session cookie across app launches.
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }

    private boolean isTrustedAppUri(Uri uri) {
        return uri != null
                && SITE_URI.getScheme() != null
                && SITE_URI.getScheme().equalsIgnoreCase(uri.getScheme())
                && SITE_HOST != null
                && SITE_HOST.equalsIgnoreCase(uri.getHost())
                && SITE_URI.getPort() == uri.getPort();
    }

    private boolean isTrustedMediaOrigin(Uri origin) {
        return IS_HTTPS_SITE && isTrustedAppUri(origin);
    }

    private boolean isGoogleAuthUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "accounts.google.com".equalsIgnoreCase(uri.getHost());
    }

    // Account choice is handled by Google Play services, not by a web browser.
    // The site's JavaScript must supply a signed-in session after native Google completes.
    private void beginNativeGoogleFromUi() {
        if (!IS_HTTPS_SITE || !isSiteLoaded() || webView == null) {
            Toast.makeText(this, "Для входа через Google требуется HTTPS-версия приложения.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String probe = "(function(){try{var candidates=[window],f=document.querySelector('iframe');"
                + "try{if(f&&f.contentWindow&&f.contentWindow.location.origin===location.origin)"
                + "candidates.unshift(f.contentWindow)}catch(e){}"
                + "for(var i=0;i<candidates.length;i++){var w=candidates[i];"
                + "if(typeof w.__morkovkiNativeGoogleStart==='function'){"
                + "w.__morkovkiNativeGoogleStart();return 'delegated'}"
                + "if(typeof w.__morkovkiNativeGoogleComplete==='function')return 'ready'}"
                + "return 'unsupported'}catch(e){return 'unsupported'}})()";
        webView.evaluateJavascript(probe, result -> {
            if (!isSiteLoaded()) return;
            if ("\"delegated\"".equals(result)) return;
            if (!"\"ready\"".equals(result)) {
                Toast.makeText(this,
                        "Сайт пока не поддерживает вход Google через Android.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            // Compatibility mode for sites with a Complete callback but no Start callback.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String nonce = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            String script = "(function(){var n=" + JSONObject.quote(nonce)
                    + ";dispatchEvent(new CustomEvent('morkovki-native-google-start',{detail:{nonce:n}}));"
                    + "var f=document.querySelector('iframe');try{"
                    + "if(f&&f.contentWindow&&f.contentWindow.location.origin===location.origin)"
                    + "f.contentWindow.dispatchEvent(new CustomEvent('morkovki-native-google-start',"
                    + "{detail:{nonce:n}}))}catch(e){}})()";
            webView.evaluateJavascript(script, ignored -> startGoogleSignIn(nonce));
        });
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (request == null) return;
        if (!isTrustedMediaOrigin(request.getOrigin()) || !isSiteLoaded()) {
            request.deny();
            return;
        }

        String[] resources = request.getResources();
        List<String> requiredPermissions = new ArrayList<>();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                requiredPermissions.add(Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                requiredPermissions.add(Manifest.permission.CAMERA);
            } else {
                request.deny();
                return;
            }
        }
        if (requiredPermissions.isEmpty()) {
            request.deny();
            return;
        }

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
                    && !missingPermissions.contains(permission)) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.isEmpty()) {
            request.grant(resources);
            return;
        }

        if (pendingWebPermissionRequest != null) pendingWebPermissionRequest.deny();
        pendingWebPermissionRequest = request;
        ActivityCompat.requestPermissions(
                this,
                missingPermissions.toArray(new String[0]),
                MEDIA_PERMISSION_REQUEST);
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (!isTrustedAppUri(uri)) {
            Toast.makeText(this, "Можно загружать только файлы из Морковок.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (fileName == null || fileName.trim().isEmpty()) fileName = "morkovki-file";
            fileName = new File(fileName).getName();

            DownloadManager.Request download = new DownloadManager.Request(uri)
                    .setTitle(fileName)
                    .setDescription("Загрузка из Морковок")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) download.addRequestHeader("Cookie", cookie);
            if (userAgent != null && !userAgent.isEmpty()) download.addRequestHeader("User-Agent", userAgent);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadRequest = download;
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        DOWNLOAD_PERMISSION_REQUEST);
                return;
            }
            enqueueDownload(download);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Не удалось начать загрузку файла.", Toast.LENGTH_SHORT).show();
        }
    }

    private void enqueueDownload(DownloadManager.Request request) {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Загрузка недоступна на этом устройстве.", Toast.LENGTH_SHORT).show();
            return;
        }
        manager.enqueue(request);
        Toast.makeText(this, "Файл загружается в папку «Загрузки».", Toast.LENGTH_SHORT).show();
    }

    private boolean isSiteLoaded() {
        if (webView == null || webView.getUrl() == null) return false;
        Uri current = Uri.parse(webView.getUrl());
        return isTrustedAppUri(current);
    }

    private void openExternalUri(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"mailto".equals(scheme) && !"tel".equals(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (RuntimeException ignored) {
        }
    }

    private void handleIncomingShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("text/")) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared == null) return;
        String text = shared.toString().trim();
        if (text.isEmpty()) return;
        pendingSharedText = text;
        if (webView != null && isSiteLoaded()) deliverPendingSharedText();
    }

    private void deliverPendingSharedText() {
        if (webView == null || pendingSharedText == null || !isSiteLoaded()) return;
        String sharedText = pendingSharedText;
        String script = "(function(text){"
                + "var docs=[document];try{var frame=document.querySelector('iframe');"
                + "if(frame&&frame.contentDocument)docs.unshift(frame.contentDocument);}catch(e){}"
                + "for(var d=0;d<docs.length;d++){var nodes=docs[d].querySelectorAll('textarea,input:not([type]),"
                + "input[type=text],[contenteditable=true]');for(var i=0;i<nodes.length;i++){var el=nodes[i];"
                + "var hint=[el.placeholder,el.getAttribute('aria-label'),el.getAttribute('name'),"
                + "el.getAttribute('data-placeholder')].join(' ').toLowerCase();"
                + "if(!/(message|сообщ|напиш|ответ|reply)/i.test(hint))continue;"
                + "if(el.isContentEditable){el.textContent=text;}else{var p=el.tagName==='TEXTAREA'?"
                + "HTMLTextAreaElement.prototype:HTMLInputElement.prototype;var desc=Object.getOwnPropertyDescriptor(p,'value');"
                + "if(desc&&desc.set)desc.set.call(el,text);else el.value=text;}"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "el.focus();return true;}}return false;})(" + JSONObject.quote(sharedText) + ");";
        webView.evaluateJavascript(script, result -> {
            if (!isTrustedAppUri(webView == null || webView.getUrl() == null ? null : Uri.parse(webView.getUrl()))) return;
            pendingSharedText = null;
            if ("true".equals(result)) {
                Toast.makeText(this, "Текст добавлен в поле сообщения.", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Текст для Морковок", sharedText));
                    Toast.makeText(this, "Текст скопирован. Откройте чат и вставьте его.", Toast.LENGTH_LONG).show();
                } catch (RuntimeException exception) {
                    Toast.makeText(this, "Не удалось передать текст в чат.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showFullscreenCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (view == null || contentRoot == null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        if (fullscreenCustomView != null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        fullscreenCustomView = view;
        fullscreenCustomViewCallback = callback;
        webView.setVisibility(View.GONE);
        if (googleLoginButton != null) googleLoginButton.setVisibility(View.GONE);
        contentRoot.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), contentRoot);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void hideFullscreenCustomView() {
        if (fullscreenCustomView == null) return;
        contentRoot.removeView(fullscreenCustomView);
        fullscreenCustomView = null;
        webView.setVisibility(View.VISIBLE);
        if (googleLoginButton != null) googleLoginButton.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.getInsetsController(getWindow(), contentRoot).show(WindowInsetsCompat.Type.systemBars());
        WebChromeClient.CustomViewCallback callback = fullscreenCustomViewCallback;
        fullscreenCustomViewCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShare(intent);
    }

    private WebResourceResponse blockedResponse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse(
                "text/plain", "UTF-8", 403, "Blocked", Collections.unmodifiableMap(headers),
                new ByteArrayInputStream(new byte[0]));
    }

    private void startGoogleSignIn(String nonce) {
        if (!IS_HTTPS_SITE || !isSiteLoaded() || nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) {
            notifyGoogleSignInFailure("Не удалось начать защищённый вход Google. Обновите страницу и попробуйте ещё раз.");
            return;
        }
        if (googleSignInInProgress) return;
        googleSignInInProgress = true;

        try {
            // This is an explicit "Sign in with Google" button, not silent sign-in.
            // Google's documentation recommends GetSignInWithGoogleOption here.
            GetSignInWithGoogleOption googleOption = new GetSignInWithGoogleOption.Builder(
                    getString(R.string.google_server_client_id))
                    .setNonce(nonce)
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build();
            Executor mainExecutor = ContextCompat.getMainExecutor(this);
            credentialManager.getCredentialAsync(this, request, null, mainExecutor,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            googleSignInInProgress = false;
                            handleGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(GetCredentialException error) {
                            googleSignInInProgress = false;
                            if (error instanceof GetCredentialCancellationException) {
                                evaluateInChatFrame("__morkovkiNativeGoogleCancel", null);
                            } else {
                                Log.e("MorkovkiGoogle", "Google credential failed: "
                                        + error.getClass().getSimpleName() + " / " + error.getType(), error);
                                String code = error.getClass().getSimpleName();
                                if (code.length() > 60) code = code.substring(0, 60);
                                notifyGoogleSignInFailure("Google-вход не запустился (" + code
                                        + "). Проверьте Google Play и Android OAuth: пакет ru.morkovki.chat, SHA-1 APK.");
                            }
                        }
                    });
        } catch (RuntimeException exception) {
            googleSignInInProgress = false;
            Log.e("MorkovkiGoogle", "Google sign-in request could not start", exception);
            notifyGoogleSignInFailure("Ошибка Google-входа (" + exception.getClass().getSimpleName()
                    + "). Проверьте версию Google Play и OAuth-подпись Android APK.");
        }
    }

    private void handleGoogleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            notifyGoogleSignInFailure("Google не вернул данные для входа. Попробуйте ещё раз.");
            return;
        }
        try {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            evaluateInChatFrame("__morkovkiNativeGoogleComplete", google.getIdToken());
        } catch (RuntimeException exception) {
            notifyGoogleSignInFailure("Не удалось прочитать ответ Google. Попробуйте ещё раз.");
        }
    }

    private void notifyGoogleSignInFailure(String message) {
        evaluateInChatFrame("__morkovkiNativeGoogleFailure", message);
    }

    private void evaluateInChatFrame(String functionName, String argument) {
        if (webView == null || !IS_HTTPS_SITE || !isSiteLoaded()) return;
        String call = "w." + functionName + "(" + (argument == null ? "" : JSONObject.quote(argument)) + ")";
        // Never send the Google ID token to a cross-origin iframe.
        String script = "(function(){var list=[window],f=document.querySelector('iframe');"
                + "try{if(f&&f.contentWindow&&f.contentWindow.location.origin===location.origin)"
                + "list.unshift(f.contentWindow)}catch(e){}"
                + "for(var i=0;i<list.length;i++){var w=list[i];"
                + "if(typeof w." + functionName + "==='function'){" + call + ";return true;}}"
                + "return false;})()";
        webView.evaluateJavascript(script, result -> {
            if ("__morkovkiNativeGoogleComplete".equals(functionName) && !"true".equals(result)) {
                Toast.makeText(this, "Сайт ещё не завершает вход Google из приложения.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private final class MorkovkiAndroidBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return isSiteLoaded();
        }

        @JavascriptInterface
        public void startGoogleSignIn(String nonce) {
            runOnUiThread(() -> MainActivity.this.startGoogleSignIn(nonce));
        }

        @JavascriptInterface
        public boolean copyToClipboard(String value) {
            if (!isSiteLoaded() || value == null || value.length() > 32) return false;
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Тег Морковок", value));
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    private void showLaunchError() {
        TextView message = new TextView(this);
        message.setText("Не удалось открыть Морковки.\nПроверьте подключение к серверу: " + START_URL + "\nОбновите Android System WebView, если проблема повторяется.");
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        message.setPadding(28, 24, 28, 24);
        message.setBackgroundColor(Color.rgb(18, 26, 41));
        setContentView(message);
        webView = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            pendingFileChooser = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_PERMISSION_REQUEST) {
            DownloadManager.Request download = pendingDownloadRequest;
            pendingDownloadRequest = null;
            if (download != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(download);
            } else if (download != null) {
                Toast.makeText(this, "Разрешите доступ к файлам для загрузки вложения.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode != MEDIA_PERMISSION_REQUEST) return;

        PermissionRequest pending = pendingWebPermissionRequest;
        pendingWebPermissionRequest = null;
        if (pending == null) return;

        boolean granted = grantResults.length > 0 && isTrustedMediaOrigin(pending.getOrigin()) && isSiteLoaded();
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        if (granted) pending.grant(pending.getResources());
        else pending.deny();
    }

    @Override
    public void onBackPressed() {
        if (fullscreenCustomView != null) {
            hideFullscreenCustomView();
            return;
        }
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        if (!isSiteLoaded()) {
            if (webView.canGoBack()) webView.goBack();
            else super.onBackPressed();
            return;
        }
        if (backActionPending) return;
        backActionPending = true;
        webView.evaluateJavascript(
                "(function(){var frame=document.querySelector('iframe');var win=frame&&frame.contentWindow;"
                        + "return !!(win&&typeof win.__morkovkiAndroidBack==='function'&&win.__morkovkiAndroidBack());})()",
                handled -> {
                    backActionPending = false;
                    if ("true".equals(handled)) return;
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else MainActivity.super.onBackPressed();
                });
    }

    @Override
    protected void onDestroy() {
        if (fullscreenCustomViewCallback != null) {
            fullscreenCustomViewCallback.onCustomViewHidden();
            fullscreenCustomViewCallback = null;
            fullscreenCustomView = null;
        }
        if (pendingWebPermissionRequest != null) {
            pendingWebPermissionRequest.deny();
            pendingWebPermissionRequest = null;
        }
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
