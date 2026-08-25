package com.unbox.nsearch.index;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.unbox.nsearch.FileScanner;
import com.unbox.nsearch.FileType;
import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.LuceneManager;
import com.unbox.nsearch.Settings;
import com.unbox.nsearch.db.FileMetaDao;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.util.ErrorReporter;
import com.unbox.nsearch.util.TextExtractor;

import org.apache.lucene.document.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 索引流水线：单次扫描 → 跳过 / 写入 → 清理 已删除。
 *
 * <p>这是「索引业务流程」封装，{@link IndexController} 仅持有 State / Listener / 暂停-取消标志，
 * 把整段「for-loop」+ 提交/刷新节奏 + 写历史逻辑都委托给本类。
 *
 * <p>拆分后：
 * <ul>
 *   <li>单元测试可注入 fake 的 {@link FileMetaDao}、{@link LuceneManager} 直接驱动单文件路径；
 *   <li>本类不依赖 Android 主线程（除了通过 {@link ScanRecord} 持久化历史），
 *       适合将来跑在 Instrumented Test 之外的纯 JUnit 路径。
 * </ul>
 */
public final class IndexPipeline {

    /** 每处理 N 个文件后 commit + maybeRefresh 一次。 */
    @VisibleForTesting
    static final int COMMIT_EVERY = 200;
    /** 每处理 N 个文件后向 UI 投递一次进度(避免主线程被通知淹没,但要够细
     *  让首页 resultCount 行能实时反映已索引文件数)。
     */
    @VisibleForTesting
    static final int NOTIFY_EVERY = 5;
    /** 暂停时 wait 的间隔；用于取消检查。 */
    @VisibleForTesting
    static final long PAUSE_POLL_MS = 200;
    /** 「前 100 个文件」区间内每多少个 toast 一次。 */
    @VisibleForTesting
    static final int TOAST_EARLY = 50;
    /** 「前 100 个文件」区间长度,超过这个区间后改用 {@link #TOAST_LATE} 频率。 */
    @VisibleForTesting
    static final int TOAST_EARLY_RANGE = 100;
    /** 越过早区间后,每多少个文件 toast 一次。 */
    @VisibleForTesting
    static final int TOAST_LATE = 500;

    private final Context appCtx;
    private final IndexController.State state;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean paused;
    private final Object pauseLock;
    private final IndexController.NotifySink notifier;

    public IndexPipeline(@NonNull Context appCtx,
                         @NonNull IndexController.State state,
                         @NonNull AtomicBoolean cancelled,
                         @NonNull AtomicBoolean paused,
                         @NonNull Object pauseLock,
                         @NonNull IndexController.NotifySink notifier) {
        this.appCtx = appCtx;
        this.state = state;
        this.cancelled = cancelled;
        this.paused = paused;
        this.pauseLock = pauseLock;
        this.notifier = notifier;
    }

    /**
     * 跑一轮完整索引。
     *
     * @return 写入历史的 {@link ScanRecord}（失败/异常也返回一个近似值，便于上层无脑写历史）。
     */
    @NonNull
    public ScanRecord run() {
        long start = System.currentTimeMillis();
        IndexDatabase db = IndexDatabase.get(appCtx);
        Settings settings = new Settings(appCtx);
        FileMetaDao fileDao = db.fileMeta();

        resetState(start);
        // 立刻 toast 一次「扫描中」,让用户知道已收到请求
        toast("开始扫描…");

        // 扫描期间按节流策略 toast:每 200 个文件 / 每 1 秒一次
        final long[] lastScanToastMs = {0L};
        final int[] lastScanToastCount = {0};
        java.util.function.Consumer<Integer> scanProgress = count -> {
            long now = System.currentTimeMillis();
            if (count - lastScanToastCount[0] >= 200 || now - lastScanToastMs[0] >= 1000) {
                lastScanToastCount[0] = count;
                lastScanToastMs[0] = now;
                toast("扫描中…已发现 " + count + " 个候选文件");
            }
        };
        List<FileScanner.ScanItem> items = safeScan(settings, scanProgress);
        synchronized (state) {
            state.total = items.size();
        }
        notifier.notifyProgress(state);
        toast("扫描完成,共 " + items.size() + " 个候选文件,开始索引");

        int indexed = 0;
        int skipped = 0;
        int failed = 0;
        final int[] lastToastAt = {0}; // 上一次 toast 时的 indexed 数

        LuceneManager km = null;
        IncrementalSync sync = null;
        try {
            km = LuceneManager.get(appCtx);
            sync = new IncrementalSync(km, fileDao);
            int charLimit = settings.getCharLimit();
            Set<String> seen = IncrementalSync.newSeenSet();

            for (FileScanner.ScanItem item : items) {
                if (cancelled.get()) break;
                waitWhilePaused();
                if (cancelled.get()) break;

                seen.add(item.getPath());

                if (sync.isUnchanged(item)) {
                    synchronized (state) {
                        state.indexed++;
                        state.skipped++;
                        state.currentFile = item.getName();
                    }
                    skipped++;
                    notifier.notifyProgress(state);
                    maybeToastByCount(state.indexed, lastToastAt);
                    continue;
                }

                indexOne(km, fileDao, item, charLimit);

                synchronized (state) {
                    if (state.failed > failed) {
                        failed = state.failed;
                    }
                    state.indexed++;
                    state.currentFile = item.getName();
                }
                indexed++;

                if (state.indexed % COMMIT_EVERY == 0) {
                    km.commit();
                    km.maybeRefresh();
                }
                if (state.indexed % NOTIFY_EVERY == 0) {
                    notifier.notifyProgress(state);
                }
                maybeToastByCount(state.indexed, lastToastAt);
            }

            if (km != null) {
                km.commit();
                km.maybeRefresh();
            }

            if (km != null) sync.cleanupDeleted(seen);
            if (km != null) {
                km.commit();
                km.maybeRefresh();
            }
        } catch (Throwable t) {
            ErrorReporter.report("IndexPipeline.run", t);
        } finally {
            long end = System.currentTimeMillis();
            synchronized (state) {
                state.status = cancelled.get() ? IndexController.Status.CANCELLED : IndexController.Status.DONE;
                state.endTime = end;
            }
            notifier.notifyStatus(state);
            notifier.notifyProgress(state);
        }

        int total = state.indexed;
        int indexedForHistory = Math.max(0, total - skipped);
        long end = System.currentTimeMillis();
        try {
            ScanRecord rec = new ScanRecord(start, end, items.size(),
                    indexedForHistory, state.failed, skipped,
                    end - start, "manual");
            db.scanHistory().insert(rec);
            return rec;
        } catch (Throwable t) {
            ErrorReporter.report("IndexPipeline.run:history", t);
            return new ScanRecord(start, end, items.size(),
                    indexedForHistory, state.failed, skipped,
                    end - start, "manual");
        }
    }

