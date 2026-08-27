package com.unbox.nsearch;

import android.content.Context;

import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.search.QueryAnalyzer;
import com.unbox.nsearch.search.QueryBuilder;
import com.unbox.nsearch.search.ResultBuilder;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索 facade：用户查询 → 分词 → Lucene 查询 → 结果转换。
 *
 * <p>每个步骤委托给专门的协作类：
 * <ul>
 *   <li>{@link QueryAnalyzer}   —— 取得查询分析器（含同义词回退）</li>
 *   <li>{@link QueryBuilder}    —— term 列表 → Lucene Query</li>
 *   <li>{@link ResultBuilder}   —— TopDocs → SearchResult 列表（含高亮）</li>
 * </ul>
 *
 * 本类只保留串联这三个步骤的「编排」工作；tokenize 因只在 search 路径里使用、且依赖 analyzer
 * 上下文，暂留作 private static 方法。如未来需要独立单测可再抽。
 */
public final class SearchEngine {

    /** NRT refresh 最小间隔：避免每次搜索都触发段刷新（索引写入活跃时开销明显）。 */
    private static final long REFRESH_INTERVAL_MS = 500L;
    private static volatile long lastRefreshMs = 0L;

    private SearchEngine() {}

    public static List<SearchResult> search(Context context, LuceneManager km, String queryStr,
                                            Settings settings, int topN) {
        List<SearchResult> results = new ArrayList<>();
        if (queryStr == null || queryStr.trim().isEmpty()) return results;

        long t0 = System.nanoTime();
        Analyzer queryAnalyzer = QueryAnalyzer.obtain(context, km, settings.isSynonymEnabled());

        List<String> terms = tokenize(queryAnalyzer, queryStr);
        if (terms.isEmpty()) return results;
        long t1 = System.nanoTime();

        int minShouldMatch = QueryBuilder.computeMinShouldMatch(settings, terms.size());
        Query query = QueryBuilder.build(terms, minShouldMatch);
        long t2 = System.nanoTime();

        IndexSearcher searcher = null;
        try {
            // 节流 NRT refresh：500ms 内最多刷新一次，避免每个查询都等待/触发段刷新。
            long now = System.currentTimeMillis();
            if (now - lastRefreshMs >= REFRESH_INTERVAL_MS) {
                km.maybeRefresh();
                lastRefreshMs = now;
            }
            searcher = km.acquire();
            long t3 = System.nanoTime();
            TopDocs td = searcher.search(query, topN);
            long t4 = System.nanoTime();
            results.addAll(ResultBuilder.build(context, queryAnalyzer, query, searcher, td));
            long t5 = System.nanoTime();
            android.util.Log.d("NSearchPerf",
                    String.format(java.util.Locale.US,
                            "analyze=%dms tokenize=%dms build=%dms refresh=%dms search=%dms topN=%d",
                            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000,
                            (t4 - t3) / 1_000_000, (t5 - t4) / 1_000_000, topN));
        } catch (IOException e) {
            // 搜索失败时返回已收集的部分结果（通常为空）
        } finally {
            if (searcher != null) km.release(searcher);
        }
        return results;
    }

    /**
     * 用给定 analyzer 对文本分词，去重 + 顺序保持（LinkedHashSet）。
     * 与原 SearchEngine.tokenize 行为等价：失败/IOException → 返回空列表。
     */
    private static List<String> tokenize(Analyzer analyzer, String text) {
        Set<String> set = new LinkedHashSet<>();
        try (TokenStream ts = analyzer.tokenStream(LuceneManager.Fields.CONTENT, new StringReader(text))) {
            CharTermAttribute cat = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String t = cat.toString();
                if (t != null && !t.isEmpty()) set.add(t);
            }
            ts.end();
        } catch (IOException ignored) {
        }
        return new ArrayList<>(set);
    }
}
