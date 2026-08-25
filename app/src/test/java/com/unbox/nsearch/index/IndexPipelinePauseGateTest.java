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

    @Test
    public void notPaused_returnsImmediately() {
        AtomicBoolean paused = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long t0 = System.currentTimeMillis();
        IndexPipeline.waitWhilePaused(paused, cancelled, new Object(), 500);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue("应立即返回,但耗时 " + elapsed + "ms", elapsed < 100);
    }

    @Test
    public void pausedButCancelled_returnsImmediately() {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean(true);
        long t0 = System.currentTimeMillis();
        IndexPipeline.waitWhilePaused(paused, cancelled, new Object(), 500);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue("cancel 后应立即返回,但耗时 " + elapsed + "ms", elapsed < 100);
    }

    @Test
    public void paused_blocksUntilResumed() throws InterruptedException {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Object lock = new Object();

        Thread t = new Thread(() -> {
            // 持续阻塞直到 paused=false
            IndexPipeline.waitWhilePaused(paused, cancelled, lock, 200);
        });
        t.start();
        Thread.sleep(150); // 让它真的进入 wait
        assertTrue("线程应仍处于 RUNNABLE/WAITING", t.isAlive());

        paused.set(false);
        synchronized (lock) {
            lock.notifyAll();
        }
        t.join(1000);
        assertFalse("resume 后线程应已退出", t.isAlive());
    }

    @Test
    public void paused_blocksUntilCancelled() throws InterruptedException {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Object lock = new Object();

        Thread t = new Thread(() -> {
            IndexPipeline.waitWhilePaused(paused, cancelled, lock, 500);
        });
        t.start();
        Thread.sleep(150);
        assertTrue("线程应仍处于阻塞", t.isAlive());

        cancelled.set(true);
        synchronized (lock) {
            lock.notifyAll();
        }
        t.join(1000);
        assertFalse("cancel 后线程应已退出", t.isAlive());
    }
}