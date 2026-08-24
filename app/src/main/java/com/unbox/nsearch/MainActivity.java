package com.unbox.nsearch;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.SearchResult;
import com.unbox.nsearch.ui.SearchResultAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;
import com.unbox.nsearch.util.FileOpener;
import com.unbox.nsearch.util.PermissionHelper;
import com.unbox.nsearch.util.SearchExecutor;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements IndexController.Listener, SearchResultAdapter.OnItemClick,
        PermissionHelper.Callback {

    private IndexController controller;
    private Settings settings;
    private LuceneManager km;
    private SearchResultAdapter adapter;

    // ═══ 搜索区视图 ═══
    private EditText searchBox;
    private ImageView btnClear;
    private ImageButton btnAdvanced;

    // ═══ 索引进度视图 ═══
    private View progressCard;
    private ProgressBar progressBar;
    private TextView progressStats, progressCurrent, resultCount, emptyView, progressTitle;
    private MaterialButton btnPauseResume;

    // ═══ 工具栏进度圈 ═══
    private CircularProgressIndicator ring;
    private TextView ringPct;
    private View indexProgressAction;

    // ═══ 工具栏菜单项（索引按钮 / 进度圈） ═══
    private MenuItem indexMenuItem;
    private MenuItem indexProgressMenuItem;
    private boolean indexingActive = false;

    private String lastQuery = "";

    private final android.os.Handler debounce = new android.os.Handler();

    private PermissionHelper permissionHelper;

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

        // ── 绑定索引进度区 ──
        progressCard = findViewById(R.id.progressCard);
        progressBar = findViewById(R.id.progressBar);
        progressStats = findViewById(R.id.progressStats);
        progressCurrent = findViewById(R.id.progressCurrent);
        progressTitle = findViewById(R.id.progressTitle);
        resultCount = findViewById(R.id.resultCount);
        emptyView = findViewById(R.id.emptyView);
        btnPauseResume = findViewById(R.id.btnPauseResume);

        // ── 结果列表 ──
        RecyclerView results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        results.addItemDecoration(new SpaceItemDecoration(8, 0, this));
        adapter = new SearchResultAdapter(this);
        results.setAdapter(adapter);

        // 尝试打开索引（失败则搜索不可用，可到设置里删除索引重试）
        try {
            km = LuceneManager.get(this);
        } catch (java.io.IOException e) {
            km = null;
            Toast.makeText(this, "索引打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        permissionHelper = new PermissionHelper(this, this);

        initSearchBox();
        btnAdvanced.setOnClickListener(v -> openAdvancedSheet());
        btnPauseResume.setOnClickListener(v -> {
            if (controller.isPaused()) controller.resume();
            else controller.pause();
        });
    }

    /**
     * 高级搜索底部抽屉：匹配精度（严格/中等/宽松）+ 同义词扩展。
     */
    private void openAdvancedSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_advanced_search, null);
        sheet.setContentView(root);

        MaterialButtonToggleGroup modeGroup = root.findViewById(R.id.sheetModeGroup);
        MaterialSwitch synonym = root.findViewById(R.id.sheetSynonym);
        MaterialButton done = root.findViewById(R.id.sheetDone);

        // 还原当前状态（先设置，再挂监听，避免打开时误触发一次搜索）
        switch (settings.getSearchMode()) {
            case STRICT: modeGroup.check(R.id.sheetModeStrict); break;
            case LOOSE: modeGroup.check(R.id.sheetModeLoose); break;
            default: modeGroup.check(R.id.sheetModeMedium); break;
        }
        synonym.setChecked(settings.isSynonymEnabled());

        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return; // 取消选中事件忽略，只在选中时处理
            String v = checkedId == R.id.sheetModeStrict ? "strict"
                    : checkedId == R.id.sheetModeLoose ? "loose" : "medium";
            settings.putString(Settings.KEY_MODE, v);
            if (!TextUtils.isEmpty(lastQuery)) runSearch(lastQuery);
        });

        synonym.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setSynonymEnabled(isChecked);
            if (!TextUtils.isEmpty(lastQuery)) runSearch(lastQuery);
        });

        done.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    /**
     * 初始化搜索框：键盘搜索键 + 文本变化防抖 + 清空按钮显隐。
     */
    private void initSearchBox() {
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            runSearch(v.getText().toString());
            return true;
        });

        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { }
            @Override             public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString();
                // 清空按钮：有文字显示，无文字隐藏
                btnClear.setVisibility(TextUtils.isEmpty(q) ? View.GONE : View.VISIBLE);
                // 防抖搜索（300ms）
                debounce.postDelayed(() -> runSearch(q), 300);
            }
        });

        // 点击清空按钮
        btnClear.setOnClickListener(v -> {
            searchBox.setText("");
            runSearch("");
        });
    }

    private void runSearch(String query) {
        lastQuery = query == null ? "" : query.trim();
        if (km == null) {
            Toast.makeText(this, "索引不可用，请到设置删除索引后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastQuery.isEmpty()) {
            adapter.clear();
            updateIdleView();
            return;
        }
        final String q = lastQuery;
        // 用 SearchExecutor 取代之前 MainActivity 自建的 SingleThreadExecutor。
        // 搜索本身在 worker 线程完成，结果在主线程显示（保留与原版完全一致的语义）。
        SearchExecutor.get().submit(() -> {
            List<SearchResult> res = SearchEngine.search(MainActivity.this, km, q, settings, 200);
            SearchExecutor.get().postToMain(() -> showResults(res, q));
        });
    }

    private void showResults(List<SearchResult> res, String q) {
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

    private String indexedInfo() {
        int n = (km != null) ? km.docCount() : IndexDatabase.get(this).countDone();
        return getString(R.string.files_total, n);
    }

    // ---------------- IndexController.Listener ----------------

    @Override
    public void onProgress(IndexController.State s) {
        progressBar.setMax(Math.max(1, s.total));
        progressBar.setProgress(s.indexed);
        progressStats.setText(getString(R.string.index_stats, s.indexed, s.total));
        progressCurrent.setText(getString(R.string.indexing_current, s.currentFile));
        int pct = s.total > 0 ? (int) (s.indexed * 100L / s.total) : 0;
        if (ring != null) ring.setProgress(pct);
        if (ringPct != null) ringPct.setText(pct + "%");
        if (s.status != IndexController.Status.RUNNING && s.status != IndexController.Status.PAUSED) {
            resultCount.setText(indexedInfo());
        }
    }

    @Override
    public void onStatus(IndexController.State s) {
        boolean active = s.status == IndexController.Status.RUNNING || s.status == IndexController.Status.PAUSED;
        indexingActive = active;
        applyIndexUiState();
        btnPauseResume.setText(controller.isPaused() ? R.string.btn_resume : R.string.btn_pause);
        if (!active) {
            progressCard.setVisibility(View.GONE);
            resultCount.setText(indexedInfo());
        }
    }

    /** 索引进行中：隐藏「索引」按钮、显示工具栏进度圈；否则相反。 */
    private void applyIndexUiState() {
        boolean active = indexingActive;
        if (indexMenuItem != null) indexMenuItem.setVisible(!active);
        if (indexProgressMenuItem != null) indexProgressMenuItem.setVisible(active);
        if (indexProgressAction != null) indexProgressAction.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    /** 点击工具栏进度圈 → 展开/收起索引进度详情卡 */
    private void toggleIndexDetail() {
        if (progressCard != null) {
            progressCard.setVisibility(progressCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    // ---------------- 菜单 ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        indexMenuItem = menu.findItem(R.id.action_index);
        indexProgressMenuItem = menu.findItem(R.id.action_index_progress);
        indexProgressAction = indexProgressMenuItem != null ? indexProgressMenuItem.getActionView() : null;
        if (indexProgressAction != null) {
            ring = indexProgressAction.findViewById(R.id.ring);
            ringPct = indexProgressAction.findViewById(R.id.ringPct);
            indexProgressAction.setVisibility(View.GONE);
            indexProgressAction.setOnClickListener(v -> toggleIndexDetail());
        }
        applyIndexUiState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_index) {
            ensurePermissionThenIndex();
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

    private void ensurePermissionThenIndex() {
        permissionHelper.request();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
        if (launchedSettings) return; // 已经在跳系统设置页，等用户回来
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
        controller.addListener(this);
        resultCount.setText(indexedInfo());
    }

    @Override
    protected void onStop() {
        controller.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
