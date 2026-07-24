package com.nova.ai;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nova.ai.data.ChatStorage;
import com.nova.ai.data.Settings;
import com.nova.ai.net.ProviderFetcher;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private EditText apiBase, apiKey, systemPrompt, searchUrl;
    private AutoCompleteTextView model;
    private SeekBar temperatureBar;
    private TextView temperatureValue;
    private Switch streamToggle;
    private Button saveButton, clearAllButton, chooseProviderButton, fetchModelsButton;
    private List<String> dynamicModels = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        Settings s = Settings.get();

        apiBase = findViewById(R.id.apiBaseUrl);
        apiKey = findViewById(R.id.apiKey);
        model = findViewById(R.id.modelSpinner);
        systemPrompt = findViewById(R.id.systemPrompt);
        searchUrl = findViewById(R.id.searchUrl);
        temperatureBar = findViewById(R.id.temperatureSlider);
        temperatureValue = findViewById(R.id.temperatureValue);
        streamToggle = findViewById(R.id.streamToggle);
        saveButton = findViewById(R.id.saveButton);
        clearAllButton = findViewById(R.id.clearAllButton);
        chooseProviderButton = findViewById(R.id.btnChooseProvider);
        fetchModelsButton = findViewById(R.id.btnFetchModels);

        apiBase.setText(s.apiBase);
        apiKey.setText(s.apiKey);
        systemPrompt.setText(s.systemPrompt);
        streamToggle.setChecked(s.stream);
        searchUrl.setText(s.searchUrl != null ? s.searchUrl : "");

        updateModelAdapter(com.nova.ai.data.ModelRegistry.ids());
        model.setText(s.model != null ? s.model : "big-pickle", false);

        float temp = s.temperature;
        if (temp < 0f) temp = 0f;
        if (temp > 2f) temp = 2f;
        int progress = Math.round(temp * 10f);
        temperatureBar.setMax(20);
        temperatureBar.setProgress(progress);
        temperatureValue.setText(String.format(java.util.Locale.US, "%.1f", temp));
        temperatureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 10f;
                temperatureValue.setText(String.format(java.util.Locale.US, "%.1f", val));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        saveButton.setOnClickListener(v -> save());
        clearAllButton.setOnClickListener(v -> confirmClear());
        chooseProviderButton.setOnClickListener(v -> showProviderPicker());
        fetchModelsButton.setOnClickListener(v -> fetchModelsFromProvider());
    }

    private void updateModelAdapter(String[] ids) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, ids);
        model.setAdapter(adapter);
    }

    private void showProviderPicker() {
        ProgressBar bar = new ProgressBar(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        container.addView(bar, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.select_provider)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        ProviderFetcher.fetchProviders(new ProviderFetcher.Callback<List<ProviderFetcher.ProviderInfo>>() {
            @Override
            public void onSuccess(List<ProviderFetcher.ProviderInfo> providers) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showProviderList(providers);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.err_network, message), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showProviderList(List<ProviderFetcher.ProviderInfo> providers) {
        EditText search = new EditText(this);
        search.setHint(R.string.search_providers);
        search.setPadding(48, 32, 48, 32);

        List<String> names = new ArrayList<>();
        for (ProviderFetcher.ProviderInfo p : providers) {
            names.add(p.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, names);
        ListView listView = new ListView(this);
        listView.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable e) {
                String q = e.toString().toLowerCase();
                List<String> filtered = new ArrayList<>();
                for (ProviderFetcher.ProviderInfo p : providers) {
                    if (p.name.toLowerCase().contains(q)) {
                        filtered.add(p.name);
                    }
                }
                adapter.clear();
                adapter.addAll(filtered);
                adapter.notifyDataSetChanged();
            }
        });

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(search);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.select_provider)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = adapter.getItem(position);
            for (ProviderFetcher.ProviderInfo p : providers) {
                if (p.name.equals(selectedName)) {
                    apiBase.setText(p.apiUrl);
                    dynamicModels = null;
                    updateModelAdapter(com.nova.ai.data.ModelRegistry.ids());
                    Toast.makeText(this, p.name + ": " + p.apiUrl, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    break;
                }
            }
        });
    }

    private void fetchModelsFromProvider() {
        String base = apiBase.getText() != null ? apiBase.getText().toString().trim() : "";
        String key = apiKey.getText() != null ? apiKey.getText().toString().trim() : "";

        if (TextUtils.isEmpty(base)) {
            Toast.makeText(this, "Enter API base URL first", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressBar bar = new ProgressBar(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        container.addView(bar, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.loading)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        ProviderFetcher.fetchModels(base, key, new ProviderFetcher.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> models) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    dynamicModels = models;
                    String[] arr = models.toArray(new String[0]);
                    updateModelAdapter(arr);
                    showModelPicker(models);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.no_models), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showModelPicker(List<String> models) {
        EditText search = new EditText(this);
        search.setHint(R.string.search_models);
        search.setPadding(48, 32, 48, 32);

        List<String> filtered = new ArrayList<>(models);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, filtered);
        ListView listView = new ListView(this);
        listView.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable e) {
                String q = e.toString().toLowerCase();
                filtered.clear();
                for (String m : models) {
                    if (m.toLowerCase().contains(q)) {
                        filtered.add(m);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(search);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.select_model)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            model.setText(selected, false);
            dialog.dismiss();
        });
    }

    private void save() {
        Settings s = Settings.get();
        s.apiBase = TextUtils.isEmpty(apiBase.getText())
                ? "https://opencode.ai/zen/v1"
                : apiBase.getText().toString().trim();
        s.apiKey = apiKey.getText() == null ? "" : apiKey.getText().toString().trim();
        s.model = TextUtils.isEmpty(model.getText())
                ? "big-pickle" : model.getText().toString().trim();
        s.systemPrompt = TextUtils.isEmpty(systemPrompt.getText())
                ? "" : systemPrompt.getText().toString().trim();
        s.temperature = temperatureBar.getProgress() / 10f;
        s.stream = streamToggle.isChecked();
        s.searchUrl = TextUtils.isEmpty(searchUrl.getText())
                ? ""
                : searchUrl.getText().toString().trim();
        s.save();
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_all)
                .setMessage(R.string.confirm_clear)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    ChatStorage.get().clearAll();
                    ChatStorage.get().create();
                    Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
