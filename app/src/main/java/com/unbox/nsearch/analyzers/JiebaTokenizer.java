package com.unbox.nsearch.analyzers;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 混合分词 Tokenizer：
 *  - 连续的汉字（CJK Unified Ideographs 及其扩展区）交给 jieba 做词级切分，中文搜索召回最佳；
 *  - 其它脚本（拉丁字母、日文假名、谚文、数字等）交给 ICU 的 BreakIterator 做词边界切分；
 *  - 标点与空白被跳过。
 *
 * 注意：jieba-analysis 的 JiebaSegmenter.process 需要整段文本，因此本 Tokenizer 会把输入整体读入内存后再切分。
 * 对超大文件内容（如整本电子书）会占用较多内存，属已知权衡；如需流式可后续改为分块处理。
 */
public final class JiebaTokenizer extends Tokenizer {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posAtt = addAttribute(PositionIncrementAttribute.class);

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();
    private static final Object SEG_LOCK = new Object();

    private final List<Token> tokens = new ArrayList<>();
    private int cursor = 0;

    private static final class Token {
        final String text;
        final int start;
        final int end;

        Token(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        // 整体读入输入文本（jieba 需要完整字符串）
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = input.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        String text = sb.toString();
        tokens.clear();
        cursor = 0;
        segment(text);
    }

    /** 按脚本（汉字 / 非汉字）把文本切分成连续 run，再分别交给 jieba 或 ICU 处理。 */
    private void segment(String text) {
        int len = text.length();
        if (len == 0) {
            return;
        }
        int runStart = 0;
        boolean runIsCJK = isCJK(text.charAt(0));
        int i = 1;
        while (i < len) {
            boolean cjk = isCJK(text.charAt(i));
            if (cjk != runIsCJK) {
                flushRun(text, runStart, i, runIsCJK);
                runStart = i;
                runIsCJK = cjk;
            }
            i++;
        }
        flushRun(text, runStart, len, runIsCJK);
    }

    private void flushRun(String text, int start, int end, boolean cjk) {
        if (end <= start) {
            return;
        }
        String run = text.substring(start, end);
        if (cjk) {
            List<SegToken> segs;
            synchronized (SEG_LOCK) {
                segs = SEGMENTER.process(run, JiebaSegmenter.SegMode.SEARCH);
            }
            for (SegToken t : segs) {
                if (t.word == null || t.word.isEmpty()) {
                    continue;
                }
                if (!isMeaningful(t.word)) {
                    continue;
                }
                tokens.add(new Token(t.word, start + t.startOffset, start + t.endOffset));
            }
        } else {
            BreakIterator bi = BreakIterator.getWordInstance(ULocale.ROOT);
            bi.setText(run);
            int b = bi.first();
            int e = bi.next();
            while (e != BreakIterator.DONE) {
                if (b >= 0 && e > b) {
                    String w = run.substring(b, e);
                    if (isMeaningful(w)) {
                        tokens.add(new Token(w, start + b, start + e));
                    }
                }
                b = e;
                e = bi.next();
            }
        }
    }

    /** 仅保留含字母/数字或汉字的词，过滤纯标点与空白。 */
    private static boolean isMeaningful(String w) {
        for (int i = 0; i < w.length(); i++) {
            int cp = w.codePointAt(i);
            if (Character.isLetterOrDigit(cp) || isCJK(cp)) {
                return true;
            }
        }
        return false;
    }

    /** 是否汉字（CJK Unified Ideographs 及扩展区）。假名/谚文归为非 CJK，交由 ICU 处理。 */
    private static boolean isCJK(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)      // CJK Ext A
                || (cp >= 0x4E00 && cp <= 0x9FFF)   // CJK Unified
                || (cp >= 0x20000 && cp <= 0x2A6DF) // CJK Ext B
                || (cp >= 0x2A700 && cp <= 0x2B73F) // CJK Ext C
                || (cp >= 0x2B740 && cp <= 0x2B81F) // CJK Ext D
                || (cp >= 0x2B820 && cp <= 0x2CEAF) // CJK Ext E
                || (cp >= 0xF900 && cp <= 0xFAFF)   // CJK Compatibility Ideographs
                || (cp >= 0x2F800 && cp <= 0x2FA1F);// CJK Compatibility Supplement
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (cursor >= tokens.size()) {
            return false;
        }
        Token t = tokens.get(cursor++);
        clearAttributes();
        termAtt.setEmpty().append(t.text);
        offsetAtt.setOffset(correctOffset(t.start), correctOffset(t.end));
        posAtt.setPositionIncrement(1);
        return true;
    }

    @Override
    public void end() throws IOException {
        super.end();
        int finalOffset = tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).end;
        offsetAtt.setOffset(correctOffset(finalOffset), correctOffset(finalOffset));
    }
}
