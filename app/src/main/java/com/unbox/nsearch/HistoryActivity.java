package com.unbox.nsearch;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.ui.EmptyStateView;
import com.unbox.nsearch.ui.HistoryAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;

import java.util.List;

/**
 * 历史页（极简风重构版）：
 *  - 共用 AppBar（无进度环，进度由主页 AppBar 承担）；
 *  - 列表 + 空态容器（EmptyStateView 渲染「还没有扫描记录」）；
 *  - 不再监听 IndexController（运行态信息统一上移至主页 AppBar 指示器）。
 */
public class HistoryActivity extends AppCompatActivity {

    private HistoryAdapter adapter;
    private EmptyStateView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 兼容 toolbar 占位
        View toolbarPlaceholder = findViewById(R.id.toolbar);
        if (toolbarPlaceholder != null) toolbarPlaceholder.setVisibility(View.GONE);

        // AppBar 标题
        View appBar = findViewById(R.id.appBarTitle);
        if (appBar != null) appBar.setVisibility(View.GONE); // 由 Activity 标题承担

        View appBarProgress = findViewById(R.id.appBarProgress);
        if (appBarProgress != null) appBarProgress.setVisibility(View.GONE);

        // 索引入口在历史页隐藏（运行态信息统一在主页 AppBar）
        View indexIndicator = findViewById(R.id.indexIndicator);
        if (indexIndicator != null) indexIndicator.setVisibility(View.GONE);

        RecyclerView list = findViewById(R.id.historyList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new SpaceItemDecoration(12, 24, this));
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        emptyState = findViewById(R.id.emptyState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<ScanRecord> records = IndexDatabase.get(this).scanHistory().getAll();
        adapter.setItems(records);
        // 空态：有数据时隐藏;否则显示
        boolean showEmpty = records.isEmpty();
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            emptyState.applyState(EmptyStateView.State.EMPTY, null);
        }
    }
}