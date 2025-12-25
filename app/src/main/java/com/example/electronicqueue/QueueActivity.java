package com.example.electronicqueue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.material.chip.Chip;
import com.google.android.material.appbar.MaterialToolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.electronicqueue.api.ApiClient;
import com.example.electronicqueue.model.StatusResponse;
import com.example.electronicqueue.util.NotificationHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QueueActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int ticket;
    private boolean notified = false;

    private TextView ticketText;
    private TextView currentText;
    private Chip positionText;
    private Chip statusText;

    private View progressRow;
    private TextView progressHint;

    private ProgressBar progress;
    private Button refreshBtn;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ticket = getIntent().getIntExtra("ticket", -1);

        ticketText = findViewById(R.id.ticketText);
        currentText = findViewById(R.id.currentText);
        positionText = findViewById(R.id.positionText);
        statusText = findViewById(R.id.statusText);

        progressRow = findViewById(R.id.progressRow);
        progressHint = findViewById(R.id.progressHint);
        progress = findViewById(R.id.progressQueue);
        refreshBtn = findViewById(R.id.refreshBtn);

        ticketText.setText("№" + ticket);

        NotificationHelper.ensureChannel(this);
        ensureNotificationPermissionIfNeeded();

        refreshBtn.setOnClickListener(v -> refresh());

        handler.post(pollTask);
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF);
            }
        }
    }

    private void refresh() {
        progressRow.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        refreshBtn.setEnabled(false);

        executor.execute(() -> {
            try {
                StatusResponse s = ApiClient.status(ticket);

                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    progressRow.setVisibility(View.GONE);
                    refreshBtn.setEnabled(true);

                    currentText.setText("№" + s.current_ticket);
                    positionText.setText("Перед вами: " + s.position);
                    statusText.setText("Статус: " + s.status);
                    applyStatusChipStyle(s.status);
                    applyPositionChipStyle(s.position, s.status);

                    if (!notified && "CALLED".equalsIgnoreCase(s.status)) {
                        notified = true;
                        NotificationHelper.showTurnNotification(QueueActivity.this, ticket);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    progressRow.setVisibility(View.GONE);
                    refreshBtn.setEnabled(true);
                    statusText.setText("Ошибка: " + e.getMessage());
                    statusText.setChipBackgroundColorResource(R.color.eq_error);
                    statusText.setTextColor(ContextCompat.getColor(QueueActivity.this, R.color.eq_on_error));
                    statusText.setChipStrokeColorResource(R.color.eq_error);
                });
            }
        });
    }

    
    private void applyStatusChipStyle(String status) {
        if (status == null) status = "";
        String s = status.trim().toUpperCase();

        int bg;
        int text;

        switch (s) {
            case "CALLED":
                bg = R.color.eq_status_called_bg;
                text = R.color.eq_status_called_text;
                break;
            case "DONE":
                bg = R.color.eq_status_done_bg;
                text = R.color.eq_status_done_text;
                break;
            case "WAITING":
            default:
                bg = R.color.eq_status_waiting_bg;
                text = R.color.eq_status_waiting_text;
                break;
        }

        statusText.setChipBackgroundColorResource(bg);
        statusText.setTextColor(ContextCompat.getColor(this, text));
        statusText.setChipStrokeColorResource(R.color.eq_outline);
    }

    private void applyPositionChipStyle(int position, String status) {
        // Highlight "0 ahead" as an upcoming call, but keep CALLED as the strongest state
        boolean called = status != null && "CALLED".equalsIgnoreCase(status.trim());
        if (called) {
            positionText.setChipBackgroundColorResource(R.color.eq_status_called_bg);
            positionText.setTextColor(ContextCompat.getColor(this, R.color.eq_status_called_text));
        } else if (position <= 0) {
            positionText.setChipBackgroundColorResource(R.color.eq_status_waiting_bg);
            positionText.setTextColor(ContextCompat.getColor(this, R.color.eq_status_waiting_text));
        } else {
            positionText.setChipBackgroundColorResource(R.color.eq_background);
            positionText.setTextColor(ContextCompat.getColor(this, R.color.eq_on_surface));
        }
        positionText.setChipStrokeColorResource(R.color.eq_outline);
    }


@Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollTask);
        executor.shutdownNow();
    }
}
