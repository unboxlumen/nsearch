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
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
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
import java.util.Arrays;
import java.util.List;

/**
 * Lucene 单例封装：
 *  - 持有长期打开的 {@link IndexWriter}（索引与搜索可同时进行）；
 *  - 通过 {@link SearcherManager} 提供近实时（NRT）搜索，新建文档在 refresh 后即可被搜到；
 *  - 索引分析器不带同义词（内容中立），查询分析器按设置叠加同义词扩展。
 *
 * <p>崩溃自愈（手机进程随时可能被杀，索引必须能自动恢复）：
 *  <ol>
 *   <li>自定义 {@link KeepLastNDeletionPolicy} 保留最近 2 个 commit，防止"最新 commit 损坏且旧 commit 已被清理"；</li>
 *   <li>{@link #get(Context)} 打开失败时先尝试回退到上一个有效 commit（删除损坏的最新 segments 文件）；
 *       若没有可用 commit 则清空索引目录，由上层触发重建；</li>
 *   <li>回退/清空只影响最后一次 commit 前后的少量文档，重新索引即可补齐，不会丢全部数据。</li>
 *  </ol>
 */
public final class LuceneManager {

    /** 索引在私有存储中的目录名。 */
    public static final String INDEX_DIR_NAME = "lucene_index";
    /** IndexWriter RAM 缓冲区大小（MB）。 */
    public static final int RAM_BUFFER_MB = 64;

    /** 摘要字段存储上限：超过此长度的内容只取头部用于生成高亮片段。 */
    public static final int SNIPPET_LIMIT = 200_000;

    /** 保留的 commit 数量：最新损坏时可回退到上一个，避免整个索引不可读。 */
    private static final int KEEP_COMMITS = 2;

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

    /**
     * 获取单例。若索引因进程被杀而损坏，自动尝试恢复：
     * 回退到上一个 commit；无可用 commit 时清空索引目录（索引文件删除，但保留目录本身）。
     */
    public static synchronized LuceneManager get(Context context) throws IOException {
        if (instance == null) {
            instance = openWithRecovery(context.getApplicationContext());
        }
        return instance;
    }

    /** 仅供删除索引等场景使用：丢弃单例，下次 {@link #get(Context)} 会重新打开（或自愈）。 */
    public static synchronized void resetInstance() {
        instance = null;
    }

    private static LuceneManager openWithRecovery(Context ctx) throws IOException {
        IOException last = null;
        // 最多 3 次：①正常打开 ②回退上一个 commit 后重开 ③清空目录后重开（空索引必成功）
        for (int attempt = 0; attempt < 3; attempt++) {
            LuceneManager km = null;
            IndexSearcher searcher = null;
            try {
                km = new LuceneManager(ctx);
                // 健康检查：强制 refresh + acquire + 一次真实 term 搜索，
                // 真正加载 segment 的 terms dict 数据。进程被杀可能只损坏数据文件而
                // segments 文件仍可解析，若只在用户搜索时才暴露损坏体验很差；这里提前暴露并恢复。
                km.maybeRefresh();
                searcher = km.acquire();
                searcher.search(new org.apache.lucene.search.TermQuery(
                        new Term(LuceneManager.Fields.NAME, "__health_check__")), 1);
                return km;
            } catch (IOException e) {
                last = e;
                if (km != null) {
                    try { km.close(); } catch (IOException ignored) { }
                }
                if (!recoverIndex(ctx)) break;
            } finally {
                if (searcher != null && km != null) km.release(searcher);
            }
        }
        throw last != null ? last : new IOException("索引无法恢复");
    }

    /**
     * 尝试修复损坏的索引目录。返回 true 表示目录已可安全重新打开
     * （可能回退到了上一个 commit）；false 表示已清空目录、无任何可用 commit，
     * 需要上层触发全量重建。
     */
    static boolean recoverIndex(Context ctx) {
        File dir = ctx.getDir("lucene_index", Context.MODE_PRIVATE);
        if (!dir.exists() || !dir.isDirectory()) return true; // 无索引 = 全新状态，可直接打开
        File[] segments = dir.listFiles((d, name) -> name.startsWith("segments_"));
        File gen = new File(dir, "segments.gen");
        if (segments == null) return true;
        if (segments.length <= 1) {
            // 只有一个（或没有）commit：无论是否损坏都无法回退，清空后由上层触发重建
            deleteAllFiles(dir);
            return false;
        }
        // 多个 commit：删除最新的（损坏的）那个 + segments.gen，让 Lucene 回退到上一个
        Arrays.sort(segments, (a, b) -> Long.compare(segNumber(a), segNumber(b)));
        segments[segments.length - 1].delete();
        if (gen.exists()) gen.delete();
        return true;
    }

    /** 解析 "segments_2r" 中的十六进制序号，无法解析返回 0。 */
    private static long segNumber(File f) {
        String name = f.getName();
        String hex = name.startsWith("segments_") ? name.substring("segments_".length()) : "";
        try {
            return Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteAllFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
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
        cfg.setIndexDeletionPolicy(new KeepLastNDeletionPolicy(KEEP_COMMITS));
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

    /**
     * 保留最近 N 个 commit 的删除策略。
     * 默认的 KeepOnlyLastCommitDeletionPolicy 只留 1 个 commit，进程在 commit 中途被杀
     * 且旧 commit 已被删除时，索引会整体不可读；保留多个可回退。
     */
    static final class KeepLastNDeletionPolicy extends IndexDeletionPolicy {
        private final int keep;

        KeepLastNDeletionPolicy(int keep) {
            this.keep = Math.max(1, keep);
        }

        @Override
        public void onInit(List<? extends IndexCommit> commits) throws IOException {
            // 打开时不做任何删除：保留所有现存 commit 以备崩溃回退
        }

        @Override
        public void onCommit(List<? extends IndexCommit> commits) throws IOException {
            // commits 按时间从旧到新；只保留最近 keep 个，删除更旧的
            for (int i = 0; i < commits.size() - keep; i++) {
                commits.get(i).delete();
            }
        }
    }
}
