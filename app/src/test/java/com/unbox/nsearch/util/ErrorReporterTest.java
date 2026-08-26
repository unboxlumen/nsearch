package com.unbox.nsearch.util;

import org.junit.After;
import org.junit.Test;

/**
 * {@link ErrorReporter} 的最小单测:
 *  - enabled=true 时 report 不抛异常;
 *  - enabled=false 时不抛异常(测试用开关);
 *  - 开关恢复避免影响其他测试。
 */
public class ErrorReporterTest {

    @After
    public void restore() {
        ErrorReporter.setEnabled(true);
    }

    @Test
    public void report_doesNotThrow() {
        ErrorReporter.report("Test", new RuntimeException("boom"));
        // 不抛即通过
    }

    @Test
    public void report_withMessage_doesNotThrow() {
        ErrorReporter.report("Test", new RuntimeException("boom"), "context");
        // 不抛即通过
    }

    @Test
    public void disabledReport_stillSafe() {
        ErrorReporter.setEnabled(false);
        ErrorReporter.report("Test", new RuntimeException("should be ignored"));
        // 不抛即通过
    }
}
