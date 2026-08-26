package com.unbox.nsearch.util;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * .xlsx 文本提取（不依赖 POI）。
 * .xlsx 本质是一个 zip：先读 xl/sharedStrings.xml 得到共享字符串表，
 * 再遍历各 xl/worksheets/sheetN.xml 还原单元格文本。
 *
 * <p>zip 读取、limit 归一化、异常包装由 {@link OoxmlText} 共享；
 * 单元格/共享字符串的 XML 遍历结构与 docx/pptx 不同，各自独立。
 */
public final class XlsxTextExtractor {

    private static final int MAX_BYTES = 60 * 1024 * 1024;

    private XlsxTextExtractor() {
    }

    public static String extract(InputStream in, int charLimit) throws IOException {
        try {
            byte[] data = OoxmlText.readAll(in, MAX_BYTES);
            List<String> shared = parseSharedStrings(data);
            int limit = OoxmlText.normalizeLimit(charLimit);
            StringBuilder sb = new StringBuilder();
            parseSheets(data, shared, sb, limit);
            return TextExtractor.truncate(sb.toString(), limit);
        } catch (XmlPullParserException e) {
            throw OoxmlText.wrapXmlError("xlsx", e);
        }
    }

    private static List<String> parseSharedStrings(byte[] data) throws IOException, XmlPullParserException {
        List<String> shared = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(data)) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(is, "UTF-8");
            StringBuilder siBuf = new StringBuilder();
            StringBuilder tBuf = new StringBuilder();
            boolean inSi = false, inT = false;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                switch (ev) {
                    case XmlPullParser.START_TAG: {
                        String n = p.getName();
                        if ("si".equals(n)) {
                            inSi = true;
                            siBuf.setLength(0);
                        } else if ("t".equals(n) && inSi) {
                            inT = true;
                            tBuf.setLength(0);
                        }
                        break;
                    }
                    case XmlPullParser.TEXT:
                    case XmlPullParser.CDSECT:
                        if (inT) tBuf.append(p.getText());
                        break;
                    case XmlPullParser.END_TAG: {
                        String n = p.getName();
                        if ("t".equals(n)) {
                            inT = false;
                            siBuf.append(tBuf);
                        } else if ("si".equals(n)) {
                            inSi = false;
                            shared.add(siBuf.toString());
                        }
                        break;
                    }
                }
                ev = p.next();
            }
        }
        return shared;
    }

    private static void parseSheets(byte[] data, List<String> shared, StringBuilder sb, int limit)
            throws IOException, XmlPullParserException {
        try (InputStream is = new ByteArrayInputStream(data)) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(is, "UTF-8");
            StringBuilder vBuf = new StringBuilder();
            StringBuilder inlineBuf = new StringBuilder();
            boolean inC = false, inV = false, inInlineStr = false, inIsT = false;
            String cellType = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT && sb.length() < limit) {
                switch (ev) {
                    case XmlPullParser.START_TAG: {
                        String n = p.getName();
                        if ("c".equals(n)) {
                            inC = true;
                            cellType = p.getAttributeValue(null, "t");
                            vBuf.setLength(0);
                            inlineBuf.setLength(0);
                            inInlineStr = false;
                            inIsT = false;
                        } else if ("v".equals(n) && inC) {
                            inV = true;
                            vBuf.setLength(0);
                        } else if ("is".equals(n)) {
                            inInlineStr = true;
                        } else if ("t".equals(n) && inInlineStr) {
                            inIsT = true;
                            inlineBuf.setLength(0);
                        }
                        break;
                    }
                    case XmlPullParser.TEXT:
                        if (inV) vBuf.append(p.getText());
                        else if (inIsT) inlineBuf.append(p.getText());
                        break;
                    case XmlPullParser.END_TAG: {
                        String n = p.getName();
                        if ("v".equals(n)) {
                            inV = false;
                            if ("s".equals(cellType)) {
                                try {
                                    int idx = Integer.parseInt(vBuf.toString().trim());
                                    if (idx >= 0 && idx < shared.size()) sb.append(shared.get(idx));
                                } catch (NumberFormatException ignore) {
                                }
                            } else {
                                sb.append(vBuf);
                            }
                            sb.append(' ');
                        } else if ("t".equals(n) && inInlineStr) {
                            inIsT = false;
                            sb.append(inlineBuf).append(' ');
                        } else if ("is".equals(n)) {
                            inInlineStr = false;
                        } else if ("c".equals(n)) {
                            inC = false;
                            cellType = null;
                        } else if ("row".equals(n)) {
                            sb.append('\n');
                        }
                        break;
                    }
                }
                ev = p.next();
            }
        }
    }
}
