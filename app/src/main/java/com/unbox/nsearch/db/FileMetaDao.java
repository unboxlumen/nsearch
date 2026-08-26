package com.unbox.nsearch.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * indexed_files 表的数据访问对象。
 *
 * 仅负责单表的 CRUD，SQL 集中在此；{@link IndexDatabase} 提供底层的可写/可读 {@link SQLiteDatabase}。
 * 之所以独立于 {@link ScanHistoryDao}，是因为两者字段差异大且无跨表查询，合并会让单类变得过重。
 */
public final class FileMetaDao {

    public static final String TABLE = "indexed_files";

    public static final class Cols {
        public static final String PATH = "path";
        public static final String NAME = "name";
        public static final String SIZE = "size";
        public static final String MODIFIED = "modified";
        public static final String LENGTH_CHARS = "length_chars";
        public static final String STATUS = "status";
        public static final String EXT = "ext";

        private Cols() {}
    }

    private final IndexDatabase db;

    public FileMetaDao(@NonNull IndexDatabase db) {
        this.db = db;
    }

    public FileRow getRow(String path) {
        try (Cursor c = db.getReadableDatabase().query(TABLE, null,
                Cols.PATH + "=?", new String[]{path}, null, null, null)) {
            if (c.moveToFirst()) {
                FileRow r = new FileRow();
                r.path = c.getString(c.getColumnIndexOrThrow(Cols.PATH));
                r.name = c.getString(c.getColumnIndexOrThrow(Cols.NAME));
                r.size = c.getLong(c.getColumnIndexOrThrow(Cols.SIZE));
                r.modified = c.getLong(c.getColumnIndexOrThrow(Cols.MODIFIED));
                r.lengthChars = c.getInt(c.getColumnIndexOrThrow(Cols.LENGTH_CHARS));
                r.status = c.getInt(c.getColumnIndexOrThrow(Cols.STATUS));
                r.ext = c.getString(c.getColumnIndexOrThrow(Cols.EXT));
                return r;
            }
        }
        return null;
    }

    /** 写入/更新一条文件索引记录。 */
    public void upsert(String path, String name, long size, long modified, int lengthChars,
                       int status, String ext) {
        ContentValues v = new ContentValues();
        v.put(Cols.PATH, path);
        v.put(Cols.NAME, name);
        v.put(Cols.SIZE, size);
        v.put(Cols.MODIFIED, modified);
        v.put(Cols.LENGTH_CHARS, lengthChars);
        v.put(Cols.STATUS, status);
        v.put(Cols.EXT, ext);
        db.getWritableDatabase().insertWithOnConflict(TABLE, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteByPath(String path) {
        db.getWritableDatabase().delete(TABLE, Cols.PATH + "=?", new String[]{path});
    }

    public void clear() {
        db.getWritableDatabase().delete(TABLE, null, null);
    }

    /** 返回库中全部 path（用于与本次扫描结果做差集，清理已删除文件）。 */
    public Set<String> getAllPaths() {
        Set<String> set = new HashSet<>();
        try (Cursor c = db.getReadableDatabase().query(TABLE,
                new String[]{Cols.PATH}, null, null, null, null, null)) {
            while (c.moveToNext()) set.add(c.getString(c.getColumnIndexOrThrow(Cols.PATH)));
        }
        return set;
    }

    public int countDone() {
        try (Cursor c = db.getReadableDatabase().query(TABLE,
                new String[]{"COUNT(*)"},
                Cols.STATUS + "=?",
                new String[]{String.valueOf(IndexDatabase.STATUS_DONE)},
                null, null, null)) {
            return c.moveToFirst() ? c.getInt(c.getColumnIndexOrThrow("COUNT(*)")) : 0;
        }
    }

    /**
     * 与原 {@code IndexDatabase.FileRow} 保持完全一致的字段（保持调用方不感知拆分）。
     */
    public static class FileRow {
        public String path;
        public String name;
        public long size;
        public long modified;
        public int lengthChars;
        public int status;
        public String ext;
    }
}
