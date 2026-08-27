package com.unbox.nsearch.ui;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.R;
import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果 Adapter（极简风）：
 *  - 文件名 + 路径 + 类型·大小 + 摘要（命中高亮）；
 *  - 行尾 24dp 打开箭头（替代旧 48dp ImageButton）；
 *  - 整卡点击 = 打开文件。
 *
 * <p>路径取自 {@link SearchResult#displayPath}；无路径时不显示该行（visibility=gone）。
 */
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

        h.name.setText(r.name);

        // 路径行（无则隐藏）
        if (r.displayPath != null && !r.displayPath.isEmpty()) {
            h.path.setVisibility(View.VISIBLE);
            h.path.setText(r.displayPath);
        } else {
            h.path.setVisibility(View.GONE);
        }

        // 元信息：类型 · 大小
        h.meta.setText(r.typeLabel + FormatUtil.SEPARATOR + FormatUtil.formatSize(r.size));

        // 摘要片段（含命中高亮 HTML）
        if (r.snippetHtml != null && !r.snippetHtml.isEmpty()) {
            h.snippet.setText(Html.fromHtml(r.snippetHtml, Html.FROM_HTML_MODE_COMPACT));
        } else {
            h.snippet.setText(R.string.snippet_unavailable);
        }

        // 点击事件：整卡 + 行尾打开箭头
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
        final TextView path;      // 路径
        final TextView meta;      // 类型 · 大小
        final TextView snippet;   // 摘要
        final ImageView open;     // 行尾打开箭头

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.resultName);
            path = v.findViewById(R.id.resultPath);
            meta = v.findViewById(R.id.resultMeta);
            snippet = v.findViewById(R.id.resultSnippet);
            open = v.findViewById(R.id.resultOpen);
        }
    }
}