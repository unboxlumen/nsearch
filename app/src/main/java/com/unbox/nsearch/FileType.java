package com.unbox.nsearch;

import java.util.Locale;

/**
 * 可索引的文件类型。XLS 同时覆盖 .xls 与 .xlsx；DOC 同时覆盖 .doc 与 .docx；PPT 同时覆盖 .ppt 与 .pptx。
 */
public enum FileType {
    TXT("txt", R.string.type_txt),
    MD("md", R.string.type_md),
    CSV("csv", R.string.type_csv),
    PDF("pdf", R.string.type_pdf),
    XLS("xls", R.string.type_xlsx), // 同时匹配 .xls / .xlsx
    DOC("doc", R.string.type_doc), // 同时匹配 .doc / .docx
    PPT("ppt", R.string.type_ppt); // 同时匹配 .ppt / .pptx

    /** 对应的扩展名（小写，无点）。包级可见，供 {@link FileScanner} 使用。 */
    final String ext;
    public final int labelRes;

    FileType(String ext, int labelRes) {
        this.ext = ext;
        this.labelRes = labelRes;
    }

    /** 根据文件名匹配类型；不匹配返回 null。 */
    public static FileType match(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return null;
        String e = lower.substring(dot + 1);
        if (e.equals("xlsx")) return XLS;
        if (e.equals("docx")) return DOC;
        if (e.equals("pptx")) return PPT;
        if (e.equals("dot")) return DOC;     // Word 模板（老格式）
        if (e.equals("pps") || e.equals("pot")) return PPT; // PPT 放映/模板（老格式）
        for (FileType t : values()) {
            if (t.ext.equals(e)) return t;
        }
        return null;
    }

    boolean isEnabled(java.util.Set<String> enabledTypes) {
        return enabledTypes.contains(ext);
    }
}
