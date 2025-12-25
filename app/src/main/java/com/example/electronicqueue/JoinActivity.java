package com.example.electronicqueue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import com.example.electronicqueue.api.ApiClient;
import com.example.electronicqueue.model.JoinResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JoinActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText nameInput;
    private Button joinBtn;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.app_name));

        nameInput = findViewById(R.id.nameInput);
        joinBtn = findViewById(R.id.joinBtn);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.errorText);

        joinBtn.setOnClickListener(v -> joinQueue());
    }

    private void joinQueue() {
        errorText.setText("");
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            errorText.setText("Введите имя");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        joinBtn.setEnabled(false);

        executor.execute(() -> {
            try {
                JoinResponse resp = ApiClient.join(name);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    joinBtn.setEnabled(true);

                    Intent i = new Intent(JoinActivity.this, QueueActivity.class);
                    i.putExtra("ticket", resp.ticket);
                    i.putExtra("name", name);
                    startActivity(i);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    joinBtn.setEnabled(true);
                    errorText.setText("Ошибка: " + e.getMessage());
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
