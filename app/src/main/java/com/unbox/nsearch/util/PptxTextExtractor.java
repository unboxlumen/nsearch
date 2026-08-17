package com.unbox.nsearch.util;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .pptx 文本提取（不依赖 POI）。
 * .pptx 本质是一个 zip：遍历 ppt/slides/slideN.xml，提取 &lt;a:t&gt; 文本节点。
 */
public final class PptxTextExtractor {

    private static final int MAX_BYTES = 80 * 1024 * 1024;
    private static final Pattern SLIDE_PATTERN = Pattern.compile("^ppt/slides/slide\\d+\\.xml$");

    private PptxTextExtractor() {
    }

    public static String extract(InputStream in, int charLimit) throws IOException {
        try {
            int limit = charLimit <= 0 ? Integer.MAX_VALUE : charLimit;
            StringBuilder sb = new StringBuilder();
            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (SLIDE_PATTERN.matcher(ze.getName()).matches()) {
                        byte[] slideData = readAll(zis, MAX_BYTES);
                        if (slideData != null) {
                            parseSlideXml(slideData, sb, limit);
                            if (sb.length() >= limit) break;
                            if (sb.length() < limit) sb.append('\n'); // 幻灯片间分隔
                        }
                    }
                    zis.closeEntry();
                }
            }
            int end = Math.min(sb.length(), limit);
            return sb.substring(0, end);
        } catch (XmlPullParserException e) {
            throw new IOException("pptx 解析失败: " + e.getMessage(), e);
        }
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("pptx 过大");
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * 解析单张幻灯片 XML，提取所有 &lt;a:t&gt; 文本（段落内文本合并，段间换行）。
     * 命名空间：http://schemas.openxmlformats.org/drawingml/2006/main
     */
    private static void parseSlideXml(byte[] data, StringBuilder sb, int limit)
            throws IOException, XmlPullParserException {
        try (InputStream is = new ByteArrayInputStream(data)) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(is, "UTF-8");
            boolean inT = false;
            boolean needNewline = false;
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
                            // 段落结束：追加换行
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
    }
}
