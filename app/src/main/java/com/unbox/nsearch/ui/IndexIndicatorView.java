package com.unbox.nsearch.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;

/**
 * AppBar 索引入口视图（空闲/活跃两种状态自动切换）：
 * <ul>
 *   <li>空闲：左侧 pill「已索引 N」 + 右侧 20dp 索引图标，整体 1dp 描边替代阴影做层级</li>
 *   <li>活跃：28dp 环形进度 + 中心 11sp「62%」</li>
 * </ul>
 *
 * <p>不持有任何业务状态：只接受 {@link IndexController.State} 推送并刷新外观。
 * 点击事件通过 {@link OnClickListener} 上抛给宿主（典型为弹 BottomSheet / 触发索引）。
 *
 * <p>尺寸：固定 48dp 触控高（§5），宽度 wrap_content。
 */
public final class IndexIndicatorView extends FrameLayout {

    private final View idleGroup;
    private final View activeGroup;
    private final TextView idleBadge;
    private final CircularProgressIndicator ring;
    private final TextView ringPct;

    public IndexIndicatorView(@NonNull Context context) {
        this(context, null);
    }

    public IndexIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndexIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 复用 view_index_indicator.xml 的 <merge> 根
        LayoutInflater.from(context).inflate(R.layout.view_index_indicator, this, true);

        idleGroup = findViewById(R.id.idleGroup);
        activeGroup = findViewById(R.id.activeGroup);
        idleBadge = findViewById(R.id.idleBadge);
        ring = findViewById(R.id.ring);
        ringPct = findViewById(R.id.ringPct);

        // 点击波纹
        setBackground(android.util.TypedValue.complexToFraction(0, 0, 0) == 0
                ? null : null);
        setClickable(true);
        setFocusable(true);
    }

    /** 切换到空闲态，显示已索引文件数。 */
    public void showIdle(int indexedCount) {
        idleGroup.setVisibility(View.VISIBLE);
        activeGroup.setVisibility(View.GONE);
        idleBadge.setText(formatCount(indexedCount));
        setContentDescription(getResources().getString(R.string.cd_index_idle));
    }

    /**
     * 切换到活跃态，显示当前百分比；pause=true 时显示「…」并降饱和度。
     */
    public void showActive(int indexed, int total, boolean paused) {
        idleGroup.setVisibility(View.GONE);
        activeGroup.setVisibility(View.VISIBLE);
        int pct = total > 0 ? (int) (indexed * 100L / total) : 0;
        ring.setProgressCompat(pct, !UiAnimations.shouldAnimate(getContext()));
        if (paused) {
            ringPct.setText("…");
            int brandColor = getContext().getResources()
                    .getColor(R.color.brand, null);
            // 在原色基础上叠加 60% alpha,视觉上明显比运行中浅
            ring.setIndicatorColor((brandColor & 0x00FFFFFF) | 0x99000000);
        } else {
            ringPct.setText(pct + "%");
            ring.setIndicatorColor(getContext().getResources()
                    .getColor(R.color.brand, null));
        }
        setContentDescription(getResources().getString(R.string.cd_index_active));
    }

    private static String formatCount(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 10000) return String.format("%.1fk", n / 1000.0);
        return String.format("%dk", n / 1000);
    }

    /** 兼容旧 ID 调用（保留供现有逻辑迁移期使用）。 */
    @Deprecated
    public void setRingProgress(int pct) {
        if (ring != null) ring.setProgressCompat(pct, !UiAnimations.shouldAnimate(getContext()));
    }

    /** 兼容旧 ID 调用。 */
    @Deprecated
    public void setRingPctText(String s) {
        if (ringPct != null) ringPct.setText(s);
    }

    /** 兼容旧 ID 调用。 */
    @Deprecated
    public View getActiveGroup() { return activeGroup; }

    /** 兼容旧 ID 调用。 */
    @Deprecated
    public TextView getIdleBadge() { return idleBadge; }

    /** 兼容旧 ID 调用。 */
    @Deprecated
    public ImageView getIdleIcon() {
        return findViewById(R.id.idleIcon);
    }
}