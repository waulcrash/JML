package com.example.jml;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;

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

    private FrameLayout notificationButton;
    private TextView notificationBadge;

    private List<NotificationStorage.NotificationData> notificationsList = new ArrayList<>();
    private int currentNotificationIndex = -1;

    private static final String SHOWN_NOTIFICATIONS_PREFS = "shown_notifications";
    private static final String KEY_SHOWN_IDS = "shown_ids";

    private NotificationReceiver notificationReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");
        debugIntent(getIntent());

        wasNotificationHandled = false;
        lastNotificationTime = 0;

        initNotificationUI();
        subscribeToTopics(TOPIC_ALL_USERS);

        myWebView = findViewById(R.id.webview);
        setupWebView();

        // Регистрируем BroadcastReceiver
        notificationReceiver = new NotificationReceiver();
        IntentFilter filter = new IntentFilter("NEW_NOTIFICATION_RECEIVED");
        registerReceiver(notificationReceiver, filter);

        // ТОЛЬКО загружаем уведомления, НЕ показываем автоматически
        loadNotificationsAndUpdateUI();

        // Проверяем интент на уведомления
        processIntentForNotification(getIntent());

        // Проверяем сохраненные уведомления из SharedPreferences
        checkAppNotificationsForStorage();

        // Всегда загружаем сессию
        loadSavedSession();
    }

    private void initNotificationUI() {
        notificationButton = findViewById(R.id.notificationButton);
        notificationBadge = findViewById(R.id.notificationBadge);

        notificationButton.setOnClickListener(v -> {
            if (notificationsList.isEmpty()) {
                Toast.makeText(MainActivity.this, "Нет новых уведомлений", Toast.LENGTH_SHORT).show();
                notificationButton.setVisibility(View.GONE);
                return;
            }
            showNextNotification();
        });
    }

    private void loadNotificationsAndUpdateUI() {
        // Загружаем только НЕпрочитанные уведомления
        notificationsList = NotificationStorage.getInstance(this).getUnshownNotifications();

        // Убираем те, которые уже были показаны в этом сеансе
        List<NotificationStorage.NotificationData> filteredList = new ArrayList<>();
        SharedPreferences shownPrefs = getSharedPreferences(SHOWN_NOTIFICATIONS_PREFS, MODE_PRIVATE);
        String shownIdsJson = shownPrefs.getString(KEY_SHOWN_IDS, "[]");

        try {
            JSONArray shownIdsArray = new JSONArray(shownIdsJson);
            List<String> shownIds = new ArrayList<>();
            for (int i = 0; i < shownIdsArray.length(); i++) {
                shownIds.add(shownIdsArray.getString(i));
            }

            for (NotificationStorage.NotificationData notification : notificationsList) {
                if (!shownIds.contains(notification.id)) {
                    filteredList.add(notification);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error filtering shown notifications", e);
            filteredList = notificationsList;
        }

        notificationsList = filteredList;
        updateNotificationBadge(notificationsList.size());

        runOnUiThread(() -> {
            if (notificationsList.size() > 0) {
                notificationButton.setVisibility(View.VISIBLE);
            } else {
                notificationButton.setVisibility(View.GONE);
            }
        });
    }

    private void updateNotificationBadge(int count) {
        runOnUiThread(() -> {
            if (count > 0) {
                notificationBadge.setText(String.valueOf(count));
                notificationBadge.setVisibility(View.VISIBLE);
            } else {
                notificationBadge.setVisibility(View.GONE);
            }
        });
    }

    private void showNextNotification() {
        if (notificationsList.isEmpty()) {
            Toast.makeText(this, "Нет новых уведомлений", Toast.LENGTH_SHORT).show();
            notificationButton.setVisibility(View.GONE);
            return;
        }

        currentNotificationIndex++;
        if (currentNotificationIndex >= notificationsList.size()) {
            currentNotificationIndex = 0;
        }

        NotificationStorage.NotificationData notification = notificationsList.get(currentNotificationIndex);
        showNotificationDialog(notification, true);
    }

    private void showNotificationDialog(NotificationStorage.NotificationData notification, boolean fromButton) {
        if (wasNotificationHandled && !fromButton) {
            Log.d(TAG, "Dialog already showing, skipping notification: " + notification.id);
            return;
        }

        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append(notification.message);

            if (notification.data != null && !notification.data.isEmpty()) {
                fullMessage.append("\n\nДополнительные данные:\n");
                for (Map.Entry<String, String> entry : notification.data.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (!"title".equals(key) && !"body".equals(key) && !"message".equals(key)) {
                        if (value != null) {
                            fullMessage.append("• ").append(key).append(": ").append(value).append("\n");
                        }
                    }
                }
            }

            ScrollView scrollView = new ScrollView(this);
            TextView messageView = new TextView(this);
            messageView.setText(fullMessage.toString());
            messageView.setTextSize(16);
            messageView.setPadding(50, 30, 50, 30);
            messageView.setTextIsSelectable(true);
            scrollView.addView(messageView);

            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
            int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

            String positiveButtonText = "OK";
            if (fromButton && notificationsList.size() > 1) {
                positiveButtonText = "Следующее (" + (currentNotificationIndex + 1) + "/" + notificationsList.size() + ")";
            }

            builder.setTitle(notification.title)
                    .setView(scrollView)
                    .setPositiveButton(positiveButtonText, (dialog, which) -> {
                        dialog.dismiss();
                        wasNotificationHandled = false;

                        // ОТМЕЧАЕМ КАК ПРОЧИТАННОЕ ТОЛЬКО ПРИ НАЖАТИИ "OK"
                        NotificationStorage.getInstance(this).markAsShown(notification.id);

                        // Добавляем в список показанных в этом сеансе
                        markAsShownInSession(notification.id);

                        if (fromButton) {
                            // Удаляем из локального списка
                            notificationsList.remove(currentNotificationIndex);
                            currentNotificationIndex--;
                            loadNotificationsAndUpdateUI();

                            // Если есть еще уведомления, показываем следующее
                            if (!notificationsList.isEmpty()) {
                                if (currentNotificationIndex < 0) currentNotificationIndex = 0;
                                showNextNotification();
                            }
                        } else {
                            loadNotificationsAndUpdateUI();
                        }
                    })
                    .setNegativeButton("Закрыть", (dialog, which) -> {
                        dialog.dismiss();
                        wasNotificationHandled = false;

                        if (fromButton) {
                            // ПРИ ЗАКРЫТИИ - НЕ УДАЛЯЕМ уведомления, только скрываем диалог
                            loadNotificationsAndUpdateUI();
                        }
                    })
                    .setOnCancelListener(dialog -> {
                        wasNotificationHandled = false;

                        if (fromButton) {
                            // ПРИ ОТМЕНЕ - НЕ УДАЛЯЕМ уведомления
                            loadNotificationsAndUpdateUI();
                        }
                    })
                    .setCancelable(true);

            if (notificationsList.size() > 1 && fromButton) {
                builder.setNeutralButton("Прочитать все", (dialog, which) -> {
                    // Помечаем все как прочитанные
                    for (NotificationStorage.NotificationData n : notificationsList) {
                        NotificationStorage.getInstance(this).markAsShown(n.id);
                        markAsShownInSession(n.id);
                    }
                    notificationsList.clear();
                    loadNotificationsAndUpdateUI();
                    Toast.makeText(this, "Все уведомления прочитаны", Toast.LENGTH_SHORT).show();
                });
            }

            AlertDialog dialog = builder.create();
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

            // Добавляем в список показанных в этом сеансе (для предотвращения повторного показа)
            if (!fromButton) {
                markAsShownInSession(notification.id);
            }
        });
    }

    private void markAsShownInSession(String notificationId) {
        try {
            SharedPreferences shownPrefs = getSharedPreferences(SHOWN_NOTIFICATIONS_PREFS, MODE_PRIVATE);
            String shownIdsJson = shownPrefs.getString(KEY_SHOWN_IDS, "[]");

            JSONArray shownIdsArray = new JSONArray(shownIdsJson);

            // Проверяем, нет ли уже этого ID
            boolean alreadyExists = false;
            for (int i = 0; i < shownIdsArray.length(); i++) {
                if (notificationId.equals(shownIdsArray.getString(i))) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                shownIdsArray.put(notificationId);
                shownPrefs.edit().putString(KEY_SHOWN_IDS, shownIdsArray.toString()).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking notification as shown in session", e);
        }
    }

    private void clearShownInSession() {
        SharedPreferences shownPrefs = getSharedPreferences(SHOWN_NOTIFICATIONS_PREFS, MODE_PRIVATE);
        shownPrefs.edit().clear().apply();
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

        // При возобновлении активности обновляем список уведомлений
        loadNotificationsAndUpdateUI();
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
        clearShownInSession();

        // Отменяем регистрацию ресивера
        if (notificationReceiver != null) {
            unregisterReceiver(notificationReceiver);
        }
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

            // ВАЖНО: Выводим ВСЕ ключи для поиска текста уведомления
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                String valueStr;
                if (value instanceof String) {
                    valueStr = "\"" + value + "\"";
                } else if (value instanceof Bundle) {
                    valueStr = "Bundle with keys: " + ((Bundle) value).keySet();
                } else {
                    valueStr = String.valueOf(value);
                }
                Log.d(TAG, "  [" + key + "] = " + valueStr +
                        " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");

                // Если это Bundle, выводим его содержимое
                if (value instanceof Bundle) {
                    Bundle innerBundle = (Bundle) value;
                    for (String innerKey : innerBundle.keySet()) {
                        Object innerValue = innerBundle.get(innerKey);
                        Log.d(TAG, "    -> [" + innerKey + "] = " + innerValue);
                    }
                }
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
                Log.d(TAG, "Received notification from service: " + actualTitle);
                saveNotificationFromIntent(actualTitle, actualBody, intent.getExtras());
                return;
            }
        }

        // 2. ОСНОВНОЙ МЕТОД: Проверяем уведомление из системного пуша
        // Это происходит, когда пользователь нажимает на системное уведомление
        boolean hasFcmIndicators = intent.hasExtra("google.message_id") ||
                intent.hasExtra("gcm.message_id") ||
                intent.hasExtra("from") ||
                intent.hasExtra("gcm.n.e") ||
                intent.hasExtra("gcm.n.analytics_data");

        if (Intent.ACTION_MAIN.equals(intent.getAction()) || hasFcmIndicators) {

            Log.d(TAG, "Processing push notification from intent");

            String title = null;
            String body = null;

            // ПРОВЕРЯЕМ ВСЕ ВОЗМОЖНЫЕ МЕСТА ГДЕ МОЖЕТ БЫТЬ ТЕКСТ УВЕДОМЛЕНИЯ

            // 1. Пробуем получить из стандартных полей FCM (НОВЫЕ КЛЮЧИ!)
            if (intent.hasExtra("gcm.notification.title")) {
                title = intent.getStringExtra("gcm.notification.title");
                Log.d(TAG, "Found title in gcm.notification.title: " + title);
            }

            if (intent.hasExtra("gcm.notification.body")) {
                body = intent.getStringExtra("gcm.notification.body");
                Log.d(TAG, "Found body in gcm.notification.body: " + body);
            }

            // 2. Альтернативные ключи для новых версий FCM
            if (title == null && intent.hasExtra("google.c.a.c_l")) {
                title = intent.getStringExtra("google.c.a.c_l");
                Log.d(TAG, "Found title in google.c.a.c_l: " + title);
            }

            if (body == null && intent.hasExtra("google.c.a.c_c")) {
                body = intent.getStringExtra("google.c.a.c_c");
                Log.d(TAG, "Found body in google.c.a.c_c: " + body);
            }

            // 3. Проверяем analytics_data bundle
            if ((title == null || body == null) && intent.hasExtra("gcm.n.analytics_data")) {
                Bundle analyticsData = intent.getBundleExtra("gcm.n.analytics_data");
                if (analyticsData != null) {
                    // Пробуем получить title
                    if (title == null && analyticsData.containsKey("google.c.a.c_l")) {
                        title = analyticsData.getString("google.c.a.c_l");
                        Log.d(TAG, "Got title from analytics.google.c.a.c_l: " + title);
                    }

                    // Пробуем найти body в analytics
                    if (body == null && analyticsData.containsKey("google.c.a.c_c")) {
                        body = analyticsData.getString("google.c.a.c_c");
                        Log.d(TAG, "Got body from analytics.google.c.a.c_c: " + body);
                    }

                    // Ищем по ключевым словам
                    if (body == null) {
                        for (String key : analyticsData.keySet()) {
                            if (key.contains("body") || key.contains("message") ||
                                    key.contains("content") || key.contains("text")) {
                                Object value = analyticsData.get(key);
                                if (value instanceof String) {
                                    body = (String) value;
                                    Log.d(TAG, "Got body from analytics." + key + ": " + body);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // 4. Проверяем обычные поля
            if (title == null && intent.hasExtra("title")) {
                title = intent.getStringExtra("title");
                Log.d(TAG, "Found title in title: " + title);
            }

            if (body == null && intent.hasExtra("body")) {
                body = intent.getStringExtra("body");
                Log.d(TAG, "Found body in body: " + body);
            }

            if (body == null && intent.hasExtra("message")) {
                body = intent.getStringExtra("message");
                Log.d(TAG, "Found body in message: " + body);
            }

            if (body == null && intent.hasExtra("text")) {
                body = intent.getStringExtra("text");
                Log.d(TAG, "Found body in text: " + body);
            }

            // 5. Проверяем data payload (если было отправлено как data message)
            Bundle extras = intent.getExtras();
            if (extras != null) {
                for (String key : extras.keySet()) {
                    // Пропускаем служебные ключи
                    if (key.startsWith("gcm.") || key.startsWith("google.") ||
                            key.startsWith("from") || key.equals("collapse_key")) {
                        continue;
                    }

                    Object value = extras.get(key);
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        // Если это похоже на title (короткий текст)
                        if (title == null && stringValue.length() < 50 &&
                                !stringValue.contains("=") && !stringValue.contains("&")) {
                            title = stringValue;
                            Log.d(TAG, "Possible title from " + key + ": " + title);
                        }
                        // Если это похоже на body (длинный текст)
                        else if (body == null && stringValue.length() > 10) {
                            body = stringValue;
                            Log.d(TAG, "Possible body from " + key + ": " + body);
                        }
                    }
                }
            }

            // 6. Если title найден, но body нет - используем заглушку
            if (title != null && body == null) {
                body = "Новое уведомление";
                Log.d(TAG, "Using default body");
            }

            // 7. Если title не найден - используем заглушку
            if (title == null && body != null) {
                title = "Мое приложение";
                Log.d(TAG, "Using default title");
            }

            // 8. Если ничего не найдено
            if (title == null && body == null) {
                Log.d(TAG, "No notification data found in intent");
                return;
            }

            String actualTitle = title != null ? title : "Мое приложение";
            String actualBody = body != null ? body : "Новое уведомление";

            Log.d(TAG, "Extracted push notification - Title: " + actualTitle + ", Body: " + actualBody);
            saveNotificationFromIntent(actualTitle, actualBody, intent.getExtras());
            return;
        }

        // 3. Проверяем другие кастомные действия
        if ("SHOW_SAVED_NOTIFICATION".equals(intent.getAction())) {
            Log.d(TAG, "SHOW_SAVED_NOTIFICATION action");
            String notificationId = intent.getStringExtra("notification_id");
            if (notificationId != null) {
                showNotificationFromStorage(notificationId);
                return;
            }
        }

        Log.d(TAG, "No notification data found");
    }

    private void saveNotificationFromIntent(String title, String body, Bundle originalExtras) {
        Map<String, String> data = new HashMap<>();
        data.put("title", title);
        data.put("body", body);
        data.put("source", "push_intent");

        if (originalExtras != null) {
            for (String key : originalExtras.keySet()) {
                Object value = originalExtras.get(key);
                if (value instanceof String) {
                    data.put(key, (String) value);
                }
            }
        }

        String notificationId = "push_" + System.currentTimeMillis();

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

        // Обновляем UI (показываем кнопку с бейджем)
        loadNotificationsAndUpdateUI();

        // Показываем краткое сообщение
        Toast.makeText(this, "Новое уведомление", Toast.LENGTH_SHORT).show();
    }

    private void checkAppNotificationsForStorage() {
        Log.d(TAG, "Checking app notifications from SharedPreferences for storage");

        SharedPreferences prefs = getSharedPreferences(APP_NOTIFICATIONS_PREFS, MODE_PRIVATE);
        String notificationsJson = prefs.getString("notifications_list", "[]");
        long lastNotificationTimePref = prefs.getLong("last_notification_time", 0);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTimePref > 5 * 60 * 1000) {
            Log.d(TAG, "Last notification is too old, clearing");
            prefs.edit().clear().apply();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(notificationsJson);
            if (jsonArray.length() > 0) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject notificationObj = jsonArray.getJSONObject(i);
                    String title = notificationObj.getString("title");
                    String body = notificationObj.getString("body");
                    long timestamp = notificationObj.getLong("timestamp");

                    Map<String, String> data = new HashMap<>();
                    data.put("title", title);
                    data.put("body", body);
                    data.put("source", "app_prefs");

                    Iterator<String> keys = notificationObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (!key.equals("title") && !key.equals("body") && !key.equals("timestamp")) {
                            data.put(key, notificationObj.getString(key));
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

                    // Сохраняем в хранилище
                    NotificationStorage.getInstance(this).saveNotification(notificationData);
                }

                // Очищаем SharedPreferences после сохранения
                prefs.edit().clear().apply();
                Log.d(TAG, "Saved app notifications to storage and cleared prefs");

                // Обновляем UI
                loadNotificationsAndUpdateUI();
            } else {
                Log.d(TAG, "No app notifications found in SharedPreferences");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading app notifications", e);
        }
    }

    private void showNotificationFromStorage(String notificationId) {
        Log.d(TAG, "Looking for notification with id: " + notificationId);

        NotificationStorage.NotificationData notification =
                NotificationStorage.getInstance(this).getNotificationById(notificationId);

        if (notification != null) {
            Log.d(TAG, "Found notification: " + notification.title);
            showNotificationDialog(notification, false);
        } else {
            Log.d(TAG, "Notification with id " + notificationId + " not found");
        }
    }

    private void setupWebView() {
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(myWebView, true);

        restoreCookies();
        myWebView.addJavascriptInterface(new JoomlaInterface(this), "Android");

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                saveLastUrl(url);
                saveCookies();
                view.postDelayed(() -> checkLoginStatus(), 1000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
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
            myWebView.loadUrl(lastUrl);
            subscribeToTopics(TOPIC_LOGGED_IN_USERS);
            Log.d(TAG, "Восстановлена сессия: " + lastUrl);
        } else {
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

    private class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("NEW_NOTIFICATION_RECEIVED".equals(intent.getAction())) {
                String title = intent.getStringExtra("title");
                String body = intent.getStringExtra("body");
                long timestamp = intent.getLongExtra("timestamp", 0);

                Log.d(TAG, "Received broadcast notification: " + title);

                // Сохраняем уведомление в хранилище
                Map<String, String> data = new HashMap<>();
                data.put("title", title);
                data.put("body", body);
                data.put("source", "broadcast");

                // Извлекаем дополнительные данные
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        Object value = extras.get(key);
                        if (value instanceof String &&
                                !"title".equals(key) &&
                                !"body".equals(key) &&
                                !"timestamp".equals(key)) {
                            data.put(key, (String) value);
                        }
                    }
                }

                String notificationId = "broadcast_" + System.currentTimeMillis();

                NotificationStorage.NotificationData notificationData =
                        new NotificationStorage.NotificationData(
                                notificationId,
                                title,
                                body,
                                data,
                                System.currentTimeMillis()
                        );

                NotificationStorage.getInstance(MainActivity.this).saveNotification(notificationData);

                // Обновляем UI в основном потоке
                runOnUiThread(() -> {
                    loadNotificationsAndUpdateUI();
                    // Показываем краткое сообщение
                    Toast.makeText(MainActivity.this, "Новое уведомление", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
}