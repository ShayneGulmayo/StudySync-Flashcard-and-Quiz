package com.labactivity.studysync;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LiveQuizLeaderboardsActivity extends AppCompatActivity {

    private String roomId;
    private ImageView backBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_quiz_leaderboards);
        roomId = getIntent().getStringExtra("roomId");
        backBtn = findViewById(R.id.backButton);
        backBtn.setOnClickListener(view -> finish());
    }
}