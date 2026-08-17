package com.unbox.nsearch;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.unbox.nsearch.db.IndexDatabase;
import com.unbox.nsearch.model.ScanRecord;
import com.unbox.nsearch.ui.HistoryAdapter;
import com.unbox.nsearch.ui.SpaceItemDecoration;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private HistoryAdapter adapter;
    private TextView emptyView;

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
        List<ScanRecord> records = IndexDatabase.get(this).getAllScanRecords();
        adapter.setItems(records);
        emptyView.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        emptyView.setText(R.string.empty_history);
    }
}
