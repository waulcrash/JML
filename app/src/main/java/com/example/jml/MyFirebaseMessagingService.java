package com.example.jml;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

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

        String title = "Мое приложение";
        String body = "Новое сообщение";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }
        // обработка входящих
        showNotification(title, body);
    }

    private void showNotification(String title, String message) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager);

        // Intent для открытия приложения
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Используем иконку приложения
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
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