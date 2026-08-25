package com.unbox.nsearch.index;

import androidx.annotation.NonNull;

import com.unbox.nsearch.FileScanner;
import com.unbox.nsearch.LuceneManager;
import com.unbox.nsearch.db.FileMetaDao;
import com.unbox.nsearch.db.IndexDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 增量同步策略：
 *  - 给定本次扫描得到的 items，识别「未变更」（命中 size/modified 校验）→ 返回 true 表示可跳过；
 *  - 给定本次扫描完成后的 seen 集合，清理库中不在 seen 内（=已删除）的文件 → Lucene 与 DB 同步删除。
 *
 * 之前是 IndexController#runIndex() 内联的两段逻辑（注释不再保留 javadoc {@link} 引用,
 * 避免 IncrementalSync 强依赖 IndexController）。
 * 抽出来后，主循环变成可读性更好的「scan → loop(item → indexOrSkip) → cleanup」。
 */
public final class IncrementalSync {

    private final LuceneManager km;
    private final FileMetaDao fileDao;

    public IncrementalSync(@NonNull LuceneManager km, @NonNull FileMetaDao fileDao) {
        this.km = km;
        this.fileDao = fileDao;
    }

    /**
     * 检查某个文件是否未变更。
     *
     * <p>「未变更」定义：DB 已有同 path 的记录、状态为 DONE、size 一致、（若 lastModified 非 0）modified 一致。
     * 任何一个条件不满足 → 需要重新索引。
     *
     * @return true 表示可跳过；false 表示需要重新抽取并写入索引
     */
    public boolean isUnchanged(@NonNull FileScanner.ScanItem item) {
        FileMetaDao.FileRow row = fileDao.getRow(item.getPath());
        return matches(row, item);
    }

    /**
     * 纯函数判定：给定一条已持久化的行与一项扫描结果,返回「可跳过」判定。
     *
     * <p>从 {@link #isUnchanged(FileScanner.ScanItem)} 抽出,便于在 JVM 单测中覆盖各种边界(无 DB 也能跑)。
     */
    public static boolean matches(@androidx.annotation.Nullable FileMetaDao.FileRow row,
                                  @NonNull FileScanner.ScanItem item) {
        if (row == null || row.status != IndexDatabase.STATUS_DONE) return false;
        if (row.size != item.length()) return false;
        if (item.lastModified() != 0 && row.modified != item.lastModified()) return false;
        return true;
    }

    /**
     * 同步清理已从磁盘删除的文件：本次扫描未出现的 path 视为已删除。
     *
     * @param seenPaths 本次扫描得到的全部 path 集合（可中途累加）
     * @return 实际清理的文件数
     */
    public int cleanupDeleted(@NonNull Set<String> seenPaths) throws java.io.IOException {
        Set<String> all = fileDao.getAllPaths();
        all.removeAll(seenPaths);
        int removed = 0;
        for (String p : all) {
            km.delete(p);
            fileDao.deleteByPath(p);
            removed++;
        }
        return removed;
    }

    /**
     * 把 List 转成可变 Set 容器，便于「扫描过程中 seen.add() 累加」。
     */
    public static Set<String> newSeenSet() {
        return new HashSet<>();
    }

    /**
     * 把 List 转成 Set（用于一次性完成扫描后批量 cleanup）。
     */
    public static Set<String> toSeenSet(@NonNull List<FileScanner.ScanItem> items) {
        Set<String> seen = new HashSet<>(items.size());
        for (FileScanner.ScanItem item : items) seen.add(item.getPath());
        return seen;
    }
}
