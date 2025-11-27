package com.example.jml;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "WEBVIEW_APP_CHANNEL";

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Обрабатываем данные сообщения
        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Message data: " + data);

        // Если приложение в foreground - показываем диалог сразу
        if (AppLifecycleManager.isAppInForeground()) {
            showDialogDirectly(remoteMessage);
        } else {
            // Если приложение в background - показываем уведомление
            showNotification(remoteMessage);
        }
    }

    private void showDialogDirectly(RemoteMessage remoteMessage) {
        Log.d(TAG, "App is in foreground, showing dialog directly");

        Intent dialogIntent = new Intent(this, MainActivity.class);
        dialogIntent.setAction("SHOW_DIALOG_FROM_FOREGROUND");
        setupIntentWithNotificationData(dialogIntent, remoteMessage);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(dialogIntent);
    }

    private void showNotification(RemoteMessage remoteMessage) {
        Log.d(TAG, "App is in background, showing notification");

        String title = "Мое приложение";
        String body = "Новое сообщение";

        // Получаем данные для уведомления
        Map<String, String> data = remoteMessage.getData();
        if (data != null) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
            else if (data.containsKey("message")) body = data.get("message");
        }

        // Если есть notification payload, используем его
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel(notificationManager);

        // Intent для открытия приложения с данными уведомления
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("SHOW_DIALOG_FROM_NOTIFICATION");
        setupIntentWithNotificationData(intent, remoteMessage);

        // Флаги для правильного запуска активности
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Создаем уведомление
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(notificationId, builder.build());
        Log.d(TAG, "Notification shown with title: " + title);
    }

    private void setupIntentWithNotificationData(Intent intent, RemoteMessage remoteMessage) {
        // Добавляем все данные из data payload
        Bundle dataBundle = new Bundle();
        Map<String, String> data = remoteMessage.getData();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                dataBundle.putString(entry.getKey(), entry.getValue());
            }
        }
        intent.putExtra("notification_data", dataBundle);

        // Устанавливаем title и message
        String title = "Мое приложение";
        String message = "Новое сообщение";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null ?
                    remoteMessage.getNotification().getTitle() : title;
            message = remoteMessage.getNotification().getBody() != null ?
                    remoteMessage.getNotification().getBody() : message;
        }

        // Переопределяем данными из data, если они есть
        if (data != null) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) message = data.get("body");
            else if (data.containsKey("message")) message = data.get("message");
        }

        intent.putExtra("title", title);
        intent.putExtra("message", message);

        // Добавляем специальный флаг, чтобы отличить уведомление FCM
        intent.putExtra("from_fcm_notification", true);
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Joomla Сообщения",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Сообщения с Joomla веб-сайта");
            notificationManager.createNotificationChannel(channel);
        }
    }
}