package com.unbox.nsearch.ui;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.R;
import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.VH> {

    public interface OnItemClick {
        void onOpen(SearchResult r);
    }

    private final List<SearchResult> list = new ArrayList<>();
    private final OnItemClick listener;

    public SearchResultAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void setItems(List<SearchResult> items) {
        list.clear();
        if (items != null) list.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        list.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SearchResult r = list.get(position);

        // 文件名
        h.name.setText(r.name);

        // 元信息：类型 · 大小
        h.meta.setText(r.typeLabel + FormatUtil.SEPARATOR + FormatUtil.formatSize(r.size));

        // 摘要片段（含命中高亮 HTML）
        if (r.snippetHtml != null && !r.snippetHtml.isEmpty()) {
            h.snippet.setText(Html.fromHtml(r.snippetHtml, Html.FROM_HTML_MODE_COMPACT));
        } else {
            h.snippet.setText(R.string.snippet_unavailable);
        }

        // 点击事件：整卡 + 打开按钮
        View.OnClickListener click = v -> listener.onOpen(r);
        h.itemView.setOnClickListener(click);
        h.open.setOnClickListener(click);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;      // 文件名
        final TextView meta;      // 类型 · 大小
        final TextView snippet;   // 摘要
        final ImageButton open;   // 打开按钮

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.resultName);
            meta = v.findViewById(R.id.resultMeta);
            snippet = v.findViewById(R.id.resultSnippet);
            open = v.findViewById(R.id.resultOpen);
        }
    }
}
