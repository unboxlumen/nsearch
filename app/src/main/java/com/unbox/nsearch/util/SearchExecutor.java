package com.unbox.nsearch.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用级「后台执行 + 主线程投递」工具。
 *
 * 之前 MainActivity 自己持有一个 SingleThreadExecutor + debounce Handler；
 * 后续阶段拆 SearchController / SearchEngine 时仍需要一个统一的入口，
 * 因此把这两者合并到一个轻量工具里，避免在多个组件里各自 new Handler。
 *
 * 注：索引路径仍由 IndexController 内部自己的 Executor 负责，
 * 此工具只服务「用户感知延迟」的搜索任务。
 */
public final class SearchExecutor {

    private static final SearchExecutor INSTANCE = new SearchExecutor();

    public static SearchExecutor get() {
        return INSTANCE;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nsearch-search");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    private SearchExecutor() {}

    /** 仅后台执行，不投递回主线程。 */
    public void submit(@NonNull Runnable task) {
        worker.execute(task);
    }

    /** 在主线程上执行。 */
    public void postToMain(@NonNull Runnable r) {
        main.post(r);
    }
}
