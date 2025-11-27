package com.example.jml;

import android.app.Application;

public class AppLifecycleManager extends Application {

    private static boolean isAppInForeground = false;

    public static boolean isAppInForeground() {
        return isAppInForeground;
    }

    public static void setAppInForeground(boolean inForeground) {
        isAppInForeground = inForeground;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}