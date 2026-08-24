package com.unbox.nsearch.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.unbox.nsearch.model.ScanRecord;

import java.util.List;
import java.util.Set;

/**
 * SQLite 持有者 + 表结构定义。
 *
 * 之前此文件混了 indexed_files 与 scan_history 两张表的全部 CRUD，
 * 现在拆为 {@link FileMetaDao} 与 {@link ScanHistoryDao}，
 * 这里只负责 {@link SQLiteOpenHelper} 的生命周期与建表语句。
 *
 * 出于「保持外部行为完全不变」的约束，旧的便捷方法（{@code FileRow} 嵌套类、
 * {@link #getRow}、{@link #upsert} 等）仍以委托形式保留，调用方无需改动。
 * 历史 API 标记为 {@code @Deprecated}，后续阶段会逐步迁移到 DAO 直调。
 */
public final class IndexDatabase extends SQLiteOpenHelper {

    private static final String NAME = "nsearch_index.db";
    private static final int VERSION = 1;

    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

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

    // ---------- indexed_files 兼容旧 API（委托给 FileMetaDao）----------

    /** 兼容旧调用方；内部委托给 {@link FileMetaDao#getRow}。 */
    @Deprecated
    public static class FileRow {
        public String path;
        public String name;
        public long size;
        public long modified;
        public int lengthChars;
        public int status;
        public String ext;
    }

    @Deprecated
    public FileRow getRow(String path) {
        FileMetaDao.FileRow row = fileDao.getRow(path);
        if (row == null) return null;
        FileRow r = new FileRow();
        r.path = row.path;
        r.name = row.name;
        r.size = row.size;
        r.modified = row.modified;
        r.lengthChars = row.lengthChars;
        r.status = row.status;
        r.ext = row.ext;
        return r;
    }

    @Deprecated
    public void upsert(String path, String name, long size, long modified, int lengthChars,
                       int status, String ext) {
        fileDao.upsert(path, name, size, modified, lengthChars, status, ext);
    }

    @Deprecated
    public void deleteByPath(String path) {
        fileDao.deleteByPath(path);
    }

    @Deprecated
    public void clearAll() {
        fileDao.clearAll();
    }

    @Deprecated
    public Set<String> getAllPaths() {
        return fileDao.getAllPaths();
    }

    @Deprecated
    public int countDone() {
        return fileDao.countDone();
    }

    // ---------- scan_history 兼容旧 API（委托给 ScanHistoryDao）----------

    @Deprecated
    public void insertScanRecord(ScanRecord r) {
        historyDao.insert(r);
    }

    @Deprecated
    public List<ScanRecord> getAllScanRecords() {
        return historyDao.getAll();
    }

    @Deprecated
    public void clearScanRecords() {
        historyDao.clear();
    }
}
