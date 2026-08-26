package com.unbox.nsearch;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 扫描待索引文件。
 * 范围 = 主外部存储根目录（默认）+ 用户在设置里通过 SAF 添加的文件夹。
 * 仅收集启用类型（txt/md/csv/pdf/xls/xlsx）的常规文件，跳过系统/缓存目录。
 *
 * <p>本地 {@link File} 与 SAF {@link DocumentFile} 两条路径共用同一套遍历逻辑
 * （{@link #walk(DirNode, Set, List, int, Consumer)}），差异全部收敛到
 * {@link DirNode} 适配器（{@link FileNode} / {@link DocumentNode}）中。
 */
public final class FileScanner {

    /** 目录递归最大深度，防止异常深的目录树拖垮扫描。 */
    private static final int MAX_DEPTH = 32;

    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            "android", "android/data", "android/obb", ".thumbnails", ".git", "node_modules",
            ".gradle", "build", "cache", "tmp", "lost+found", "alias", "alarms",
            "notifications", "ringtones", "podcasts"));

    private FileScanner() {
    }

    public interface ScanItem {
        String getName();

        long length();

        long lastModified();

        /** 稳定标识（本地为绝对路径，SAF 为 Uri 字符串） */
        String getPath();

        /** 界面展示路径 */
        String getDisplayPath();

        /** 打开文件所用 Uri（本地为绝对路径，SAF 为 content Uri） */
        String getOpenUri();

        boolean isContentUri();

        InputStream openStream(Context ctx) throws IOException;

        String getExt();
    }

    public static List<ScanItem> scan(Context context, Settings settings) {
        return scan(context, settings, null);
    }

    /**
     * 扫描文件,期间通过 {@code onProgress} 回调通知已发现的文件数。
     * {@code onProgress} 可为 null(等同于 {@link #scan(Context, Settings)})。
     * 回调在调用线程上执行,频率与每个目录的子节点遍历粒度相关,
     * Pipeline 应对回调做节流(例如「每 N 次才触发一次 toast」)。
     */
    public static List<ScanItem> scan(Context context, Settings settings, Consumer<Integer> onProgress) {
        Set<String> enabled = settings.getEnabledTypes();
        List<ScanItem> items = new ArrayList<>();
        // 主外部存储
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root != null && root.isDirectory()) {
                walk(new FileNode(root), enabled, items, 0, onProgress);
            }
        } catch (Throwable ignored) {
        }
        // SAF 添加的文件夹
        for (String uriStr : settings.getScopeUris()) {
            try {
                Uri uri = Uri.parse(uriStr);
                DocumentFile tree = DocumentFile.fromTreeUri(context, uri);
                if (tree != null && tree.exists()) {
                    walk(new DocumentNode(tree), enabled, items, 0, onProgress);
                }
            } catch (Throwable ignored) {
            }
        }
        if (onProgress != null) onProgress.accept(items.size());
        return items;
    }

    /**
     * 统一目录遍历：递归进入子目录，把命中启用类型的常规文件写入 {@code out}。
     * 隐藏项（含点文件）与 {@link SKIP_DIRS} 命中目录跳过；超过 {@link MAX_DEPTH} 截断。
     */
    private static void walk(DirNode dir, Set<String> enabled, List<ScanItem> out, int depth,
                             Consumer<Integer> onProgress) {
        if (depth > MAX_DEPTH) return;
        DirNode[] children = dir.children();
        for (DirNode n : children) {
            if (n.isHiddenOrDot()) continue;
            if (n.isDir()) {
                if (shouldSkipDir(n.name())) continue;
                walk(n, enabled, out, depth + 1, onProgress);
            } else if (n.isFile()) {
                FileType t = matchEnabled(n.name(), enabled);
                if (t != null) {
                    out.add(n.toItem(t.ext));
                    notifyProgress(out, onProgress);
                }
            }
        }
    }

    /** 命中 {@link SKIP_DIRS}（大小写不敏感）则跳过该目录。 */
    private static boolean shouldSkipDir(String name) {
        return SKIP_DIRS.contains(name.toLowerCase(Locale.ROOT));
    }

    /** 文件名命中启用类型时返回对应 {@link FileType}，否则 null。 */
    private static FileType matchEnabled(String name, Set<String> enabled) {
        FileType t = FileType.match(name);
        return (t != null && t.isEnabled(enabled)) ? t : null;
    }

    private static void notifyProgress(List<ScanItem> out, Consumer<Integer> onProgress) {
        if (onProgress != null) onProgress.accept(out.size());
    }

    // ---------------- 目录树节点抽象 ----------------

    /**
     * 抹平 {@link File} 与 {@link DocumentFile} 的目录遍历差异，
     * 使两条扫描路径共用同一套 {@link #walk} 逻辑。
     */
    private interface DirNode {
        String name();

        boolean isDir();

        boolean isFile();

        /** 隐藏项/点文件判定：本地走 {@link File#isHidden()}，SAF 走「以 . 开头」。 */
        boolean isHiddenOrDot();

        /** 子节点（失败或为空时返回空数组，绝不为 null；实现需过滤空元素）。 */
        DirNode[] children();

        /** 将当前节点包装为扫描结果项。 */
        ScanItem toItem(String ext);
    }

    private static final class FileNode implements DirNode {
        private final File file;

        FileNode(File file) {
            this.file = file;
        }

        @Override public String name() { return file.getName(); }
        @Override public boolean isDir() { return file.isDirectory(); }
        @Override public boolean isFile() { return file.isFile(); }
        @Override public boolean isHiddenOrDot() { return file.isHidden(); }

        @Override public DirNode[] children() {
            File[] cs = file.listFiles();
            if (cs == null || cs.length == 0) return EMPTY_NODES;
            DirNode[] ns = new DirNode[cs.length];
            int i = 0;
            for (File c : cs) {
                if (c != null) ns[i++] = new FileNode(c);
            }
            return i == cs.length ? ns : Arrays.copyOf(ns, i);
        }

        @Override public ScanItem toItem(String ext) { return new FileScanItem(file, ext); }
    }

    private static final class DocumentNode implements DirNode {
        private final DocumentFile doc;

        DocumentNode(DocumentFile doc) {
            this.doc = doc;
        }

        @Override public String name() {
            String n = doc.getName();
            return n == null ? "" : n;
        }

        @Override public boolean isDir() { return doc.isDirectory(); }
        @Override public boolean isFile() { return doc.isFile(); }

        @Override public boolean isHiddenOrDot() { return name().startsWith("."); }

        @Override public DirNode[] children() {
            DocumentFile[] cs = doc.listFiles();
            if (cs == null || cs.length == 0) return EMPTY_NODES;
            DirNode[] ns = new DirNode[cs.length];
            int i = 0;
            for (DocumentFile c : cs) {
                if (c != null) ns[i++] = new DocumentNode(c);
            }
            return i == cs.length ? ns : Arrays.copyOf(ns, i);
        }

        @Override public ScanItem toItem(String ext) { return new DocumentScanItem(doc, ext); }
    }

    private static final DirNode[] EMPTY_NODES = new DirNode[0];

    // ---------------- 实现 ----------------

    private static class FileScanItem implements ScanItem {
        private final File file;
        private final String ext;

        FileScanItem(File file, String ext) {
            this.file = file;
            this.ext = ext;
        }

        @Override public String getName() { return file.getName(); }
        @Override public long length() { return file.length(); }
        @Override public long lastModified() { return file.lastModified(); }
        @Override public String getPath() { return file.getAbsolutePath(); }
        @Override public String getDisplayPath() { return file.getAbsolutePath(); }
        @Override public String getOpenUri() { return file.getAbsolutePath(); }
        @Override public boolean isContentUri() { return false; }
        @Override public InputStream openStream(Context ctx) throws IOException {
            return new java.io.FileInputStream(file);
        }
        @Override public String getExt() { return ext; }
    }

    private static class DocumentScanItem implements ScanItem {
        private final DocumentFile doc;
        private final String ext;

        DocumentScanItem(DocumentFile doc, String ext) {
            this.doc = doc;
            this.ext = ext;
        }

        @Override public String getName() { return doc.getName() == null ? "" : doc.getName(); }
        @Override public long length() { return doc.length(); }
        @Override public long lastModified() { return doc.lastModified(); }
        @Override public String getPath() { return doc.getUri().toString(); }
        @Override public String getDisplayPath() { return getName(); }
        @Override public String getOpenUri() { return doc.getUri().toString(); }
        @Override public boolean isContentUri() { return true; }
        @Override public InputStream openStream(Context ctx) throws IOException {
            return ctx.getContentResolver().openInputStream(doc.getUri());
        }
        @Override public String getExt() { return ext; }
    }
}
