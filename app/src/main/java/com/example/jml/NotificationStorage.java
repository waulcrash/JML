package com.example.jml;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationStorage {
    private static final String TAG = "NotificationStorage";
    private static final String PREF_NAME = "notification_storage";
    private static final String KEY_NOTIFICATIONS = "pending_notifications";
    private static final String KEY_LAST_SHOWN_ID = "last_shown_id";

    private static NotificationStorage instance;
    private SharedPreferences preferences;
    private Gson gson = new Gson();

    private NotificationStorage(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized NotificationStorage getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationStorage(context);
        }
        return instance;
    }

    public static class NotificationData {
        public String id;
        public String title;
        public String message;
        public Map<String, String> data;
        public long timestamp;
        public boolean shown;

        public NotificationData(String id, String title, String message,
                                Map<String, String> data, long timestamp) {
            this.id = id;
            this.title = title;
            this.message = message;
            this.data = data;
            this.timestamp = timestamp;
            this.shown = false;
        }
    }

    public void saveNotification(NotificationData notification) {
        try {
            List<NotificationData> notifications = getNotifications();
            long currentTime = System.currentTimeMillis();
            notifications.removeIf(n -> (currentTime - n.timestamp) > 24 * 60 * 60 * 1000);
            notifications.add(0, notification);

            if (notifications.size() > 20) {
                notifications = notifications.subList(0, 20);
            }

            String json = gson.toJson(notifications);
            preferences.edit()
                    .putString(KEY_NOTIFICATIONS, json)
                    .apply();

            Log.d(TAG, "Notification saved: " + notification.id);
        } catch (Exception e) {
            Log.e(TAG, "Error saving notification", e);
        }
    }

    public List<NotificationData> getNotifications() {
        try {
            String json = preferences.getString(KEY_NOTIFICATIONS, "[]");
            Type type = new TypeToken<ArrayList<NotificationData>>(){}.getType();
            List<NotificationData> notifications = gson.fromJson(json, type);
            return notifications != null ? notifications : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting notifications", e);
            return new ArrayList<>();
        }
    }

    public NotificationData getNotificationById(String id) {
        try {
            List<NotificationData> notifications = getNotifications();
            for (NotificationData notification : notifications) {
                if (notification.id.equals(id)) {
                    return notification;
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification by id: " + id, e);
            return null;
        }
    }

    public List<NotificationData> getUnshownNotifications() {
        List<NotificationData> all = getNotifications();
        List<NotificationData> unshown = new ArrayList<>();

        for (NotificationData notification : all) {
            if (!notification.shown) {
                unshown.add(notification);
            }
        }

        return unshown;
    }

    public void markAsShown(String notificationId) {
        try {
            List<NotificationData> notifications = getNotifications();
            for (NotificationData notification : notifications) {
                if (notification.id.equals(notificationId)) {
                    notification.shown = true;
                    break;
                }
            }

            String json = gson.toJson(notifications);
            preferences.edit()
                    .putString(KEY_NOTIFICATIONS, json)
                    .apply();

            Log.d(TAG, "Notification marked as shown: " + notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Error marking notification as shown", e);
        }
    }

    public void markAllAsShown() {
        try {
            List<NotificationData> notifications = getNotifications();
            for (NotificationData notification : notifications) {
                notification.shown = true;
            }

            String json = gson.toJson(notifications);
            preferences.edit()
                    .putString(KEY_NOTIFICATIONS, json)
                    .apply();

            Log.d(TAG, "All notifications marked as shown");
        } catch (Exception e) {
            Log.e(TAG, "Error marking all notifications as shown", e);
        }
    }

    public void clearAllNotifications() {
        preferences.edit()
                .remove(KEY_NOTIFICATIONS)
                .apply();
        Log.d(TAG, "All notifications cleared");
    }

    public void saveLastShownId(String id) {
        preferences.edit()
                .putString(KEY_LAST_SHOWN_ID, id)
                .apply();
    }

    public String getLastShownId() {
        return preferences.getString(KEY_LAST_SHOWN_ID, "");
    }
}