package com.unbox.nsearch.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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

}
