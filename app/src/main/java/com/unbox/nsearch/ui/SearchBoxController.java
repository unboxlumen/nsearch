package com.unbox.nsearch.ui;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;

/**
 * 搜索框控制器：把「键盘 action + 防抖搜索 + 清空按钮显隐」集中在一处。
 *
 * <p>使用方式：宿主 Activity 在 onCreate 里 {@code new SearchBoxController(...)} 后，
 * 本类接管 EditText 文本变化监听器与 ImageView 点击；宿主通过 {@link Listener} 拿到 debounced query。
 *
 * <p>为何要 debounce：避免每个字符触发一次 Lucene 查询；300ms 是经验值（参考主流客户端）。
 */
public final class SearchBoxController {

    /** 防抖间隔，单位毫秒。暴露成常量便于单测验证。 */
    public static final long DEBOUNCE_MS = 300L;

    public interface Listener {
        /** 当用户触发「键盘搜索键」「清空按钮」「debounce 等待后」任一动作时回调。 */
        void onQueryChanged(@NonNull String query);
    }

    private final EditText searchBox;
    private final ImageView btnClear;
    private final Listener listener;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable debounceTask = new Runnable() {
        @Override public void run() {
            listener.onQueryChanged(currentQuery);
        }
    };
    private String currentQuery = "";

    public SearchBoxController(@NonNull EditText searchBox,
                               @NonNull ImageView btnClear,
                               @NonNull Listener listener) {
        this.searchBox = searchBox;
        this.btnClear = btnClear;
        this.listener = listener;
        bind();
    }

    /**
     * 主动写入文本（不触发 debounce）。供外部「清空按钮」等场景使用。
     */
    public void setQuery(@NonNull String q) {
        searchBox.setText(q);
        searchBox.setSelection(q.length());
    }

    /**
     * 主动触发一次查询（绕过 debounce），用于「点击清空按钮立刻清空列表」的场景。
     */
    public void submitImmediate() {
        handler.removeCallbacks(debounceTask);
        listener.onQueryChanged(currentQuery);
    }

    private void bind() {
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            handler.removeCallbacks(debounceTask);
            listener.onQueryChanged(currentQuery);
            return true;
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String q = s == null ? "" : s.toString();
                currentQuery = q;
                btnClear.setVisibility(TextUtils.isEmpty(q) ? View.GONE : View.VISIBLE);
                handler.removeCallbacks(debounceTask);
                handler.postDelayed(debounceTask, DEBOUNCE_MS);
            }
        });
        btnClear.setOnClickListener(v -> {
            setQuery("");
            submitImmediate();
        });
    }
}