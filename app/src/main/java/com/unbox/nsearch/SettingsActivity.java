package com.unbox.nsearch;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置页（极简风重构版）：
 *  - 共用 AppBar；标题栏提供「返回」按钮；
 *  - 内部用 PreferenceFragmentCompat 容器承载设置项。
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 兼容 toolbar 占位（新版无 MaterialToolbar,标题由 Activity 标题承担）
        View toolbarPlaceholder = findViewById(R.id.toolbar);
        if (toolbarPlaceholder != null) toolbarPlaceholder.setVisibility(View.GONE);

        // 索引入口在设置页隐藏
        View indexIndicator = findViewById(R.id.indexIndicator);
        if (indexIndicator != null) indexIndicator.setVisibility(View.GONE);
        View appBarProgress = findViewById(R.id.appBarProgress);
        if (appBarProgress != null) appBarProgress.setVisibility(View.GONE);
        View appBarTitle = findViewById(R.id.appBarTitle);
        if (appBarTitle != null) appBarTitle.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}