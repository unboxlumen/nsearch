package com.unbox.nsearch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link Settings} 中与 Android 框架无关的纯逻辑测试。
 *
 * 由于 Settings 直接依赖 SharedPreferences，不能在纯 JVM 单测里完整跑；
 * 这里把 enum 转换 / 工具方法提取到静态工具类后再测。这样既覆盖了关键逻辑，
 * 也保证了主路径上不引入 Robolectric 之类的重依赖。
 */
public class SettingsTest {

    // parseSearchMode 通过 SearchMode.parse 暴露：strict / loose / 其它 → MEDIUM
    @Test
    public void parseSearchMode_strict() {
        assertEquals(Settings.SearchMode.STRICT, Settings.SearchMode.parse("strict"));
    }

    @Test
    public void parseSearchMode_loose() {
        assertEquals(Settings.SearchMode.LOOSE, Settings.SearchMode.parse("loose"));
    }

    @Test
    public void parseSearchMode_defaultWhenEmpty() {
        assertEquals(Settings.SearchMode.MEDIUM, Settings.SearchMode.parse(""));
        assertEquals(Settings.SearchMode.MEDIUM, Settings.SearchMode.parse(null));
    }

    @Test
    public void parseSearchMode_unknownFallsBackToMedium() {
        assertEquals(Settings.SearchMode.MEDIUM, Settings.SearchMode.parse("unknown"));
    }

    @Test
    public void enumHasThreeValues() {
        // 防止后续误删枚举值（影响高级搜索抽屉的三个芯片）
        assertEquals(3, Settings.SearchMode.values().length);
        assertTrue(java.util.Arrays.asList(Settings.SearchMode.values()).contains(Settings.SearchMode.STRICT));
        assertTrue(java.util.Arrays.asList(Settings.SearchMode.values()).contains(Settings.SearchMode.MEDIUM));
        assertTrue(java.util.Arrays.asList(Settings.SearchMode.values()).contains(Settings.SearchMode.LOOSE));
    }
}
