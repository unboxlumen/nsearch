package com.unbox.nsearch.util;

import com.unbox.nsearch.FileType;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

/**
 * 按文件类型抽取纯文本（受字数上限约束）。
 */
public final class TextExtractor {

    private static final int UNLIMITED_CAP = 20_000_000;

    private TextExtractor() {
    }

    public static String extract(InputStream in, FileType type, String lowerExt, int charLimit)
            throws IOException {
        int limit = (charLimit <= 0) ? UNLIMITED_CAP : charLimit;
        switch (type) {
            case TXT:
            case MD:
            case CSV:
                return readPlain(in, limit);
            case PDF:
                return readPdf(in, limit);
            case XLS:
                return lowerExt.equals("xlsx")
                        ? XlsxTextExtractor.extract(in, limit)
                        : readXls(in, limit);
            case DOC:
                if (lowerExt.equals("docx")) return DocxTextExtractor.extract(in, limit);
                if (lowerExt.equals("doc") || lowerExt.equals("dot")) return DocTextExtractor.extract(in, limit);
                return "";
            case PPT:
                if (lowerExt.equals("pptx")) return PptxTextExtractor.extract(in, limit);
                if (lowerExt.equals("ppt") || lowerExt.equals("pps") || lowerExt.equals("pot")) return PptTextExtractor.extract(in, limit);
                return "";
            default:
                return "";
        }
    }

    private static String readPlain(InputStream in, int limit) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                int room = limit - sb.length();
                if (room <= 0) break;
                sb.append(buf, 0, Math.min(n, room));
            }
        }
        return sb.toString();
    }

    private static String readPdf(InputStream in, int limit) throws IOException {
        PDDocument doc = null;
        try {
            doc = PDDocument.load(in);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return truncate(text, limit);
        } finally {
            if (doc != null) try { doc.close(); } catch (IOException ignored) {
            }
        }
    }

    private static String readXls(InputStream in, int limit) throws IOException {
        Workbook wb = null;
        try {
            try {
                wb = Workbook.getWorkbook(in);
            } catch (BiffException e) {
                throw new IOException("XLS 解析失败: " + e.getMessage(), e);
            }
            StringBuilder sb = new StringBuilder();
            for (Sheet sheet : wb.getSheets()) {
                for (int r = 0; r < sheet.getRows(); r++) {
                    for (int c = 0; c < sheet.getColumns(); c++) {
                        Cell cell = sheet.getCell(c, r);
                        String s = cell.getContents();
                        if (s != null && !s.isEmpty()) {
                            sb.append(s).append(' ');
                            if (sb.length() >= limit) break;
                        }
                    }
                    if (sb.length() >= limit) break;
                }
                if (sb.length() >= limit) break;
                sb.append('\n');
            }
            return truncate(sb.toString(), limit);
        } finally {
            if (wb != null) wb.close();
        }
    }

    static String truncate(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
