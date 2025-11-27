package com.example.jml;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String KEY_HAS_PENDING_NOTIFICATION = "has_pending_notification";
    private static final String KEY_TITLE = "notification_title";
    private static final String KEY_MESSAGE = "notification_message";
    private static final String KEY_TIMESTAMP = "notification_timestamp";

    public static void saveNotification(Context context, String title, String message, Map<String, String> data) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Log.d(TAG, "Saving notification - Title: " + title + ", Message: " + message);

            editor.putBoolean(KEY_HAS_PENDING_NOTIFICATION, true);
            editor.putString(KEY_TITLE, title);
            editor.putString(KEY_MESSAGE, message);
            editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis());

            // Сохраняем дополнительные данные
            if (data != null && !data.isEmpty()) {
                Log.d(TAG, "Saving additional data: " + data.size() + " items");
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (!"title".equals(key) && !"body".equals(key) && !"message".equals(key)) {
                        String dataKey = "data_" + key;
                        editor.putString(dataKey, value);
                        Log.d(TAG, "Saved data: " + dataKey + " = " + value);
                    }
                }
            } else {
                Log.d(TAG, "No additional data to save");
            }

            boolean success = editor.commit(); // Используем commit() вместо apply() для немедленного сохранения
            Log.d(TAG, "Notification save result: " + success);

            // Немедленно проверяем что сохранилось
            boolean hasNotification = prefs.getBoolean(KEY_HAS_PENDING_NOTIFICATION, false);
            String savedTitle = prefs.getString(KEY_TITLE, null);
            String savedMessage = prefs.getString(KEY_MESSAGE, null);

            Log.d(TAG, "Verification - Has notification: " + hasNotification);
            Log.d(TAG, "Verification - Saved title: " + savedTitle);
            Log.d(TAG, "Verification - Saved message: " + savedMessage);

        } catch (Exception e) {
            Log.e(TAG, "Error saving notification", e);
        }
    }

    public static boolean hasPendingNotification(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean hasNotification = prefs.getBoolean(KEY_HAS_PENDING_NOTIFICATION, false);

            Log.d(TAG, "Checking pending notification: " + hasNotification);

            if (hasNotification) {
                // Проверяем что уведомление не старше 10 минут
                long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);
                long currentTime = System.currentTimeMillis();
                long age = currentTime - timestamp;

                Log.d(TAG, "Notification age: " + age + "ms");

                if (age > 10 * 60 * 1000) { // 10 минут
                    Log.d(TAG, "Notification too old, clearing");
                    clearNotification(context);
                    return false;
                }

                // Проверяем что есть title и message
                String title = prefs.getString(KEY_TITLE, null);
                String message = prefs.getString(KEY_MESSAGE, null);

                if (title == null || message == null) {
                    Log.d(TAG, "Notification data incomplete, clearing");
                    clearNotification(context);
                    return false;
                }

                Log.d(TAG, "Valid pending notification found");
                return true;
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking pending notification", e);
            return false;
        }
    }

    public static NotificationData getPendingNotification(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            if (!prefs.getBoolean(KEY_HAS_PENDING_NOTIFICATION, false)) {
                return null;
            }

            String title = prefs.getString(KEY_TITLE, "Уведомление");
            String message = prefs.getString(KEY_MESSAGE, "Новое сообщение");

            Log.d(TAG, "Retrieving notification - Title: " + title + ", Message: " + message);

            NotificationData data = new NotificationData(title, message);

            // Получаем дополнительные данные
            Map<String, ?> allPrefs = prefs.getAll();
            Log.d(TAG, "All preferences keys: " + allPrefs.keySet());

            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("data_") && entry.getValue() instanceof String) {
                    String dataKey = key.substring(5); // Убираем "data_" префикс
                    String value = (String) entry.getValue();
                    data.addData(dataKey, value);
                    Log.d(TAG, "Retrieved data: " + dataKey + " = " + value);
                }
            }

            return data;

        } catch (Exception e) {
            Log.e(TAG, "Error getting pending notification", e);
            return null;
        }
    }

    public static void clearNotification(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Log.d(TAG, "Clearing notification");

            // Удаляем только ключи уведомлений
            editor.remove(KEY_HAS_PENDING_NOTIFICATION);
            editor.remove(KEY_TITLE);
            editor.remove(KEY_MESSAGE);
            editor.remove(KEY_TIMESTAMP);

            // Удаляем дополнительные данные
            Map<String, ?> allPrefs = prefs.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith("data_")) {
                    editor.remove(key);
                    Log.d(TAG, "Removing data key: " + key);
                }
            }

            boolean success = editor.commit();
            Log.d(TAG, "Clear notification result: " + success);

        } catch (Exception e) {
            Log.e(TAG, "Error clearing notification", e);
        }
    }

    public static class NotificationData {
        public String title;
        public String message;
        public Map<String, String> additionalData;

        public NotificationData(String title, String message) {
            this.title = title;
            this.message = message;
            this.additionalData = new java.util.HashMap<>();
        }

        public void addData(String key, String value) {
            this.additionalData.put(key, value);
        }
    }
}