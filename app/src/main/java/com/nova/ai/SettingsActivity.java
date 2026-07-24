package com.nova.ai;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nova.ai.data.ChatStorage;
import com.nova.ai.data.ProviderManager;
import com.nova.ai.data.ProviderProfile;
import com.nova.ai.data.Settings;
import com.nova.ai.net.ProviderFetcher;
import com.nova.ai.ui.PickerAdapter;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private EditText apiBase, apiKey, systemPrompt, searchUrl;
    private AutoCompleteTextView model;
    private SeekBar temperatureBar;
    private TextView temperatureValue;
    private Switch streamToggle;
    private Button saveButton, clearAllButton, chooseProviderButton, fetchModelsButton, myProvidersButton;
    private List<String> fetchedModels = null;

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
        myProvidersButton = findViewById(R.id.btnMyProviders);

        apiBase.setText(s.apiBase);
        apiKey.setText(s.apiKey);
        systemPrompt.setText(s.systemPrompt);
        streamToggle.setChecked(s.stream);
        searchUrl.setText(s.searchUrl != null ? s.searchUrl : "");

        updateModelAdapter(Settings.modelsForActive());
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
        clearAllButton.setOnClickListener(v -> showClearConfirm());
        chooseProviderButton.setOnClickListener(v -> showProviderPicker());
        fetchModelsButton.setOnClickListener(v -> fetchModelsFromProvider());
        myProvidersButton.setOnClickListener(v -> showMyProviders());
    }

    private void updateModelAdapter(String[] ids) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, ids);
        model.setAdapter(adapter);
    }

    private BottomSheetDialog showLoadingSheet(String message) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_loading);
        TextView text = dialog.findViewById(R.id.bsLoadingText);
        if (text != null) text.setText(message);
        dialog.setCancelable(false);
        dialog.show();
        return dialog;
    }

    private void showMyProviders() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_picker);

        TextView title = sheet.findViewById(R.id.bsTitle);
        EditText search = sheet.findViewById(R.id.bsSearch);
        RecyclerView rv = sheet.findViewById(R.id.bsRecyclerView);

        if (title != null) title.setText(R.string.my_providers);
        if (search != null) search.setVisibility(android.view.View.GONE);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            String activeId = ProviderManager.get().activeId();
            List<ProviderProfile> profiles = ProviderManager.get().all();

            PickerAdapter adapter = new PickerAdapter((item, pos) -> {
                if ("__add__".equals(item.tag)) {
                    sheet.dismiss();
                    showProviderPicker();
                    return;
                }
                Settings.switchProvider(item.tag);
                reloadFromActive();
                Toast.makeText(this, getString(R.string.switch_to) + " " + item.name, Toast.LENGTH_SHORT).show();
                sheet.dismiss();
            });

            List<PickerAdapter.Item> items = new ArrayList<>();
            for (ProviderProfile p : profiles) {
                String sub = p.apiBase;
                if (p.models != null && !p.models.isEmpty()) sub += "  ·  " + p.models.size() + " models";
                items.add(new PickerAdapter.Item(p.name, sub, p.id, p.id.equals(activeId)));
            }
            items.add(new PickerAdapter.Item(getString(R.string.add_provider), "", "__add__", false));
            adapter.setItems(items);
            rv.setAdapter(adapter);
        }
        sheet.show();
    }

    private void reloadFromActive() {
        ProviderProfile p = ProviderManager.get().active();
        if (p == null) return;
        apiBase.setText(p.apiBase);
        apiKey.setText(p.apiKey);
        String[] models = Settings.modelsForActive();
        updateModelAdapter(models);
        String activeModel = p.activeModel != null && !p.activeModel.isEmpty() ? p.activeModel : "big-pickle";
        model.setText(activeModel, false);
        fetchedModels = p.models;
    }

    private void showProviderPicker() {
        BottomSheetDialog loading = showLoadingSheet(getString(R.string.fetching_providers));

        ProviderFetcher.fetchProviders(new ProviderFetcher.Callback<List<ProviderFetcher.ProviderInfo>>() {
            @Override
            public void onSuccess(List<ProviderFetcher.ProviderInfo> providers) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    showProviderSheet(providers);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.err_network, message), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showProviderSheet(List<ProviderFetcher.ProviderInfo> providers) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_picker);

        TextView title = sheet.findViewById(R.id.bsTitle);
        EditText search = sheet.findViewById(R.id.bsSearch);
        RecyclerView rv = sheet.findViewById(R.id.bsRecyclerView);

        if (title != null) title.setText(R.string.select_provider);
        if (search != null) search.setHint(R.string.search_providers);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            PickerAdapter adapter = new PickerAdapter((item, pos) -> {
                String name = item.name;
                String url = item.tag;
                ProviderProfile p = ProviderManager.get().findOrCreate(name, url, apiKey.getText().toString().trim());
                if (ProviderManager.get().all().size() == 1) {
                    ProviderManager.get().setActive(p.id);
                }
                ProviderManager.get().setActive(p.id);
                Settings.switchProvider(p.id);
                reloadFromActive();
                Toast.makeText(this, name + ": " + url, Toast.LENGTH_SHORT).show();
                sheet.dismiss();
            });

            List<PickerAdapter.Item> items = new ArrayList<>();
            for (ProviderFetcher.ProviderInfo p : providers) {
                boolean already = false;
                for (ProviderProfile saved : ProviderManager.get().all()) {
                    if (saved.apiBase.equals(p.apiUrl)) { already = true; break; }
                }
                String sub = p.apiUrl + (already ? "  ✓" : "");
                items.add(new PickerAdapter.Item(p.name, sub, p.apiUrl, false));
            }
            adapter.setItems(items);
            rv.setAdapter(adapter);

            if (search != null) {
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable e) {
                        adapter.filter(e.toString());
                    }
                });
            }
        }
        sheet.show();
    }

    private void fetchModelsFromProvider() {
        String base = apiBase.getText() != null ? apiBase.getText().toString().trim() : "";
        String key = apiKey.getText() != null ? apiKey.getText().toString().trim() : "";

        if (TextUtils.isEmpty(base)) {
            Toast.makeText(this, "Enter API base URL first", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog loading = showLoadingSheet(getString(R.string.fetching_models));

        ProviderFetcher.fetchModels(base, key, new ProviderFetcher.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> models) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    fetchedModels = models;
                    String[] arr = models.toArray(new String[0]);
                    updateModelAdapter(arr);
                    ProviderProfile active = ProviderManager.get().active();
                    if (active != null) {
                        ProviderManager.get().updateModels(active.id, models, model.getText().toString());
                    }
                    showModelSheet(models);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            R.string.no_models, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showModelSheet(List<String> models) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_picker);

        TextView title = sheet.findViewById(R.id.bsTitle);
        EditText search = sheet.findViewById(R.id.bsSearch);
        RecyclerView rv = sheet.findViewById(R.id.bsRecyclerView);

        if (title != null) title.setText(R.string.select_model);
        if (search != null) search.setHint(R.string.search_models);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            String currentModel = model.getText() != null ? model.getText().toString() : "";
            PickerAdapter adapter = new PickerAdapter((item, pos) -> {
                model.setText(item.name, false);
                ProviderProfile active = ProviderManager.get().active();
                if (active != null) {
                    ProviderManager.get().updateModels(active.id,
                            active.models != null ? active.models : new ArrayList<>(),
                            item.name);
                }
                sheet.dismiss();
            });

            List<PickerAdapter.Item> items = new ArrayList<>();
            for (String m : models) {
                items.add(new PickerAdapter.Item(m, "", m, m.equals(currentModel)));
            }
            adapter.setItems(items);
            rv.setAdapter(adapter);

            if (search != null) {
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable e) {
                        adapter.filter(e.toString());
                    }
                });
            }
        }
        sheet.show();
    }

    private void showClearConfirm() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_confirm);

        TextView title = sheet.findViewById(R.id.bsConfirmTitle);
        TextView message = sheet.findViewById(R.id.bsConfirmMessage);
        Button cancel = sheet.findViewById(R.id.bsBtnCancel);
        Button confirm = sheet.findViewById(R.id.bsBtnConfirm);

        if (title != null) title.setText(R.string.clear_all);
        if (message != null) message.setText(R.string.confirm_clear);
        if (cancel != null) cancel.setOnClickListener(v -> sheet.dismiss());
        if (confirm != null) confirm.setOnClickListener(v -> {
            ChatStorage.get().clearAll();
            ChatStorage.get().create();
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            sheet.dismiss();
            finish();
        });
        sheet.show();
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

        ProviderProfile active = ProviderManager.get().active();
        if (active != null) {
            active.apiBase = s.apiBase;
            active.apiKey = s.apiKey;
            active.activeModel = s.model;
            if (fetchedModels != null) {
                ProviderManager.get().updateModels(active.id, fetchedModels, s.model);
            }
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
