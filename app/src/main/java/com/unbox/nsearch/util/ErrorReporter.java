package com.unbox.nsearch.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 统一异常落点：所有索引/搜索路径上的 {@code catch (Throwable ignored)} 都改走这里。
 *
 * <p>当前仅做 Logcat 输出（带 tag + message + 堆栈第一行），后续阶段可在此接入崩溃统计。
 *
 * <p>调用方契约：传入的 tag 应该是稳定的英文短串，便于检索日志；message 是人类可读的描述。
 */
public final class ErrorReporter {

    private static final String DEFAULT_TAG = "nsearch";

    /** 仅供单测或调试用：是否启用报告；生产环境永远为 true。 */
    private static volatile boolean enabled = true;

    private ErrorReporter() {}

    public static void report(@NonNull String tag, @NonNull Throwable t) {
        if (!enabled) return;
        safeLog(DEFAULT_TAG + "." + tag, describe(t), t);
    }

    public static void report(@NonNull String tag, @NonNull Throwable t, @Nullable String message) {
        if (!enabled) return;
        safeLog(DEFAULT_TAG + "." + tag, message + " — " + describe(t), t);
    }

    /**
     * 在 JVM 单元测试下 {@link Log#e} 会抛 Stub!,包一层 try/catch 保证 reporter 永不抛异常。
     */
    private static void safeLog(String tag, String msg, Throwable t) {
        try {
            Log.e(tag, msg, t);
        } catch (Throwable ignored) {
        }
    }

    private static String describe(Throwable t) {
        if (t == null) return "null";
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        return (msg == null || msg.isEmpty()) ? name : (name + ": " + msg);
    }

    @androidx.annotation.VisibleForTesting
    static void setEnabled(boolean on) {
        enabled = on;
    }
}