package com.example.electronicqueue;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class QueueActivity extends AppCompatActivity {

    private TextView ticketText;
    private TextView currentText;
    private TextView positionText;
    private TextView statusText;
    private Button refreshBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        ticketText = findViewById(R.id.ticketText);
        currentText = findViewById(R.id.currentText);
        positionText = findViewById(R.id.positionText);
        statusText = findViewById(R.id.statusText);
        refreshBtn = findViewById(R.id.refreshBtn);

        int ticket = getIntent().getIntExtra("ticket", 0);
        ticketText.setText("Ваш талон: " + ticket);
        currentText.setText("Сейчас обслуживается: -");
        positionText.setText("Перед вами: -");
        statusText.setText("Статус: -");

        refreshBtn.setOnClickListener(v -> statusText.setText("Обновление добавим позже."));
    }
}
