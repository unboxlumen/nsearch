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
 * 原 {@code SearchEngine.buildSnippet} 拆出，逻辑保持不变：
 * <ol>
 *   <li>用 Lucene Highlighter 生成最佳片段（带 &lt;b&gt; 包裹的命中词）；</li>
 *   <li>失败 / 无片段 → 返回 snippet 前 FRAGMENT_SIZE 字符的纯文本截断。</li>
 * </ol>
 */
public final class SnippetHighlighter {

    private static final int FRAGMENT_SIZE = 160;
    private static final String HIGHLIGHT_OPEN = "<b>";
    private static final String HIGHLIGHT_CLOSE = "</b>";

    private SnippetHighlighter() {}

    /**
     * @param analyzer 已选定的查询分析器（用于 Highlighter re-analyze）
     * @param query    当前查询，用于打分匹配
     * @param snippet  Document 中预存的 SNIPPET 文本（可能为空）
     * @return 高亮 HTML 或纯文本截断；输入为空时返回空字符串
     */
    @NonNull
    public static String build(@NonNull Analyzer analyzer,
                               @NonNull Query query,
                               @Nullable String snippet) {
        if (snippet == null || snippet.isEmpty()) return "";
        try {
            String field = LuceneManager.Fields.CONTENT;
            QueryScorer scorer = new QueryScorer(query, field);
            Highlighter hl = new Highlighter(
                    new SimpleHTMLFormatter(HIGHLIGHT_OPEN, HIGHLIGHT_CLOSE),
                    new SimpleHTMLEncoder(),
                    scorer);
            hl.setTextFragmenter(new SimpleFragmenter(FRAGMENT_SIZE));
            String frag = hl.getBestFragment(analyzer, field, snippet);
            if (frag != null && !frag.isEmpty()) return frag;
        } catch (Exception ignored) {
        }
        int end = Math.min(FRAGMENT_SIZE, snippet.length());
        return snippet.substring(0, end);
    }
}
