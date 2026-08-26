package com.unbox.nsearch.util;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.InputStream;

/**
 * 老格式 .doc 文本提取（Apache POI HWPF）。
 * .doc 是 OLE2 复合文档，HWPF 解析 WordDocument 流与文本片段表后抽取正文。
 */
public final class DocTextExtractor {

    private static final String TAG = "nSearch.Doc";

    private DocTextExtractor() {
    }

    /**
     * @return 抽取到的正文（已截断到 charLimit）；任何解析/运行时异常均降级为空字符串，
     *         以保证索引进程在个别损坏文件或 POI 在 Android 上的兼容问题上不崩溃（文件名仍可命中）。
     */
    public static String extract(InputStream in, int charLimit) {
        int limit = OoxmlText.normalizeLimit(charLimit);
        try (HWPFDocument doc = new HWPFDocument(in);
             WordExtractor ex = new WordExtractor(doc)) {
            String text = ex.getText();
            return TextExtractor.truncate(text, limit);
        } catch (Throwable t) {
            ErrorReporter.report(TAG, t);
            return "";
        }
    }
}
