package com.unbox.nsearch.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.unbox.nsearch.model.ScanRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 索引状态库。保存每个已索引文件的元数据，用于「重新打开时增量同步」。
 * 同时保存每次扫描的「文件扫描历史」。
 */
public final class IndexDatabase extends SQLiteOpenHelper {

    private static final String NAME = "nsearch_index.db";
    private static final int VERSION = 1;

    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    private static IndexDatabase instance;

    public static synchronized IndexDatabase get(Context context) {
        if (instance == null) instance = new IndexDatabase(context.getApplicationContext());
        return instance;
    }

    private IndexDatabase(Context ctx) {
        super(ctx, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE indexed_files (" +
                "path TEXT PRIMARY KEY, name TEXT, size INTEGER, modified INTEGER, " +
                "length_chars INTEGER, status INTEGER, ext TEXT)");
        db.execSQL("CREATE TABLE scan_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER, finished INTEGER, " +
                "total INTEGER, indexed INTEGER, failed INTEGER, skipped INTEGER, " +
                "duration INTEGER, trigger TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS indexed_files");
        db.execSQL("DROP TABLE IF EXISTS scan_history");
        onCreate(db);
    }

    // ---------- indexed_files ----------

    public static class FileRow {
        public String path;
        public String name;
        public long size;
        public long modified;
        public int lengthChars;
        public int status;
        public String ext;
    }

    public FileRow getRow(String path) {
        try (Cursor c = getReadableDatabase().query("indexed_files", null,
                "path=?", new String[]{path}, null, null, null)) {
            if (c.moveToFirst()) {
                FileRow r = new FileRow();
                r.path = c.getString(c.getColumnIndexOrThrow("path"));
                r.name = c.getString(c.getColumnIndexOrThrow("name"));
                r.size = c.getLong(c.getColumnIndexOrThrow("size"));
                r.modified = c.getLong(c.getColumnIndexOrThrow("modified"));
                r.lengthChars = c.getInt(c.getColumnIndexOrThrow("length_chars"));
                r.status = c.getInt(c.getColumnIndexOrThrow("status"));
                r.ext = c.getString(c.getColumnIndexOrThrow("ext"));
                return r;
            }
        }
        return null;
    }

    /** 写入/更新一条文件索引记录。 */
    public void upsert(String path, String name, long size, long modified, int lengthChars,
                       int status, String ext) {
        ContentValues v = new ContentValues();
        v.put("path", path);
        v.put("name", name);
        v.put("size", size);
        v.put("modified", modified);
        v.put("length_chars", lengthChars);
        v.put("status", status);
        v.put("ext", ext);
        getWritableDatabase().insertWithOnConflict("indexed_files", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteByPath(String path) {
        getWritableDatabase().delete("indexed_files", "path=?", new String[]{path});
    }

    public void clearAll() {
        getWritableDatabase().delete("indexed_files", null, null);
    }

    /** 返回库中全部 path（用于与本次扫描结果做差集，清理已删除文件）。 */
    public Set<String> getAllPaths() {
        Set<String> set = new HashSet<>();
        try (Cursor c = getReadableDatabase().query("indexed_files",
                new String[]{"path"}, null, null, null, null, null)) {
            while (c.moveToNext()) set.add(c.getString(0));
        }
        return set;
    }

    public int countDone() {
        try (Cursor c = getReadableDatabase().query("indexed_files",
                new String[]{"COUNT(*)"}, "status=?", new String[]{String.valueOf(STATUS_DONE)},
                null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------- scan_history ----------

    public void insertScanRecord(ScanRecord r) {
        ContentValues v = new ContentValues();
        v.put("started", r.startedAt);
        v.put("finished", r.finishedAt);
        v.put("total", r.totalFiles);
        v.put("indexed", r.indexedFiles);
        v.put("failed", r.failedFiles);
        v.put("skipped", r.skippedFiles);
        v.put("duration", r.durationMs);
        v.put("trigger", r.trigger);
        getWritableDatabase().insert("scan_history", null, v);
    }

    public List<ScanRecord> getAllScanRecords() {
        List<ScanRecord> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("scan_history", null, null, null,
                null, null, "id DESC")) {
            while (c.moveToNext()) {
                ScanRecord r = new ScanRecord();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.startedAt = c.getLong(c.getColumnIndexOrThrow("started"));
                r.finishedAt = c.getLong(c.getColumnIndexOrThrow("finished"));
                r.totalFiles = c.getInt(c.getColumnIndexOrThrow("total"));
                r.indexedFiles = c.getInt(c.getColumnIndexOrThrow("indexed"));
                r.failedFiles = c.getInt(c.getColumnIndexOrThrow("failed"));
                r.skippedFiles = c.getInt(c.getColumnIndexOrThrow("skipped"));
                r.durationMs = c.getLong(c.getColumnIndexOrThrow("duration"));
                r.trigger = c.getString(c.getColumnIndexOrThrow("trigger"));
                list.add(r);
            }
        }
        return list;
    }

    public void clearScanRecords() {
        getWritableDatabase().delete("scan_history", null, null);
    }
}
