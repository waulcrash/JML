package com.example.jml;

import com.google.firebase.messaging.RemoteMessage;

public class NotificationDataHolder {
    private static RemoteMessage lastRemoteMessage;

    public static void setLastRemoteMessage(RemoteMessage remoteMessage) {
        lastRemoteMessage = remoteMessage;
    }

    public static RemoteMessage getLastRemoteMessage() {
        return lastRemoteMessage;
    }

    public static void clear() {
        lastRemoteMessage = null;
    }
}