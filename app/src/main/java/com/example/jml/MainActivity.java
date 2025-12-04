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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "JoomlaSession";
    private static final String KEY_LAST_URL = "lastUrl";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_COOKIES = "cookies";
    private static final String APP_NOTIFICATIONS_PREFS = "app_notifications";

    private static final String TOPIC_ALL_USERS = "all_users";
    private static final String TOPIC_LOGGED_IN_USERS = "logged_in_users";

    private boolean wasNotificationHandled = false;
    private long lastNotificationTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");
        debugIntent(getIntent());

        wasNotificationHandled = false;
        lastNotificationTime = 0;

        // Подписываемся на тему для уведомлений
        subscribeToTopics(TOPIC_ALL_USERS);

        // Находим WebView по ID
        myWebView = findViewById(R.id.webview);
        setupWebView();

        // Сначала обрабатываем входящие уведомления
        processIntentForNotification(getIntent());

        // Затем проверяем сохраненные уведомления из SharedPreferences
        checkAppNotifications();

        // Затем проверяем сохраненные уведомления из NotificationStorage
        checkSavedNotifications();

        // Загружаем сессию (если не было уведомления)
        if (!wasNotificationHandled) {
            loadSavedSession();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent);
        debugIntent(intent);
        processIntentForNotification(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLifecycleManager.setAppInForeground(true);
        restoreCookies();

        // При входе проверяем сохраненные уведомления
        checkAppNotifications();
        checkSavedNotifications();

        // Проверяем интент
        processIntentForNotification(getIntent());
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

    private void debugIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "Intent is null");
            return;
        }

        Log.d(TAG, "=== DEBUG INTENT ===");
        Log.d(TAG, "Action: " + intent.getAction());
        Log.d(TAG, "Data: " + intent.getData());
        Log.d(TAG, "Flags: " + intent.getFlags());
        Log.d(TAG, "Component: " + intent.getComponent());

        if (intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            Log.d(TAG, "Extras count: " + extras.size());

            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                String valueStr;
                if (value instanceof String) {
                    valueStr = "\"" + value + "\"";
                } else {
                    valueStr = String.valueOf(value);
                }
                Log.d(TAG, "  [" + key + "] = " + valueStr +
                        " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
            }
        }
        Log.d(TAG, "=== END DEBUG ===");
    }

    private void processIntentForNotification(Intent intent) {
        Log.d(TAG, "processIntentForNotification called");

        if (intent == null) {
            Log.d(TAG, "Intent is null");
            return;
        }

        debugIntent(intent);

        // Проверяем время
        long currentTime = System.currentTimeMillis();
        long intentTime = intent.getLongExtra("timestamp", 0);

        if (intentTime > 0 && (currentTime - intentTime) > 30000) {
            Log.d(TAG, "Ignoring old intent");
            return;
        }

        // 1. Проверяем наше кастомное действие из сервиса
        if ("SHOW_NOTIFICATION_FROM_SERVICE".equals(intent.getAction())) {
            Log.d(TAG, "SHOW_NOTIFICATION_FROM_SERVICE action");

            String title = intent.getStringExtra("title");
            String body = intent.getStringExtra("body");

            if (title != null || body != null) {
                String actualTitle = title != null ? title : "Мое приложение";
                String actualBody = body != null ? body : "Новое уведомление";

                Log.d(TAG, "Showing notification from service: " + actualTitle);
                createAndShowNotification(actualTitle, actualBody, intent.getExtras());
                return;
            }
        }

        // 2. Проверяем ACTION_MAIN с from_system_notification
        if (Intent.ACTION_MAIN.equals(intent.getAction()) &&
                intent.getBooleanExtra("from_system_notification", false)) {
            Log.d(TAG, "ACTION_MAIN from system notification");

            String title = intent.getStringExtra("title");
            String body = intent.getStringExtra("body");

            if (title != null || body != null) {
                String actualTitle = title != null ? title : "Мое приложение";
                String actualBody = body != null ? body : "Новое уведомление";

                Log.d(TAG, "Showing notification from system click: " + actualTitle);
                createAndShowNotification(actualTitle, actualBody, intent.getExtras());
                return;
            }
        }

        // 3. Проверяем ACTION_MAIN с данными FCM
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            Log.d(TAG, "ACTION_MAIN intent received");

            Bundle extras = intent.getExtras();
            if (extras != null) {
                // Проверяем наличие FCM данных
                boolean hasFcmData = extras.containsKey("google.message_id") ||
                        extras.containsKey("gcm.message_id") ||
                        extras.containsKey("from") ||
                        extras.containsKey("gcm.n.analytics_data");

                if (hasFcmData) {
                    Log.d(TAG, "Found FCM data in ACTION_MAIN intent");

                    // Извлекаем данные из analytics_data
                    String title = null;
                    String body = null;

                    if (extras.containsKey("gcm.n.analytics_data")) {
                        Bundle analyticsData = extras.getBundle("gcm.n.analytics_data");
                        if (analyticsData != null) {
                            // Пробуем получить title
                            if (analyticsData.containsKey("google.c.a.c_l")) {
                                title = analyticsData.getString("google.c.a.c_l");
                                Log.d(TAG, "Got title from google.c.a.c_l: " + title);
                            }

                            // Пробуем найти body в других полях
                            for (String key : analyticsData.keySet()) {
                                if (key.contains("body") || key.contains("message") ||
                                        key.contains("content") || key.contains("text")) {
                                    Object value = analyticsData.get(key);
                                    if (value instanceof String) {
                                        body = (String) value;
                                        Log.d(TAG, "Got body from " + key + ": " + body);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (title != null) {
                        String actualTitle = title;
                        String actualBody = body != null ? body : "Новое уведомление";

                        Log.d(TAG, "Extracted notification - Title: " + actualTitle + ", Body: " + actualBody);
                        createAndShowNotification(actualTitle, actualBody, extras);
                        return;
                    }
                }
            }
        }

        // 4. Проверяем другие кастомные действия
        if ("SHOW_SAVED_NOTIFICATION".equals(intent.getAction())) {
            Log.d(TAG, "SHOW_SAVED_NOTIFICATION action");
            String notificationId = intent.getStringExtra("notification_id");
            if (notificationId != null) {
                showNotificationFromStorage(notificationId);
                return;
            }
        }

        // 5. Если ничего не нашли, проверяем сохраненные уведомления
        Log.d(TAG, "No immediate notification data, checking saved notifications");
        checkAppNotifications();
        checkSavedNotifications();
    }

    private void checkAppNotifications() {
        Log.d(TAG, "Checking app notifications from SharedPreferences");

        SharedPreferences prefs = getSharedPreferences(APP_NOTIFICATIONS_PREFS, MODE_PRIVATE);
        String notificationsJson = prefs.getString("notifications_list", "[]");
        long lastNotificationTimePref = prefs.getLong("last_notification_time", 0);

        // Проверяем не слишком ли старое уведомление (больше 5 минут)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTimePref > 5 * 60 * 1000) {
            Log.d(TAG, "Last notification is too old, clearing");
            prefs.edit().clear().apply();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(notificationsJson);
            if (jsonArray.length() > 0) {
                // Берем самое свежее уведомление
                JSONObject latestNotification = jsonArray.getJSONObject(0);
                String title = latestNotification.getString("title");
                String body = latestNotification.getString("body");
                long timestamp = latestNotification.getLong("timestamp");

                Log.d(TAG, "Found app notification: " + title);

                // Проверяем, не показывали ли мы уже это уведомление
                long timeSinceLastNotification = currentTime - lastNotificationTime;
                if (!wasNotificationHandled &&
                        (currentTime - timestamp < 5 * 60 * 1000) &&
                        timeSinceLastNotification > 1000) {

                    // Создаем NotificationData
                    Map<String, String> data = new HashMap<>();
                    data.put("title", title);
                    data.put("body", body);

                    // Собираем все остальные поля
                    Iterator<String> keys = latestNotification.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (!key.equals("title") && !key.equals("body") && !key.equals("timestamp")) {
                            data.put(key, latestNotification.getString(key));
                        }
                    }

                    NotificationStorage.NotificationData notificationData =
                            new NotificationStorage.NotificationData(
                                    "app_pref_" + timestamp,
                                    title,
                                    body,
                                    data,
                                    timestamp
                            );

                    showNotificationDialog(notificationData);

                    // Удаляем показанное уведомление из списка
                    removeShownNotificationFromPrefs();
                } else {
                    Log.d(TAG, "App notification skipped (already shown or too recent)");
                }
            } else {
                Log.d(TAG, "No app notifications found in SharedPreferences");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading app notifications", e);
        }
    }

    private void removeShownNotificationFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(APP_NOTIFICATIONS_PREFS, MODE_PRIVATE);
            String notificationsJson = prefs.getString("notifications_list", "[]");

            JSONArray jsonArray = new JSONArray(notificationsJson);
            if (jsonArray.length() > 0) {
                // Удаляем первое (последнее) уведомление
                JSONArray newArray = new JSONArray();
                for (int i = 1; i < jsonArray.length(); i++) {
                    newArray.put(jsonArray.get(i));
                }

                prefs.edit()
                        .putString("notifications_list", newArray.toString())
                        .apply();

                Log.d(TAG, "Removed shown notification from preferences");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing shown notification", e);
        }
    }

    private void checkSavedNotifications() {
        Log.d(TAG, "Checking saved notifications from storage");

        List<NotificationStorage.NotificationData> unshownNotifications =
                NotificationStorage.getInstance(this).getUnshownNotifications();

        if (!unshownNotifications.isEmpty()) {
            Log.d(TAG, "Found " + unshownNotifications.size() + " unshown notifications");

            // Показываем самое свежее уведомление
            NotificationStorage.NotificationData latestNotification = unshownNotifications.get(0);

            long timeSinceLastNotification = System.currentTimeMillis() - lastNotificationTime;

            if (!wasNotificationHandled && timeSinceLastNotification > 1000) {
                Log.d(TAG, "Showing latest unshown notification: " + latestNotification.title);
                showNotificationDialog(latestNotification);
            } else {
                Log.d(TAG, "Notification already showing or shown too recently");
            }
        } else {
            Log.d(TAG, "No unshown notifications found");
        }
    }

    private void showNotificationFromStorage(String notificationId) {
        Log.d(TAG, "Looking for notification with id: " + notificationId);

        NotificationStorage.NotificationData notification =
                NotificationStorage.getInstance(this).getNotificationById(notificationId);

        if (notification != null) {
            Log.d(TAG, "Found notification: " + notification.title);
            showNotificationDialog(notification);
        } else {
            Log.d(TAG, "Notification with id " + notificationId + " not found");
        }
    }

    private void createAndShowNotification(String title, String body, Bundle originalExtras) {
        // Проверяем, не показывали ли мы уже такое уведомление
        long timeSinceLastNotification = System.currentTimeMillis() - lastNotificationTime;
        if (timeSinceLastNotification < 1000) {
            Log.d(TAG, "Notification shown too recently, skipping");
            return;
        }

        // Сохраняем уведомление
        Map<String, String> data = new HashMap<>();
        data.put("title", title);
        data.put("body", body);
        data.put("source", "intent");

        // Сохраняем дополнительные данные если есть
        if (originalExtras != null) {
            for (String key : originalExtras.keySet()) {
                Object value = originalExtras.get(key);
                if (value instanceof String) {
                    data.put(key, (String) value);
                }
            }
        }

        String notificationId = "intent_" + System.currentTimeMillis();

        NotificationStorage.NotificationData notificationData =
                new NotificationStorage.NotificationData(
                        notificationId,
                        title,
                        body,
                        data,
                        System.currentTimeMillis()
                );

        // Сохраняем в хранилище
        NotificationStorage.getInstance(this).saveNotification(notificationData);

        // Показываем диалог
        showNotificationDialog(notificationData);
    }

    private void showNotificationDialog(NotificationStorage.NotificationData notification) {
        if (wasNotificationHandled) {
            Log.d(TAG, "Dialog already showing, skipping notification: " + notification.id);
            return;
        }

        Log.d(TAG, "Showing notification dialog: " + notification.id);
        Log.d(TAG, "Title: " + notification.title);
        Log.d(TAG, "Message: " + notification.message);

        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Собираем полное сообщение
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append(notification.message);

            // Добавляем дополнительную информацию из data payload если есть
            if (notification.data != null && !notification.data.isEmpty()) {
                fullMessage.append("\n\nДополнительные данные:\n");

                for (Map.Entry<String, String> entry : notification.data.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    // Пропускаем стандартные поля
                    if (!"title".equals(key) && !"body".equals(key) && !"message".equals(key)) {
                        if (value != null) {
                            fullMessage.append("• ").append(key).append(": ").append(value).append("\n");
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
            messageView.setTextIsSelectable(true);

            // Добавляем TextView в ScrollView
            scrollView.addView(messageView);

            // фиксированные размеры для ScrollView
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
            int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);

            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    maxWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            builder.setTitle(notification.title)
                    .setView(scrollView)
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        wasNotificationHandled = false;

                        // Помечаем уведомление как показанное
                        NotificationStorage.getInstance(this).markAsShown(notification.id);

                        // Загружаем сессию после закрытия диалога
                        if (myWebView != null) {
                            loadSavedSession();
                        }
                    })
                    .setOnCancelListener(dialog -> {
                        wasNotificationHandled = false;

                        // Помечаем уведомление как показанное
                        NotificationStorage.getInstance(this).markAsShown(notification.id);

                        // Загружаем сессию
                        if (myWebView != null) {
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
                window.setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            dialog.show();
            wasNotificationHandled = true;
            lastNotificationTime = System.currentTimeMillis();

            Log.d(TAG, "Notification dialog shown: " + notification.id);
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