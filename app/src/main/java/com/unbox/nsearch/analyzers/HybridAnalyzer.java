package com.unbox.nsearch.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;

/**
 * 混合全文分析器（Google 式）：
 *  - 分词由 {@link JiebaTokenizer} 完成（中文 jieba 词级切分 + 其它语言 ICU 词边界切分）；
 *  - 索引时使用无同义词的实例；查询时按设置包一层 {@link SynonymGraphFilter} 实现同义词扩展。
 */
public final class HybridAnalyzer extends Analyzer {

    private final SynonymMap synonymMap;
    private final boolean useSynonyms;

    public HybridAnalyzer(SynonymMap synonymMap, boolean useSynonyms) {
        this.synonymMap = synonymMap;
        this.useSynonyms = useSynonyms && synonymMap != null;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer src = new JiebaTokenizer();
        TokenStream tok = new LowerCaseFilter(src);
        if (useSynonyms && synonymMap != null) {
            tok = new SynonymGraphFilter(tok, synonymMap, true);
        }
        return new TokenStreamComponents(src, tok);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }
}
