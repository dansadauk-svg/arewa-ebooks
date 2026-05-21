package com.arewascope.ebooks;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://arewascope.com.ng/ebooks/";
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final long MIN_SPLASH_DURATION_MS = 4000;

    private WebView webView;
    private ProgressBar progressBar;
    private View splashOverlay;
    private AdView adView;
    private ValueCallback<Uri[]> filePathCallback;

    private boolean minimumSplashTimeDone = false;
    private boolean pageFinishedOrFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLightSystemBars();
        setContentView(R.layout.activity_main);

        View rootLayout = findViewById(R.id.rootLayout);
        applySafeAreaPadding(rootLayout);

        webView = findViewById(R.id.mainWebView);
        progressBar = findViewById(R.id.pageProgress);
        splashOverlay = findViewById(R.id.splashOverlay);
        adView = findViewById(R.id.adView);

        setupWebView();
        setupAdMob();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            minimumSplashTimeDone = true;
            hideSplashWhenReady();
        }, MIN_SPLASH_DURATION_MS);

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
            pageFinishedOrFailed = true;
            hideSplashWhenReady();
        }
    }

    private void setLightSystemBars() {
        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.setStatusBarColor(getResources().getColor(R.color.safe_area_light));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(getResources().getColor(R.color.safe_area_light));
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
    }

    private void applySafeAreaPadding(View rootView) {
        if (rootView == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            rootView.setOnApplyWindowInsetsListener((view, insets) -> {
                int left = insets.getSystemWindowInsetLeft();
                int top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight();
                int bottom = insets.getSystemWindowInsetBottom();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = insets.getDisplayCutout();

                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }

                view.setPadding(left, top, right, bottom);
                return insets;
            });

            rootView.requestApplyInsets();
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSaveFormData(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        if (isNetworkAvailable()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            Toast.makeText(this, "Offline mode: opening saved pages if available", Toast.LENGTH_LONG).show();
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new ArewaWebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);

                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "File chooser not available", Toast.LENGTH_SHORT).show();
                    return false;
                }

                return true;
            }
        });

        webView.setDownloadListener(createDownloadListener());
    }

    private void setupAdMob() {
        MobileAds.initialize(this, initializationStatus -> {});

        if (adView != null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        }
    }

    private void hideSplashWhenReady() {
        if (minimumSplashTimeDone && pageFinishedOrFailed && splashOverlay != null) {
            splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> splashOverlay.setVisibility(View.GONE))
                    .start();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

            return capabilities != null &&
                    (
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    );
        } else {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    private DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setTitle(fileName);
                request.setDescription("Downloading file...");
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                String cookies = CookieManager.getInstance().getCookie(url);

                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies);
                }

                request.addRequestHeader("User-Agent", userAgent);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                if (downloadManager != null) {
                    downloadManager.enqueue(request);
                    Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
                } else {
                    openExternal(Uri.parse(url));
                }

            } catch (Exception e) {
                Toast.makeText(this, "Opening download in browser", Toast.LENGTH_SHORT).show();
                openExternal(Uri.parse(url));
            }
        };
    }

    private class ArewaWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pageFinishedOrFailed = true;
            hideSplashWhenReady();
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            super.onReceivedError(view, request, error);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (request != null && request.isForMainFrame()) {
                    pageFinishedOrFailed = true;
                    hideSplashWhenReady();
                }
            }
        }

        @Override
        public void onReceivedError(
                WebView view,
                int errorCode,
                String description,
                String failingUrl
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            pageFinishedOrFailed = true;
            hideSplashWhenReady();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            pageFinishedOrFailed = true;
            hideSplashWhenReady();
            Toast.makeText(MainActivity.this, "SSL security error", Toast.LENGTH_LONG).show();
        }

        private boolean handleUrl(Uri uri) {
            if (uri == null) {
                return false;
            }

            String scheme = uri.getScheme();

            if (scheme == null) {
                return false;
            }

            if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
                if (isArewaScopeLink(uri)) {
                    return false;
                }

                openExternal(uri);
                return true;
            }

            if (
                    scheme.equalsIgnoreCase("mailto") ||
                            scheme.equalsIgnoreCase("tel") ||
                            scheme.equalsIgnoreCase("sms") ||
                            scheme.equalsIgnoreCase("whatsapp")
            ) {
                openExternal(uri);
                return true;
            }

            return false;
        }
    }

    private boolean isArewaScopeLink(Uri uri) {
        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        return host.equalsIgnoreCase("arewascope.com.ng")
                || host.equalsIgnoreCase("www.arewascope.com.ng");
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }

        if (adView != null) {
            adView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (adView != null) {
            adView.pause();
        }

        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (adView != null) {
            adView.destroy();
        }

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
