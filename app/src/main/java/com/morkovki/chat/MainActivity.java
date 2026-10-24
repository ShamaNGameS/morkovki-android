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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private static final Uri SITE_URI = Uri.parse(BuildConfig.SITE_URL);
    private static final String SITE_HOST = SITE_URI.getHost();
    private static final boolean IS_HTTPS_SITE = "https".equalsIgnoreCase(SITE_URI.getScheme());
    private static final String START_URL = BuildConfig.SITE_URL;
    private static final int FILE_CHOOSER_REQUEST = 4207;
    private static final int MEDIA_PERMISSION_REQUEST = 4208;
    private static final int DOWNLOAD_PERMISSION_REQUEST = 4209;

    private WebView webView;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(18, 26, 41));
        window.setNavigationBarColor(Color.rgb(18, 26, 41));

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

            setContentView(contentRoot);
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (view, windowInsets) -> {
                Insets safeInsets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webView.getLayoutParams();
                params.setMargins(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom);
                webView.setLayoutParams(params);
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(contentRoot);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDefaultTextEncodingName("UTF-8");
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
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
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.google_server_client_id))
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
                                notifyGoogleSignInFailure("Не удалось открыть вход Google. Проверьте Android OAuth-клиент и попробуйте ещё раз.");
                            }
                        }
                    });
        } catch (RuntimeException exception) {
            googleSignInInProgress = false;
            notifyGoogleSignInFailure("Не удалось открыть вход Google. Проверьте Android OAuth-клиент и попробуйте ещё раз.");
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
        if (webView == null) return;
        String call = "w." + functionName + "(" + (argument == null ? "" : JSONObject.quote(argument)) + ")";
        String script = "(function(){var frame=document.querySelector('iframe');var w=frame&&frame.contentWindow;"
                + "if(w&&typeof w." + functionName + "==='function'){" + call + ";}})();";
        webView.evaluateJavascript(script, null);
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
