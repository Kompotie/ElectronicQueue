package com.example.electronicqueue;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.electronicqueue.api.ApiClient;
import com.example.electronicqueue.model.StatusResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QueueActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView ticketText;
    private TextView currentText;
    private TextView positionText;
    private TextView statusText;
    private Button refreshBtn;
    private ProgressBar progress;

    private int ticket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        ticketText = findViewById(R.id.ticketText);
        currentText = findViewById(R.id.currentText);
        positionText = findViewById(R.id.positionText);
        statusText = findViewById(R.id.statusText);
        refreshBtn = findViewById(R.id.refreshBtn);
        progress = findViewById(R.id.progress);

        ticket = getIntent().getIntExtra("ticket", 0);
        ticketText.setText("Ваш талон: " + ticket);

        refreshBtn.setOnClickListener(v -> loadStatus());
        loadStatus();
    }

    private void loadStatus() {
        setLoading(true);
        executor.execute(() -> {
            try {
                StatusResponse r = ApiClient.status(ticket);
                runOnUiThread(() -> {
                    setLoading(false);
                    currentText.setText("Сейчас обслуживается: " + r.current_ticket);
                    positionText.setText("Перед вами: " + r.position);
                    statusText.setText("Статус: " + r.status);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    statusText.setText("Ошибка: " + e.getMessage());
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshBtn.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
