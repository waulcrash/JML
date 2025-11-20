package com.example.jml;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "JoomlaSession";
    private static final String KEY_LAST_URL = "lastUrl";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_COOKIES = "cookies";

    private static final String TOPIC_ALL_USERS = "all_users";
    private static final String TOPIC_LOGGED_IN_USERS = "logged_in_users";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Подписываемся на тему для уведомлений
        subscribeToTopics(TOPIC_ALL_USERS);

        // Находим WebView по ID
        myWebView = findViewById(R.id.webview);
        setupWebView();

        // Загружаем сохраненную сессию или стартовую страницу
        loadSavedSession();
    }

    private void setupWebView() {
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        //настройка кук
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(myWebView, true);

        // Восстанавливаем куки если есть
        restoreCookies();

        // Добавляем JavaScript интерфейс
        myWebView.addJavascriptInterface(new JoomlaInterface(this), "Android");

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Сохраняем текущий URL
                saveLastUrl(url);

                // Сохраняем куки после загрузки страницы
                saveCookies();

                view.postDelayed(() -> checkLoginStatus(), 1000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Если URL содержит выходотмечаем как выход
                if (url.contains("logout") || url.contains("task=user.logout")) {
                    setLoggedIn(false);
                    clearCookies();
                    Toast.makeText(MainActivity.this, "Выход выполнен", Toast.LENGTH_SHORT).show();
                }

                view.loadUrl(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    String url = request.getUrl().toString();
                    if (url.contains("logout") || url.contains("task=user.logout")) {
                        setLoggedIn(false);
                        clearCookies();
                        Toast.makeText(MainActivity.this, "Выход выполнен", Toast.LENGTH_SHORT).show();
                    }
                    view.loadUrl(url);
                }
                return true;
            }
        });
    }

    // Сохранение кук
    private void saveCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie("http://cozyli3l.beget.tech");
        if (cookies != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_COOKIES, cookies);
            editor.apply();
            Log.d(TAG, "Cookies saved: " + cookies);
        }
    }

    // Восстановление кук
    private void restoreCookies() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cookies = prefs.getString(KEY_COOKIES, null);
        if (cookies != null) {
            CookieManager cookieManager = CookieManager.getInstance();
            String domain = "http://cozyli3l.beget.tech";
            cookieManager.setCookie(domain, cookies);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.flush();
            }
            Log.d(TAG, "Cookies restored: " + cookies);
        }
    }

    // Очистка кук при выходе
    private void clearCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_COOKIES);
        editor.apply();

        Log.d(TAG, "Cookies cleared");
    }

    //Подписка на рассылку
    private void subscribeToTopics(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener(task -> {
                    String msg = task.isSuccessful() ? "Подписан на группу " + topic: "Ошибка подписки " + topic;
                    Log.d(TAG, msg);
                });
    }

    private void unsubscribeFromTopics(String topic) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener(task -> {
                    String msg = task.isSuccessful() ? "Отписан от группу " + topic: "Ошибка отписки " + topic;
                    Log.d(TAG, msg);
                });
    }

    private void saveLastUrl(String url) {
        if (url != null && !url.equals("about:blank")) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_LAST_URL, url);
            editor.apply();
        }
    }

    private void loadSavedSession() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastUrl = prefs.getString(KEY_LAST_URL, null);
        boolean isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);


        if (lastUrl != null && isLoggedIn) {
            // Восстанавливаем сессию
            myWebView.loadUrl(lastUrl);
            subscribeToTopics(TOPIC_LOGGED_IN_USERS);
            Log.d(TAG, "Восстановлена сессия: " + lastUrl);
        } else {
            // Загружаем стартовую страницу
            myWebView.loadUrl("http://cozyli3l.beget.tech/");
        }
    }

    private void setLoggedIn(boolean isLoggedIn) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
        if(isLoggedIn){
            subscribeToTopics(TOPIC_LOGGED_IN_USERS);
        }else {
            unsubscribeFromTopics(TOPIC_LOGGED_IN_USERS);
        }

        Log.d(TAG, "Статус входа: " + (isLoggedIn ? "Вход выполнен" : "Выход выполнен"));
    }

    private void checkLoginStatus() {
        // проверка входа в Joomla
        String checkScript =
                "(function() {" +
                        "   var hasLogout = document.querySelector('a[href*=\"logout\"], a[href*=\"task=user.logout\"], .logout-button, .user-logout, .logout') !== null;" +
                        "   var hasUserMenu = document.querySelector('.user-info, .user-menu, .user-profile') !== null;" +
                        "   var hasLoginForm = document.querySelector('input[name=\"username\"], input[name=\"password\"], #login-form, .login-form, #form-login') !== null;" +
                        "   var hasAdminPanel = document.querySelector('#mod-login, .com-login') !== null;" +
                        "   " +
                        "   if (hasLogout || hasUserMenu) return 'logged_in';" +
                        "   if (hasLoginForm || hasAdminPanel) return 'not_logged_in';" +
                        "   return 'unknown';" +
                        "})()";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            myWebView.evaluateJavascript(checkScript, value -> {
                if (value != null) {
                    String status = value.replace("\"", "").trim();
                    Log.d(TAG, "Статус входа: " + status);

                    if ("logged_in".equals(status)) {
                        setLoggedIn(true);
                        saveCookies(); // Сохраняем куки при успешном входе
                    } else if ("not_logged_in".equals(status)) {
                        setLoggedIn(false);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveLastUrl(myWebView.getUrl());
        saveCookies();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreCookies();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveCookies();
    }

    // JavaScript Interface
    public class JoomlaInterface {
        Context mContext;

        JoomlaInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onUserLogin() {
            runOnUiThread(() -> {
                setLoggedIn(true);
                saveCookies();
                Toast.makeText(mContext, "Вход выполнен успешно", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void onUserLogout() {
            runOnUiThread(() -> {
                setLoggedIn(false);
                clearCookies();
                Toast.makeText(mContext, "Выход выполнен", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }
}