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
import android.content.DialogInterface;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;



import com.google.firebase.firestore.*;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateQuizActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String username;
    private LinearLayout quizContainer;
    private FloatingActionButton addQuizButton;
    private ImageView backButton, checkButton;
    private EditText quizTitleInput;
    private int questionCount = 0;
    private final int MAX_QUESTIONS = 50;
    private String quizId = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        quizContainer = findViewById(R.id.container_add_quiz);
        addQuizButton = findViewById(R.id.floating_add_btn);
        backButton = findViewById(R.id.back_button);
        checkButton = findViewById(R.id.save_button);
        quizTitleInput = findViewById(R.id.quiz_name);


        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        quizId = getIntent().getStringExtra("quizId");

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        username = documentSnapshot.getString("username");
                    }
                });

        if (quizId != null) {
            loadQuizData(quizId); // 🔁 Load existing quiz data
        } else {
            addQuizView(); // ➕ Add blank question for new quiz
        }

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
            if (quizTitleInput.getText().toString().trim().isEmpty()) {
                quizTitleInput.setError("Quiz title is required");
                quizTitleInput.requestFocus(); // optional: moves cursor to the field
                return;
            }


            if (validateAllQuestions()) {
                Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show();
                saveQuizToFirebase();
                finish();
            }
        });
    }

    private void saveQuizToFirebase() {
        List<Map<String, Object>> questionList = new ArrayList<>();

        for (int i = 0; i < quizContainer.getChildCount(); i++) {
            View quizItem = quizContainer.getChildAt(i);
            Spinner spinner = quizItem.findViewById(R.id.quiz_type_spinner);
            LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
            EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
            String quizType = spinner.getSelectedItem().toString().toLowerCase();

            Map<String, Object> questionData = new HashMap<>();
            questionData.put("question", questionInput.getText().toString().trim());

            if (quizType.equals("multiple choice")) {
                List<String> choices = new ArrayList<>();
                String correctAnswer = "";
                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View option = optionsContainer.getChildAt(j);
                    RadioButton rb = option.findViewById(R.id.radioOption);
                    EditText et = option.findViewById(R.id.edit_option_text);
                    String text = et.getText().toString().trim();
                    choices.add(text);
                    if (rb != null && rb.isChecked()) {
                        correctAnswer = text;
                    }
                }
                questionData.put("choices", choices);
                questionData.put("correctAnswer", correctAnswer);
            } else if (quizType.equals("enumeration")) {
                List<String> answers = new ArrayList<>();
                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View answer = optionsContainer.getChildAt(j);
                    EditText answerInput = answer.findViewById(R.id.edit_option_text);
                    answers.add(answerInput.getText().toString().trim());
                }
                questionData.put("choices", answers);
            }

            questionList.add(questionData);
        }

        Map<String, Object> quizData = new HashMap<>();
        quizData.put("title", quizTitleInput.getText().toString().trim());
        quizData.put("owner_uid", auth.getCurrentUser().getUid());
        quizData.put("owner_username", username);
        quizData.put("privacy", "public");
        quizData.put("number_of_items", questionCount);
        quizData.put("progress", 0);
        quizData.put("created_at", Timestamp.now());
        quizData.put("questions", questionList);

        if (quizId != null) {
            // 🔁 Update existing quiz
            db.collection("quiz").document(quizId)
                    .update(quizData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Quiz updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update quiz", Toast.LENGTH_SHORT).show());
        } else {
            // ➕ Create new quiz
            db.collection("quiz")
                    .add(quizData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save quiz", Toast.LENGTH_SHORT).show());
        }

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

        setupAddOptionListener(addOptionText, quizTypeSpinner, optionsContainer);
            String type = quizTypeSpinner.getSelectedItem().toString().toLowerCase();
            int currentCount = optionsContainer.getChildCount();

            if (type.equals("multiple choice")) {
                if (currentCount >= 4) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 4 options allowed", Toast.LENGTH_SHORT).show();
                    return;
                }
                View optionView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_options, null);
                RadioButton rb = optionView.findViewById(R.id.radioOption);
                EditText et = optionView.findViewById(R.id.edit_option_text);
                ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

                rb.setOnClickListener(btn -> {
                    for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                        View child = optionsContainer.getChildAt(i);
                        RadioButton other = child.findViewById(R.id.radioOption);
                        if (other != rb) other.setChecked(false);
                    }
                });

                deleteOption.setOnClickListener(btn -> optionsContainer.removeView(optionView));
                optionsContainer.addView(optionView);

            } else if (type.equals("enumeration")) {
                if (currentCount >= 15) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 15 answers allowed", Toast.LENGTH_SHORT).show();
                    return;
                }

                View answerView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_enumerations, null);
                EditText et = answerView.findViewById(R.id.edit_option_text);
                TextView numberLabel = answerView.findViewById(R.id.enumeration_number);
                ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);

                numberLabel.setText(String.valueOf(currentCount + 1));
                deleteAnswer.setOnClickListener(btn -> {
                    optionsContainer.removeView(answerView);
                    renumberEnumerationInputs(optionsContainer);
                });

                optionsContainer.addView(answerView);

        };


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
                } else {
                    // optionally add one blank enum if you want
                    View answerView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_enumerations, null);
                    optionsContainer.addView(answerView);
                    renumberEnumerationInputs(optionsContainer);
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
            if (container.getChildCount() < 2) {
                Toast.makeText(this, "A question must have at least 2 options", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(optionView);
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
            EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
            String quizType = spinner.getSelectedItem().toString().toLowerCase();

            if (questionInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Each question must have text", Toast.LENGTH_SHORT).show();
                return false;
            }

            if (quizType.equals("multiple choice")) {
                if (optionsContainer.getChildCount() < 2) {
                    Toast.makeText(this, "Each multiple choice question must have at least 2 options", Toast.LENGTH_SHORT).show();
                    return false;
                }

                boolean hasSelected = false;

                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View option = optionsContainer.getChildAt(j);
                    RadioButton rb = option.findViewById(R.id.radioOption);
                    EditText et = option.findViewById(R.id.edit_option_text);
                    if (et == null || et.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, "All multiple choice options must be filled", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (rb != null && rb.isChecked()) {
                        hasSelected = true;
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
                    if (answerInput == null || answerInput.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, "All enumeration answers must be filled", Toast.LENGTH_SHORT).show();
                        return false;
                    } else {
                        hasInput = true;
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
    private void renumberEnumerationInputs(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            TextView numberLabel = child.findViewById(R.id.enumeration_number);
            if (numberLabel != null) {
                numberLabel.setText(String.valueOf(i + 1));
            }
        }
    }
    private void loadQuizData(String quizId) {
        db.collection("quiz").document(quizId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        quizTitleInput.setText(documentSnapshot.getString("title"));

                        List<Map<String, Object>> questions = (List<Map<String, Object>>) documentSnapshot.get("questions");
                        if (questions != null) {
                            for (Map<String, Object> question : questions) {
                                addQuizViewFromData(question);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load quiz data", Toast.LENGTH_SHORT).show());
    }
    private void addQuizViewFromData(Map<String, Object> questionData) {
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

        EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
        TextView addOptionText = quizItem.findViewById(R.id.add_option_text);
        setupAddOptionListener(addOptionText, quizTypeSpinner, optionsContainer);


        String questionText = (String) questionData.get("question");
        questionInput.setText(questionText);

        List<String> choices = (List<String>) questionData.get("choices");
        String correctAnswer = (String) questionData.get("correctAnswer");

        boolean isMultipleChoice = correctAnswer != null;

        quizTypeSpinner.post(() -> {
            AdapterView.OnItemSelectedListener listener = quizTypeSpinner.getOnItemSelectedListener();
            if (listener != null) {
                listener.onItemSelected(quizTypeSpinner, quizTypeSpinner.getSelectedView(), quizTypeSpinner.getSelectedItemPosition(), 0);
            }
        });

        optionsContainer.removeAllViews();

        if (choices != null) {
            for (String choice : choices) {
                if (isMultipleChoice) {
                    View optionView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_options, null);
                    RadioButton rb = optionView.findViewById(R.id.radioOption);
                    EditText et = optionView.findViewById(R.id.edit_option_text);
                    ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

                    et.setText(choice);
                    rb.setChecked(choice.equals(correctAnswer));

                    rb.setOnClickListener(v -> {
                        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                            View child = optionsContainer.getChildAt(i);
                            RadioButton other = child.findViewById(R.id.radioOption);
                            if (other != rb) other.setChecked(false);
                        }
                    });

                    deleteOption.setOnClickListener(v -> {
                        optionsContainer.removeView(optionView);
                    });

                    optionsContainer.addView(optionView);

                } else {
                    View answerView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_enumerations, null);
                    EditText et = answerView.findViewById(R.id.edit_option_text);
                    TextView numberLabel = answerView.findViewById(R.id.enumeration_number);
                    ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);

                    et.setText(choice);
                    numberLabel.setText(String.valueOf(optionsContainer.getChildCount() + 1));
                    deleteAnswer.setOnClickListener(v -> {
                        optionsContainer.removeView(answerView);
                        renumberEnumerationInputs(optionsContainer);
                    });

                    optionsContainer.addView(answerView);
                }
            }
        }

        ImageButton deleteBtn = quizItem.findViewById(R.id.delete_question_button);
        deleteBtn.setOnClickListener(v -> {
            quizContainer.removeView(quizItem);
            questionCount--;
        });

        quizTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Ignore change during loading
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        quizContainer.addView(quizItem);
    }
    private void setupAddOptionListener(TextView addOptionText, Spinner quizTypeSpinner, LinearLayout optionsContainer) {
        addOptionText.setOnClickListener(v -> {
            String type = quizTypeSpinner.getSelectedItem().toString().toLowerCase();
            int currentCount = optionsContainer.getChildCount();

            if (type.equals("multiple choice")) {
                if (currentCount >= 4) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 4 options allowed", Toast.LENGTH_SHORT).show();
                    return;
                }

                View optionView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_options, null);
                RadioButton rb = optionView.findViewById(R.id.radioOption);
                EditText et = optionView.findViewById(R.id.edit_option_text);
                ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

                rb.setOnClickListener(btn -> {
                    for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                        View child = optionsContainer.getChildAt(i);
                        RadioButton other = child.findViewById(R.id.radioOption);
                        if (other != rb) other.setChecked(false);
                    }
                });

                deleteOption.setOnClickListener(btn -> optionsContainer.removeView(optionView));
                optionsContainer.addView(optionView);

            } else if (type.equals("enumeration")) {
                if (currentCount >= 15) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 15 answers allowed", Toast.LENGTH_SHORT).show();
                    return;
                }

                View answerView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_enumerations, null);
                EditText et = answerView.findViewById(R.id.edit_option_text);
                TextView numberLabel = answerView.findViewById(R.id.enumeration_number);
                ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);

                numberLabel.setText(String.valueOf(currentCount + 1));
                deleteAnswer.setOnClickListener(btn -> {
                    optionsContainer.removeView(answerView);
                    renumberEnumerationInputs(optionsContainer);
                });

                optionsContainer.addView(answerView);
            }
        });
    }


}
