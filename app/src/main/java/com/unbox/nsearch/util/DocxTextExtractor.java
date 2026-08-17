package com.unbox.nsearch.util;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .docx 文本提取（不依赖 POI）。
 * .docx 本质是一个 zip：读 word/document.xml，提取 &lt;w:t&gt; 文本节点。
 */
public final class DocxTextExtractor {

    private static final int MAX_BYTES = 60 * 1024 * 1024;

    private DocxTextExtractor() {
    }

    public static String extract(InputStream in, int charLimit) throws IOException {
        try {
            byte[] data = readZipEntry(in, "word/document.xml", MAX_BYTES);
            if (data == null) throw new IOException("docx 中未找到 word/document.xml");
            StringBuilder sb = parseDocumentXml(data, charLimit <= 0 ? Integer.MAX_VALUE : charLimit);
            int end = Math.min(sb.length(), charLimit <= 0 ? sb.length() : charLimit);
            return sb.substring(0, end);
        } catch (XmlPullParserException e) {
            throw new IOException("docx 解析失败: " + e.getMessage(), e);
        }
    }

    /** 从 zip 流中读取指定条目的全部字节；找不到返回 null。 */
    private static byte[] readZipEntry(InputStream zipIn, String entryName, int maxBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipIn)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(entryName)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = zis.read(buf)) > 0) {
                        total += n;
                        if (total > maxBytes) throw new IOException("docx 条目过大");
                        bos.write(buf, 0, n);
                    }
                    zis.closeEntry();
                    return bos.toByteArray();
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * 解析 word/document.xml，提取所有 &lt;w:t&gt; 文本内容（保留段落换行）。
     * 命名空间：http://schemas.openxmlformats.org/wordprocessingml/2006/main
     */
    private static StringBuilder parseDocumentXml(byte[] data, int limit)
            throws IOException, XmlPullParserException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new ByteArrayInputStream(data)) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(is, "UTF-8");
            boolean inT = false;
            boolean needNewline = false; // 段落结束后插入换行
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT && sb.length() < limit) {
                switch (ev) {
                    case XmlPullParser.START_TAG:
                        if ("t".equals(p.getName())) {
                            inT = true;
                        }
                        break;
                    case XmlPullParser.TEXT:
                    case XmlPullParser.CDSECT:
                        if (inT) {
                            sb.append(p.getText());
                            needNewline = true;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        String name = p.getName();
                        if ("t".equals(name)) {
                            inT = false;
                        } else if ("p".equals(name)) {
                            // 段落结束：追加换行分隔
                            if (needNewline && sb.length() < limit) {
                                sb.append('\n');
                                needNewline = false;
                            }
                        }
                        break;
                }
                ev = p.next();
            }
        }
        return sb;
    }
}
