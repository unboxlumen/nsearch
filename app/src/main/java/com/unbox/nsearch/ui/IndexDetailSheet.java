package com.unbox.nsearch.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;

/**
 * 索引进度详情 BottomSheet：替代旧底部固定卡片，不遮挡结果列表。
 *
 * <p>使用方式（由 MainActivity 调用）：
 * <pre>{@code
 *   IndexDetailSheet.show(this, controller, s -> {
 *       // s 即将更新，更新后调用 setProgress 等
 *   });
 * }</pre>
 *
 * <p>动作通过 {@link Listener} 回调给宿主（暂停 / 继续 / 取消），不反向依赖 Controller。
 */
public final class IndexDetailSheet {

    public interface Listener {
        void onTogglePause();
        void onCancel();
    }

    private final Context context;
    private final IndexController controller;
    private final Listener listener;
    private BottomSheetDialog dialog;
    private TextView title;
    private TextView subtitle;
    private TextView current;
    private LinearProgressIndicator progress;
    private MaterialButton btnPauseResume;
    private MaterialButton btnCancel;

    private IndexDetailSheet(@NonNull Context context,
                              @NonNull IndexController controller,
                              @NonNull Listener listener) {
        this.context = context;
        this.controller = controller;
        this.listener = listener;
    }

    public static IndexDetailSheet show(@NonNull Context context,
                                        @NonNull IndexController controller,
                                        @NonNull Listener listener) {
        IndexDetailSheet sheet = new IndexDetailSheet(context, controller, listener);
        sheet.show();
        return sheet;
    }

    private void show() {
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_index_detail, null);
        title = root.findViewById(R.id.sheetTitle);
        subtitle = root.findViewById(R.id.sheetSubtitle);
        current = root.findViewById(R.id.sheetCurrent);
        progress = root.findViewById(R.id.sheetProgress);
        btnPauseResume = root.findViewById(R.id.sheetPauseResume);
        btnCancel = root.findViewById(R.id.sheetCancel);

        btnPauseResume.setOnClickListener(v -> listener.onTogglePause());
        btnCancel.setOnClickListener(v -> listener.onCancel());

        dialog = new BottomSheetDialog(context);
        dialog.setContentView(root);
        // 立即把当前状态推送一次，避免「打开 sheet 显示空内容」
        applyState(controller.getState());
        dialog.show();
    }

    /** 由 MainActivity 监听到状态变化时推送。 */
    public void applyState(@NonNull IndexController.State s) {
        if (dialog == null) return;
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (!active) {
            // 已结束：3 秒后自动关闭
            dialog.dismiss();
            return;
        }
        int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
        progress.setProgressCompat(pct, !UiAnimations.shouldAnimate(context));
        subtitle.setText(context.getString(R.string.index_detail_subtitle, s.indexed, s.total));
        if (s.currentFile != null && !s.currentFile.isEmpty()) {
            current.setText(context.getString(R.string.index_detail_current, s.currentFile));
            current.setVisibility(View.VISIBLE);
        } else {
            current.setVisibility(View.GONE);
        }
        btnPauseResume.setText(s.status == IndexController.Status.PAUSED
                ? R.string.index_detail_resume
                : R.string.index_detail_pause);
    }
}