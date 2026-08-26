package com.unbox.nsearch.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.unbox.nsearch.R;
import com.unbox.nsearch.Settings;

/**
 * 高级搜索底部抽屉：匹配精度(严格/中等/宽松) + 同义词开关。
 *
 * <p>内部封装：
 *  - 从 layout 装配并填充初始状态；
 *  - 把用户选择通过 {@link Listener} 回调给宿主；
 *  - 「设置变更立刻重搜」的副作用由宿主在 listener 里触发，本类不持有搜索执行器。
 *
 * <p>使用方式：
 * <pre>{@code
 *   AdvancedSearchSheet.show(this, settings, (mode, synonym) -> runSearch(lastQuery));
 * }</pre>
 */
public final class AdvancedSearchSheet {

    public interface Listener {
        /** mode 恒非空；synonym 仅在用户拨动了同义词开关时非空。 */
        void onSettingChanged(@NonNull Settings.SearchMode mode, @Nullable Boolean synonym);
    }

    public static void show(@NonNull Context context,
                            @NonNull Settings settings,
                            @NonNull Listener listener) {
        new AdvancedSearchSheet(context, settings, listener).show();
    }

    private final Context context;
    private final Settings settings;
    private final Listener listener;

    private AdvancedSearchSheet(@NonNull Context context,
                                @NonNull Settings settings,
                                @NonNull Listener listener) {
        this.context = context;
        this.settings = settings;
        this.listener = listener;
    }

    private void show() {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_advanced_search, null);
        sheet.setContentView(root);

        MaterialButtonToggleGroup modeGroup = root.findViewById(R.id.sheetModeGroup);
        MaterialSwitch synonymSwitch = root.findViewById(R.id.sheetSynonym);
        MaterialButton done = root.findViewById(R.id.sheetDone);

        // 还原当前状态(先设置,再挂监听,避免打开时误触发一次搜索)
        switch (settings.getSearchMode()) {
            case STRICT: modeGroup.check(R.id.sheetModeStrict); break;
            case LOOSE: modeGroup.check(R.id.sheetModeLoose); break;
            default: modeGroup.check(R.id.sheetModeMedium); break;
        }
        synonymSwitch.setChecked(settings.isSynonymEnabled());

        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            Settings.SearchMode mode = modeForId(checkedId);
            settings.setSearchMode(mode);
            listener.onSettingChanged(mode, null);
        });

        synonymSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setSynonymEnabled(isChecked);
            listener.onSettingChanged(settings.getSearchMode(), isChecked);
        });

        done.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    /**
     * 从 layout id 反查枚举；集中在一处,避免散落字符串。
     */
    @NonNull
    private static Settings.SearchMode modeForId(int checkedId) {
        if (checkedId == R.id.sheetModeStrict) return Settings.SearchMode.STRICT;
        if (checkedId == R.id.sheetModeLoose) return Settings.SearchMode.LOOSE;
        return Settings.SearchMode.MEDIUM;
    }
}
