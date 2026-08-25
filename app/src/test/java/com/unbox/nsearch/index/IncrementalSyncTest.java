package com.unbox.nsearch.index;

import android.content.Context;

import com.unbox.nsearch.FileScanner;
import com.unbox.nsearch.db.FileMetaDao;
import com.unbox.nsearch.db.IndexDatabase;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link IncrementalSync#matches(FileMetaDao.FileRow, FileScanner.ScanItem)} 的纯逻辑测试。
 *
 * <p>这条规则是「增量跳过」的核心判定,改之前必须保证语义不变 ——
 * 单测覆盖边界:null 行、FAILED 状态、size 变化、modified 变化、lastModified=0 的特殊处理。
 */
public class IncrementalSyncTest {

    private static FileScanner.ScanItem fakeItem(String name, long size, long modified) {
        return new FileScanner.ScanItem() {
            @Override public String getName() { return name; }
            @Override public long length() { return size; }
            @Override public long lastModified() { return modified; }
            @Override public String getPath() { return "/tmp/" + name; }
            @Override public String getDisplayPath() { return getPath(); }
            @Override public String getOpenUri() { return getPath(); }
            @Override public boolean isContentUri() { return false; }
            @Override public InputStream openStream(Context ctx) throws IOException {
                return new ByteArrayInputStream(new byte[0]);
            }
            @Override public String getExt() { return "txt"; }
        };
    }

    private static FileMetaDao.FileRow row(int status, long size, long modified) {
        FileMetaDao.FileRow r = new FileMetaDao.FileRow();
        r.path = "/tmp/x";
        r.name = "x";
        r.size = size;
        r.modified = modified;
        r.lengthChars = 0;
        r.status = status;
        r.ext = "txt";
        return r;
    }

    @Test
    public void nullRow_neverUnchanged() {
        assertFalse(IncrementalSync.matches(null, fakeItem("a.txt", 10, 100)));
    }

    @Test
    public void failedStatus_neverUnchanged() {
        assertFalse(IncrementalSync.matches(row(IndexDatabase.STATUS_FAILED, 10, 100),
                fakeItem("a.txt", 10, 100)));
    }

    @Test
    public void sizeMismatch_notUnchanged() {
        assertFalse(IncrementalSync.matches(row(IndexDatabase.STATUS_DONE, 10, 100),
                fakeItem("a.txt", 11, 100)));
    }

    @Test
    public void modifiedMismatch_notUnchanged() {
        assertFalse(IncrementalSync.matches(row(IndexDatabase.STATUS_DONE, 10, 100),
                fakeItem("a.txt", 10, 101)));
    }

    @Test
    public void itemModifiedZero_ignoresStoredModified() {
        // lastModified==0 视为「未知」,不再校验;只比较 size 与 status。
        assertTrue(IncrementalSync.matches(row(IndexDatabase.STATUS_DONE, 10, 12345),
                fakeItem("a.txt", 10, 0)));
    }

    @Test
    public void allMatching_unchanged() {
        assertTrue(IncrementalSync.matches(row(IndexDatabase.STATUS_DONE, 10, 100),
                fakeItem("a.txt", 10, 100)));
    }
}