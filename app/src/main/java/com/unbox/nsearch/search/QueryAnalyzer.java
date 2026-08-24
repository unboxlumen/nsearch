package com.unbox.nsearch.search;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.unbox.nsearch.LuceneManager;
import com.unbox.nsearch.analyzers.SynonymMapLoader;

import org.apache.lucene.analysis.Analyzer;

import java.io.IOException;

/**
 * 解析查询端的 Lucene {@link Analyzer}。
 *
 * 设计动机：原 SearchEngine 在方法内部组合 useSynonym / SynonymMapLoader.get(ctx) 失败回退逻辑，
 * 这里集中一处，便于搜索路径之外的代码（如测试）也能复用同样的策略。
 */
public final class QueryAnalyzer {

    private QueryAnalyzer() {}

    /**
     * 取得查询分析器。
     *
     * <p>策略：
     * <ol>
     *   <li>若 {@code useSynonyms == true} 尝试加载同义词词典，失败则回退到无同义词的分析器；</li>
     *   <li>若 {@code useSynonyms == false} 直接返回无同义词的分析器。</li>
     * </ol>
     *
     * @param ctx         用于加载 {@code res/raw/synonyms.txt}；可为 null（仅在 {@code useSynonyms == false} 时允许）
     * @param km          LuceneManager，提供 analyzer 工厂
     * @param useSynonyms 是否启用同义词扩展
     */
    @NonNull
    public static Analyzer obtain(@Nullable Context ctx, @NonNull LuceneManager km, boolean useSynonyms) {
        if (useSynonyms && ctx != null) {
            try {
                return km.getQueryAnalyzer(true, SynonymMapLoader.get(ctx));
            } catch (IOException ignored) {
                // 同义词词典加载失败则回退
            }
        }
        return km.getQueryAnalyzer(false, null);
    }
}
