package com.unbox.nsearch.util;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * OOXML（docx / pptx / xlsx）抽取的共享小工具。
 *
 * <p>把三个抽取器各自重复的「有上限读流」「t/p 文本节点遍历」「limit 归一化」「异常包装」
 * 收拢到一处，避免同一逻辑在多个类里各写一份。
 *
 * <p>均为包级可见静态方法，只服务本包内的抽取器，不对外暴露。
 */
final class OoxmlText {

    static final int IO_BUFFER_SIZE = 8192;

    private OoxmlText() {
    }

    /**
     * 把输入流中的全部字节读入 {@code byte[]}，但累计超过 {@code maxBytes} 时抛 {@link IOException}，
     * 防止单个超大条目把内存撑爆。
     */
    static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[IO_BUFFER_SIZE];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > maxBytes) throw new IOException("OOXML 条目超过大小上限");
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * 提取 OOXML 正文片段中的 {@code <t>} 文本节点，保留段落（{@code <p>}）之间的换行。
     *
     * <p>docx（word/document.xml）与 pptx（ppt/slides/slideN.xml）共享同一套
     * 「{@code t} 节点拼文本、{@code p} 结束插换行、超限即停」的状态机。
     * xlsx 的单元格/共享字符串结构不同，不走这里。
     *
     * @param data 文档 XML 的字节
     * @param sb   输出缓冲区（docx 传空白 buffer，pptx 传跨 slide 累积的 buffer）
     * @param limit 字符上限，达到后停止解析
     */
    static void appendTextNodes(byte[] data, StringBuilder sb, int limit)
            throws IOException, XmlPullParserException {
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
    }

    /** 把 {@code charLimit <= 0} 归一化为「无限制」，供各抽取器统一使用。 */
    static int normalizeLimit(int charLimit) {
        return charLimit <= 0 ? Integer.MAX_VALUE : charLimit;
    }

    /** 把 {@link XmlPullParserException} 包装成带格式名的 {@link IOException}。 */
    static IOException wrapXmlError(String formatName, XmlPullParserException e) {
        return new IOException(formatName + " 解析失败: " + e.getMessage(), e);
    }
}
