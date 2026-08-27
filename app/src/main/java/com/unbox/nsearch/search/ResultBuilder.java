package com.unbox.nsearch.search;

import android.content.Context;

import androidx.annotation.NonNull;

import com.unbox.nsearch.FileType;
import com.unbox.nsearch.LuceneManager;
import com.unbox.nsearch.R;
import com.unbox.nsearch.model.SearchResult;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@link TopDocs} 里的 {@link ScoreDoc} 列表转成 {@link SearchResult}，附带高亮摘要。
 *
 * 原 {@code SearchEngine.search} 的 for 循环拆出，保留 typeLabel 解析与 snippetHtml 设置。
 */
public final class ResultBuilder {

    private ResultBuilder() {}

    /**
     * 把 TopDocs 转换为 SearchResult 列表。
     *
     * <p>索引文档字段必须与 {@link LuceneManager.Fields} 一致：
     * NAME / DISPLAY / OPEN_URI / IS_CONTENT / SIZE / MODIFIED / SNIPPET。
     *
     * @param appContext 用于解析 typeLabel（string resource）
     * @param analyzer   查询端分析器（用于 Highlighter re-analyze）
     * @param query      当前查询（用于高亮打分）
     * @param searcher   当前 Searcher
     * @param td         TopDocs
     * @return 转换后的 SearchResult 列表，顺序与 score 一致
     */
    @NonNull
    public static List<SearchResult> build(@NonNull Context appContext,
                                           @NonNull Analyzer analyzer,
                                           @NonNull Query query,
                                           @NonNull IndexSearcher searcher,
                                           @NonNull TopDocs td) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        // 复用高亮组件（scorer/formatter/fragmenter 一次构造），避免每篇结果重建。
        SnippetHighlighter highlighter = new SnippetHighlighter(analyzer, query);
        for (ScoreDoc sd : td.scoreDocs) {
            Document d = searcher.doc(sd.doc);
            String name = d.get(LuceneManager.Fields.NAME);
            String display = d.get(LuceneManager.Fields.DISPLAY);
            String openUri = d.get(LuceneManager.Fields.OPEN_URI);
            boolean contentUri = LuceneManager.Fields.IS_CONTENT_TRUE.equals(
                    d.get(LuceneManager.Fields.IS_CONTENT));
            long size = fieldLong(d, LuceneManager.Fields.SIZE);
            long modified = fieldLong(d, LuceneManager.Fields.MODIFIED);
            String snippet = d.get(LuceneManager.Fields.SNIPPET);
            String html = highlighter.build(snippet);
            FileType ft = FileType.match(name);
            String typeLabel = (ft != null) ? appContext.getString(ft.labelRes)
                    : appContext.getString(R.string.type_file);
            SearchResult r = new SearchResult(name, display, openUri, contentUri, size, modified,
                    sd.score, typeLabel);
            r.snippetHtml = html;
            results.add(r);
        }
        return results;
    }

    private static long fieldLong(Document d, String name) {
        IndexableField f = d.getField(name);
        if (f == null || f.numericValue() == null) return 0;
        return f.numericValue().longValue();
    }
}
