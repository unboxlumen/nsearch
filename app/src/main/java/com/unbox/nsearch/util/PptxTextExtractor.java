package com.unbox.nsearch.util;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .pptx 文本提取（不依赖 POI）。
 * .pptx 本质是一个 zip：遍历 ppt/slides/slideN.xml，提取 &lt;a:t&gt; 文本节点。
 *
 * <p>zip 读取、{@code t} 文本节点遍历、limit 归一化均由 {@link OoxmlText} 共享。
 */
public final class PptxTextExtractor {

    private static final int MAX_BYTES = 80 * 1024 * 1024;
    private static final Pattern SLIDE_PATTERN = Pattern.compile("^ppt/slides/slide\\d+\\.xml$");

    private PptxTextExtractor() {
    }

    public static String extract(InputStream in, int charLimit) throws IOException {
        try {
            int limit = OoxmlText.normalizeLimit(charLimit);
            StringBuilder sb = new StringBuilder();
            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (SLIDE_PATTERN.matcher(ze.getName()).matches()) {
                        byte[] slideData = OoxmlText.readAll(zis, MAX_BYTES);
                        if (slideData != null) {
                            OoxmlText.appendTextNodes(slideData, sb, limit);
                            if (sb.length() >= limit) break;
                            if (sb.length() < limit) sb.append('\n'); // 幻灯片间分隔
                        }
                    }
                    zis.closeEntry();
                }
            }
            return TextExtractor.truncate(sb.toString(), limit);
        } catch (XmlPullParserException e) {
            throw OoxmlText.wrapXmlError("pptx", e);
        }
    }
}
