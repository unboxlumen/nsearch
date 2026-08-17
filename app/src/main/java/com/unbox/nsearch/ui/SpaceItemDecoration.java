package com.unbox.nsearch.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 列表项间距：项与项之间统一 8dp 纵向间隔，末项补 16dp 底部间距（见 UI_DESIGN_GUIDE §2/§6）。
 * 左右外边距由 RecyclerView 的 paddingStart/End（16dp）承担，这里只处理纵向，避免散落的 margin。
 */
public class SpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final int verticalGap;
    private final int bottomExtra;

    public SpaceItemDecoration(int verticalGapDp, int bottomExtraDp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        this.verticalGap = Math.round(verticalGapDp * density);
        this.bottomExtra = Math.round(bottomExtraDp * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        int count = adapter == null ? 0 : adapter.getItemCount();
        int pos = parent.getChildAdapterPosition(view);
        outRect.top = (pos == 0) ? 0 : verticalGap;
        outRect.bottom = (pos == count - 1) ? bottomExtra : 0;
    }
}
