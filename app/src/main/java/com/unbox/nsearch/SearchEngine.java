package com.unbox.nsearch;

import android.content.Context;

import com.unbox.nsearch.analyzers.SynonymMapLoader;
import com.unbox.nsearch.model.SearchResult;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleHTMLEncoder;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索执行：把用户输入按当前「匹配模式 / 同义词」构建成 Lucene 查询，
 * 在近实时索引上检索并生成高亮摘要。
 */
public final class SearchEngine {

    private static final int FRAGMENT_SIZE = 160;

    private SearchEngine() {
    }

    public static List<SearchResult> search(Context context, LuceneManager km, String queryStr,
                                            Settings settings, int topN) {
        List<SearchResult> results = new ArrayList<>();
        if (queryStr == null || queryStr.trim().isEmpty()) return results;

        boolean synonym = settings.isSynonymEnabled();
        Analyzer queryAnalyzer;
        try {
            queryAnalyzer = km.getQueryAnalyzer(synonym, synonym ? SynonymMapLoader.get(context) : null);
        } catch (IOException e) {
            queryAnalyzer = km.getQueryAnalyzer(false, null); // 同义词词典加载失败则回退
        }

        List<String> terms = tokenize(queryAnalyzer, queryStr);
        if (terms.isEmpty()) return results;

        int minShouldMatch = computeMinShouldMatch(settings, terms.size());
        Query query = buildQuery(terms, minShouldMatch);

        IndexSearcher searcher = null;
        try {
            searcher = km.acquire();
            km.maybeRefresh();
            TopDocs td = searcher.search(query, topN);
            for (ScoreDoc sd : td.scoreDocs) {
                org.apache.lucene.document.Document d = searcher.doc(sd.doc);
                String name = d.get(LuceneManager.Fields.NAME);
                String display = d.get(LuceneManager.Fields.DISPLAY);
                String openUri = d.get(LuceneManager.Fields.OPEN_URI);
                boolean contentUri = "1".equals(d.get(LuceneManager.Fields.IS_CONTENT));
                long size = fieldLong(d, LuceneManager.Fields.SIZE);
                long modified = fieldLong(d, LuceneManager.Fields.MODIFIED);
                String snippet = d.get(LuceneManager.Fields.SNIPPET);
                String html = buildSnippet(queryAnalyzer, query, snippet);
                FileType ft = FileType.match(name);
                String typeLabel = (ft != null) ? context.getString(ft.labelRes)
                        : context.getString(R.string.type_file);
                SearchResult r = new SearchResult(name, display, openUri, contentUri, size, modified,
                        sd.score, typeLabel);
                r.snippetHtml = html;
                results.add(r);
            }
        } catch (IOException e) {
            // 搜索失败时返回已收集的部分结果（通常为空）
        } finally {
            if (searcher != null) km.release(searcher);
        }
        return results;
    }

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

    /**
     * 构建查询：每个词在「内容」与「文件名」两个字段做 SHOULD 匹配（文件名加权），
     * 外层以 minimumShouldMatch 控制「全部匹配 / 任一匹配」。
     */
    private static Query buildQuery(List<String> terms, int minShouldMatch) {
        BooleanQuery.Builder outer = new BooleanQuery.Builder();
        for (String term : terms) {
            BooleanQuery.Builder perTerm = new BooleanQuery.Builder();
            perTerm.add(new BoostQuery(new TermQuery(new Term(LuceneManager.Fields.CONTENT, term)), 1.0f),
                    BooleanClause.Occur.SHOULD);
            perTerm.add(new BoostQuery(new TermQuery(new Term(LuceneManager.Fields.NAME, term)), 2.0f),
                    BooleanClause.Occur.SHOULD);
            outer.add(perTerm.build(), BooleanClause.Occur.SHOULD);
        }
        outer.setMinimumNumberShouldMatch(minShouldMatch);
        return outer.build();
    }

    private static int computeMinShouldMatch(Settings settings, int termCount) {
        Settings.SearchMode mode = settings.getSearchMode();
        switch (mode) {
            case STRICT: // 严格：包含所有词
                return termCount;
            case LOOSE:  // 宽松：包含任一词
                return 1;
            case MEDIUM: // 中等：默认全部匹配，但允许少匹配 1 个词
            default:
                return Math.max(1, termCount - 1);
        }
    }

    private static long fieldLong(org.apache.lucene.document.Document d, String name) {
        org.apache.lucene.index.IndexableField f = d.getField(name);
        if (f == null || f.numericValue() == null) return 0;
        return f.numericValue().longValue();
    }

    private static String buildSnippet(Analyzer analyzer, Query query, String snippet) {
        if (snippet == null || snippet.isEmpty()) return "";
        try {
            QueryScorer scorer = new QueryScorer(query, LuceneManager.Fields.CONTENT);
            Highlighter hl = new Highlighter(new SimpleHTMLFormatter("<b>", "</b>"),
                    new SimpleHTMLEncoder(), scorer);
            hl.setTextFragmenter(new SimpleFragmenter(FRAGMENT_SIZE));
            String frag = hl.getBestFragment(analyzer, LuceneManager.Fields.CONTENT, snippet);
            if (frag != null && !frag.isEmpty()) return frag;
        } catch (Exception ignored) {
        }
        int end = Math.min(FRAGMENT_SIZE, snippet.length());
        return snippet.substring(0, end);
    }
}
