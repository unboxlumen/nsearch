package com.unbox.nsearch.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.R;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录列表 Adapter（极简风）：
 *  - 单一 viewType：每条历史是一个 pill 状态徽章 + 时间 + meta 行 + 路径（默认隐藏）；
 *  - 「正在运行」虚拟行已上移至主页 AppBar 进度条，本 Adapter 不再处理；
 *  - 状态徽章颜色由成功 / 失败 / 跳过 文件数决定：
 *      - 全部成功 → 绿色「成功」徽章；
 *      - 有失败 → 红色「N 个失败」徽章；
 *      - 仅跳过 → 琥珀「N 个跳过」徽章。
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final List<ScanRecord> history = new ArrayList<>();
    /** 已展开的项（点按行可切换）。 */
    private long expandedId = -1L;

    public void setItems(List<ScanRecord> items) {
        history.clear();
        if (items != null) history.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ScanRecord r = history.get(position);
        h.time.setText(FormatUtil.formatDate(r.startedAt));

        // 状态徽章（用 selector 切色）：
        //  - 全成功 → activated（绿）
        //  - 有失败 → selected（红）
        //  - 仅跳过 → 默认（琥珀）
        boolean hasFailed = r.failedFiles > 0;
        boolean hasSkipped = r.skippedFiles > 0;
        if (hasFailed) {
            h.badge.setSelected(true);
            h.badge.setActivated(false);
            h.badge.setText(h.itemView.getContext()
                    .getString(R.string.history_status_failed, r.failedFiles));
        } else if (hasSkipped) {
            h.badge.setSelected(false);
            h.badge.setActivated(false);
            h.badge.setText(h.itemView.getContext()
                    .getString(R.string.history_status_skipped, r.skippedFiles));
        } else {
            h.badge.setActivated(true);
            h.badge.setSelected(false);
            h.badge.setText(R.string.history_status_success);
        }

        // meta 行（成功 / 失败 / 跳过 / 用时）
        StringBuilder sb = new StringBuilder();
        sb.append(h.itemView.getContext().getString(R.string.files_total, r.totalFiles))
                .append("  ·  ")
                .append(h.itemView.getContext().getString(R.string.history_ok))
                .append(' ').append(r.indexedFiles);
        if (r.failedFiles > 0) {
            sb.append("  ·  ")
                    .append(h.itemView.getContext().getString(R.string.history_failed))
                    .append(' ').append(r.failedFiles);
        }
        if (r.skippedFiles > 0) {
            sb.append("  ·  ")
                    .append(h.itemView.getContext().getString(R.string.history_skipped))
                    .append(' ').append(r.skippedFiles);
        }
        sb.append("  ·  ")
                .append(h.itemView.getContext().getString(R.string.history_duration,
                        FormatUtil.formatDuration(r.durationMs)));
        h.summary.setText(sb);

        // 路径行：默认隐藏；点击行展开/收起
        if (r.trigger != null && !r.trigger.isEmpty()) {
            h.path.setText(r.trigger);
            h.path.setVisibility(expandedId == r.id ? View.VISIBLE : View.GONE);
        } else {
            h.path.setVisibility(View.GONE);
        }

        // 整行点击切换展开
        h.itemView.setOnClickListener(v -> {
            long prev = expandedId;
            expandedId = (expandedId == r.id) ? -1L : r.id;
            if (prev != -1L) notifyItemChanged(history.indexOf(byId(prev)));
            notifyItemChanged(position);
        });
    }

    private ScanRecord byId(long id) {
        for (ScanRecord r : history) if (r.id == id) return r;
        return null;
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView badge;
        final TextView summary;
        final TextView path;

        VH(View v) {
            super(v);
            time = v.findViewById(R.id.historyTime);
            badge = v.findViewById(R.id.historyStatusBadge);
            summary = v.findViewById(R.id.historySummary);
            path = v.findViewById(R.id.historyPath);
        }
    }
}