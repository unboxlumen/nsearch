package com.unbox.nsearch;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.unbox.nsearch.index.IndexPipeline;
import com.unbox.nsearch.index.StateNotifier;
import com.unbox.nsearch.service.IndexingService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 索引编排核心（应用级单例）。
 *  - 在独立线程中扫描 + 建索引，支持暂停 / 继续 / 取消；
 *  - 通过 {@link LuceneManager} 实现「索引与搜索同时进行」(NRT)；
 *  - 依据数据库中的文件元数据做增量同步：未变更的文件直接跳过，已删除文件清理；
 *  - 进度通过 {@link Listener} 回调到主线程，UI 可随时绑定/解绑；
 *  - 前台 Service 仅负责常驻通知与 Wakelock，真正的状态在单例中，故重开 App 也能续传。
 *
 * <p>重构后本类只承担「状态机 + 生命周期」职责：
 *  - {@link StateNotifier}       状态回调到主线程
 *  - {@link IndexPipeline}        扫描 → 循环 → 跳过/索引 → 提交/清理
 *
 * <p>{@link NotifySink} 是 Controller 对 Pipeline 的最小通知接口，避免 Pipeline 依赖具体 Controller 内部。
 */
public final class IndexController {

    public enum Status {
        IDLE, RUNNING, PAUSED, CANCELLED, DONE;

        /** 是否处于「正在索引」的活跃态（RUNNING / PAUSED）。 */
        public boolean isActive() {
            return this == RUNNING || this == PAUSED;
        }
    }

    public static class State {
        public Status status = Status.IDLE;
        public int total = 0;
        public int indexed = 0;     // 已处理文件数（含跳过/失败）
        public int skipped = 0;     // 增量跳过的未变更文件
        public int failed = 0;
        public String currentFile = "";
        public long startTime = 0;
        public long endTime = 0;

        /** 索引进度百分比（0..100）；{@code total <= 0} 时为 0。 */
        public int percent() {
            return total > 0 ? (int) (indexed * 100L / total) : 0;
        }
    }

    public interface Listener {
        void onProgress(State s);

        void onStatus(State s);
    }

    /**
     * Pipeline 只需 Controller 暴露一个最小通知能力，
     * 这样 Pipeline 既不依赖 Controller 的全部方法，也不引入循环依赖。
     */
    public interface NotifySink {
        void notifyProgress(State s);
        void notifyStatus(State s);
    }

    private static IndexController instance;

    private final Context appCtx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StateNotifier notifier = new StateNotifier();
    private final State state = new State();
    private final Object pauseLock = new Object();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private IndexingService host;

    public static synchronized IndexController get(Context ctx) {
        if (instance == null) instance = new IndexController(ctx.getApplicationContext());
        return instance;
    }

    private IndexController(Context ctx) {
        this.appCtx = ctx;
    }

    public State getState() {
        return state;
    }

    /**
     * 把当前 {@link State} 拍成一行可读字符串,便于 UI 层直接 toast 显示状态。
     * 线程安全:对 {@link State} 各字段的读取是单线程串行化(主线程读 / executor 线程写),
     * 即使有可见性延迟也只影响"看到的瞬时快照",不影响功能正确性。
     */
    @NonNull
    public String debugSnapshot() {
        State s = state;
        return s.status.name()
                + " · indexed " + s.indexed + "/" + s.total
                + " · skipped " + s.skipped
                + " · failed " + s.failed
                + (s.currentFile == null || s.currentFile.isEmpty() ? "" : " · " + s.currentFile);
    }

    public void addListener(Listener l) {
        notifier.addListener(l);
        l.onStatus(state);
        l.onProgress(state);
    }

    public void removeListener(Listener l) {
        notifier.removeListener(l);
    }

    public void setHost(IndexingService s) {
        host = s;
    }

    public void clearHost(IndexingService s) {
        if (host == s) host = null;
    }

    public void requestStart() {
        synchronized (state) {
            // 只有真正在跑时才拒绝;PAUSED 视为仍占用旧 pipeline 的循环,
            // 强制先取消以避免新旧两轮并发索引。
            if (state.status == Status.RUNNING) return;
        }
        if (state.status == Status.PAUSED) {
            // 唤醒旧的 wait,让它下一轮检测到 cancelled 退出
            cancel();
            // 等旧 pipeline 退出再启动新的;executor 是单线程,execute 会排队。
            // 这里不再重置 state.status,留给 IndexPipeline.run() 的 resetState() 统一处理。
        }
        cancelled.set(false);
        paused.set(false);
        Intent i = new Intent(appCtx, IndexingService.class);
        i.setAction(IndexingService.ACTION_START);
        ContextCompat.startForegroundService(appCtx, i);
        executor.execute(this::runIndex);
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void cancel() {
        cancelled.set(true);
        paused.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void deleteIndex() {
        executor.execute(() -> {
            try {
                LuceneManager km = LuceneManager.get(appCtx);
                km.deleteAll();
                km.commit();
                km.maybeRefresh();
            } catch (Exception ignored) {
            }
            com.unbox.nsearch.db.IndexDatabase.get(appCtx).fileMeta().clear();
            synchronized (state) {
                state.status = Status.IDLE;
                state.indexed = 0;
                state.total = 0;
                state.skipped = 0;
                state.failed = 0;
                state.currentFile = "";
            }
            notifier.notifyAllState(state);
        });
    }

    // ---------------- 执行 ----------------

    private void runIndex() {
        try {
            IndexPipeline pipeline = new IndexPipeline(appCtx, state, cancelled, paused, pauseLock, sink());
            pipeline.run();
        } finally {
            if (host != null) host.stopForegroundService();
        }
    }

    private NotifySink sink() {
        return new NotifySink() {
            @Override public void notifyProgress(State s) { notifier.notifyProgress(s); }
            @Override public void notifyStatus(State s) { notifier.notifyStatus(s); }
        };
    }

    /**
     * 仅测试用：暴露同步的通知器，便于在 JVM 单测里同步派发回调。
     */
    @VisibleForTesting
    StateNotifier notifierForTest() {
        return notifier;
    }
}
