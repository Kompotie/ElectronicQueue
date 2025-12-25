package com.example.electronicqueue;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class JoinActivity extends AppCompatActivity {

    private EditText nameInput;
    private Button joinBtn;
    private TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        nameInput = findViewById(R.id.nameInput);
        joinBtn = findViewById(R.id.joinBtn);
        infoText = findViewById(R.id.infoText);

        joinBtn.setOnClickListener(v -> {
            infoText.setText("Сетевой запрос добавим на следующем этапе.");
            Intent i = new Intent(this, QueueActivity.class);
            i.putExtra("ticket", 0);
            startActivity(i);
        });
    }
}
