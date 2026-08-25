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
 */
public final class FileScanner {

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
                walkFile(root, enabled, items, 0, onProgress);
            }
        } catch (Throwable ignored) {
        }
        // SAF 添加的文件夹
        for (String uriStr : settings.getScopeUris()) {
            try {
                Uri uri = Uri.parse(uriStr);
                DocumentFile tree = DocumentFile.fromTreeUri(context, uri);
                if (tree != null && tree.exists()) {
                    walkDocument(context, tree, enabled, items, 0, onProgress);
                }
            } catch (Throwable ignored) {
            }
        }
        if (onProgress != null) onProgress.accept(items.size());
        return items;
    }

    private static void walkFile(File dir, Set<String> enabled, List<ScanItem> out, int depth,
                                  Consumer<Integer> onProgress) {
        if (depth > 32) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isHidden()) continue;
            if (f.isDirectory()) {
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (SKIP_DIRS.contains(lower)) continue;
                walkFile(f, enabled, out, depth + 1, onProgress);
            } else if (f.isFile()) {
                FileType t = FileType.match(f.getName());
                if (t != null && t.isEnabled(enabled)) {
                    out.add(new FileScanItem(f, t.ext));
                    if (onProgress != null) onProgress.accept(out.size());
                }
            }
        }
    }

    private static void walkDocument(Context ctx, DocumentFile dir, Set<String> enabled,
                                      List<ScanItem> out, int depth,
                                      Consumer<Integer> onProgress) {
        if (depth > 32) return;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;
        for (DocumentFile f : children) {
            if (f == null) continue;
            String name = f.getName();
            if (name != null && name.startsWith(".")) continue;
            if (f.isDirectory()) {
                String lower = (name == null ? "" : name.toLowerCase(Locale.ROOT));
                if (SKIP_DIRS.contains(lower)) continue;
                walkDocument(ctx, f, enabled, out, depth + 1, onProgress);
            } else if (f.isFile()) {
                FileType t = FileType.match(name);
                if (t != null && t.isEnabled(enabled)) {
                    out.add(new DocumentScanItem(f, t.ext));
                    if (onProgress != null) onProgress.accept(out.size());
                }
            }
        }
    }

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
