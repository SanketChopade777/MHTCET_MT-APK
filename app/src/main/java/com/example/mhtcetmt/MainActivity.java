package com.example.mhtcetmt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String WEBSITE_URL = "https://www.rayvila.com/exam/login.php?tid=260407094033";
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private static final String CHANNEL_ID = "download_channel";

    private WebView webview;
    private LinearLayout loadingContainer;
    private FrameLayout webViewContainer;
    private LinearLayout noInternetLayout;
    private LottieAnimationView loadingAnimation;
    private TextView loadingText;
    //    private TextView toolbarTitle;
//    private ImageButton refreshButton;
    private FloatingActionButton fabActions;
    private View statusBarBackground;

    private ValueCallback<Uri[]> filePathCallback;
    private boolean isLoading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        createNotificationChannel();
        checkPermissions();
        initializeViews();
        handleStatusBar();
        setupClickListeners();
        setupBackPressHandler();
        checkInternetAndLoad();

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    runOnUiThread(() -> {
                        hideNoInternet();
                        if (webview != null) webview.reload();
                    });
                }

                @Override
                public void onLost(android.net.Network network) {
                    runOnUiThread(() -> {
                        showNoInternet();
                    });
                }
            });
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Downloads";
            String description = "Notifications for file downloads";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void handleStatusBar() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (statusBarBackground != null) {
                ViewGroup.LayoutParams params = statusBarBackground.getLayoutParams();
                params.height = insets.top;
                statusBarBackground.setLayoutParams(params);
            }

            v.setPadding(0, 0, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initializeViews() {
        loadingContainer = findViewById(R.id.loadingContainer);
        webViewContainer = findViewById(R.id.webViewContainer);
        noInternetLayout = findViewById(R.id.noInternetLayout);
        loadingAnimation = findViewById(R.id.loadingAnimation);
        loadingText = findViewById(R.id.loadingText);
//        toolbarTitle = findViewById(R.id.toolbarTitle);
//        refreshButton = findViewById(R.id.refreshButton);
        fabActions = findViewById(R.id.fabActions);
        statusBarBackground = findViewById(R.id.statusBarBackground);

        MaterialButton retryButton = findViewById(R.id.retryButton);
        retryButton.setOnClickListener(v -> checkInternetAndLoad());
    }

    private void setupClickListeners() {
//        refreshButton.setOnClickListener(v -> refreshWebView());
        fabActions.setOnClickListener(v -> showActionMenu());
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webview != null && webview.canGoBack()) {
                    webview.goBack();
                    showInfoSnackbar("Going back...");
                } else {
                    showExitDialog();
                }
            }
        });
    }

    private void checkInternetAndLoad() {
        if (isInternetAvailable()) {
            showLoading(true);
            hideNoInternet();
            initializeWebView();
            startLoadingAnimation();
        } else {
            showNoInternet();
            hideLoading();
        }
    }

    private void startLoadingAnimation() {
        final String[] messages = {
                "Connecting to server...",
                "Fetching dashboard...",
                "Almost there...",
                "Welcome to TrackPM!"
        };

        Handler handler = new Handler();
        for (int i = 0; i < messages.length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (isLoading && index < messages.length) {
                    loadingText.setText(messages[index]);
                }
            }, i * 1200);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initializeWebView() {
        webview = findViewById(R.id.webView);

        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDatabaseEnabled(true);

        // Handling multiple windows correctly
        webSettings.setSupportMultipleWindows(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Standard modern user agent
        String chromeUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36";
        webSettings.setUserAgentString(chromeUA);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);

        webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webview.addJavascriptInterface(new WebAppInterface(), "Android");

        webview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Extra check to catch redirects to Google Sign-In
                if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                    view.stopLoading();
                    showGoogleSignInNotice();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showWebView();

//                String title = view.getTitle();
//                if (title != null && !title.isEmpty() && !url.contains("accounts.google.com")) {
//                    toolbarTitle.setText(title);
//                }

                if (url.contains("trackpm.com")) {
                    fabActions.show();
                } else {
                    fabActions.hide();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);

                if (!isInternetAvailable()) {
                    showNoInternet();
                    hideLoading();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) { // important
                    if (!isInternetAvailable()) {
                        showNoInternet();
                        hideLoading();
                    }
                }
            }
        });



        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String url = result.getExtra();

                if (url != null && !url.isEmpty() && !url.equals("about:blank")) {
                    if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                        showGoogleSignInNotice();
                    } else {
                        openInCustomTab(url);
                    }
                    return true;
                }

                // If URL is not immediately available, catch it from the transport WebView
                WebView transportWebView = new WebView(MainActivity.this);
                transportWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                            showGoogleSignInNotice();
                        } else if (!url.equals("about:blank")) {
                            openInCustomTab(url);
                        }
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        if (url != null && !url.isEmpty() && !url.equals("about:blank")) {
                            if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                                showGoogleSignInNotice();
                            } else {
                                openInCustomTab(url);
                            }
                            view.stopLoading();
                        }
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(transportWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webview.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                handleBlobDownload(url, mimeType);
            } else {
                handleDownload(url, userAgent, contentDisposition, mimeType);
            }
        });

        webview.loadUrl(WEBSITE_URL);
    }

    private void showGoogleSignInNotice() {
        new MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Google Security Update")
                .setMessage("To ensure your account safety, Google requires sign-in to be completed in your device's browser. We will open the TrackPM Dashboard for you there.")
                .setIcon(android.R.drawable.ic_lock_lock)
                .setCancelable(true)
                .setPositiveButton("Open in Browser", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        showErrorSnackbar("Could not open browser");
                    }
                })
                .setNegativeButton("Go Back", null)
                .show();
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void processBlob(String base64Data, String fileName, String mimeType) {
            saveBlobToStorage(base64Data, fileName, mimeType);
        }
    }

    private void handleBlobDownload(String blobUrl, String suggestedMimeType) {
        showInfoSnackbar("Downloading file...");
        String script = "javascript:(function() { " +
                "   var xhr = new XMLHttpRequest(); " +
                "   xhr.open('GET', '" + blobUrl + "', true); " +
                "   xhr.responseType = 'blob'; " +
                "   xhr.onload = function() { " +
                "       if (this.status == 200) { " +
                "           var blob = this.response; " +
                "           var reader = new FileReader(); " +
                "           reader.onloadend = function() { " +
                "               var base64data = reader.result.split(',')[1]; " +
                "               var filename = 'document_' + Date.now(); " +
                "               Android.processBlob(base64data, filename, blob.type || '" + suggestedMimeType + "'); " +
                "           }; " +
                "           reader.readAsDataURL(blob); " +
                "       } " +
                "   }; " +
                "   xhr.send(); " +
                "})()";
        webview.evaluateJavascript(script, null);
    }

    private void saveBlobToStorage(String base64Data, String fileName, String mimeType) {
        try {
            byte[] fileBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension == null || extension.isEmpty()) {
                if (mimeType.contains("csv")) extension = "csv";
                else if (mimeType.contains("excel") || mimeType.contains("spreadsheet")) extension = "xlsx";
                else extension = "bin";
            }

            File file = new File(path, fileName + "." + extension);
            FileOutputStream os = new FileOutputStream(file);
            os.write(fileBytes);
            os.close();

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(file));
            sendBroadcast(mediaScanIntent);

            showDownloadNotification(file.getName(), file);
            runOnUiThread(() -> showSuccessSnackbar("Download complete: " + file.getName()));
        } catch (Exception e) {
            runOnUiThread(() -> showErrorSnackbar("Error: " + e.getMessage()));
        }
    }

    private void showDownloadNotification(String fileName, File file) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath()));
        intent.setDataAndType(contentUri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText(fileName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
            request.addRequestHeader("User-Agent", userAgent);
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                showInfoSnackbar("Download started...");
            }
        } catch (Exception e) {
            showErrorSnackbar("Download failed: " + e.getMessage());
        }
    }

    private void refreshWebView() {
        if (isInternetAvailable()) {
            webview.reload();
            showSuccessSnackbar("Refreshing...");
        } else {
            showNoInternet();
        }
    }

    private boolean handleUrlLoading(String url) {
        if (url == null || url.equals("about:blank")) return false;

        // Detect Google Auth and show the security notice
        if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
            showGoogleSignInNotice();
            return true;
        }

        // Allow our site to load normally inside the app
        if (url.contains("trackpm.com")) {
            return false;
        }

        // For other external links (Terms, Policy, etc.), open directly in browser/custom tab
        if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:") || !url.contains("localhost")) {
            if (url.startsWith("http")) {
                openInCustomTab(url);
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    showErrorSnackbar("Could not open application");
                }
            }
            return true;
        }

        return false;
    }

    private void openInCustomTab(String url) {
        if (url == null || url.isEmpty() || url.equals("about:blank")) return;

        CustomTabColorSchemeParams colorParams = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.toolbar_bg))
                .build();

        CustomTabsIntent intent = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .build();

        intent.launchUrl(this, Uri.parse(url));
    }

    private void showExitDialog() {
        new MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Exit App")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (dialog, which) -> finish())
                .setNegativeButton("No", null)
                .show();
    }

    private void showActionMenu() {
        String[] options = {"Refresh", "Copy Link", "Share App"};
        new MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Actions")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: refreshWebView(); break;
                        case 1: copyToClipboard(webview.getUrl()); break;
                        case 2: shareApp(); break;
                    }
                })
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("URL", text);
            clipboard.setPrimaryClip(clip);
            showInfoSnackbar("Copied!");
        }
    }

    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Check out TrackPM: " + WEBSITE_URL);
        startActivity(Intent.createChooser(intent, "Share"));
    }

    private void showLoading(boolean show) {
        isLoading = show;
        loadingContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) loadingAnimation.playAnimation();
        else loadingAnimation.pauseAnimation();
    }

    private void showWebView() {
        showLoading(false);
        hideNoInternet();
        webViewContainer.setVisibility(View.VISIBLE);
    }

    private void showNoInternet() {
        noInternetLayout.setVisibility(View.VISIBLE);
        webViewContainer.setVisibility(View.GONE);
    }

    private void hideNoInternet() {
        noInternetLayout.setVisibility(View.GONE);
    }

    private void hideLoading() {
        showLoading(false);
    }

    private void showSuccessSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.success_green));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    private void showInfoSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT);
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    private void showErrorSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.error_red));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
}