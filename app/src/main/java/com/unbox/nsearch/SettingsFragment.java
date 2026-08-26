package com.unbox.nsearch;

import android.content.Intent;

import com.unbox.nsearch.BuildConfig;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.unbox.nsearch.db.IndexDatabase;

public class SettingsFragment extends PreferenceFragmentCompat {

    private ActivityResultLauncher<Uri> treeLauncher;
    private IndexController controller;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        controller = IndexController.get(requireContext());

        treeLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                requireContext().getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                new Settings(requireContext()).addScopeUri(uri.toString());
                toast(R.string.add_folder);
            }
        });

        Preference about = findPreference("about_version");
        if (about != null) about.setSummary(getString(R.string.version, BuildConfig.VERSION_NAME));

        Preference add = findPreference("add_folder");
        if (add != null) add.setOnPreferenceClickListener(p -> {
            treeLauncher.launch(null);
            return true;
        });

        Preference trigger = findPreference("trigger_index");
        if (trigger != null) trigger.setOnPreferenceClickListener(p -> {
            IndexController.State s = controller.getState();
            // 先 toast 一次当前状态作为点击反馈
            toast(controller.debugSnapshot());
            if (s.status == IndexController.Status.RUNNING) {
                toast(R.string.toast_index_already_running);
            } else {
                controller.requestStart();
                toast(R.string.toast_index_started);
            }
            return true;
        });

        Preference del = findPreference("delete_index");
        if (del != null) del.setOnPreferenceClickListener(p -> {
            confirmDelete();
            return true;
        });

        Preference hist = findPreference("scan_history");
        if (hist != null) hist.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(getContext(), HistoryActivity.class));
            return true;
        });

        Preference clr = findPreference("clear_history");
        if (clr != null) clr.setOnPreferenceClickListener(p -> {
            IndexDatabase.get(requireContext()).scanHistory().clear();
            toast(R.string.clear_history);
            return true;
        });
    }

    private void toast(int resId) {
        Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_index)
                .setMessage(R.string.delete_index_confirm)
                .setPositiveButton(R.string.confirm, (d, w) -> controller.deleteIndex())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
