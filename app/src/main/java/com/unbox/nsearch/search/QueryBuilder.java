package com.unbox.nsearch.search;

import com.unbox.nsearch.LuceneManager;
import com.unbox.nsearch.Settings;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;

import java.util.List;

/**
 * 把已分词后的 term 列表构建成 Lucene 查询。
 *
 * 原 {@code SearchEngine.buildQuery} / {@code computeMinShouldMatch} 拆出。
 * 拆出后是纯逻辑（无 IO / 无 Android），可单独单元测试。
 */
public final class QueryBuilder {

    private static final float CONTENT_BOOST = 1.0f;
    private static final float NAME_BOOST = 2.0f;
    private static final int MIN_MATCH_FLOOR = 1;

    private QueryBuilder() {}

    /**
     * 构建查询：每个词在「内容」与「文件名」两个字段做 SHOULD 匹配（文件名加权），
     * 外层以 {@code minimumShouldMatch} 控制「全部匹配 / 任一匹配」。
     */
    public static Query build(List<String> terms, int minShouldMatch) {
        BooleanQuery.Builder outer = new BooleanQuery.Builder();
        for (String term : terms) {
            BooleanQuery.Builder perTerm = new BooleanQuery.Builder();
            addShould(perTerm, LuceneManager.Fields.CONTENT, term, CONTENT_BOOST);
            addShould(perTerm, LuceneManager.Fields.NAME, term, NAME_BOOST);
            outer.add(perTerm.build(), BooleanClause.Occur.SHOULD);
        }
        outer.setMinimumNumberShouldMatch(minShouldMatch);
        return outer.build();
    }

    private static void addShould(BooleanQuery.Builder b, String field, String term, float boost) {
        b.add(new BoostQuery(new TermQuery(new Term(field, term)), boost),
                BooleanClause.Occur.SHOULD);
    }

    /**
     * 根据匹配模式与词数计算 minimumShouldMatch。
     *
     * @return 0..termCount 之间的整数；最小为 1（loose 模式除外，loose 永远为 1）
     */
    public static int computeMinShouldMatch(Settings.SearchMode mode, int termCount) {
        switch (mode) {
            case STRICT:
                return termCount;
            case LOOSE:
                return MIN_MATCH_FLOOR;
            case MEDIUM:
            default:
                return Math.max(MIN_MATCH_FLOOR, termCount - 1);
        }
    }

    /** 直接从 Settings 计算（封装避免调用方写 switch）。 */
    public static int computeMinShouldMatch(Settings settings, int termCount) {
        return computeMinShouldMatch(settings.getSearchMode(), termCount);
    }
}
