package com.unbox.nsearch.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.unbox.nsearch.LuceneManager;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLEncoder;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;

/**
 * 把 {@code Document.SNIPPET} 转成高亮 HTML 摘要。
 *
 * <p>性能关键点：高亮只需定位命中词附近的片段，因此会对源文本做截断
 * （{@link #MAX_HIGHLIGHT_SOURCE}）后再交给 Lucene Highlighter 重新分词；
 * 否则每次搜索都会对最长 {@link LuceneManager#SNIPPET_LIMIT}（20 万字符）的
 * snippet 做全量 jieba 分词，命中一多就是秒级延迟。
 *
 * <p>用法：一次搜索构造一个 {@link SnippetHighlighter} 实例（复用 scorer/formatter），
 * 对每篇结果调用 {@link #build(String)}；旧的静态便捷方法 {@link #build(Analyzer, Query, String)}
 * 保留兼容，内部等价于新建实例。
 */
public final class SnippetHighlighter {

    private static final int FRAGMENT_SIZE = 160;
    private static final String HIGHLIGHT_OPEN = "<b>";
    private static final String HIGHLIGHT_CLOSE = "</b>";

    /** 高亮源文本截断上限：足够定位命中词附近片段，同时把分词成本限制在常量级。 */
    public static final int MAX_HIGHLIGHT_SOURCE = 4096;

    private final Analyzer analyzer;
    private final Highlighter highlighter;

    public SnippetHighlighter(@NonNull Analyzer analyzer, @NonNull Query query) {
        this.analyzer = analyzer;
        QueryScorer scorer = new QueryScorer(query, LuceneManager.Fields.CONTENT);
        this.highlighter = new Highlighter(
                new SimpleHTMLFormatter("<b>", "</b>"),
                new SimpleHTMLEncoder(),
                scorer);
        this.highlighter.setTextFragmenter(new SimpleFragmenter(FRAGMENT_SIZE));
    }

    /**
     * @param snippet Document 中预存的 SNIPPET 文本（可能为空）
     * @return 高亮 HTML 或纯文本截断；输入为空时返回空字符串
     */
    @NonNull
    public String build(@Nullable String snippet) {
        if (snippet == null || snippet.isEmpty()) return "";
        String source = snippet.length() > MAX_HIGHLIGHT_SOURCE
                ? snippet.substring(0, MAX_HIGHLIGHT_SOURCE) : snippet;
        try {
            String frag = highlighter.getBestFragment(analyzer, LuceneManager.Fields.CONTENT, source);
            if (frag != null && !frag.isEmpty()) return frag;
        } catch (Exception ignored) {
        }
        int end = Math.min(FRAGMENT_SIZE, snippet.length());
        return snippet.substring(0, end);
    }

    /** 静态便捷方法（保持旧 API）：每次新建实例，等价于旧行为 + 截断优化。 */
    @NonNull
    public static String build(@NonNull Analyzer analyzer, @NonNull Query query, @Nullable String snippet) {
        return new SnippetHighlighter(analyzer, query).build(snippet);
    }
}
