package com.unbox.nsearch.index;

import androidx.annotation.NonNull;

import com.unbox.nsearch.FileScanner;
import com.unbox.nsearch.LuceneManager;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

/**
 * 把一个扫描项 + 抽取出的文本构造为 Lucene {@link Document}。
 *
 * 之前是 {@link com.unbox.nsearch.IndexController#buildDoc(FileScanner.ScanItem, String)}，
 * 现在独立成类，方便单元测试构造结果（不依赖 Android）。
 *
 * 字段 schema 必须与 {@link LuceneManager.Fields} 保持完全一致，否则索引无法被搜索。
 */
public final class DocumentBuilder {

    private DocumentBuilder() {}

    public static Document build(@NonNull FileScanner.ScanItem item, @NonNull String text) {
        Document doc = new Document();
        doc.add(new StringField(LuceneManager.Fields.PATH, item.getPath(), Field.Store.YES));
        doc.add(new TextField(LuceneManager.Fields.NAME, item.getName(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.EXT, item.getExt(), Field.Store.YES));
        doc.add(new LongPoint(LuceneManager.Fields.SIZE, item.length()));
        doc.add(new StoredField(LuceneManager.Fields.SIZE, item.length()));
        doc.add(new LongPoint(LuceneManager.Fields.MODIFIED, item.lastModified()));
        doc.add(new StoredField(LuceneManager.Fields.MODIFIED, item.lastModified()));
        String snippet = text.length() > LuceneManager.SNIPPET_LIMIT
                ? text.substring(0, LuceneManager.SNIPPET_LIMIT) : text;
        doc.add(new TextField(LuceneManager.Fields.CONTENT, text, Field.Store.NO));
        doc.add(new StoredField(LuceneManager.Fields.SNIPPET, snippet));
        doc.add(new StringField(LuceneManager.Fields.DISPLAY, item.getDisplayPath(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.OPEN_URI, item.getOpenUri(), Field.Store.YES));
        doc.add(new StringField(LuceneManager.Fields.IS_CONTENT,
                item.isContentUri() ? LuceneManager.Fields.IS_CONTENT_TRUE : LuceneManager.Fields.IS_CONTENT_FALSE,
                Field.Store.YES));
        return doc;
    }
}
