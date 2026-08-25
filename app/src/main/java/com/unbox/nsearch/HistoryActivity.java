package com.unbox.nsearch;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.ui.HistoryAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;

import java.util.List;

public class HistoryActivity extends AppCompatActivity implements IndexController.Listener {

    private HistoryAdapter adapter;
    private TextView emptyView;
    private IndexController controller;

    // 历史页工具栏右侧的环形进度(active ring + idle icon)
    private View actionView;
    private View activeGroup;
    private CircularProgressIndicator ring;
    private TextView ringPct;
    private View idleIcon;

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
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        MenuItem item = menu.findItem(R.id.action_history_progress);
        if (item != null) {
            actionView = item.getActionView();
            if (actionView != null) {
                activeGroup = actionView.findViewById(R.id.activeGroup);
                ring = actionView.findViewById(R.id.ring);
                ringPct = actionView.findViewById(R.id.ringPct);
                idleIcon = actionView.findViewById(R.id.idleIcon);
                // 点击 action 视图:返回首页(用户用环感知进度,顺路跳转回去)
                actionView.setOnClickListener(v -> finish());
                // 用当前 state 立即驱动一次,避免出现"刚进入页面环是 0%"的空挡
                applyToolbarState(controller.getState());
            }
        }
        return true;
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
        IndexController.State s = controller.getState();
        adapter.updateRunning(s);
        applyToolbarState(s);
        // 空态文案(只在没有任何历史且没有正在运行的任务时显示)
        boolean showEmpty = records.isEmpty() && (s.status != IndexController.Status.RUNNING
                && s.status != IndexController.Status.PAUSED);
        emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        emptyView.setText(R.string.empty_history);
    }

    @Override
    protected void onPause() {
        controller.removeListener(this);
        super.onPause();
    }

    /**
     * 根据当前 {@link IndexController.State} 切换历史页工具栏右侧视图:
     *  - 活跃态:环形进度 + 百分比
     *  - 空闲态:历史 icon
     */
    private void applyToolbarState(@NonNull IndexController.State s) {
        if (activeGroup == null || idleIcon == null) return;
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (active) {
            activeGroup.setVisibility(View.VISIBLE);
            idleIcon.setVisibility(View.GONE);
            int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
            if (ring != null) ring.setProgress(pct);
            if (ringPct != null) ringPct.setText(pct + "%");
        } else {
            activeGroup.setVisibility(View.GONE);
            idleIcon.setVisibility(View.VISIBLE);
        }
    }

    // ---------------- IndexController.Listener ----------------

    @Override
    public void onProgress(@NonNull IndexController.State s) {
        if (adapter != null) adapter.updateRunning(s);
        applyToolbarState(s);
    }

    @Override
    public void onStatus(@NonNull IndexController.State s) {
        if (adapter != null) adapter.updateRunning(s);
        applyToolbarState(s);
    }
}