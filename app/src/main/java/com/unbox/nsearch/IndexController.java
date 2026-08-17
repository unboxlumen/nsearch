package com.unbox.nsearch;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.util.TextExtractor;
import com.unbox.nsearch.service.IndexingService;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 索引编排核心（应用级单例）。
 *  - 在独立线程中扫描 + 建索引，支持暂停 / 继续 / 取消；
 *  - 通过 {@link LuceneManager} 实现「索引与搜索同时进行」（NRT）；
 *  - 依据数据库中的文件元数据做增量同步：未变更的文件直接跳过，已删除文件清理；
 *  - 进度通过 {@link Listener} 回调到主线程，UI 可随时绑定/解绑；
 *  - 前台 Service 仅负责常驻通知与 Wakelock，真正的状态在单例中，故重开 App 也能续传。
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
    private final android.os.Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
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
        this.mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    }

    public State getState() {
        return state;
    }

    public void addListener(Listener l) {
        listeners.add(l);
        l.onStatus(state);
        l.onProgress(state);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
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
            notifyStatus();
            notifyProgress();
        });
    }

    // ---------------- 执行 ----------------

    private void runIndex() {
        IndexDatabase db = IndexDatabase.get(appCtx);
        Settings settings = new Settings(appCtx);
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
        notifyStatus();

        List<FileScanner.ScanItem> items;
        try {
            items = FileScanner.scan(appCtx, settings);
        } catch (Throwable t) {
            items = new ArrayList<>();
        }
        synchronized (state) {
            state.total = items.size();
        }
        notifyProgress();

        LuceneManager km = null;
        try {
            km = LuceneManager.get(appCtx);
            int charLimit = settings.getCharLimit();
            Set<String> seen = new HashSet<>();

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
                IndexDatabase.FileRow row = db.getRow(item.getPath());
                if (row != null && row.status == IndexDatabase.STATUS_DONE
                        && row.size == item.length()
                        && (item.lastModified() == 0 || row.modified == item.lastModified())) {
                    synchronized (state) {
                        state.indexed++;
                        state.skipped++;
                        state.currentFile = item.getName();
                    }
                    notifyProgress();
                    continue;
                }

                indexOne(km, db, item, charLimit);

                if (state.indexed % 200 == 0) {
                    km.commit();
                    km.maybeRefresh();
                }
                synchronized (state) {
                    state.indexed++;
                    state.currentFile = item.getName();
                }
                if (state.indexed % 25 == 0) notifyProgress();
            }

            if (km != null) {
                km.commit();
                km.maybeRefresh();
            }

            // 清理已从磁盘删除的文件
            Set<String> all = db.getAllPaths();
            all.removeAll(seen);
            for (String p : all) {
                if (km != null) km.delete(p);
                db.deleteByPath(p);
            }
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
            notifyStatus();
            notifyProgress();

            ScanRecord rec = new ScanRecord(start, end, state.total,
                    state.indexed - state.skipped, state.failed, state.skipped,
                    end - start, "manual");
            try {
                db.insertScanRecord(rec);
            } catch (Throwable ignored) {
            }

            if (host != null) host.stopForegroundService();
        }
    }

    private void indexOne(LuceneManager km, IndexDatabase db, FileScanner.ScanItem item, int charLimit) {
        String text;
        try (InputStream in = item.openStream(appCtx)) {
            FileType type = FileType.match(item.getName());
            text = TextExtractor.extract(in, type, item.getExt(), charLimit);
        } catch (Exception e) {
            db.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    0, IndexDatabase.STATUS_FAILED, item.getExt());
            synchronized (state) {
                state.failed++;
            }
            return;
        }
        if (text == null) text = "";
        Document doc = buildDoc(item, text);
        try {
            km.updateDocument(item.getPath(), doc);
            db.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    text.length(), IndexDatabase.STATUS_DONE, item.getExt());
        } catch (Exception e) {
            db.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    0, IndexDatabase.STATUS_FAILED, item.getExt());
            synchronized (state) {
                state.failed++;
            }
        }
    }

    private Document buildDoc(FileScanner.ScanItem item, String text) {
        Document doc = new Document();
        doc.add(new StringField(LuceneManager.Fields.PATH, item.getPath(), Field.Store.YES));
        doc.add(new TextField(LuceneManager.Fields.NAME, item.getName(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.EXT, item.getExt(), Field.Store.YES));
        doc.add(new LongPoint(LuceneManager.Fields.SIZE, item.length()));
        doc.add(new StoredField(LuceneManager.Fields.SIZE, item.length()));
        doc.add(new LongPoint(LuceneManager.Fields.MODIFIED, item.lastModified()));
        doc.add(new StoredField(LuceneManager.Fields.MODIFIED, item.lastModified()));
        String snippet = text.length() > LuceneManager.SNIPPET_LIMIT
                ? text.substring(0, LuceneManager.SNIPPET_LIMIT) : text;
        doc.add(new TextField(LuceneManager.Fields.CONTENT, text, Field.Store.NO));
        doc.add(new StoredField(LuceneManager.Fields.SNIPPET, snippet));
        doc.add(new StringField(LuceneManager.Fields.DISPLAY, item.getDisplayPath(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.OPEN_URI, item.getOpenUri(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.IS_CONTENT,
                item.isContentUri() ? "1" : "0", Field.Store.YES));
        return doc;
    }

    private void notifyProgress() {
        final State s = state;
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onProgress(s);
        });
    }

    private void notifyStatus() {
        final State s = state;
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onStatus(s);
        });
    }
}
