package com.unbox.nsearch.ui;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;

/**
 * 旧版索引进度详情卡 + 工具栏进度环 的视图控制器（保留兼容桩）。
 *
 * <p>UI 重构后,主页改用 {@link IndexIndicatorView} + {@link IndexDetailSheet},
 * 历史页不再监听 Controller。本类保留以便任何缓存 / 反射引用不报 NoSuchFieldError。
 *
 * <p>为了对找不到的 R.id 容错：所有 {@code findViewById} 接收 null 时不抛 NPE,
 * 仅 {@code Listener} 回调路径保留给外部显式调用。
 */
@Deprecated
public final class IndexProgressCard implements IndexController.Listener {

    public interface Listener {
        void onTogglePause();
        void onCancel();
    }

    private final TextView resultCount;
    private final Listener listener;
    private final ResultCountFormatter resultCountFormatter;

    @Nullable private final View progressCard;
    @Nullable private final CircularProgressIndicator ring;
    @Nullable private final TextView ringPct;
    @Nullable private final View indexProgressAction;
    @Nullable private final View activeGroup;
    @Nullable private final TextView idleBadge;

    public IndexProgressCard(@NonNull View root,
                             @Nullable View indexProgressAction,
                             @Nullable CircularProgressIndicator ring,
                             @Nullable TextView ringPct,
                             @NonNull TextView resultCount,
                             @NonNull Listener listener,
                             @NonNull ResultCountFormatter formatter) {
        this.progressCard = safeFind(root, R.id.progressCard);
        this.ring = ring;
        this.ringPct = ringPct;
        this.resultCount = resultCount;
        this.indexProgressAction = indexProgressAction;
        this.listener = listener;
        this.resultCountFormatter = formatter;
        if (indexProgressAction != null) {
            this.activeGroup = indexProgressAction.findViewById(R.id.activeGroup);
            this.idleBadge = indexProgressAction.findViewById(R.id.idleBadge);
        } else {
            this.activeGroup = null;
            this.idleBadge = null;
        }
    }

    private static View safeFind(View root, int id) {
        try {
            return root.findViewById(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public void toggleDetail() {
        if (progressCard != null) {
            progressCard.setVisibility(progressCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onProgress(IndexController.State s) {
        int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
        if (ring != null) ring.setProgressCompat(pct, true);
        if (ringPct != null) ringPct.setText(pct + "%");
        boolean active = s.status.isActive();
        if (active) {
            resultCount.setText(resultCountFormatter.formatActive(s.indexed, s.total, s.currentFile));
        } else {
            if (idleBadge != null) {
                idleBadge.setText(idleBadge.getContext().getString(R.string.toolbar_idle_badge, resultCountFormatter.formatIdleCount()));
            }
            resultCount.setText(resultCountFormatter.formatIdle());
        }
    }

    @Override
    public void onStatus(IndexController.State s) {
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (indexProgressAction != null) indexProgressAction.setVisibility(View.VISIBLE);
        if (activeGroup != null) activeGroup.setVisibility(active ? View.VISIBLE : View.GONE);
        if (idleBadge != null) idleBadge.setVisibility(active ? View.GONE : View.VISIBLE);
        if (!active) {
            if (idleBadge != null) {
                idleBadge.setText(idleBadge.getContext().getString(R.string.toolbar_idle_badge, resultCountFormatter.formatIdleCount()));
            }
            if (progressCard != null) progressCard.setVisibility(View.GONE);
            resultCount.setText(resultCountFormatter.formatIdle());
        }
    }

    public void hideDetail() {
        if (progressCard != null) progressCard.setVisibility(View.GONE);
    }

    public interface ResultCountFormatter {
        @NonNull String formatIdle();
        int formatIdleCount();
        @NonNull String formatActive(int indexed, int total, @Nullable String currentFile);
    }
}