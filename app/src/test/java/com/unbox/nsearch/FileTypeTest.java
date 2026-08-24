package com.unbox.nsearch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * {@link FileType#match(String)} 的纯逻辑测试。
 * 涉及多扩展名归一化（xls/xlsx → XLS，doc/docx → DOC，ppt/pptx/pps/pot → PPT）。
 */
public class FileTypeTest {

    @Test
    public void match_plainText() {
        assertEquals(FileType.TXT, FileType.match("notes.txt"));
        assertEquals(FileType.TXT, FileType.match("README.TXT")); // 大小写不敏感
    }

    @Test
    public void match_markdown() {
        assertEquals(FileType.MD, FileType.match("guide.md"));
        assertEquals(FileType.MD, FileType.match("README.MD"));
    }

    @Test
    public void match_csv() {
        assertEquals(FileType.CSV, FileType.match("data.csv"));
    }

    @Test
    public void match_pdf() {
        assertEquals(FileType.PDF, FileType.match("book.pdf"));
    }

    @Test
    public void match_excel_oldAndNewNormalizeToXLS() {
        assertEquals(FileType.XLS, FileType.match("table.xls"));
        assertEquals(FileType.XLS, FileType.match("table.xlsx"));
    }

    @Test
    public void match_word_oldAndNewNormalizeToDOC() {
        assertEquals(FileType.DOC, FileType.match("paper.doc"));
        assertEquals(FileType.DOC, FileType.match("paper.docx"));
        assertEquals(FileType.DOC, FileType.match("template.dot"));
    }

    @Test
    public void match_powerPoint_oldAndNewNormalizeToPPT() {
        assertEquals(FileType.PPT, FileType.match("deck.ppt"));
        assertEquals(FileType.PPT, FileType.match("deck.pptx"));
        assertEquals(FileType.PPT, FileType.match("slideshow.pps"));
        assertEquals(FileType.PPT, FileType.match("template.pot"));
    }

    @Test
    public void match_unknownExtensionReturnsNull() {
        assertNull(FileType.match("photo.jpg"));
        assertNull(FileType.match("archive.zip"));
        assertNull(FileType.match("script.sh"));
    }

    @Test
    public void match_noExtensionReturnsNull() {
        assertNull(FileType.match("Makefile"));
        assertNull(FileType.match(""));
    }

    @Test
    public void match_nullReturnsNull() {
        assertNull(FileType.match(null));
    }

    @Test
    public void match_dotfileReturnsNull() {
        // ".gitignore" 没有扩展名主体
        assertNull(FileType.match(".gitignore"));
    }

    @Test
    public void isEnabled_setContainsExt() {
        java.util.Set<String> enabled = new java.util.HashSet<>();
        enabled.add("txt");
        assertTrue(FileType.TXT.isEnabled(enabled));
        assertFalse(FileType.PDF.isEnabled(enabled));
    }

    @Test
    public void isEnabled_emptySetIsFalse() {
        java.util.Set<String> empty = new java.util.HashSet<>();
        for (FileType t : FileType.values()) {
            assertFalse(t.isEnabled(empty));
        }
    }
}
