package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LiveQuizActivity extends AppCompatActivity {
    private ImageView backBtn;
    private CardView createPrompt, generateSet, historyBtn;
    private String roomId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_quiz);
        backBtn = findViewById(R.id.back_button);
        createPrompt = findViewById(R.id.createPrompt);
        generateSet = findViewById(R.id.generateFromSet);
        historyBtn = findViewById(R.id.historyBtn);
        roomId = getIntent().getStringExtra("roomId");

        backBtn.setOnClickListener(view -> finish());
        historyBtn.setOnClickListener(view -> {
            Intent intent = new Intent(LiveQuizActivity.this, LiveQuizzesActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });
        createPrompt.setOnClickListener(view -> {
            Intent intent = new Intent(LiveQuizActivity.this, InputLiveQuizActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });
        generateSet.setOnClickListener(view -> {
            Intent intent = new Intent(LiveQuizActivity.this, SelectSetLiveQuizActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });
    }
}