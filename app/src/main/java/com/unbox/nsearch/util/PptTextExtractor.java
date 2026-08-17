package com.unbox.nsearch.util;

import android.util.Log;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;

import java.io.InputStream;

/**
 * 老格式 .ppt 文本提取（Apache POI HSLF）。
 * .ppt 是 OLE2 复合文档；POI 5.x 用 {@link SlideShowExtractor}（位于 poi 核心包）遍历
 * 幻灯片 / 备注 / 批注中的文本运行后抽取正文。
 */
public final class PptTextExtractor {

    private static final String TAG = "nSearch.Ppt";

    private PptTextExtractor() {
    }

    /**
     * @return 抽取到的正文（已截断到 charLimit）；任何解析/运行时异常均降级为空字符串，
     *         以保证索引进程在个别损坏文件或 POI 在 Android 上的兼容问题上不崩溃（文件名仍可命中）。
     */
    @SuppressWarnings("unchecked")
    public static String extract(InputStream in, int charLimit) {
        int limit = charLimit <= 0 ? Integer.MAX_VALUE : charLimit;
        try (HSLFSlideShow ss = new HSLFSlideShow(in)) {
            SlideShowExtractor<?, ?> ex = new SlideShowExtractor<>(ss);
            String text = ex.getText();
            if (text == null) return "";
            int end = Math.min(text.length(), limit);
            return text.substring(0, end);
        } catch (Throwable t) {
            Log.w(TAG, "ppt 抽取失败，降级为空: " + t.getMessage());
            return "";
        }
    }
}
