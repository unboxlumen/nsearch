package com.unbox.nsearch.ui;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;

/**
 * 索引进度详情卡 + 工具栏进度环 的视图控制器。
 *
 * <p>负责把 {@link IndexController.State} 翻译成：
 *  - 详情卡内的进度条 / 统计行 / 当前文件行 / 暂停按钮文案；
 *  - 工具栏进度环的百分比文字；
 *  - 详情卡的显隐（仅在运行中显示）。
 *
 * <p>pause/resume 动作通过 {@link Listener} 回调给宿主 Activity，避免本类反向依赖 Controller。
 */
public final class IndexProgressCard implements IndexController.Listener {

    public interface Listener {
        void onTogglePause();
    }

    private final View progressCard;
    private final ProgressBar progressBar;
    private final TextView progressStats;
    private final TextView progressCurrent;
    private final TextView progressTitle;
    private final TextView resultCount;
    private final MaterialButton btnPauseResume;

    @Nullable private final CircularProgressIndicator ring;
    @Nullable private final TextView ringPct;
    @Nullable private final View indexProgressAction;

    private final Listener listener;
    private final ResultCountFormatter resultCountFormatter;

    public IndexProgressCard(@NonNull View root,
                             @Nullable View indexProgressAction,
                             @Nullable CircularProgressIndicator ring,
                             @Nullable TextView ringPct,
                             @NonNull TextView resultCount,
                             @NonNull Listener listener,
                             @NonNull ResultCountFormatter formatter) {
        this.progressCard = root.findViewById(R.id.progressCard);
        this.progressBar = root.findViewById(R.id.progressBar);
        this.progressStats = root.findViewById(R.id.progressStats);
        this.progressCurrent = root.findViewById(R.id.progressCurrent);
        this.progressTitle = root.findViewById(R.id.progressTitle);
        this.btnPauseResume = root.findViewById(R.id.btnPauseResume);
        this.resultCount = resultCount;
        this.indexProgressAction = indexProgressAction;
        this.ring = ring;
        this.ringPct = ringPct;
        this.listener = listener;
        this.resultCountFormatter = formatter;

        btnPauseResume.setOnClickListener(v -> listener.onTogglePause());
    }

    /**
     * 切换详情卡的显隐。
     */
    public void toggleDetail() {
        if (progressCard != null) {
            progressCard.setVisibility(progressCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onProgress(IndexController.State s) {
        progressBar.setMax(Math.max(1, s.total));
        progressBar.setProgress(s.indexed);
        progressStats.setText(progressStats.getContext().getString(R.string.index_stats, s.indexed, s.total));
        progressCurrent.setText(progressCurrent.getContext().getString(R.string.indexing_current, s.currentFile));
        int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
        if (ring != null) ring.setProgress(pct);
        if (ringPct != null) ringPct.setText(pct + "%");
        if (s.status != IndexController.Status.RUNNING && s.status != IndexController.Status.PAUSED) {
            resultCount.setText(resultCountFormatter.formatIdle());
        }
    }

    @Override
    public void onStatus(IndexController.State s) {
        boolean active = s.status == IndexController.Status.RUNNING || s.status == IndexController.Status.PAUSED;
        applyActiveState(active);
        btnPauseResume.setText(active && s.status == IndexController.Status.PAUSED
                ? R.string.btn_resume : R.string.btn_pause);
        if (!active) {
            progressCard.setVisibility(View.GONE);
            resultCount.setText(resultCountFormatter.formatIdle());
        }
    }

    private void applyActiveState(boolean active) {
        if (indexProgressAction != null) {
            indexProgressAction.setVisibility(active ? View.VISIBLE : View.GONE);
        }
    }

    /** 让宿主在详情卡点击外部时被关闭。 */
    public void hideDetail() {
        if (progressCard != null) progressCard.setVisibility(View.GONE);
    }

    /**
     * 「空闲态」结果计数行格式化：通常显示索引文件总数。
     */
    public interface ResultCountFormatter {
        @NonNull String formatIdle();
    }
}