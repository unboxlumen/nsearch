package com.unbox.nsearch;

import android.content.Context;

import com.unbox.nsearch.analyzers.HybridAnalyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.File;

/**
 * Lucene 单例封装：
 *  - 持有长期打开的 {@link IndexWriter}（索引与搜索可同时进行）；
 *  - 通过 {@link SearcherManager} 提供近实时（NRT）搜索，新建文档在 refresh 后即可被搜到；
 *  - 索引分析器不带同义词（内容中立），查询分析器按设置叠加同义词扩展。
 */
public final class LuceneManager {

    /** 索引在私有存储中的目录名。 */
    public static final String INDEX_DIR_NAME = "lucene_index";
    /** IndexWriter RAM 缓冲区大小（MB）。 */
    public static final int RAM_BUFFER_MB = 64;

    /** 摘要字段存储上限：超过此长度的内容只取头部用于生成高亮片段。 */
    public static final int SNIPPET_LIMIT = 200_000;

    public static final class Fields {
        public static final String PATH = "path";
        public static final String NAME = "name";
        public static final String EXT = "ext";
        public static final String SIZE = "size";
        public static final String MODIFIED = "modified";
        public static final String CONTENT = "content";
        public static final String SNIPPET = "snippet";
        /** 人类可读路径（用于界面展示） */
        public static final String DISPLAY = "display";
        /** 打开文件所用的 Uri（本地绝对路径或 content Uri 字符串） */
        public static final String OPEN_URI = "openuri";
        /** 是否为 content Uri（SAF 文档为 1，本地文件为 0） */
        public static final String IS_CONTENT = "iscontent";
        /** {@link #IS_CONTENT} 字段值：表示 content Uri（SAF 文档）。 */
        public static final String IS_CONTENT_TRUE = "1";
        /** {@link #IS_CONTENT} 字段值：表示本地文件。 */
        public static final String IS_CONTENT_FALSE = "0";
    }

    private static LuceneManager instance;

    private final Directory directory;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final Analyzer indexAnalyzer;

    public static synchronized LuceneManager get(Context context) throws IOException {
        if (instance == null) {
            instance = new LuceneManager(context.getApplicationContext());
        }
        return instance;
    }

    private LuceneManager(Context ctx) throws IOException {
        File dir = ctx.getDir(INDEX_DIR_NAME, Context.MODE_PRIVATE);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        this.directory = FSDirectory.open(dir.toPath());
        this.indexAnalyzer = new HybridAnalyzer(null, false);
        IndexWriterConfig cfg = new IndexWriterConfig(indexAnalyzer);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        cfg.setRAMBufferSizeMB(RAM_BUFFER_MB);
        this.writer = new IndexWriter(directory, cfg);
        this.searcherManager = new SearcherManager(writer, new SearcherFactory());
    }

    public Analyzer getIndexAnalyzer() {
        return indexAnalyzer;
    }

    public Analyzer getQueryAnalyzer(boolean useSynonyms, SynonymMap synonymMap) {
        return new HybridAnalyzer(synonymMap, useSynonyms);
    }

    /** 新增或替换某路径的文档（按 path 唯一）。 */
    public void updateDocument(String path, Document doc) throws IOException {
        writer.updateDocument(new Term(Fields.PATH, path), doc);
    }

    public void delete(String path) throws IOException {
        writer.deleteDocuments(new Term(Fields.PATH, path));
    }

    public void deleteAll() throws IOException {
        writer.deleteAll();
    }

    public void commit() throws IOException {
        writer.commit();
    }

    /** 让搜索器看到最新写入的文档（NRT）。索引过程中周期性调用。 */
    public void maybeRefresh() throws IOException {
        searcherManager.maybeRefresh();
    }

    /** 提交 + 刷新搜索器，一次调用完成「落盘并立即可搜」。 */
    public void commitAndRefresh() throws IOException {
        writer.commit();
        searcherManager.maybeRefresh();
    }

    public IndexSearcher acquire() throws IOException {
        return searcherManager.acquire();
    }

    public void release(IndexSearcher searcher) {
        try {
            searcherManager.release(searcher);
        } catch (IOException ignored) {
        }
    }

    public int docCount() {
        return writer.getDocStats().numDocs;
    }

    public void close() throws IOException {
        searcherManager.close();
        writer.close();
        directory.close();
    }
}
