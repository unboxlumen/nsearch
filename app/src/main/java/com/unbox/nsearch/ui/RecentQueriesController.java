package com.unbox.nsearch.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.unbox.nsearch.R;
import com.unbox.nsearch.Settings;

import java.util.Collections;
import java.util.List;

/**
 * 「最近搜索词」Chip 行控制器：
 *  - 读 {@link Settings#getRecentQueries()} 填充 ChipGroup；
 *  - 每枚 Chip 点击 → 写回 searchBox 并触发搜索；
 *  - 「清空」按钮 → 调用 {@link Settings#clearRecentQueries()}。
 *
 * <p>数据由 {@link Settings} 持久化（最多 8 条，LRU）。每次搜索成功后由 MainActivity 调
 * {@link Settings#addRecentQuery(String)} 写入。
 *
 * <p>仅空闲态显示（{@link #show(List)} 接收的列表非空时）。
 */
public final class RecentQueriesController {

    /** 最大保存条数。 */
    public static final int MAX = 8;

    public interface OnPickListener {
        void onPick(@NonNull String query);
    }

    private final View root;
    private final ChipGroup chips;
    private final TextView clear;

    public RecentQueriesController(@NonNull View root) {
        this.root = root;
        this.chips = root.findViewById(R.id.recentChips);
        this.clear = root.findViewById(R.id.recentClear);
        clear.setOnClickListener(v -> {
            // 清空回调由宿主提供，避免本类反向依赖 Settings 写入
            if (onClear != null) onClear.run();
        });
    }

    private Runnable onClear;

    /** 设置清空动作回调。 */
    public void setOnClear(@NonNull Runnable r) {
        this.onClear = r;
    }

    /** 用最近词条填充 Chip 行；空列表则整组隐藏。 */
    public void show(@NonNull List<String> queries, @NonNull OnPickListener listener) {
        chips.removeAllViews();
        if (queries.isEmpty()) {
            root.setVisibility(View.GONE);
            return;
        }
        root.setVisibility(View.VISIBLE);
        Context ctx = root.getContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (String q : queries) {
            Chip c = (Chip) inflater.inflate(R.layout.item_recent_chip, chips, false);
            c.setText(q);
            c.setContentDescription(ctx.getString(R.string.cd_recent_query, q));
            c.setOnClickListener(v -> listener.onPick(q));
            chips.addView(c);
        }
    }

    /** 倒序展示（最新在前）。 */
    @NonNull
    public static List<String> reversed(@NonNull List<String> in) {
        Collections.reverse(in);
        return in;
    }
}