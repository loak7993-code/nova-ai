package com.nova.ai;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nova.ai.data.ChatStorage;
import com.nova.ai.data.Settings;

public class SettingsActivity extends AppCompatActivity {

    private EditText apiBase, apiKey, systemPrompt, searchUrl;
    private AutoCompleteTextView model;
    private SeekBar temperatureBar;
    private TextView temperatureValue;
    private Switch streamToggle;
    private Button saveButton, clearAllButton;

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

        apiBase.setText(s.apiBase);
        apiKey.setText(s.apiKey);
        systemPrompt.setText(s.systemPrompt);
        streamToggle.setChecked(s.stream);
        searchUrl.setText(s.searchUrl != null ? s.searchUrl : "");

        String[] models = com.nova.ai.data.ModelRegistry.ids();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, models);
        model.setAdapter(adapter);
        model.setText(s.model != null ? s.model : "zhipuai/glm-5.2", false);

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
    }

    private void save() {
        Settings s = Settings.get();
        s.apiBase = TextUtils.isEmpty(apiBase.getText())
                ? ""
                : apiBase.getText().toString().trim();
        s.apiKey = apiKey.getText() == null ? "" : apiKey.getText().toString().trim();
        s.model = TextUtils.isEmpty(model.getText())
                ? "zhipuai/glm-5.2" : model.getText().toString().trim();
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
