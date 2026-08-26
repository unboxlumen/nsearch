package com.unbox.nsearch.util;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .docx 文本提取（不依赖 POI）。
 * .docx 本质是一个 zip：读 word/document.xml，提取 &lt;w:t&gt; 文本节点。
 *
 * <p>zip 读取、{@code t} 文本节点遍历、limit 归一化均由 {@link OoxmlText} 共享。
 */
public final class DocxTextExtractor {

    private static final int MAX_BYTES = 60 * 1024 * 1024;
    private static final String DOCX_ENTRY = "word/document.xml";

    private DocxTextExtractor() {
    }

    public static String extract(InputStream in, int charLimit) throws IOException {
        try {
            byte[] data = readZipEntry(in, DOCX_ENTRY, MAX_BYTES);
            if (data == null) throw new IOException("docx 中未找到 " + DOCX_ENTRY);
            int limit = OoxmlText.normalizeLimit(charLimit);
            StringBuilder sb = new StringBuilder();
            OoxmlText.appendTextNodes(data, sb, limit);
            return TextExtractor.truncate(sb.toString(), limit);
        } catch (XmlPullParserException e) {
            throw OoxmlText.wrapXmlError("docx", e);
        }
    }

    /** 从 zip 流中读取指定条目的全部字节；找不到返回 null。 */
    private static byte[] readZipEntry(InputStream zipIn, String entryName, int maxBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipIn)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(entryName)) {
                    return OoxmlText.readAll(zis, maxBytes);
                }
                zis.closeEntry();
            }
        }
        return null;
    }
}
