package com.nova.ai;

import android.app.Application;

import com.nova.ai.data.ChatStorage;
import com.nova.ai.data.Settings;

public class NovaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Settings.get(this);
        ChatStorage.get(this);
    }
}
