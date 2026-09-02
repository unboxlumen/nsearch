package com.unbox.nsearch;

import android.app.Application;

import com.unbox.nsearch.analyzers.JiebaTokenizer;
import com.unbox.nsearch.db.IndexDatabase;

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

        // 启动期回填历史：若 indexed_files 已有 DONE 但 scan_history 为空（典型：旧版本
        // 数据迁移/外部数据残留），补一条 recovered 占位记录。
        // 放在 Application.onCreate 而不是 Activity.onCreate,确保即使首页 inflate 出错,
        // 这次回填也已经持久化,历史页不会永远空白。
        // 同步执行：只是几条 SQLite 读 + 一次 insert,毫秒级,放在主线程可以接受。
        IndexDatabase.get(this).maybeBackfillHistoryIfStale();
    }
}
