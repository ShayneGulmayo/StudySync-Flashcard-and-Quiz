package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class QuizProgressActivity extends AppCompatActivity {

    private ImageView back_button;
    private ImageView more_button;
    private Button review_questions_btn;
    private TextView retake_quiz_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_progress);


        back_button = findViewById(R.id.back_button);
        more_button = findViewById(R.id.more_button);
        review_questions_btn = findViewById(R.id.review_questions_btn);
        retake_quiz_btn = findViewById(R.id.retake_quiz_btn);

        // back button goes to QuizzesActivity
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizProgressActivity.this, QuizzesActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // retake quiz when pressed will go to QuizViewerActivity
        retake_quiz_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // review questions when pressed goes to ReviewQuestionsActivity
        review_questions_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizProgressActivity.this, ReviewQuestionsActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // more button – empty for now
        more_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Future implementation for "more" button
            }
        });
    }
}