    /**
     * 根据当前已处理文件数按「前 100 个每 50 个、之后每 500 个」的策略决定是否 toast。
     * 每次 toast 后更新 {@code lastToastAt[0]}。
     */
    private void maybeToastByCount(int currentIndexed, int[] lastToastAt) {
        int last = lastToastAt[0];
        if (currentIndexed <= TOAST_EARLY_RANGE) {
            // 前 100 个:每 50 个 toast,但要在到达时 toast(50, 100)
            if (currentIndexed > 0 && currentIndexed % TOAST_EARLY == 0 && currentIndexed != last) {
                toast("已索引 " + currentIndexed + " 个");
                lastToastAt[0] = currentIndexed;
            }
        } else {
            // 越过 100 后:每 500 个 toast,但要比 lastToastAt 多 TOAST_LATE 个
            if (currentIndexed - last >= TOAST_LATE) {
                toast("已索引 " + currentIndexed + " 个");
                lastToastAt[0] = currentIndexed;
            }
        }
    }

    /**
     * 跨线程安全的 toast 入口:Toast.makeText 自己 post 到主线程。
     */
    private void toast(String msg) {
        try {
            Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 索引单个文件：抽取 → 构造 Document → 写 Lucene → 写元数据。失败时只更新 DB 状态。
     */
    private void indexOne(@NonNull LuceneManager km,
                          @NonNull FileMetaDao fileDao,
                          @NonNull FileScanner.ScanItem item,
                          int charLimit) {
        String text;
        try (InputStream in = item.openStream(appCtx)) {
            FileType type = FileType.match(item.getName());
            text = TextExtractor.extract(in, type, item.getExt(), charLimit);
        } catch (Exception e) {
            markFailed(fileDao, item);
            return;
        }
        if (text == null) text = "";
        Document doc = DocumentBuilder.build(item, text);
        try {
            km.updateDocument(item.getPath(), doc);
            fileDao.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                    text.length(), IndexDatabase.STATUS_DONE, item.getExt());
        } catch (IOException e) {
            ErrorReporter.report("IndexPipeline.indexOne", e);
            markFailed(fileDao, item);
        }
    }

    private void markFailed(@NonNull FileMetaDao fileDao, @NonNull FileScanner.ScanItem item) {
        fileDao.upsert(item.getPath(), item.getName(), item.length(), item.lastModified(),
                0, IndexDatabase.STATUS_FAILED, item.getExt());
        synchronized (state) {
            state.failed++;
        }
    }

    private void resetState(long start) {
        synchronized (state) {
            state.status = IndexController.Status.RUNNING;
            state.startTime = start;
            state.endTime = 0;
            state.indexed = 0;
            state.skipped = 0;
            state.failed = 0;
            state.currentFile = "";
            state.total = 0;
        }
        notifier.notifyStatus(state);
    }

    private List<FileScanner.ScanItem> safeScan(@NonNull Settings settings,
                                                  @NonNull java.util.function.Consumer<Integer> onProgress) {
        try {
            return FileScanner.scan(appCtx, settings, onProgress);
        } catch (Throwable t) {
            ErrorReporter.report("IndexPipeline.safeScan", t);
            return new ArrayList<>();
        }
    }

    private void waitWhilePaused() {
        waitWhilePaused(paused, cancelled, pauseLock, PAUSE_POLL_MS);
    }

    /**
     * 纯函数形式的「暂停门」：当 {@code paused} 为 true 且 {@code cancelled} 为 false 时持续阻塞,
     * 直到被 {@link Object#notifyAll()} 唤醒或超时。
     *
     * <p>抽出目的是让 Pipeline 的暂停/取消契约可在 JVM 单测里覆盖。
     */
    @VisibleForTesting
    static void waitWhilePaused(@NonNull AtomicBoolean paused,
                                @NonNull AtomicBoolean cancelled,
                                @NonNull Object lock,
                                long pollMs) {
        while (paused.get() && !cancelled.get()) {
            synchronized (lock) {
                try {
                    lock.wait(pollMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}