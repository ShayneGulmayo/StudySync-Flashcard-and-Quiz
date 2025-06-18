package com.labactivity.studysync;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CreateQuizActivity extends AppCompatActivity {

    private LinearLayout quizContainer;
    private FloatingActionButton addQuizButton;
    private ImageView backButton, checkButton;

    private int questionCount = 0;
    private final int MAX_QUESTIONS = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        quizContainer = findViewById(R.id.container_add_quiz);
        addQuizButton = findViewById(R.id.floating_add_btn);
        backButton = findViewById(R.id.back_button);
        checkButton = findViewById(R.id.save_button);

        addQuizView();

        addQuizButton.setOnClickListener(v -> {
            if (questionCount >= MAX_QUESTIONS) {
                Toast.makeText(this, "Maximum of 50 questions reached", Toast.LENGTH_SHORT).show();
            } else {
                addQuizView();
            }
        });

        backButton.setOnClickListener(v -> {
            if (questionCount > 0) {
                showExitConfirmation();
            } else {
                finish();
            }
        });

        checkButton.setOnClickListener(v -> {
            if (validateAllQuestions()) {
                Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Quiz?")
                .setMessage("Are you sure you want to leave making this quiz?")
                .setPositiveButton("No", null)
                .setNegativeButton("Yes", (dialog, which) -> finish())
                .show();
    }

    private void addQuizView() {
        View quizItem = LayoutInflater.from(this).inflate(R.layout.item_add_quiz, null);
        questionCount++;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 24);
        quizItem.setLayoutParams(params);

        Spinner quizTypeSpinner = quizItem.findViewById(R.id.quiz_type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.quiz_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quizTypeSpinner.setAdapter(adapter);

        ImageButton deleteBtn = quizItem.findViewById(R.id.delete_question_button);
        deleteBtn.setOnClickListener(v -> {
            quizContainer.removeView(quizItem);
            questionCount--;
        });

        TextView addOptionText = quizItem.findViewById(R.id.add_option_text);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);

        for (int i = 0; i < 2; i++) {
            addOptionView(optionsContainer);
        }

        addOptionText.setOnClickListener(v -> {
            String type = quizTypeSpinner.getSelectedItem().toString().toLowerCase();
            int currentCount = optionsContainer.getChildCount();

            if (type.equals("multiple choice")) {
                if (currentCount >= 4) {
                    Toast.makeText(this, "Maximum of 4 options allowed", Toast.LENGTH_SHORT).show();
                    return;
                }
                addOptionView(optionsContainer);

            } else if (type.equals("enumeration")) {
                if (currentCount >= 15) {
                    Toast.makeText(this, "Maximum of 15 answers allowed", Toast.LENGTH_SHORT).show();
                    return;
                }
                View answerView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_enumerations, null);
                ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);
                deleteAnswer.setOnClickListener(btn -> optionsContainer.removeView(answerView));
                optionsContainer.addView(answerView);
            }
        });

        quizTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                String type = adapterView.getItemAtPosition(pos).toString().toLowerCase();
                addOptionText.setText(type.equals("enumeration") ? "Add answer" : "Add option");
                optionsContainer.removeAllViews();

                if (type.equals("multiple choice")) {
                    for (int i = 0; i < 2; i++) {
                        addOptionView(optionsContainer);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        quizContainer.addView(quizItem);
    }

    private void addOptionView(LinearLayout container) {
        View optionView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_options, null);
        RadioButton radioButton = optionView.findViewById(R.id.radioOption);
        ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

        radioButton.setOnClickListener(v -> {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                RadioButton rb = child.findViewById(R.id.radioOption);
                if (rb != null && rb != radioButton) {
                    rb.setChecked(false);
                }
            }
        });

        deleteOption.setOnClickListener(v -> {
            container.removeView(optionView);
            checkMinimumOptions(container);
        });

        container.addView(optionView);
    }

    private void checkMinimumOptions(LinearLayout container) {
        if (container.getChildCount() < 2) {
            Toast.makeText(this, "A question must have at least 2 options", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateAllQuestions() {
        int quizCount = quizContainer.getChildCount();

        if (quizCount == 0) {
            Toast.makeText(this, "You need at least one question", Toast.LENGTH_SHORT).show();
            return false;
        }

        for (int i = 0; i < quizCount; i++) {
            View quizItem = quizContainer.getChildAt(i);
            Spinner spinner = quizItem.findViewById(R.id.quiz_type_spinner);
            LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
            String quizType = spinner.getSelectedItem().toString().toLowerCase();

            if (quizType.equals("multiple choice")) {
                boolean hasSelected = false;

                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View option = optionsContainer.getChildAt(j);
                    RadioButton rb = option.findViewById(R.id.radioOption);
                    if (rb != null && rb.isChecked()) {
                        hasSelected = true;
                        break;
                    }
                }

                if (!hasSelected) {
                    Toast.makeText(this, "All questions must have a selected correct answer", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else if (quizType.equals("enumeration")) {
                boolean hasInput = false;

                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View answer = optionsContainer.getChildAt(j);
                    EditText answerInput = answer.findViewById(R.id.edit_option_text);
                    if (answerInput != null && !answerInput.getText().toString().trim().isEmpty()) {
                        hasInput = true;
                        break;
                    }
                }

                if (!hasInput) {
                    Toast.makeText(this, "Enumeration questions must have at least one answer with input", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }

        return true;
    }
}
