package com.example.electronicqueue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.electronicqueue.api.ApiClient;
import com.example.electronicqueue.model.JoinResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JoinActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText nameInput;
    private Button joinBtn;
    private ProgressBar progress;
    private TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        nameInput = findViewById(R.id.nameInput);
        joinBtn = findViewById(R.id.joinBtn);
        progress = findViewById(R.id.progress);
        infoText = findViewById(R.id.infoText);

        joinBtn.setOnClickListener(v -> doJoin());
    }

    private void doJoin() {
        final String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            infoText.setText("Введите имя.");
            return;
        }

        setLoading(true);
        executor.execute(() -> {
            try {
                JoinResponse r = ApiClient.join(name);
                runOnUiThread(() -> {
                    setLoading(false);
                    infoText.setText("Ваш талон: " + r.ticket);
                    Intent i = new Intent(this, QueueActivity.class);
                    i.putExtra("ticket", r.ticket);
                    startActivity(i);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    infoText.setText("Ошибка сети: " + e.getMessage());
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        joinBtn.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
