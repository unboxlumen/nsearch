package com.unbox.nsearch.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录列表的 Adapter。
 *
 * <p>支持两类 item:
 * <ul>
 *   <li>{@link Type#RUNNING}  —— 当 {@link IndexController.State#status} 是 RUNNING/PAUSED 时,
 *       在列表顶部插入一条"正在索引"虚拟行,显示当前实时进度和当前文件。
 *       这条行由 {@link HistoryActivity} 主动监听 Controller 刷新,
 *       并通过 {@link #updateRunning(IndexController.State)} 推送进来。</li>
 *   <li>{@link Type#HISTORY}  —— 已完成/历史 {@link ScanRecord}。</li>
 * </ul>
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_RUNNING = 0;
    private static final int TYPE_HISTORY = 1;

    private final List<ScanRecord> history = new ArrayList<>();
    private IndexController.State running;
    private boolean showRunning;

    public void setItems(List<ScanRecord> items) {
        history.clear();
        if (items != null) history.addAll(items);
        notifyDataSetChanged();
    }

    /**
     * 推送 IndexController 当前状态,适配器决定是否在顶部显示"正在运行"行。
     * status 为 RUNNING/PAUSED 时显示;其它状态隐藏。
     */
    public void updateRunning(@NonNull IndexController.State state) {
        boolean active = state.status == IndexController.Status.RUNNING
                || state.status == IndexController.Status.PAUSED;
        boolean changed = (showRunning != active) || (active && running != state);
        this.running = state;
        this.showRunning = active;
        if (changed) notifyDataSetChanged();
        // 同一行内数字变化时局部刷新这一行
        if (active) notifyItemChanged(0);
    }

    @Override
    public int getItemViewType(int position) {
        if (showRunning && position == 0) return TYPE_RUNNING;
        return TYPE_HISTORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        if (viewType == TYPE_RUNNING) {
            return new RunningVH(v);
        }
        return new HistoryVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (showRunning && position == 0) {
            bindRunning((RunningVH) holder, running);
            return;
        }
        int histIdx = showRunning ? position - 1 : position;
        ScanRecord r = history.get(histIdx);
        bindHistory((HistoryVH) holder, r);
    }

    private void bindRunning(@NonNull RunningVH h, @NonNull IndexController.State s) {
        h.time.setText(R.string.history_running);
        // 用 brand 色突出正在运行:用 typed value 解析 ?attr/colorPrimary
        int primary = resolveThemeColor(h.itemView, com.google.android.material.R.attr.colorPrimary);
        h.time.setTextColor(primary);
        String pct = s.total > 0 ? (s.indexed * 100L / s.total) + "%" : "0%";
        StringBuilder sb = new StringBuilder();
        sb.append(h.itemView.getContext().getString(R.string.index_active_count, s.indexed, s.total));
        sb.append(" · ").append(pct);
        if (s.currentFile != null && !s.currentFile.isEmpty()) {
            sb.append(" · ").append(s.currentFile);
        }
        if (s.status == IndexController.Status.PAUSED) {
            sb.append(" · ").append(h.itemView.getContext().getString(R.string.btn_pause));
        }
        h.summary.setText(sb.toString());
    }

    private static int resolveThemeColor(View v, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        android.view.ContextThemeWrapper ctx = new android.view.ContextThemeWrapper(v.getContext(), 0);
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void bindHistory(@NonNull HistoryVH h, @NonNull ScanRecord r) {
        h.time.setText(FormatUtil.formatDate(r.startedAt));
        // 历史 item:让 TextView 自己回到 ?textColorPrimary(布局里已设,这里只需重置状态即可)
        StringBuilder sb = new StringBuilder();
        sb.append(h.itemView.getContext().getString(R.string.files_total, r.totalFiles)).append("  ·  ");
        sb.append(h.itemView.getContext().getString(R.string.history_ok)).append(' ').append(r.indexedFiles);
        if (r.failedFiles > 0) {
            sb.append("  · ").append(h.itemView.getContext().getString(R.string.history_failed))
                    .append(' ').append(r.failedFiles);
        }
        if (r.skippedFiles > 0) {
            sb.append("  · ").append(h.itemView.getContext().getString(R.string.history_skipped))
                    .append(' ').append(r.skippedFiles);
        }
        sb.append("  · ").append(h.itemView.getContext().getString(R.string.history_duration,
                FormatUtil.formatDuration(r.durationMs)));
        h.summary.setText(sb.toString());
    }

    @Override
    public int getItemCount() {
        return history.size() + (showRunning ? 1 : 0);
    }

    /** 历史 item ViewHolder:使用 item_history 布局的默认字段。 */
    static class HistoryVH extends RecyclerView.ViewHolder {
        final TextView time, summary;

        HistoryVH(View v) {
            super(v);
            time = v.findViewById(R.id.historyTime);
            summary = v.findViewById(R.id.historySummary);
        }
    }

    /** "正在运行" item ViewHolder:复用同一布局,通过 tag/binding 区分。 */
    static class RunningVH extends RecyclerView.ViewHolder {
        final TextView time, summary;

        RunningVH(View v) {
            super(v);
            time = v.findViewById(R.id.historyTime);
            summary = v.findViewById(R.id.historySummary);
        }
    }
}