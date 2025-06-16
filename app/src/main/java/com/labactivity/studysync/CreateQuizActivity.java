package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CreateQuizActivity extends AppCompatActivity {

    private LinearLayout containerAddQuiz;
    private FloatingActionButton floatingActionButton;

    private ImageView backButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        containerAddQuiz = findViewById(R.id.container_add_quiz);

        floatingActionButton = findViewById(R.id.floatingActionButton);

        backButton = findViewById(R.id.back_button);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewQuizItem();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CreateQuizActivity.this, QuizzesActivity.class);
                startActivity(intent);
                finish(); // Optional: removes this activity from the back stack
            }
        });
    }

    private void addNewQuizItem() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View quizItem = inflater.inflate(R.layout.item_add_quiz, containerAddQuiz, false);
        containerAddQuiz.addView(quizItem);
    }
}