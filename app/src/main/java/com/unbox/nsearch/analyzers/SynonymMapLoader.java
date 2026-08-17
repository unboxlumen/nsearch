package com.unbox.nsearch.analyzers;

import android.content.Context;

import com.unbox.nsearch.R;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

/**
 * 从 res/raw/synonyms.txt 构建 {@link SynonymMap}（SolrSynonymParser 格式）。
 * 结果缓存为单例，避免每次搜索重复解析。
 */
public final class SynonymMapLoader {

    private static SynonymMap cached;

    private SynonymMapLoader() {
    }

    public static synchronized SynonymMap get(Context context) throws IOException {
        if (cached == null) {
            cached = build(context.getApplicationContext());
        }
        return cached;
    }

    /** 仅供解析同义词词条用的极简分析器：按空白切分 + 转小写。 */
    private static Analyzer parsingAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer src = new WhitespaceTokenizer();
                return new TokenStreamComponents(src, new LowerCaseFilter(src));
            }
        };
    }

    private static SynonymMap build(Context context) throws IOException {
        try (InputStream in = context.getResources().openRawResource(R.raw.synonyms);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            SolrSynonymParser parser = new SolrSynonymParser(true, true, parsingAnalyzer());
            try {
                parser.parse(reader);
            } catch (ParseException e) {
                throw new IOException("同义词解析失败: " + e.getMessage(), e);
            }
            return parser.build();
        }
    }
}
