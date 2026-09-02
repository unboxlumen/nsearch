package com.unbox.nsearch.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.unbox.nsearch.model.ScanRecord;

/**
 * SQLite 持有者 + 表结构定义。
 *
 * 之前此文件混了 indexed_files 与 scan_history 两张表的全部 CRUD，
 * 现在拆为 {@link FileMetaDao} 与 {@link ScanHistoryDao}，
 * 这里只负责 {@link SQLiteOpenHelper} 的生命周期、建表语句与 DAO 装配。
 */
public final class IndexDatabase extends SQLiteOpenHelper {

    private static final String NAME = "nsearch_index.db";
    private static final int VERSION = 1;

    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    /**
     * {@link ScanRecord#trigger} 已知取值：
     * <ul>
     *   <li>{@code manual}     — 用户在主页/设置主动触发的全量/增量扫描</li>
     *   <li>{@code recovered}  — 启动时检测到「历史表空但已有 DONE 文件」，自动回填的占位记录
     *                              （典型场景：旧版本/外部数据迁移留下，未跑过 IndexPipeline）</li>
     * </ul>
     */
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_RECOVERED = "recovered";

    private static IndexDatabase instance;

    private final FileMetaDao fileDao;
    private final ScanHistoryDao historyDao;

    public static synchronized IndexDatabase get(Context context) {
        if (instance == null) instance = new IndexDatabase(context.getApplicationContext());
        return instance;
    }

    private IndexDatabase(Context ctx) {
        super(ctx, NAME, null, VERSION);
        this.fileDao = new FileMetaDao(this);
        this.historyDao = new ScanHistoryDao(this);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + FileMetaDao.TABLE + " (" +
                FileMetaDao.Cols.PATH + " TEXT PRIMARY KEY, " +
                FileMetaDao.Cols.NAME + " TEXT, " +
                FileMetaDao.Cols.SIZE + " INTEGER, " +
                FileMetaDao.Cols.MODIFIED + " INTEGER, " +
                FileMetaDao.Cols.LENGTH_CHARS + " INTEGER, " +
                FileMetaDao.Cols.STATUS + " INTEGER, " +
                FileMetaDao.Cols.EXT + " TEXT)");
        db.execSQL("CREATE TABLE " + ScanHistoryDao.TABLE + " (" +
                ScanHistoryDao.Cols.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                ScanHistoryDao.Cols.STARTED + " INTEGER, " +
                ScanHistoryDao.Cols.FINISHED + " INTEGER, " +
                ScanHistoryDao.Cols.TOTAL + " INTEGER, " +
                ScanHistoryDao.Cols.INDEXED + " INTEGER, " +
                ScanHistoryDao.Cols.FAILED + " INTEGER, " +
                ScanHistoryDao.Cols.SKIPPED + " INTEGER, " +
                ScanHistoryDao.Cols.DURATION + " INTEGER, " +
                ScanHistoryDao.Cols.TRIGGER + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + FileMetaDao.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ScanHistoryDao.TABLE);
        onCreate(db);
    }

    // ---------- DAO 访问 ----------

    public FileMetaDao fileMeta() {
        return fileDao;
    }

    public ScanHistoryDao scanHistory() {
        return historyDao;
    }

    /**
     * 启动期回填：历史表为空但 {@code indexed_files} 已有 {@code STATUS_DONE} 行时，
     * 插入一条 {@code trigger=recovered} 的占位记录，避免历史页永远空。
     *
     * <p>典型触发：旧版本/外部数据迁移后启动，DB 里已有大量 DONE 文件，但从未跑过
     * {@code IndexPipeline}（也就没写过历史）。UI 端会把这条记录展示为「N 个跳过 / 已完成」，
     * 用户能直观看到「这些文件就是上次留下的索引」。
     *
     * <p>幂等：每次启动只最多补一条；调用方应传入稳定时间戳（比如最新文件的
     * {@code modified}，回退到当前时间），保证重复启动不会改写历史。
     */
    public void maybeBackfillHistoryIfStale(long nowMs, long indexedAtMs) {
        ScanRecord rec = buildBackfillRecordIfStale(
                scanHistory().getAll().size(),
                fileDao.countDone(),
                nowMs, indexedAtMs);
        if (rec == null) return;
        scanHistory().insert(rec);
    }

    /**
     * 便捷入口：用当前时间作为「现在」与「索引时间」（仅用于一次性回填，精度不敏感）。
     */
    public void maybeBackfillHistoryIfStale() {
        long now = System.currentTimeMillis();
        maybeBackfillHistoryIfStale(now, now);
    }

    /**
     * 纯函数：给定「历史条数 + 已索引文件数 + 两个时间戳」,返回要写入历史的记录,
     * 或 {@code null} 表示「无需回填」(历史已存在,或根本没有可索引的文件)。
     *
     * <p>抽出为静态方法便于纯 JVM 单测覆盖——{@link IndexDatabase} 本身依赖 Android
     * Context,无法直接单测。
     */
    @androidx.annotation.VisibleForTesting
    static ScanRecord buildBackfillRecordIfStale(int historyCount,
                                                  int doneCount,
                                                  long nowMs,
                                                  long indexedAtMs) {
        if (historyCount > 0) return null;
        if (doneCount <= 0) return null;
        long finished = Math.max(nowMs, indexedAtMs);
        return new ScanRecord(indexedAtMs, finished,
                doneCount, doneCount, 0, 0, 0L, TRIGGER_RECOVERED);
    }
}
