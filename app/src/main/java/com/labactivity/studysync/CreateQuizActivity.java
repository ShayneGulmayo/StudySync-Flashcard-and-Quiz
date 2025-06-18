package com.labactivity.studysync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CreateQuizActivity extends AppCompatActivity {

    private LinearLayout quizContainer;
    private FloatingActionButton addQuizButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        quizContainer = findViewById(R.id.container_add_quiz);
        addQuizButton = findViewById(R.id.floating_add_btn);

        // Add initial empty quiz item
        addQuizView();

        addQuizButton.setOnClickListener(v -> addQuizView());
    }

    private void addQuizView() {
        // Inflate the item_add_quiz.xml layout
        View quizItem = LayoutInflater.from(this).inflate(R.layout.item_add_quiz, null);

        // Set margin between quiz items
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 24); // bottom margin = 24px (adjust as needed)
        quizItem.setLayoutParams(params);

        // Delete quiz item
        ImageButton deleteBtn = quizItem.findViewById(R.id.delete_question_button);
        deleteBtn.setOnClickListener(v -> quizContainer.removeView(quizItem));

        // Handle "Add Option"
        TextView addOptionText = quizItem.findViewById(R.id.add_option_text);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);

        addOptionText.setOnClickListener(v -> {
            View optionView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_options, null);

            ImageButton deleteOptionBtn = optionView.findViewById(R.id.delete_option);
            deleteOptionBtn.setOnClickListener(btn -> optionsContainer.removeView(optionView));

            optionsContainer.addView(optionView);
        });

        quizContainer.addView(quizItem);
    }
}
