package com.unbox.nsearch.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * 把 MainActivity 中的「外部存储权限 + 跳系统设置」统一封装。
 *
 * 设计动机：之前那段 if-else（API 30+ 走 MANAGE_APP_ALL_FILES_ACCESS_PERMISSION，
 * API 30- 走 READ_EXTERNAL_STORAGE + Settings.ACTION_APPLICATION_DETAILS_SETTINGS，
 * Intent 找不到就 fallback 到 MANAGE_ALL_FILES_ACCESS_PERMISSION，再不行退到
 * ACTION_SETTINGS）混在 Activity 里约 60 行，新增 Activity 复用成本高。
 *
 * 使用方式：宿主 Activity 实现 {@link Callback}，把回调方法委托给 {@link PermissionHelper}；
 * onRequestPermissionsResult / onActivityResult 里把结果回灌给本类。
 */
public final class PermissionHelper {

    public static final int REQ_ALL_FILES = 1001;
    public static final int REQ_READ = 1002;

    /** 由宿主 Activity 实现以拿到「权限/跳转是否成功」的回调。 */
    public interface Callback {
        /** 当前已具备所有文件访问权（可直接启动索引）。 */
        void onGranted();

        /** 当前不具备权限；{@code launchedSettings} 表示本类是否已尝试拉起系统设置。 */
        void onDenied(boolean launchedSettings);
    }

    private final Activity activity;
    private final Callback callback;

    public PermissionHelper(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * 入口：检查权限并申请。
     * - 已授权：直接回调 {@link Callback#onGranted()}
     * - 未授权：尝试启动系统授权页，回调 {@link Callback#onDenied(true)}
     * - 异常（设备无对应设置入口）：回调 {@link Callback#onDenied(false)}
     */
    public void request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                callback.onGranted();
                return;
            }
            try {
                activity.startActivityForResult(buildAllFilesAccessIntent(), REQ_ALL_FILES);
                callback.onDenied(true);
            } catch (Exception e) {
                callback.onDenied(false);
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                callback.onGranted();
            } else {
                activity.requestPermissions(
                        new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
                callback.onDenied(true);
            }
        }
    }

    /**
     * 在宿主 Activity 的 {@code onActivityResult} 中调用。
     */
    public void onActivityResult(int requestCode) {
        if (requestCode != REQ_ALL_FILES) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager()) {
            callback.onGranted();
        } else {
            callback.onDenied(false);
        }
    }

    /**
     * 在宿主 Activity 的 {@code onRequestPermissionsResult} 中调用。
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != REQ_READ) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            callback.onGranted();
        } else {
            callback.onDenied(false);
        }
    }

    /**
     * 「去应用详情页 / 文件访问授权页 / 系统设置」的多级 fallback。
     */
    public void openAppSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            i = buildAllFilesAccessIntent();
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
        }
        try {
            activity.startActivity(i);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private Intent buildAllFilesAccessIntent() {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        i.setData(Uri.parse("package:" + activity.getPackageName()));
        if (activity.getPackageManager().resolveActivity(i, 0) == null) {
            i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
        }
        return i;
    }

    /** 当前是否具备外部存储全量访问权（仅作查询，不发起任何跳转）。 */
    public static boolean hasAllFilesAccess(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true;
        return Environment.isExternalStorageManager();
    }
}
