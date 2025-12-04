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
import java.util.UUID;

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
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Message data payload: " + data);

        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Notification payload - Title: " + remoteMessage.getNotification().getTitle() +
                    ", Body: " + remoteMessage.getNotification().getBody());
        }

        // Получаем данные уведомления
        String title = "Мое приложение";
        String body = "Новое уведомление";

        // Получаем данные из notification payload (Firebase Console)
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        // Data payload имеет приоритет (если отправляется с сервера)
        if (data != null && !data.isEmpty()) {
            if (data.containsKey("title")) {
                title = data.get("title");
            }
            if (data.containsKey("body")) {
                body = data.get("body");
            } else if (data.containsKey("message")) {
                body = data.get("message");
            }
        }

        // ВАЖНО: Всегда сохраняем уведомление в SharedPreferences
        saveNotificationToAppPreferences(title, body, data);

        Log.d(TAG, "Notification processed - Title: " + title + ", Body: " + body);

        // Если приложение в foreground - показываем диалог
        if (AppLifecycleManager.isAppInForeground()) {
            Log.d(TAG, "App is in foreground, showing dialog");
            showDialogDirectly(title, body);
        } else {
            // Если приложение в background - показываем системное уведомление
            Log.d(TAG, "App is in background, showing system notification");
            showSystemNotification(title, body, data);
        }
    }

    // Сохраняем уведомление в SharedPreferences приложения
    private void saveNotificationToAppPreferences(String title, String body, Map<String, String> data) {
        try {
            // Используем SharedPreferences самого приложения
            SharedPreferences prefs = getSharedPreferences("app_notifications", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Сохраняем последние 5 уведомлений (ротация)
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

            // Добавляем новое уведомление
            Map<String, String> newNotification = new HashMap<>();
            newNotification.put("title", title);
            newNotification.put("body", body);
            newNotification.put("timestamp", String.valueOf(System.currentTimeMillis()));

            if (data != null) {
                newNotification.putAll(data);
            }

            notificationsList.add(0, newNotification); // Добавляем в начало

            // Ограничиваем список 5 элементами
            if (notificationsList.size() > 5) {
                notificationsList = notificationsList.subList(0, 5);
            }

            // Сохраняем обратно в JSON
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

    private void showDialogDirectly(String title, String body) {
        Log.d(TAG, "Showing dialog directly: " + title);

        Intent dialogIntent = new Intent(this, MainActivity.class);
        dialogIntent.setAction("SHOW_NOTIFICATION_FROM_SERVICE");
        dialogIntent.putExtra("title", title);
        dialogIntent.putExtra("body", body);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(dialogIntent);
    }

    private void showSystemNotification(String title, String body, Map<String, String> data) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel(notificationManager);

        // Создаем Intent для открытия приложения
        Intent intent = new Intent(this, MainActivity.class);

        // Передаем title и body
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        intent.putExtra("from_system_notification", true);
        intent.putExtra("timestamp", System.currentTimeMillis());

        // Добавляем data если есть
        if (data != null && !data.isEmpty()) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        // Используем стандартное действие
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        // Флаги для правильного запуска
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Уникальные ID
        int requestCode = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        int notificationUniqueId = (int) (System.currentTimeMillis() / 1000);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Создаем уведомление
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Добавляем расширенный текст
        builder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(body));

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