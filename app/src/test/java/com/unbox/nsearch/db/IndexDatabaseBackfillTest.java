package com.unbox.nsearch.db;

import com.unbox.nsearch.model.ScanRecord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link IndexDatabase#buildBackfillRecordIfStale} 的纯 JVM 单测。
 *
 * <p>把决策逻辑抽到静态方法后,无需 Robolectric / Android Context 即可覆盖以下场景:
 * <ul>
 *   <li>历史已存在 → 不回填</li>
 *   <li>无 DONE 文件 → 不回填</li>
 *   <li>正常场景 → 写入 recovered 占位记录</li>
 *   <li>indexedAtMs 晚于 nowMs → finished 至少是 nowMs（不会让历史「早于现在」)</li>
 * </ul>
 */
public class IndexDatabaseBackfillTest {

    @Test
    public void historyNotEmpty_skipsBackfill() {
        // 已有 1 条历史 → 不应回填,即便 DONE 文件很多
        assertNull(IndexDatabase.buildBackfillRecordIfStale(
                /* historyCount */ 1,
                /* doneCount */ 200,
                /* nowMs */ 1000L,
                /* indexedAtMs */ 500L));
    }

    @Test
    public void noDoneFiles_skipsBackfill() {
        // 没历史也没 DONE 文件 → 不回填
        assertNull(IndexDatabase.buildBackfillRecordIfStale(
                0, 0, 1000L, 500L));
    }

    @Test
    public void staleData_insertsRecoveredRecord() {
        // 经典场景：迁移后无历史、已索引 501 个文件
        ScanRecord rec = IndexDatabase.buildBackfillRecordIfStale(
                0, 501, 1000L, 500L);
        assertNotNull(rec);
        assertEquals(501, rec.totalFiles);
        assertEquals(501, rec.indexedFiles);
        assertEquals(0, rec.failedFiles);
        assertEquals(0, rec.skippedFiles);
        assertEquals(IndexDatabase.TRIGGER_RECOVERED, rec.trigger);
        // finished 至少 = nowMs,不会让历史在视觉上「早于现在」
        assertTrue(rec.finishedAt >= 1000L);
        // indexedAtMs 早于 nowMs → started 反映「数据原本的时间」
        assertEquals(500L, rec.startedAt);
    }

    @Test
    public void indexedAtInFuture_finishedTracksLaterTimestamp() {
        // 异常输入: indexedAtMs 晚于 nowMs → finished 取两者中较晚者(indexedAtMs)
        // 这样历史展示的时间窗不会「早于现在」导致看上去仍在进行中
        long now = 1000L;
        long indexedAt = 5000L;
        ScanRecord rec = IndexDatabase.buildBackfillRecordIfStale(0, 10, now, indexedAt);
        assertNotNull(rec);
        assertEquals("finished = max(now, indexedAt)", indexedAt, rec.finishedAt);
        assertEquals("started 仍是传入的 indexedAtMs", indexedAt, rec.startedAt);
    }

    @Test
    public void negativeDoneCount_skipsBackfill() {
        // 防御: 负数 doneCount 当作"无文件"处理
        assertNull(IndexDatabase.buildBackfillRecordIfStale(0, -1, 1000L, 500L));
    }
}
