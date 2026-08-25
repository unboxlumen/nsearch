package com.unbox.nsearch;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.ui.AdvancedSearchSheet;
import com.unbox.nsearch.ui.IndexProgressCard;
import com.unbox.nsearch.ui.SearchBoxController;
import com.unbox.nsearch.ui.SearchResultAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;
import com.unbox.nsearch.util.FileOpener;
import com.unbox.nsearch.util.PermissionHelper;
import com.unbox.nsearch.util.SearchExecutor;

import java.util.List;

/**
 * 主屏幕 Activity。
 *
 * <p>本类在重构后只承担「装配」职责,业务细节都已下沉：
 * <ul>
 *   <li>搜索框防抖/清空  → {@link SearchBoxController}</li>
 *   <li>索引进度详情卡/工具栏进度环 → {@link IndexProgressCard}</li>
 *   <li>高级搜索底部抽屉  → {@link AdvancedSearchSheet}</li>
 *   <li>权限申请          → {@link PermissionHelper}</li>
 *   <li>搜索执行          → {@link SearchEngine} + {@link SearchExecutor}</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity
        implements SearchResultAdapter.OnItemClick,
        PermissionHelper.Callback {

    private IndexController controller;
    private Settings settings;
    private LuceneManager km;
    private SearchResultAdapter adapter;

    // ═══ 搜索区视图 ═══
    private EditText searchBox;
    private ImageView btnClear;
    private ImageButton btnAdvanced;

    // ═══ 结果区视图 ═══
    private TextView resultCount;
    private TextView emptyView;

    // ═══ 业务子控制器 ═══
    private SearchBoxController searchBoxController;
    private IndexProgressCard progressCard;
    private PermissionHelper permissionHelper;

    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.app_name);

        controller = IndexController.get(this);
        settings = new Settings(this);

        // ── 绑定搜索区 ──
        searchBox = findViewById(R.id.searchBox);
        btnClear = findViewById(R.id.btnClear);
        btnAdvanced = findViewById(R.id.btnAdvanced);

        // ── 绑定结果区 ──
        resultCount = findViewById(R.id.resultCount);
        emptyView = findViewById(R.id.emptyView);

        // ── 结果列表 ──
        RecyclerView results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        results.addItemDecoration(new SpaceItemDecoration(8, 0, this));
        adapter = new SearchResultAdapter(this);
        results.setAdapter(adapter);

        // 尝试打开索引（失败则搜索不可用,可到设置里删除索引重试）
        try {
            km = LuceneManager.get(this);
        } catch (java.io.IOException e) {
            km = null;
            Toast.makeText(this, "索引打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // ── 子控制器 ──
        permissionHelper = new PermissionHelper(this, this);

        searchBoxController = new SearchBoxController(searchBox, btnClear, this::runSearch);

        // progressCard 在 onCreateOptionsMenu 中绑定到工具栏 ring / action 视图,
        // 但它本身也可独立工作(无 ring 时退化)。
        progressCard = new IndexProgressCard(
                findViewById(android.R.id.content),
                /* indexProgressAction */ null,
                /* ring */ null,
                /* ringPct */ null,
                resultCount,
                this::togglePause,
                this::indexedInfo);

        btnAdvanced.setOnClickListener(v -> AdvancedSearchSheet.show(this, settings,
                (mode, synonym) -> {
                    if (!lastQuery.isEmpty()) runSearch(lastQuery);
                }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem indexMenuItem = menu.findItem(R.id.action_index);
        MenuItem indexProgressMenuItem = menu.findItem(R.id.action_index_progress);
        View indexProgressAction = indexProgressMenuItem != null ? indexProgressMenuItem.getActionView() : null;
        CircularProgressIndicator ring = null;
        TextView ringPct = null;
        if (indexProgressAction != null) {
            ring = indexProgressAction.findViewById(R.id.ring);
            ringPct = indexProgressAction.findViewById(R.id.ringPct);
            indexProgressAction.setVisibility(View.GONE);
            indexProgressAction.setOnClickListener(v -> toggleIndexDetail());
            indexMenuItem.setVisible(true);
            indexProgressMenuItem.setVisible(false);
        }
        // 重新构造一个绑定了 ring 的 progressCard,替换之前的空 ring 版本
        progressCard = new IndexProgressCard(
                findViewById(android.R.id.content),
                indexProgressAction,
                ring,
                ringPct,
                resultCount,
                this::togglePause,
                this::indexedInfo);
        return true;
    }

    private void runSearch(@NonNull String query) {
        lastQuery = query == null ? "" : query.trim();
        if (km == null) {
            Toast.makeText(this, "索引不可用,请到设置删除索引后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastQuery.isEmpty()) {
            adapter.clear();
            updateIdleView();
            return;
        }
        final String q = lastQuery;
        SearchExecutor.get().submit(() -> {
            List<SearchResult> res = SearchEngine.search(MainActivity.this, km, q, settings, 200);
            SearchExecutor.get().postToMain(() -> showResults(res, q));
        });
    }

    private void showResults(@NonNull List<SearchResult> res, @NonNull String q) {
        if (q.equals(lastQuery)) {
            adapter.setItems(res);
            if (res.isEmpty()) {
                resultCount.setText(R.string.empty_results);
                emptyView.setVisibility(View.VISIBLE);
                emptyView.setText(R.string.empty_results);
            } else {
                resultCount.setText(getString(R.string.found_results, res.size()));
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    private void updateIdleView() {
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(R.string.empty_hint);
        resultCount.setText(indexedInfo());
    }

    @NonNull
    private String indexedInfo() {
        int n = (km != null) ? km.docCount() : IndexDatabase.get(this).countDone();
        return getString(R.string.files_total, n);
    }

    // ---------------- 索引控制 ----------------

    private void togglePause() {
        if (controller.isPaused()) controller.resume();
        else controller.pause();
    }

    private void toggleIndexDetail() {
        progressCard.toggleDetail();
    }

    // ---------------- 菜单 ----------------

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_index) {
            permissionHelper.request();
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        permissionHelper.onActivityResult(requestCode);
    }

    // 兼容新旧 AndroidX 签名：使用 Activity 基类 4 参数版本
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults,
                                           @SuppressWarnings("unused") int extraData) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, extraData);
        permissionHelper.onRequestPermissionsResult(requestCode, grantResults);
    }

    // ---------------- PermissionHelper.Callback ----------------

    @Override
    public void onGranted() {
        controller.requestStart();
    }

    @Override
    public void onDenied(boolean launchedSettings) {
        if (launchedSettings) return;
        Snackbar.make(findViewById(android.R.id.content), R.string.no_permission, Snackbar.LENGTH_LONG)
                .setAction(R.string.go_settings, v -> permissionHelper.openAppSettings())
                .show();
    }

    // ---------------- 打开文件 ----------------

    @Override
    public void onOpen(SearchResult r) {
        FileOpener.open(this, r);
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(progressCard);
        resultCount.setText(indexedInfo());
    }

    @Override
    protected void onStop() {
        controller.removeListener(progressCard);
        super.onStop();
    }
}