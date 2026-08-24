package com.unbox.nsearch.index;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.unbox.nsearch.IndexController;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 状态通知器：把 {@link IndexController.State} 异步投递到主线程给所有 {@link IndexController.Listener}。
 *
 * 之前是 {@link IndexController} 内联的 notifyProgress/notifyStatus 两个方法；
 * 抽出来后，Controller 自身不再直接 import android.os.Handler / Looper，
 * 也让「State + Listener + Handler」这个三角形的职责集中在一处。
 */
public final class StateNotifier {

    private final Handler mainHandler;
    private final List<IndexController.Listener> listeners = new CopyOnWriteArrayList<>();

    public StateNotifier() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 用于测试：注入自定义 Handler（主线程或其他线程）。
     */
    public StateNotifier(@NonNull Handler handler) {
        this.mainHandler = handler;
    }

    public void addListener(@NonNull IndexController.Listener l) {
        listeners.add(l);
    }

    public void removeListener(@NonNull IndexController.Listener l) {
        listeners.remove(l);
    }

    public void notifyProgress(@NonNull IndexController.State s) {
        final IndexController.State snap = s;
        mainHandler.post(() -> {
            for (IndexController.Listener l : listeners) l.onProgress(snap);
        });
    }

    public void notifyStatus(@NonNull IndexController.State s) {
        final IndexController.State snap = s;
        mainHandler.post(() -> {
            for (IndexController.Listener l : listeners) l.onStatus(snap);
        });
    }
}
