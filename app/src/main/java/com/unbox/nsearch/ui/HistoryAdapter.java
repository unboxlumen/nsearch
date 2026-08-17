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

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final List<ScanRecord> list = new ArrayList<>();

    public void setItems(List<ScanRecord> items) {
        list.clear();
        if (items != null) list.addAll(items);
        notifyDataSetChanged();
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
        ScanRecord r = list.get(position);
        h.time.setText(FormatUtil.formatDate(r.startedAt));
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
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView time, summary;

        VH(View v) {
            super(v);
            time = v.findViewById(R.id.historyTime);
            summary = v.findViewById(R.id.historySummary);
        }
    }
}
