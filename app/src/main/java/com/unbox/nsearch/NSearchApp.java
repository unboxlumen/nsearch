package com.unbox.nsearch;

import android.app.Application;

/**
 * 应用入口：预取索引单例（搜索/索引核心）。
 * 主题跟随系统（manifest 使用 Theme.MaterialComponents.DayNight.NoActionBar），
 * 不做独立的日间/夜间切换，因此此处无需任何夜间模式处理。
 */
public class NSearchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
