package ru.morkovki.chat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String SERVER_URL = "http://212.220.99.132:5761/";
    private static final String SERVER_HOST = "212.220.99.132";
    private static final int SERVER_PORT = 5761;

    private WebView webView;
    private LinearLayout errorPanel;
    private ProgressBar loading;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18, 26, 41));
        getWindow().setNavigationBarColor(Color.rgb(18, 26, 41));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(18, 26, 41));
        setContentView(root);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(18, 26, 41));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Unencrypted pages must not have access to a privileged JS-to-Android bridge.
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isServerUrl(url) || "https".equalsIgnoreCase(url.getScheme())) {
                    return false;
                }
                if ("http".equalsIgnoreCase(url.getScheme())) {
                    return true;
                }
                if ("intent".equalsIgnoreCase(url.getScheme()) || "tel".equalsIgnoreCase(url.getScheme())
                        || "mailto".equalsIgnoreCase(url.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (Exception ignored) {}
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    loading.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(100, 100, Gravity.CENTER);
        root.addView(loading, spinnerParams);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(40, 30, 40, 30);
        errorPanel.setBackgroundColor(Color.rgb(18, 26, 41));

        TextView message = new TextView(this);
        message.setText("Не удалось подключиться к серверу\n" + SERVER_URL + "\n\nПроверьте доступность сервера и порта 5761.");
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Color.WHITE);
        message.setTextSize(18);
        errorPanel.addView(message);

        Button retry = new Button(this);
        retry.setText("Повторить подключение");
        retry.setOnClickListener(v -> loadServer());
        errorPanel.addView(retry);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadServer();
    }

    private boolean isServerUrl(Uri url) {
        return "http".equalsIgnoreCase(url.getScheme())
                && SERVER_HOST.equalsIgnoreCase(url.getHost())
                && SERVER_PORT == url.getPort();
    }

    private void loadServer() {
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        webView.loadUrl(SERVER_URL);
    }

    @Override
    public void onBackPressed() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            loadServer();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
