package com.unbox.nsearch;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.unbox.nsearch.db.FileMetaDao;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.index.DocumentBuilder;
import com.unbox.nsearch.index.IncrementalSync;
import com.unbox.nsearch.index.StateNotifier;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.service.IndexingService;
import com.unbox.nsearch.util.TextExtractor;

import org.apache.lucene.document.Document;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 索引编排核心（应用级单例）。
 *  - 在独立线程中扫描 + 建索引，支持暂停 / 继续 / 取消；
 *  - 通过 {@link LuceneManager} 实现「索引与搜索同时进行」（NRT）；
 *  - 依据数据库中的文件元数据做增量同步：未变更的文件直接跳过，已删除文件清理；
 *  - 进度通过 {@link Listener} 回调到主线程，UI 可随时绑定/解绑；
 *  - 前台 Service 仅负责常驻通知与 Wakelock，真正的状态在单例中，故重开 App 也能续传。
 *
 * <p>重构后：
 *  - {@link DocumentBuilder}    构建 Lucene Document
 *  - {@link IncrementalSync}     增量跳过 / 删除清理
 *  - {@link StateNotifier}       状态回调到主线程
 *
 * 本类只剩「编排」职责：扫描 → 循环 → 暂停/取消 → 调度上述三者 → 写历史。
 */
public final class IndexController {

    public enum Status { IDLE, RUNNING, PAUSED, CANCELLED, DONE }

    public static class State {
        public Status status = Status.IDLE;
        public int total = 0;
        public int indexed = 0;     // 已处理文件数（含跳过/失败）
        public int skipped = 0;     // 增量跳过的未变更文件
        public int failed = 0;
        public String currentFile = "";
        public long startTime = 0;
        public long endTime = 0;
    }

    public interface Listener {
        void onProgress(State s);

        void onStatus(State s);
    }

    private static IndexController instance;

    private final Context appCtx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StateNotifier notifier = new StateNotifier();
    private final State state = new State();
    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
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
            if (state.status == Status.RUNNING) return;
        }
        cancelled = false;
        paused = false;
        Intent i = new Intent(appCtx, IndexingService.class);
        i.setAction(IndexingService.ACTION_START);
        ContextCompat.startForegroundService(appCtx, i);
        executor.execute(this::runIndex);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void cancel() {
        cancelled = true;
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused;
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
            IndexDatabase.get(appCtx).clearAll();
            synchronized (state) {
                state.status = Status.IDLE;
                state.indexed = 0;
                state.total = 0;
                state.skipped = 0;
                state.failed = 0;
                state.currentFile = "";
            }
            notifier.notifyStatus(state);
            notifier.notifyProgress(state);
        });
    }

    // ---------------- 执行 ----------------

    private void runIndex() {
        IndexDatabase db = IndexDatabase.get(appCtx);
        Settings settings = new Settings(appCtx);
        FileMetaDao fileDao = db.fileMeta();
        long start = System.currentTimeMillis();

        synchronized (state) {
            state.status = Status.RUNNING;
            state.startTime = start;
            state.endTime = 0;
            state.indexed = 0;
            state.skipped = 0;
            state.failed = 0;
            state.currentFile = "";
            state.total = 0;
        }
        notifier.notifyStatus(state);

        List<FileScanner.ScanItem> items;
        try {
            items = FileScanner.scan(appCtx, settings);
        } catch (Throwable t) {
            items = new ArrayList<>();
        }
        synchronized (state) {
            state.total = items.size();
        }
        notifier.notifyProgress(state);

        LuceneManager km = null;
        IncrementalSync sync = null;
        try {
            km = LuceneManager.get(appCtx);
            sync = new IncrementalSync(km, fileDao);
            int charLimit = settings.getCharLimit();
            Set<String> seen = IncrementalSync.newSeenSet();

            for (FileScanner.ScanItem item : items) {
                if (cancelled) break;
                while (paused && !cancelled) {
                    synchronized (pauseLock) {
                        pauseLock.wait(200);
                    }
                }
                if (cancelled) break;

                seen.add(item.getPath());

                // 增量同步：未变更则跳过
                if (sync.isUnchanged(item)) {
                    synchronized (state) {
                        state.indexed++;
                        state.skipped++;
                        state.currentFile = item.getName();
                    }
                    notifier.notifyProgress(state);
                    continue;
                }

                indexOne(km, fileDao, item, charLimit);

                if (state.indexed % 200 == 0) {
                    km.commit();
                    km.maybeRefresh();
                }
                synchronized (state) {
                    state.indexed++;
                    state.currentFile = item.getName();
                }
                if (state.indexed % 25 == 0) notifier.notifyProgress(state);
            }

            if (km != null) {
                km.commit();
                km.maybeRefresh();
            }

            // 清理已从磁盘删除的文件
            if (km != null) sync.cleanupDeleted(seen);
            if (km != null) {
                km.commit();
                km.maybeRefresh();
            }
        } catch (Throwable t) {
            // 记录异常，继续收尾
        } finally {
            long end = System.currentTimeMillis();
            synchronized (state) {
                state.status = cancelled ? Status.CANCELLED : Status.DONE;
                state.endTime = end;
            }
            notifier.notifyStatus(state);
            notifier.notifyProgress(state);

            ScanRecord rec = new ScanRecord(start, end, state.total,
                    state.indexed - state.skipped, state.failed, state.skipped,
                    end - start, "manual");
            try {
                db.scanHistory().insert(rec);
            } catch (Throwable ignored) {
            }

            if (host != null) host.stopForegroundService();
        }
    }

    private void indexOne(LuceneManager km, FileMetaDao fileDao, FileScanner.ScanItem item, int charLimit) {
        String text;
        try (InputStream in = item.openStream(appCtx)) {
            FileType type = FileType.match(item.getName());
            text = TextExtractor.extract(in, type, item.getExt(), charLimit);
        } catch (Exception e) {
            fileDao.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    0, IndexDatabase.STATUS_FAILED, item.getExt());
            synchronized (state) {
                state.failed++;
            }
            return;
        }
        if (text == null) text = "";
        Document doc = DocumentBuilder.build(item, text);
        try {
            km.updateDocument(item.getPath(), doc);
            fileDao.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    text.length(), IndexDatabase.STATUS_DONE, item.getExt());
        } catch (Exception e) {
            fileDao.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    0, IndexDatabase.STATUS_FAILED, item.getExt());
            synchronized (state) {
                state.failed++;
            }
        }
    }
}
