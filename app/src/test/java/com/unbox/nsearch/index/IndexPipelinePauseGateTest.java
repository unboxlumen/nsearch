package com.unbox.nsearch.index;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link IndexPipeline#waitWhilePaused(AtomicBoolean, AtomicBoolean, Object, long)} 的契约测试。
 *
 * <p>三条规则:
 *  <ul>
 *    <li>paused=false → 立刻返回,不等待</li>
 *    <li>paused=true、cancelled=true → 立刻返回</li>
 *    <li>paused=true、cancelled=false → 阻塞;外部 notifyAll 后应可被唤醒</li>
 *  </ul>
 */
public class IndexPipelinePauseGateTest {

    private static final long WAIT_TIMEOUT_MS = 500;
    private static final long IMMEDIATE_BOUND_MS = 100;
    private static final long BLOCK_SLEEP_MS = 150;
    private static final long JOIN_TIMEOUT_MS = 1000;

    @Test
    public void notPaused_returnsImmediately() {
        AtomicBoolean paused = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long t0 = System.currentTimeMillis();
        IndexPipeline.waitWhilePaused(paused, cancelled, new Object(), WAIT_TIMEOUT_MS);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue("应立即返回,但耗时 " + elapsed + "ms", elapsed < IMMEDIATE_BOUND_MS);
    }

    @Test
    public void pausedButCancelled_returnsImmediately() {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean(true);
        long t0 = System.currentTimeMillis();
        IndexPipeline.waitWhilePaused(paused, cancelled, new Object(), WAIT_TIMEOUT_MS);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue("cancel 后应立即返回,但耗时 " + elapsed + "ms", elapsed < IMMEDIATE_BOUND_MS);
    }

    @Test
    public void paused_blocksUntilResumed() throws InterruptedException {
        assertUnblocksAfter(false, false);
    }

    @Test
    public void paused_blocksUntilCancelled() throws InterruptedException {
        assertUnblocksAfter(false, true);
    }

    private static void assertUnblocksAfter(boolean pausedValue, boolean cancelledValue) throws InterruptedException {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Object lock = new Object();

        Thread t = new Thread(() ->
                IndexPipeline.waitWhilePaused(paused, cancelled, lock, WAIT_TIMEOUT_MS));
        t.start();
        Thread.sleep(BLOCK_SLEEP_MS);
        assertTrue("线程应仍处于阻塞", t.isAlive());

        paused.set(pausedValue);
        cancelled.set(cancelledValue);
        synchronized (lock) {
            lock.notifyAll();
        }
        t.join(JOIN_TIMEOUT_MS);
        assertFalse("解锁后线程应已退出", t.isAlive());
    }
}