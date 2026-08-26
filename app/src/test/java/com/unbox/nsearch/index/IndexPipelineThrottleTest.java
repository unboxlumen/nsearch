package com.unbox.nsearch.index;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link IndexPipeline} 三个节流/节奏策略类（{@link IndexPipeline.Cadence}、
 * {@link IndexPipeline.ScanToastThrottle}、{@link IndexPipeline.IndexToastThrottle}）的
 * 行为契约测试，锁定重构后的语义不变。
 */
public class IndexPipelineThrottleTest {

    // ---------------- Cadence（commit / notify 节奏） ----------------

    @Test
    public void commit_happensEveryCommitEvery() {
        IndexPipeline.Cadence c = new IndexPipeline.Cadence(200, 5);
        assertFalse(c.shouldCommit(199));
        assertTrue(c.shouldCommit(200));
        assertFalse(c.shouldCommit(399));
        assertTrue(c.shouldCommit(400));
    }

    @Test
    public void notify_happensEveryNotifyEvery() {
        IndexPipeline.Cadence c = new IndexPipeline.Cadence(200, 5);
        assertFalse(c.shouldNotify(4));
        assertTrue(c.shouldNotify(5));
        assertFalse(c.shouldNotify(9));
        assertTrue(c.shouldNotify(10));
    }

    // ---------------- ScanToastThrottle（扫描阶段：200 个 / 1 秒） ----------------

    @Test
    public void scanThrottle_firesByCountStep() {
        IndexPipeline.ScanToastThrottle t = new IndexPipeline.ScanToastThrottle();
        // 初始 lastCount=0，count 尚未累计满 200 个、时间差也不足 → 不触发
        assertFalse(t.shouldToast(150, 5));
        // 累计满 200 个 → 触发
        assertTrue(t.shouldToast(200, 10));
        // 刚触发过，count 只差 100、时间差 100ms → 不触发
        assertFalse(t.shouldToast(300, 110));
    }

    @Test
    public void scanThrottle_firesByElapsedTime() {
        IndexPipeline.ScanToastThrottle t = new IndexPipeline.ScanToastThrottle();
        // 首次调用且时间已超过 1 秒（lastMs 初始 0）→ 放行
        assertTrue(t.shouldToast(0, 2000));
        // 刚触发过，count 未累计、时间差 500ms → 不放行
        assertFalse(t.shouldToast(1, 2500));
        // 距上次触发满 1 秒 → 放行
        assertTrue(t.shouldToast(1, 3000));
    }

    // ---------------- IndexToastThrottle（索引阶段：前 100 每 50 / 之后每 500） ----------------

    @Test
    public void indexThrottle_earlyRangeEvery50() {
        IndexPipeline.IndexToastThrottle t = new IndexPipeline.IndexToastThrottle();
        assertTrue(t.shouldToast(50));   // 第 50 个 → toast
        assertTrue(t.shouldToast(100));  // 第 100 个 → toast
        // 进入后期区间后 150 距上次(100)不足 500 → 不 toast
        assertFalse(t.shouldToast(150));
    }

    @Test
    public void indexThrottle_lateRangeEvery500() {
        IndexPipeline.IndexToastThrottle t = new IndexPipeline.IndexToastThrottle();
        assertTrue(t.shouldToast(50));
        assertTrue(t.shouldToast(100));
        assertFalse(t.shouldToast(101));
        assertFalse(t.shouldToast(599));
        assertTrue(t.shouldToast(600));  // 距上次(100)满 500 → toast
        assertFalse(t.shouldToast(601));
    }
}
