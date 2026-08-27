package com.unbox.nsearch;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.ui.EmptyStateView;
import com.unbox.nsearch.ui.IndexDetailSheet;
import com.unbox.nsearch.ui.IndexIndicatorView;
import com.unbox.nsearch.ui.RecentQueriesController;
import com.unbox.nsearch.ui.SearchBoxController;
import com.unbox.nsearch.ui.SearchResultAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;
import com.unbox.nsearch.util.FileOpener;
import com.unbox.nsearch.util.PermissionHelper;
import com.unbox.nsearch.util.SearchExecutor;

import java.util.List;

/**
 * 主屏幕 Activity（极简风重构版）。
 *
 * <p>本类在重构后只承担「装配」职责,业务细节都已下沉：
 * <ul>
 *   <li>搜索框防抖/清空  → {@link SearchBoxController}</li>
 *   <li>索引进度入口    → {@link IndexIndicatorView}（AppBar 单点） + {@link IndexDetailSheet}（详情）</li>
 *   <li>匹配精度 Chip 行 → {@link ChipGroup}（与 Settings 同步）</li>
 *   <li>最近搜索词      → {@link RecentQueriesController}</li>
 *   <li>空 / 错误三态    → {@link EmptyStateView}</li>
 *   <li>权限申请         → {@link PermissionHelper}</li>
 *   <li>搜索执行         → {@link SearchEngine} + {@link SearchExecutor}</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity
        implements SearchResultAdapter.OnItemClick,
        PermissionHelper.Callback,
        IndexController.Listener {

    /** 单次搜索最多返回的结果数。 */
    private static final int MAX_RESULTS = 200;

    private IndexController controller;
    private Settings settings;
    private LuceneManager km;
    private SearchResultAdapter adapter;

    // ═══ 搜索区视图 ═══
    private EditText searchBox;
    private ImageView btnClear;

    // ═══ 结果区视图 ═══
    private TextView resultCount;
    private RecyclerView results;
    private EmptyStateView emptyState;
    private View recentGroup;

    // ═══ AppBar 视图 ═══
    private IndexIndicatorView indexIndicator;
    private LinearProgressIndicator appBarProgress;
    @Nullable private IndexDetailSheet activeDetailSheet;

    // ═══ 业务子控制器 ═══
    private SearchBoxController searchBoxController;
    private RecentQueriesController recentQueriesController;
    private PermissionHelper permissionHelper;

    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── AppBar ──
        // 不再使用 MaterialToolbar（已被新大标题 AppBar 替代）；保留 R.id.toolbar 作为占位。
        View toolbarPlaceholder = findViewById(R.id.toolbar);
        if (toolbarPlaceholder != null) toolbarPlaceholder.setVisibility(View.GONE);
        indexIndicator = findViewById(R.id.indexIndicator);
        appBarProgress = findViewById(R.id.appBarProgress);
        if (indexIndicator != null) {
            indexIndicator.setOnClickListener(v -> onIndexIndicatorClick());
        }

        controller = IndexController.get(this);
        settings = new Settings(this);

        // ── 绑定搜索区 ──
        searchBox = findViewById(R.id.searchBox);
        btnClear = findViewById(R.id.btnClear);
        View btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) btnSearch.setOnClickListener(v -> {
            String q = searchBox.getText() == null ? "" : searchBox.getText().toString();
            lastQuery = q.trim();
            runSearch(lastQuery);
            searchBox.clearFocus();
        });

        // ── Chip 行：匹配精度 ──
        ChipGroup modeChipGroup = findViewById(R.id.modeChipGroup);
        // 还原当前状态
        switch (settings.getSearchMode()) {
            case STRICT: modeChipGroup.check(R.id.chipModeStrict); break;
            case LOOSE: modeChipGroup.check(R.id.chipModeLoose); break;
            default: modeChipGroup.check(R.id.chipModeMedium); break;
        }
        modeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            Settings.SearchMode mode;
            if (id == R.id.chipModeStrict) mode = Settings.SearchMode.STRICT;
            else if (id == R.id.chipModeLoose) mode = Settings.SearchMode.LOOSE;
            else mode = Settings.SearchMode.MEDIUM;
            settings.setSearchMode(mode);
            if (!lastQuery.isEmpty()) runSearch(lastQuery);
        });

        // ── 同义词文字按钮（替代旧底部 Switch） ──
        TextView synonymToggle = findViewById(R.id.synonymToggle);
        updateSynonymToggleUi(synonymToggle, settings.isSynonymEnabled());
        if (synonymToggle != null) {
            synonymToggle.setOnClickListener(v -> {
                boolean now = !settings.isSynonymEnabled();
                settings.setSynonymEnabled(now);
                updateSynonymToggleUi(synonymToggle, now);
                if (!lastQuery.isEmpty()) runSearch(lastQuery);
            });
        }

        // ── 绑定结果区 ──
        resultCount = findViewById(R.id.resultCount);
        emptyState = findViewById(R.id.emptyState);
        results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        results.addItemDecoration(new SpaceItemDecoration(12, 24, this));
        adapter = new SearchResultAdapter(this);
        results.setAdapter(adapter);

        // ── 最近搜索词 ──
        recentGroup = findViewById(R.id.recentQueries);
        recentQueriesController = new RecentQueriesController(recentGroup);
        recentQueriesController.setOnClear(() -> {
            settings.clearRecentQueries();
            refreshRecentQueries();
        });

        // 尝试打开索引（失败则自动自愈/重建）
        try {
            km = LuceneManager.get(this);
        } catch (java.io.IOException e) {
            km = null;
            Toast.makeText(this, "索引异常，正在自动重建…", Toast.LENGTH_LONG).show();
            controller.deleteIndex();
            permissionHelper.request();
        }

        // ── 子控制器 ──
        permissionHelper = new PermissionHelper(this, this);
        searchBoxController = new SearchBoxController(searchBox, btnClear, this::runSearch);
    }

    private void updateSynonymToggleUi(@NonNull TextView v, boolean on) {
        v.setText(R.string.settings_synonym);
        v.setAlpha(on ? 1.0f : 0.6f);
    }

    private void refreshRecentQueries() {
        if (recentQueriesController == null || recentGroup == null) return;
        List<String> queries = settings.getRecentQueries();
        // 仅在空闲态（lastQuery 为空 + 结果列表空）显示
        boolean idle = lastQuery.isEmpty() && adapter.getItemCount() == 0;
        if (idle && !queries.isEmpty()) {
            recentQueriesController.show(queries, this::pickRecent);
        } else {
            recentGroup.setVisibility(View.GONE);
        }
    }

    private void pickRecent(@NonNull String query) {
        searchBoxController.setQuery(query);
        searchBoxController.submitImmediate();
    }

    private void onIndexIndicatorClick() {
        IndexController.State s = controller.getState();
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (active) {
            if (activeDetailSheet == null) {
                activeDetailSheet = IndexDetailSheet.show(this, controller,
                        new IndexDetailSheet.Listener() {
                            @Override public void onTogglePause() { togglePause(); }
                            @Override public void onCancel() { cancelIndex(); }
                        });
            }
        } else {
            requestIndexFromToolbar();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runSearch(@NonNull String query) {
        lastQuery = query == null ? "" : query.trim();
        if (km == null) {
            Toast.makeText(this, "索引不可用,请到设置删除索引后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastQuery.isEmpty()) {
            adapter.clear();
            showIdle();
            return;
        }
        final String q = lastQuery;
        settings.addRecentQuery(q);
        SearchExecutor.get().submit(() -> {
            try {
                List<SearchResult> res = SearchEngine.search(MainActivity.this, km, q, settings, MAX_RESULTS);
                SearchExecutor.get().postToMain(() -> showResults(res, q));
            } catch (Exception e) {
                writeSearchError(e);
                SearchExecutor.get().postToMain(() -> {
                    resultCount.setText(R.string.search_failed);
                    showError();
                });
            }
        });
    }

    private void showResults(@NonNull List<SearchResult> res, @NonNull String q) {
        if (!q.equals(lastQuery)) return;
        results.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        adapter.setItems(res);
        if (res.isEmpty()) {
            resultCount.setText(R.string.empty_results);
            emptyState.applyState(EmptyStateView.State.NO_RESULTS, null);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            resultCount.setText(getString(R.string.found_results, res.size()));
            emptyState.setVisibility(View.GONE);
        }
        recentGroup.setVisibility(View.GONE);
    }

    private void showIdle() {
        emptyState.setVisibility(View.VISIBLE);
        emptyState.applyState(EmptyStateView.State.IDLE, null);
        results.setVisibility(View.GONE);
        resultCount.setText(indexedInfo());
        refreshRecentQueries();
    }

    private void showError() {
        results.setVisibility(View.GONE);
        recentGroup.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyState.applyState(EmptyStateView.State.ERROR, () -> {
            if (TextUtils.isEmpty(lastQuery)) {
                showIdle();
            } else {
                runSearch(lastQuery);
            }
        });
    }

    /** 临时诊断：把搜索异常堆栈写入私有文件，便于 adb 排查。 */
    private void writeSearchError(Throwable t) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "search_error.log");
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f));
            t.printStackTrace(pw);
            pw.close();
        } catch (Exception ignored) {
        }
    }

    /** 当前已索引文件数（优先读 Lucene 实时计数，不可用时回退 DB）。 */
    private int indexedCount() {
        return (km != null) ? km.docCount() : IndexDatabase.get(this).fileMeta().countDone();
    }

    @NonNull
    private String indexedInfo() {
        return getString(R.string.files_total, indexedCount());
    }

    // ═══════════════════════════════════════════
    // IndexController.Listener（取代旧 IndexProgressCard 的回调）
    // ═══════════════════════════════════════════

    @Override
    public void onProgress(IndexController.State s) {
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (active) {
            int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
            if (appBarProgress != null) {
                appBarProgress.setVisibility(View.VISIBLE);
                appBarProgress.setProgressCompat(pct, true);
            }
            if (indexIndicator != null) {
                indexIndicator.showActive(s.indexed, s.total,
                        s.status == IndexController.Status.PAUSED);
            }
            resultCount.setText(getString(R.string.index_active_count, s.indexed, s.total));
        } else {
            if (appBarProgress != null) {
                appBarProgress.setVisibility(View.GONE);
            }
            if (indexIndicator != null) {
                indexIndicator.showIdle(indexedCount());
            }
            resultCount.setText(indexedInfo());
        }
        if (activeDetailSheet != null) activeDetailSheet.applyState(s);
    }

    @Override
    public void onStatus(IndexController.State s) {
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (active) {
            if (indexIndicator != null) {
                indexIndicator.showActive(s.indexed, s.total,
                        s.status == IndexController.Status.PAUSED);
            }
        } else {
            if (indexIndicator != null) indexIndicator.showIdle(indexedCount());
            if (appBarProgress != null) appBarProgress.setVisibility(View.GONE);
            if (activeDetailSheet != null) {
                activeDetailSheet.applyState(s); // 触发 dismiss
                activeDetailSheet = null;
            }
            resultCount.setText(indexedInfo());
        }
    }

    // ═══════════════════════════════════════════
    // 索引控制
    // ═══════════════════════════════════════════

    private void togglePause() {
        if (controller.isPaused()) {
            controller.resume();
            Toast.makeText(this, R.string.toast_index_paused_resumed, Toast.LENGTH_SHORT).show();
        } else {
            controller.pause();
            Toast.makeText(this, R.string.toast_index_paused, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestIndexFromToolbar() {
        if (controller.getState().status == IndexController.Status.RUNNING) {
            Toast.makeText(this, R.string.toast_index_already_running, Toast.LENGTH_SHORT).show();
        } else {
            permissionHelper.request();
        }
    }

    private void cancelIndex() {
        IndexController.State s = controller.getState();
        if (s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED) {
            controller.cancel();
            Toast.makeText(this, R.string.index_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════
    // 权限回调 / 打开文件 / 生命周期
    // ═══════════════════════════════════════════

    @Override
    public void onGranted() {
        controller.requestStart();
        Toast.makeText(this, R.string.toast_index_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDenied(boolean launchedSettings) {
        if (launchedSettings) return;
        Snackbar.make(findViewById(android.R.id.content), R.string.no_permission, Snackbar.LENGTH_LONG)
                .setAction(R.string.go_settings, v -> permissionHelper.openAppSettings())
                .show();
    }

    @Override
    public void onOpen(SearchResult r) {
        FileOpener.open(this, r);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        permissionHelper.onActivityResult(requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults,
                                           @SuppressWarnings("unused") int extraData) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, extraData);
        permissionHelper.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(this);
        resultCount.setText(indexedInfo());
        // 刷新索引入口初始态
        IndexController.State s = controller.getState();
        boolean active = s.status == IndexController.Status.RUNNING
                || s.status == IndexController.Status.PAUSED;
        if (active) {
            onProgress(s);
        } else {
            if (indexIndicator != null) indexIndicator.showIdle(indexedCount());
            if (appBarProgress != null) appBarProgress.setVisibility(View.GONE);
        }
        showIdle();
    }

    @Override
    protected void onStop() {
        controller.removeListener(this);
        super.onStop();
    }
}