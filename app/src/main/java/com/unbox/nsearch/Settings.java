package com.unbox.nsearch;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 全局设置（SharedPreferences 封装）。
 * key 与 res/xml/preferences.xml 保持一致。
 */
public final class Settings {

    public static final String PREF_NAME = "nsearch_prefs";

    public static final String KEY_MODE = "search_mode";
    public static final String KEY_SYNONYM = "synonym_enabled";
    public static final String KEY_FILE_TYPES = "file_types";
    public static final String KEY_CHAR_LIMIT = "char_limit";
    public static final String KEY_SCOPE_URIS = "scope_uris";
    /** 最近搜索词（StringSet 持久化，LRU；最多 8 条）。 */
    public static final String KEY_RECENT_QUERIES = "recent_queries";

    /** {@link #addRecentQuery} 保留的最大条数。 */
    public static final int RECENT_QUERIES_MAX = 8;

    public enum SearchMode {
        STRICT, MEDIUM, LOOSE;

        /** SharedPreferences 中持久化的小写字符串；集中在此,避免 UI 层硬编码。 */
        public String prefValue() {
            switch (this) {
                case STRICT: return "strict";
                case LOOSE: return "loose";
                default: return "medium";
            }
        }

        /**
         * 把 SharedPreferences 字符串还原为枚举。
         * 未知值与 {@code null} 一律回退到 {@link #MEDIUM}（保持原 {@code getSearchMode()} 行为）。
         */
        public static SearchMode parse(String raw) {
            if (raw == null) return MEDIUM;
            switch (raw) {
                case "strict": return STRICT;
                case "loose": return LOOSE;
                default: return MEDIUM;
            }
        }
    }

    private static final Set<String> DEFAULT_TYPES;
    static {
        Set<String> s = new HashSet<>();
        s.add("txt");   // 文本
        s.add("xls");   // Excel
        s.add("doc");   // Word
        s.add("ppt");   // PowerPoint
        // md 为可选项，默认不勾选；csv/pdf 也改为非默认
        DEFAULT_TYPES = Collections.unmodifiableSet(s);
    }

    private final SharedPreferences prefs;

    public Settings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public SearchMode getSearchMode() {
        return SearchMode.parse(prefs.getString(KEY_MODE, SearchMode.MEDIUM.prefValue()));
    }

    public void setSearchMode(@NonNull SearchMode mode) {
        prefs.edit().putString(KEY_MODE, mode.prefValue()).apply();
    }

    public boolean isSynonymEnabled() {
        return prefs.getBoolean(KEY_SYNONYM, false);
    }

    public void setSynonymEnabled(boolean on) {
        prefs.edit().putBoolean(KEY_SYNONYM, on).apply();
    }

    /** 返回启用的文件类型扩展名集合（小写，无点）。默认全部启用。 */
    public Set<String> getEnabledTypes() {
        Set<String> s = prefs.getStringSet(KEY_FILE_TYPES, null);
        if (s == null || s.isEmpty()) return new HashSet<>(DEFAULT_TYPES);
        return new HashSet<>(s);
    }

    /** 单文件索引字数上限；0 表示无限制。 */
    public int getCharLimit() {
        try {
            return Integer.parseInt(prefs.getString(KEY_CHAR_LIMIT, "1000000"));
        } catch (NumberFormatException e) {
            return 1_000_000;
        }
    }

    /** 用户通过 SAF 添加的索引文件夹（tree Uri 字符串集合）。 */
    public Set<String> getScopeUris() {
        Set<String> s = prefs.getStringSet(KEY_SCOPE_URIS, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    public void addScopeUri(String treeUri) {
        Set<String> s = new HashSet<>(getScopeUris());
        s.add(treeUri);
        prefs.edit().putStringSet(KEY_SCOPE_URIS, s).apply();
    }

    public void clearScopeUris() {
        prefs.edit().remove(KEY_SCOPE_URIS).apply();
    }

    /**
     * 最近搜索词（LRU，最新在前）。空列表返回空集合（不返回 null）。
     */
    @NonNull
    public java.util.List<String> getRecentQueries() {
        Set<String> s = prefs.getStringSet(KEY_RECENT_QUERIES, null);
        if (s == null || s.isEmpty()) return new java.util.ArrayList<>();
        // SharedPreferences 不保证顺序；用 build 顺序作为稳定 LRU 视角：
        // 持久化时把最新写入尾部，读取后翻转即可「最新在前」。
        java.util.List<String> out = new java.util.ArrayList<>(s);
        Collections.reverse(out);
        return out;
    }

    /**
     * 把搜索词加入最近列表（去重 + LRU + 截断到 {@link #RECENT_QUERIES_MAX}）。
     * trim 后空串不入库。
     */
    public void addRecentQuery(@NonNull String query) {
        String q = query.trim();
        if (q.isEmpty()) return;
        Set<String> cur = prefs.getStringSet(KEY_RECENT_QUERIES, null);
        // 用 LinkedHashSet 保留顺序
        java.util.LinkedHashSet<String> next = new java.util.LinkedHashSet<>();
        if (cur != null) next.addAll(cur);
        next.remove(q);
        next.add(q);
        while (next.size() > RECENT_QUERIES_MAX) {
            java.util.Iterator<String> it = next.iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
        prefs.edit().putStringSet(KEY_RECENT_QUERIES, next).apply();
    }

    /** 清空最近搜索词。 */
    public void clearRecentQueries() {
        prefs.edit().remove(KEY_RECENT_QUERIES).apply();
    }

    /**
     * {@code putString} 在重构中暴露了「任意 key → 任意 String」的破口
     * (无法保证 value 合法、也无法被 IDE 重命名追踪)。
     * 所有写操作请走强类型 setter（{@link #setSearchMode} / {@link #setSynonymEnabled} 等）。
     */
    @Deprecated
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }
}
