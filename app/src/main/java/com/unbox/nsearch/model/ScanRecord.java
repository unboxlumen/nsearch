package com.unbox.nsearch.model;

/**
 * 一次索引扫描的历史记录。
 */
public class ScanRecord {
    public long id;
    public long startedAt;
    public long finishedAt;
    public int totalFiles;
    public int indexedFiles;
    public int failedFiles;
    public int skippedFiles;
    public long durationMs;
    public String trigger;

    public ScanRecord() {
    }

    public ScanRecord(long startedAt, long finishedAt, int totalFiles, int indexedFiles,
                      int failedFiles, int skippedFiles, long durationMs, String trigger) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalFiles = totalFiles;
        this.indexedFiles = indexedFiles;
        this.failedFiles = failedFiles;
        this.skippedFiles = skippedFiles;
        this.durationMs = durationMs;
        this.trigger = trigger;
    }
}
