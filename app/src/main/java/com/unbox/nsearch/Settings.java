package com.unbox.nsearch;

import android.content.Context;
import android.content.SharedPreferences;

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

    public enum SearchMode {
        STRICT, MEDIUM, LOOSE;

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
        return SearchMode.parse(prefs.getString(KEY_MODE, "medium"));
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

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }
}
