package com.unbox.nsearch.model;

/**
 * 单条搜索结果。
 */
public class SearchResult {
    public final String name;
    public final String displayPath;
    /** 用于打开文件的 Uri：本地文件为绝对路径，SAF 文档为 content Uri 字符串 */
    public final String openUri;
    public final boolean contentUri;
    public final long size;
    public final long lastModified;
    public final float score;
    /** 文件类型短标签（如 PDF / Word / Excel），用于结果卡片 meta 行 */
    public final String typeLabel;
    /** 高亮摘要（HTML 片段，由搜索时生成） */
    public String snippetHtml;

    public SearchResult(String name, String displayPath, String openUri, boolean contentUri,
                        long size, long lastModified, float score, String typeLabel) {
        this.name = name;
        this.displayPath = displayPath;
        this.openUri = openUri;
        this.contentUri = contentUri;
        this.size = size;
        this.lastModified = lastModified;
        this.score = score;
        this.typeLabel = typeLabel;
    }
}
