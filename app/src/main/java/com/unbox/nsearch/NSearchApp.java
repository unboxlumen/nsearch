package com.unbox.nsearch;

import android.app.Application;

import com.unbox.nsearch.analyzers.JiebaTokenizer;

/**
 * 应用入口：预取索引单例（搜索/索引核心）。
 * 主题跟随系统（manifest 使用 Theme.MaterialComponents.DayNight.NoActionBar），
 * 不做独立的日间/夜间切换，因此此处无需任何夜间模式处理。
 */
public class NSearchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 后台预热 jieba 词典，避免冷启动后第一次搜索卡顿（词典加载约 1-3s）。
        Thread warmup = new Thread(() -> JiebaTokenizer.warmUp(), "nsearch-jieba-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }
}
