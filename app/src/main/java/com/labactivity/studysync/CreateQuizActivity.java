package com.labactivity.studysync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class CreateQuizActivity extends AppCompatActivity {

    private LinearLayout containerAddQuiz;
    private ImageView addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        containerAddQuiz = findViewById(R.id.container_add_quiz);
        addButton = findViewById(R.id.imageView); // your add_button ImageView ID

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewQuizItem();
            }
        });
    }

    private void addNewQuizItem() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View quizItemView = inflater.inflate(R.layout.item_add_quiz, containerAddQuiz, false);
        containerAddQuiz.addView(quizItemView);
    }
}