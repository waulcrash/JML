package com.example.jml;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "WEBVIEW_APP_CHANNEL";
    private static final Random random = new Random();

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "=== NEW FCM MESSAGE RECEIVED ===");
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Message data payload size: " + data.size());

        // ЛОГИРУЕМ ВСЕ ДАННЫЕ ДЛЯ ОТЛАДКИ
        if (!data.isEmpty()) {
            Log.d(TAG, "Data payload content:");
            for (Map.Entry<String, String> entry : data.entrySet()) {
                Log.d(TAG, "  [" + entry.getKey() + "] = " + entry.getValue());
            }
        }

        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Notification payload:");
            Log.d(TAG, "  Title: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "  Body: " + remoteMessage.getNotification().getBody());
            Log.d(TAG, "  Click Action: " + remoteMessage.getNotification().getClickAction());
            Log.d(TAG, "  Icon: " + remoteMessage.getNotification().getIcon());
            Log.d(TAG, "  Tag: " + remoteMessage.getNotification().getTag());
        }

        String title = "Мое приложение";
        String body = "Новое уведомление";

        // 1. Пробуем получить из notification payload (Firebase Console)
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
                Log.d(TAG, "Using title from notification payload: " + title);
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
                Log.d(TAG, "Using body from notification payload: " + body);
            }
        }

        // 2. Data payload имеет приоритет (если отправляется с сервера)
        if (data != null && !data.isEmpty()) {
            // Проверяем все возможные ключи для title
            if (data.containsKey("title")) {
                title = data.get("title");
                Log.d(TAG, "Using title from data.title: " + title);
            } else if (data.containsKey("subject")) {
                title = data.get("subject");
                Log.d(TAG, "Using title from data.subject: " + title);
            } else if (data.containsKey("header")) {
                title = data.get("header");
                Log.d(TAG, "Using title from data.header: " + title);
            } else if (data.containsKey("google.c.a.c_l")) {
                title = data.get("google.c.a.c_l");
                Log.d(TAG, "Using title from google.c.a.c_l: " + title);
            }

            // Проверяем все возможные ключи для body
            if (data.containsKey("body")) {
                body = data.get("body");
                Log.d(TAG, "Using body from data.body: " + body);
            } else if (data.containsKey("message")) {
                body = data.get("message");
                Log.d(TAG, "Using body from data.message: " + body);
            } else if (data.containsKey("text")) {
                body = data.get("text");
                Log.d(TAG, "Using body from data.text: " + body);
            } else if (data.containsKey("content")) {
                body = data.get("content");
                Log.d(TAG, "Using body from data.content: " + body);
            } else if (data.containsKey("description")) {
                body = data.get("description");
                Log.d(TAG, "Using body from data.description: " + body);
            } else if (data.containsKey("google.c.a.c_c")) {
                body = data.get("google.c.a.c_c");
                Log.d(TAG, "Using body from google.c.a.c_c: " + body);
            }
        }

        Log.d(TAG, "Final notification - Title: " + title + ", Body: " + body);
        Log.d(TAG, "App foreground: " + AppLifecycleManager.isAppInForeground());

        // Всегда сохраняем уведомление
        saveNotificationToAppPreferences(title, body, data);

        // ПРОВЕРЯЕМ: если приложение в foreground, НЕ показываем системное уведомление
        if (AppLifecycleManager.isAppInForeground()) {
            Log.d(TAG, "App is in foreground, NOT showing system notification");
            // Отправляем broadcast для обновления UI в MainActivity
            sendNotificationToMainActivity(title, body, data);
        } else {
            Log.d(TAG, "App is in background, showing system notification");
            // Показываем системное уведомление
            showSystemNotification(title, body, data);
        }

        Log.d(TAG, "=== END FCM MESSAGE PROCESSING ===");
    }

    private void sendNotificationToMainActivity(String title, String body, Map<String, String> data) {
        // Отправляем broadcast для обновления UI в MainActivity
        Intent intent = new Intent("NEW_NOTIFICATION_RECEIVED");
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        intent.putExtra("timestamp", System.currentTimeMillis());

        if (data != null && !data.isEmpty()) {
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                bundle.putString(entry.getKey(), entry.getValue());
            }
            intent.putExtras(bundle);
        }

        sendBroadcast(intent);
        Log.d(TAG, "Sent broadcast to MainActivity for notification: " + title);
    }

    private void saveNotificationToAppPreferences(String title, String body, Map<String, String> data) {
        try {
            SharedPreferences prefs = getSharedPreferences("app_notifications", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            String notificationsJson = prefs.getString("notifications_list", "[]");
            List<Map<String, String>> notificationsList = new ArrayList<>();

            try {
                JSONArray jsonArray = new JSONArray(notificationsJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    Map<String, String> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        map.put(key, obj.getString(key));
                    }
                    notificationsList.add(map);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing notifications list", e);
            }

            Map<String, String> newNotification = new HashMap<>();
            newNotification.put("title", title);
            newNotification.put("body", body);
            newNotification.put("timestamp", String.valueOf(System.currentTimeMillis()));

            if (data != null) {
                newNotification.putAll(data);
            }

            notificationsList.add(0, newNotification);
            if (notificationsList.size() > 5) {
                notificationsList = notificationsList.subList(0, 5);
            }

            JSONArray jsonArray = new JSONArray();
            for (Map<String, String> notification : notificationsList) {
                JSONObject obj = new JSONObject(notification);
                jsonArray.put(obj);
            }

            editor.putString("notifications_list", jsonArray.toString());
            editor.putLong("last_notification_time", System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Notification saved to app preferences: " + title);
        } catch (Exception e) {
            Log.e(TAG, "Error saving notification to app preferences", e);
        }
    }

    private void showSystemNotification(String title, String body, Map<String, String> data) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel(notificationManager);

        Intent intent = new Intent(this, MainActivity.class);

        // Передаем ВСЕ данные в интент, чтобы MainActivity мог их извлечь
        intent.putExtra("gcm.notification.title", title);
        intent.putExtra("gcm.notification.body", body);
        intent.putExtra("from_system_notification", true);
        intent.putExtra("timestamp", System.currentTimeMillis());

        // Передаем все найденные данные
        intent.putExtra("title", title);
        intent.putExtra("body", body);

        // Также передаем все data поля
        if (data != null && !data.isEmpty()) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        int notificationUniqueId = (int) (System.currentTimeMillis() / 1000);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        notificationManager.notify(notificationUniqueId, builder.build());
        Log.d(TAG, "System notification shown: " + title);
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Joomla Сообщения",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Сообщения с Joomla веб-сайта");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 100, 200});
            notificationManager.createNotificationChannel(channel);
        }
    }
}