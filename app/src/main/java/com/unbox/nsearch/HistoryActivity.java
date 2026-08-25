package com.unbox.nsearch;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.ui.HistoryAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;

import java.util.List;

public class HistoryActivity extends AppCompatActivity implements IndexController.Listener {

    private HistoryAdapter adapter;
    private TextView emptyView;
    private IndexController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_scan_history);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView list = findViewById(R.id.historyList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new SpaceItemDecoration(8, 16, this));
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        emptyView = findViewById(R.id.emptyView);

        controller = IndexController.get(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 加载历史
        List<ScanRecord> records = IndexDatabase.get(this).getAllScanRecords();
        adapter.setItems(records);
        // 注册 controller 监听,这样首页运行中的索引能实时反映到这里
        controller.addListener(this);
        // 立刻推送一次当前状态,避免错过「正在运行」那条
        adapter.updateRunning(controller.getState());
        // 空态文案(只在没有任何历史且没有正在运行的任务时显示)
        boolean showEmpty = records.isEmpty() && (controller.getState().status != IndexController.Status.RUNNING
                && controller.getState().status != IndexController.Status.PAUSED);
        emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        emptyView.setText(R.string.empty_history);
    }

    @Override
    protected void onPause() {
        controller.removeListener(this);
        super.onPause();
    }

    // ---------------- IndexController.Listener ----------------

    @Override
    public void onProgress(@NonNull IndexController.State s) {
        if (adapter != null) adapter.updateRunning(s);
    }

    @Override
    public void onStatus(@NonNull IndexController.State s) {
        if (adapter != null) adapter.updateRunning(s);
    }
}