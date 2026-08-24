package com.unbox.nsearch.db;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import com.unbox.nsearch.model.ScanRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * scan_history 表的数据访问对象。
 *
 * 之前与 indexed_files 共用 {@link IndexDatabase}，拆分后职责更清晰：
 * 历史记录的写入/读取不依赖文件元数据表，反之亦然。
 */
public final class ScanHistoryDao {

    public static final String TABLE = "scan_history";

    public static final class Cols {
        public static final String ID = "id";
        public static final String STARTED = "started";
        public static final String FINISHED = "finished";
        public static final String TOTAL = "total";
        public static final String INDEXED = "indexed";
        public static final String FAILED = "failed";
        public static final String SKIPPED = "skipped";
        public static final String DURATION = "duration";
        public static final String TRIGGER = "trigger";

        private Cols() {}
    }

    private final IndexDatabase db;

    public ScanHistoryDao(@NonNull IndexDatabase db) {
        this.db = db;
    }

    public void insert(ScanRecord r) {
        ContentValues v = new ContentValues();
        v.put(Cols.STARTED, r.startedAt);
        v.put(Cols.FINISHED, r.finishedAt);
        v.put(Cols.TOTAL, r.totalFiles);
        v.put(Cols.INDEXED, r.indexedFiles);
        v.put(Cols.FAILED, r.failedFiles);
        v.put(Cols.SKIPPED, r.skippedFiles);
        v.put(Cols.DURATION, r.durationMs);
        v.put(Cols.TRIGGER, r.trigger);
        db.getWritableDatabase().insert(TABLE, null, v);
    }

    public List<ScanRecord> getAll() {
        List<ScanRecord> list = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(TABLE, null, null, null,
                null, null, Cols.ID + " DESC")) {
            while (c.moveToNext()) {
                ScanRecord r = new ScanRecord();
                r.id = c.getLong(c.getColumnIndexOrThrow(Cols.ID));
                r.startedAt = c.getLong(c.getColumnIndexOrThrow(Cols.STARTED));
                r.finishedAt = c.getLong(c.getColumnIndexOrThrow(Cols.FINISHED));
                r.totalFiles = c.getInt(c.getColumnIndexOrThrow(Cols.TOTAL));
                r.indexedFiles = c.getInt(c.getColumnIndexOrThrow(Cols.INDEXED));
                r.failedFiles = c.getInt(c.getColumnIndexOrThrow(Cols.FAILED));
                r.skippedFiles = c.getInt(c.getColumnIndexOrThrow(Cols.SKIPPED));
                r.durationMs = c.getLong(c.getColumnIndexOrThrow(Cols.DURATION));
                r.trigger = c.getString(c.getColumnIndexOrThrow(Cols.TRIGGER));
                list.add(r);
            }
        }
        return list;
    }

    public void clear() {
        db.getWritableDatabase().delete(TABLE, null, null);
    }
}
