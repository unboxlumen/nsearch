package com.unbox.nsearch.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.unbox.nsearch.model.SearchResult;

import java.io.File;

/**
 * 把 SearchResult 打开成系统 Intent 的逻辑集中到一处。
 *
 * 之前的实现混在 MainActivity.onOpen 里：先看 r.contentUri 是 true 就用 parse 出来的 content uri；
 * 否则把 r.openUri 当成本地 File 路径，找不到则 Toast；找到则用 FileProvider 包装；
 * 最后 ACTION_VIEW + FLAG_GRANT_READ_URI_PERMISSION，抛异常时再 Toast。
 *
 * 为保持「外部行为完全不变」，Toast 文本沿用原硬编码字符串，未抽到 strings.xml。
 */
public final class FileOpener {

    private static final String AUTHORITY_SUFFIX = ".fileprovider";

    private FileOpener() {}

    /**
     * 打开一个搜索结果。
     *
     * @return true 表示成功发出 Intent；false 表示文件不存在 / Intent 启动失败。
     */
    public static boolean open(@NonNull Context context, @NonNull SearchResult r) {
        Uri uri;
        try {
            if (r.contentUri) {
                uri = Uri.parse(r.openUri);
            } else {
                File f = new File(r.openUri);
                if (!f.exists()) {
                    Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
                    return false;
                }
                String authority = context.getPackageName() + AUTHORITY_SUFFIX;
                uri = FileProvider.getUriForFile(context, authority, f);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "无法打开：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(context, "无法打开：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
