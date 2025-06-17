package com.labactivity.studysync;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CreateQuizActivity extends AppCompatActivity {

    private LinearLayout containerAddQuiz;
    private ImageView backButton, saveButton;
    private FloatingActionButton addQuestionButton;
    private static final int MAX_QUESTIONS = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        containerAddQuiz = findViewById(R.id.container_add_quiz);
        addQuestionButton = findViewById(R.id.floating_add_btn);
        backButton = findViewById(R.id.back_button);
        saveButton = findViewById(R.id.save_button);

        addQuestionButton.setOnClickListener(v -> {
            if (containerAddQuiz.getChildCount() >= MAX_QUESTIONS) {
                Toast.makeText(this, "Maximum of 50 questions reached", Toast.LENGTH_SHORT).show();
                return;
            }

            View quizItem = LayoutInflater.from(this).inflate(R.layout.item_add_quiz, containerAddQuiz, false);
            LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);

            addDefaultOptions(optionsContainer);

            setupOptionLogic(quizItem);

            containerAddQuiz.addView(quizItem);
        });

        saveButton.setOnClickListener(v -> {
            if (validateAllQuestions()) {
                // Save logic here
                Toast.makeText(this, "Quiz saved successfully", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(v -> {
            if (containerAddQuiz.getChildCount() > 0) {
                new AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to leave making this quiz?")
                        .setPositiveButton("No", (dialog, which) -> {
                            finish();
                            startActivity(new Intent(CreateQuizActivity.this, QuizzesActivity.class));
                        })
                        .setNegativeButton("Yes", null)
                        .show();
            } else {
                finish();
            }
        });
    }

    private void addDefaultOptions(LinearLayout container) {
        for (int i = 0; i < 2; i++) {
            View optionView = LayoutInflater.from(this).inflate(R.layout.item_quiz_options, container, false);
            setupRadioBehavior(optionView, container);
            container.addView(optionView);
        }
    }

    private void setupOptionLogic(View quizItem) {
        ImageView addOptionBtn = quizItem.findViewById(R.id.add_option_text);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);

        addOptionBtn.setOnClickListener(v -> {
            if (optionsContainer.getChildCount() >= 4) {
                Toast.makeText(this, "Maximum of 4 options only", Toast.LENGTH_SHORT).show();
                return;
            }
            View optionView = LayoutInflater.from(this).inflate(R.layout.item_quiz_options, optionsContainer, false);
            setupRadioBehavior(optionView, optionsContainer);
            optionsContainer.addView(optionView);
        });
    }

    private void setupRadioBehavior(View optionView, LinearLayout container) {
        RadioButton radioButton = optionView.findViewById(R.id.radioOption);

        radioButton.setOnClickListener(v -> {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                RadioButton rb = child.findViewById(R.id.radioOption);
                rb.setChecked(false);
            }
            radioButton.setChecked(true);
        });
    }

    private boolean validateAllQuestions() {
        for (int i = 0; i < containerAddQuiz.getChildCount(); i++) {
            View questionView = containerAddQuiz.getChildAt(i);
            LinearLayout optionsContainer = questionView.findViewById(R.id.answer_choices_container);
            boolean hasChecked = false;

            for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                View opt = optionsContainer.getChildAt(j);
                RadioButton rb = opt.findViewById(R.id.radioOption);
                if (rb.isChecked()) {
                    hasChecked = true;
                    break;
                }
            }

            if (!hasChecked) {
                Toast.makeText(this, "Each question must have a correct answer selected", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (containerAddQuiz.getChildCount() == 0) {
            Toast.makeText(this, "Quiz must contain at least 1 question", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }
}
