package com.example.jml;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class NotificationDatabase extends SQLiteOpenHelper {
    private static final String TAG = "NotificationDB";
    private static final String DATABASE_NAME = "notifications.db";
    private static final int DATABASE_VERSION = 1;

    // Таблица уведомлений
    public static final String TABLE_NOTIFICATIONS = "notifications";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_DATA = "data";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_SHOWN = "shown";

    // SQL для создания таблицы
    private static final String CREATE_TABLE_NOTIFICATIONS =
            "CREATE TABLE " + TABLE_NOTIFICATIONS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_TITLE + " TEXT NOT NULL, " +
                    COLUMN_MESSAGE + " TEXT NOT NULL, " +
                    COLUMN_DATA + " TEXT, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_SHOWN + " INTEGER DEFAULT 0)";

    private static NotificationDatabase instance;

    public static synchronized NotificationDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private NotificationDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_NOTIFICATIONS);
        Log.d(TAG, "Database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTIFICATIONS);
        onCreate(db);
    }

    // Сохранить уведомление
    public void saveNotification(String id, String title, String message, String data) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, id);
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_MESSAGE, message);
        values.put(COLUMN_DATA, data);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_SHOWN, 0); // Не показано

        db.insertWithOnConflict(TABLE_NOTIFICATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();

        Log.d(TAG, "Notification saved to DB: " + title);
    }

    // Получить все непоказанные уведомления
    public List<NotificationItem> getUnshownNotifications() {
        List<NotificationItem> notifications = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_NOTIFICATIONS +
                " WHERE " + COLUMN_SHOWN + " = 0" +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                NotificationItem item = new NotificationItem();
                item.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                item.message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE));
                item.data = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATA));
                item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                item.shown = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOWN)) == 1;

                notifications.add(item);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return notifications;
    }

    // Пометить уведомление как показанное
    public void markAsShown(String notificationId) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_SHOWN, 1);

        db.update(TABLE_NOTIFICATIONS, values,
                COLUMN_ID + " = ?", new String[]{notificationId});
        db.close();

        Log.d(TAG, "Notification marked as shown: " + notificationId);
    }

    // Удалить старые уведомления (старше 7 дней)
    public void deleteOldNotifications() {
        SQLiteDatabase db = getWritableDatabase();
        long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);

        db.delete(TABLE_NOTIFICATIONS,
                COLUMN_TIMESTAMP + " < ?",
                new String[]{String.valueOf(weekAgo)});
        db.close();

        Log.d(TAG, "Old notifications deleted");
    }

    // Получить уведомление по ID
    public NotificationItem getNotificationById(String id) {
        SQLiteDatabase db = getReadableDatabase();
        NotificationItem item = null;

        Cursor cursor = db.query(TABLE_NOTIFICATIONS, null,
                COLUMN_ID + " = ?",
                new String[]{id},
                null, null, null);

        if (cursor.moveToFirst()) {
            item = new NotificationItem();
            item.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
            item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
            item.message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE));
            item.data = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATA));
            item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
            item.shown = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOWN)) == 1;
        }

        cursor.close();
        db.close();

        return item;
    }

    // Класс для хранения уведомления
    public static class NotificationItem {
        public String id;
        public String title;
        public String message;
        public String data;
        public long timestamp;
        public boolean shown;
    }
}