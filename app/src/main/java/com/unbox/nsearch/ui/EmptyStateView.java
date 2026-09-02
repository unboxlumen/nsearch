package com.unbox.nsearch.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatButton;

import com.unbox.nsearch.R;

/**
 * 空 / 加载 / 错误三态共用的容器（icon + title + subtitle + 可选 action）。
 *
 * <p>把三态样式收敛到一处，避免散落实现造成的「三种样式各异」反模式
 * （见 UI_DESIGN_GUIDE.md §8「易忽略的盲点 — 三态」）。
 *
 * <p>{@code action} 用 {@link AppCompatButton} 而非 {@code MaterialButton}：
 * 后者在某些 device 上 inflate 时会因解析 {@code ?attr/shapeAppearanceSmallComponent}
 * 等链而抛 {@code Resources$NotFoundException: Can't find ColorStateList}。改用普通
 * Button + 自定义 selector 实现「Pill 描边按钮」外观,避开该问题。
 */
public final class EmptyStateView extends android.widget.LinearLayout {

    public enum State { IDLE, EMPTY, NO_RESULTS, ERROR }

    public interface ActionListener {
        void onAction();
    }

    private final ImageView icon;
    private final TextView title;
    private final TextView subtitle;
    private final AppCompatButton action;

    public EmptyStateView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER);
        LayoutInflater.from(context).inflate(R.layout.include_empty_state, this, true);
        icon = findViewById(R.id.stateIcon);
        title = findViewById(R.id.stateTitle);
        subtitle = findViewById(R.id.stateSubtitle);
        action = findViewById(R.id.stateAction);
    }

    /**
     * 应用预定义状态。
     */
    public void applyState(@NonNull State state, @Nullable ActionListener listener) {
        switch (state) {
            case IDLE:
                show(R.drawable.ic_search,
                        R.string.state_empty_title,
                        R.string.state_empty_subtitle,
                        0, null);
                break;
            case NO_RESULTS:
                show(R.drawable.ic_search,
                        R.string.state_no_results_title,
                        R.string.state_no_results_subtitle,
                        0, null);
                break;
            case ERROR:
                show(R.drawable.ic_error,
                        R.string.state_error_title,
                        R.string.state_error_subtitle,
                        R.string.retry,
                        listener);
                break;
            case EMPTY:
            default:
                setVisibility(GONE);
                return;
        }
    }

    /** 自定义三态。 */
    public void show(@DrawableRes int iconRes,
                     @StringRes int titleRes,
                     @StringRes int subtitleRes,
                     @StringRes int actionRes,
                     @Nullable ActionListener listener) {
        setVisibility(VISIBLE);
        icon.setImageResource(iconRes);
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
        if (actionRes != 0 && listener != null) {
            action.setVisibility(VISIBLE);
            action.setText(actionRes);
            action.setOnClickListener(v -> listener.onAction());
        } else {
            action.setVisibility(GONE);
            action.setOnClickListener(null);
        }
    }
}