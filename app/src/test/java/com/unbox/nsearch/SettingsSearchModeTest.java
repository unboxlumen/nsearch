package com.unbox.nsearch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 验证 {@link Settings.SearchMode#prefValue()} 与 {@link Settings.SearchMode#parse(String)}
 * 互为反函数 — 重构后 UI 层用 setSearchMode(mode),内部用 mode.prefValue() 持久化,
 * 不再依赖硬编码字符串。本测试防止未来「删改枚举值时漏掉 prefValue 分支」。
 */
public class SettingsSearchModeTest {

    @Test
    public void prefValue_strict() {
        assertEquals("strict", Settings.SearchMode.STRICT.prefValue());
    }

    @Test
    public void prefValue_medium() {
        assertEquals("medium", Settings.SearchMode.MEDIUM.prefValue());
    }

    @Test
    public void prefValue_loose() {
        assertEquals("loose", Settings.SearchMode.LOOSE.prefValue());
    }

    @Test
    public void allEnumValuesCovered() {
        // 强制每个枚举值都有 prefValue() 输出;
        // 漏掉一个 switch 分支会让测试失败,从而提醒补上。
        for (Settings.SearchMode m : Settings.SearchMode.values()) {
            assertEquals(m, Settings.SearchMode.parse(m.prefValue()));
        }
    }
}