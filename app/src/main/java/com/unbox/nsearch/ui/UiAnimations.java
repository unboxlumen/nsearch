package com.unbox.nsearch.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

/**
 * UI 动效工具类（统一收敛 fadeIn / fadeOut / smoothProgress）。
 *
 * <p>集中提供 200ms 标准时长的进入/退出动画，统一使用
 * {@link AccelerateDecelerateInterpolator}（≈ Material 的 FastOutSlowIn），
 * 避免动效散落在各 Activity 中导致时长不一致。
 *
 * <p>另提供 {@link #shouldAnimate(Context)}：当用户在系统设置中关闭「动画效果」
 * （Animator duration scale = 0）时，本类所有方法跳过动画直接切换状态。
 * 见 UI_DESIGN_GUIDE.md §4 / §5「reduced-motion 处理」。
 */
public final class UiAnimations {

    private static final long DEFAULT_DURATION_MS = 200L;
    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private UiAnimations() {}

    /** 当系统关闭动画或客户端禁用时返回 false。 */
    public static boolean shouldAnimate(@NonNull Context context) {
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            return scale > 0f;
        } catch (Throwable ignored) {
            // Android 11+ 下 SettingNotFoundException 是 unchecked;用 Throwable 兜底
            // 任何异常都视为「可放行动画」。
            return true;
        }
    }

    /** 把 view 平滑切到 VISIBLE（带 200ms alpha 淡入）。 */
    public static void fadeIn(@NonNull View view) {
        if (view.getVisibility() == View.VISIBLE) return;
        if (!shouldAnimate(view.getContext())) {
            view.setAlpha(1f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        ViewPropertyAnimator anim = view.animate()
                .alpha(1f)
                .setDuration(DEFAULT_DURATION_MS)
                .setInterpolator(INTERPOLATOR);
        anim.start();
    }

    /** 把 view 平滑切到 GONE（200ms alpha 淡出后再 GONE）。 */
    public static void fadeOut(@NonNull View view) {
        if (view.getVisibility() == View.GONE) return;
        if (!shouldAnimate(view.getContext())) {
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
            return;
        }
        ViewPropertyAnimator anim = view.animate()
                .alpha(0f)
                .setDuration(DEFAULT_DURATION_MS)
                .setInterpolator(INTERPOLATOR)
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setAlpha(1f);
                });
        anim.start();
    }
}