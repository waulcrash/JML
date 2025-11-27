package com.example.jml;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

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

    private boolean wasNotificationHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");

        // Подписываемся на тему для уведомлений
        subscribeToTopics(TOPIC_ALL_USERS);

        // Находим WebView по ID
        myWebView = findViewById(R.id.webview);
        setupWebView();

        // Сначала обрабатываем входящие уведомления
        handleIncomingNotification(getIntent());

        // Затем загружаем сессию (если не было уведомления)
        if (!wasNotificationHandled) {
            loadSavedSession();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent); // Важно: обновляем текущий intent
        handleIncomingNotification(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLifecycleManager.setAppInForeground(true);
        restoreCookies();

        // Проверяем интент еще раз в onResume на случай, если приложение было в фоне
        handleIncomingNotification(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLifecycleManager.setAppInForeground(false);
        saveLastUrl(myWebView.getUrl());
        saveCookies();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveCookies();
    }

    private void handleIncomingNotification(Intent intent) {
        Log.d(TAG, "handleIncomingNotification called");
        Log.d(TAG, "Intent action: " + intent.getAction());

        if (intent.getExtras() != null) {
            Log.d(TAG, "Intent extras keys: " + intent.getExtras().keySet());

            // Логируем все extras для отладки
            Bundle extras = intent.getExtras();
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "Extra [" + key + "]: " + value);
            }
        }

        // Проверяем разные возможные сценарии получения уведомления
        boolean shouldShowDialog = false;
        String title = null;
        String message = null;
        Bundle notificationExtras = null;

        // 1 Кастомное действие от FCM
        if (intent != null && (
                "SHOW_DIALOG_FROM_NOTIFICATION".equals(intent.getAction()) ||
                        "SHOW_DIALOG_FROM_FOREGROUND".equals(intent.getAction()))) {
            shouldShowDialog = true;
            title = intent.getStringExtra("title");
            message = intent.getStringExtra("message");
            notificationExtras = intent.getExtras();
        }
        // 2 Стандартный MAIN action с данными FCM
        else if (intent != null &&
                "android.intent.action.MAIN".equals(intent.getAction()) &&
                intent.hasExtra("from_fcm_notification")) {
            shouldShowDialog = true;
            title = intent.getStringExtra("title");
            message = intent.getStringExtra("message");
            notificationExtras = intent.getExtras();
        }
        // 3 Данные FCM в extras (когда приложение запускается из уведомления)
        else if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();

            // Проверяем наличие данных FCM
            if (extras.containsKey("google.message_id") ||
                    extras.containsKey("from") ||
                    extras.containsKey("collapse_key")) {

                // Ищем title и message в разных местах
                title = extras.getString("title");
                message = extras.getString("body");

                // Если не нашли, проверяем в analytics_data
                if ((title == null || message == null) && extras.containsKey("gcm.n.analytics_data")) {
                    Bundle analyticsData = extras.getBundle("gcm.n.analytics_data");
                    if (analyticsData != null) {
                        if (title == null) title = analyticsData.getString("title");
                        if (message == null) message = analyticsData.getString("body");
                        if (message == null) message = analyticsData.getString("message");
                    }
                }

                // Если все еще нет, используем значения по умолчанию
                if (title == null) title = "Мое приложение";
                if (message == null) message = "Это диалоговая пустышка для демонстрации перехода через уведомление, а также напоминание на переназначения функции, как сделано при обработке" +
                        " сообщения внутри приложения, не забудь!!!"
                        +" Сначала обработка, а потом чтение, а не наоборот";


                shouldShowDialog = true;
                notificationExtras = extras;
            }
        }

        if (shouldShowDialog && title != null && message != null) {
            Log.d(TAG, "Notification received - Title: " + title + ", Message: " + message);
            showNotificationDialog(title, message, notificationExtras);
            wasNotificationHandled = true;

            // Очищаем action и extras чтобы диалог не показывался повторно
            intent.setAction(null);
            if (intent.getExtras() != null) {
                intent.getExtras().clear();
            }
        } else {
            Log.d(TAG, "No notification data found or incomplete data");
        }
    }

    private void showNotificationDialog(String title, String message, Bundle extras) {
        Log.d(TAG, "showNotificationDialog called");

        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Собираем полное сообщение
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append(message);

            // Добавляем дополнительную информацию из data payload если есть
            if (extras != null) {
                Bundle dataBundle = extras.getBundle("notification_data");
                if (dataBundle != null && !dataBundle.isEmpty()) {
                    fullMessage.append("\n\nДополнительные данные:\n");

                    for (String key : dataBundle.keySet()) {
                        if (!"title".equals(key) && !"body".equals(key) && !"message".equals(key)) {
                            String value = dataBundle.getString(key);
                            if (value != null) {
                                fullMessage.append("• ").append(key).append(": ").append(value).append("\n");
                            }
                        }
                    }
                }
            }

            // Создаем ScrollView с TextView внутри
            ScrollView scrollView = new ScrollView(this);
            TextView messageView = new TextView(this);

            // Настраиваем TextView
            messageView.setText(fullMessage.toString());
            messageView.setTextSize(16);
            messageView.setPadding(50, 30, 50, 30);
            messageView.setTextIsSelectable(true); // выделение текста

            // Добавляем TextView в ScrollView
            scrollView.addView(messageView);

            // фиксированные размеры для ScrollView
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.5); // 50% высоты экрана
            int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9); // 90% ширины экрана

            // Устанавливаем размеры для ScrollView через LayoutParams
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    maxWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            builder.setTitle(title)
                    .setView(scrollView) // Используем setView вместо setMessage
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        // Если приложение было запущено из уведомления, загружаем сессию после закрытия диалога
                        if (wasNotificationHandled && myWebView != null) {
                            loadSavedSession();
                        }
                    })
                    .setCancelable(true);

            AlertDialog dialog = builder.create();

            // Устанавливаем фиксированный размер окна
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.copyFrom(window.getAttributes());
                layoutParams.width = maxWidth;
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(layoutParams);

                // Ограничиваем максимальную высоту через WindowManager
                window.setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            dialog.show();

            Log.d(TAG, "Dialog shown successfully");
        });
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
                        saveCookies();
                    } else if ("not_logged_in".equals(status)) {
                        setLoggedIn(false);
                    }
                }
            });
        }
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