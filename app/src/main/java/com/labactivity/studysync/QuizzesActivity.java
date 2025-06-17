package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class QuizzesActivity extends AppCompatActivity {

    private ImageView addQuizButton;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quizzes);

        addQuizButton = findViewById(R.id.add_button);
        backButton = findViewById(R.id.back_button);

        addQuizButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizzesActivity.this, CreateQuizActivity.class);
                startActivity(intent);
            }
        });

        // button back to Home
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); //replace with an Intent to HomeActivity once created
            }
        });
    }
}
