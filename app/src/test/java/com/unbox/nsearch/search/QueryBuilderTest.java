package com.unbox.nsearch.search;

import com.unbox.nsearch.Settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link QueryBuilder#computeMinShouldMatch(Settings.SearchMode, int)} 的纯逻辑测试。
 *
 * 之所以能纯 JVM 跑：这是无 IO、无 Android 依赖的枚举映射函数。
 */
public class QueryBuilderTest {

    @Test
    public void strict_returnsTermCount() {
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.STRICT, 1));
        assertEquals(2, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.STRICT, 2));
        assertEquals(5, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.STRICT, 5));
    }

    @Test
    public void loose_returnsAlwaysOne() {
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.LOOSE, 1));
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.LOOSE, 3));
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.LOOSE, 10));
    }

    @Test
    public void medium_returnsTermCountMinusOne() {
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.MEDIUM, 2));
        assertEquals(2, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.MEDIUM, 3));
        assertEquals(4, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.MEDIUM, 5));
    }

    @Test
    public void medium_singleTermStillMatchesAtLeastOne() {
        // 原实现: Math.max(1, termCount - 1) → 单 term 时返回 1
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.MEDIUM, 1));
    }

    @Test
    public void medium_zeroTermsReturnsOne() {
        // 防御性：termCount=0 时 medium 仍返回 1（与 max(1, -1) 一致）
        assertEquals(1, QueryBuilder.computeMinShouldMatch(Settings.SearchMode.MEDIUM, 0));
    }
}
