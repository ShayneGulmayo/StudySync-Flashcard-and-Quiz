package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuizViewActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ImageView back_button;
    private ImageView more_button;

    private TextView quizTitleView, quizOwnerView, quizQuestionTextView;
    private LinearLayout linearLayoutOptions;
    private List<Map<String, Object>> questions;
    private int currentQuestionIndex = 0;
    private String quizId;
    private boolean hasAnswered = false;
    private ImageView ownerProfile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_viewer);

        db = FirebaseFirestore.getInstance();
        back_button = findViewById(R.id.back_button);
        quizTitleView = findViewById(R.id.quiz_title);
        quizOwnerView = findViewById(R.id.owner_username);
        quizQuestionTextView = findViewById(R.id.quiz_question_txt_view);
        linearLayoutOptions = findViewById(R.id.linear_layout_options);
        ownerProfile = findViewById(R.id.owner_profile);
        more_button = findViewById(R.id.more_button);
        more_button.setOnClickListener(v -> showMoreBottomSheet());




        String photoUrl = getIntent().getStringExtra("photoUrl");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(ownerProfile);
        } else {
            ownerProfile.setImageResource(R.drawable.user_profile);
        }


        quizId = getIntent().getStringExtra("quizId");

        back_button.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizProgressActivity.class);
            intent.putExtra("quizId", quizId);
            startActivity(intent);
            finish();
        });

        if (quizId != null && !quizId.isEmpty()) {
            loadQuizFromFirestore();
        } else {
            Toast.makeText(this, "Quiz ID not found.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.more_bottom_sheet_menu, null);
        bottomSheetDialog.setContentView(view);

        TextView privacyOption = view.findViewById(R.id.privacy);

        // Fetch current privacy to update label
        db.collection("quiz").document(quizId).get().addOnSuccessListener(doc -> {
            String currentPrivacy = doc.getString("privacy");
            if ("private".equalsIgnoreCase(currentPrivacy)) {
                privacyOption.setText("Set as Public");
            } else {
                privacyOption.setText("Set as Private");
            }
        });

        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        privacyOption.setOnClickListener(v -> {
            db.collection("quiz").document(quizId).get().addOnSuccessListener(doc -> {
                String currentPrivacy = doc.getString("privacy");
                if (currentPrivacy == null) currentPrivacy = "private";

                String newPrivacy = currentPrivacy.equalsIgnoreCase("private") ? "public" : "private";

                db.collection("quiz").document(quizId)
                        .update("privacy", newPrivacy)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Quiz set to " + newPrivacy, Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to update privacy", Toast.LENGTH_SHORT).show();
                        });
            });
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.reminder).setOnClickListener(v -> {
            Toast.makeText(this, "Reminder clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.sendToChat).setOnClickListener(v -> {
            Toast.makeText(this, "Send to Chat clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.edit).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateQuizActivity.class);
            intent.putExtra("quizId", quizId);
            startActivity(intent);
        });

        view.findViewById(R.id.delete).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDeleteConfirmationDialog();
        });

        bottomSheetDialog.show();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Quiz")
                .setMessage("Are you sure you want to delete this quiz? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteQuiz())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteQuiz() {
        db.collection("quiz").document(quizId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Quiz deleted.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete quiz.", Toast.LENGTH_SHORT).show());
    }

    private void loadQuizFromFirestore() {
        FirebaseFirestore.getInstance().collection("quiz").document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        quizTitleView.setText(documentSnapshot.getString("title"));
                        quizOwnerView.setText(documentSnapshot.getString("owner_username"));

                        Object raw = documentSnapshot.get("questions");
                        questions = new ArrayList<>();

                        if (raw instanceof List<?>) {
                            for (Object item : (List<?>) raw) {
                                if (item instanceof Map) {
                                    questions.add((Map<String, Object>) item);
                                }
                            }
                        }

                        if (!questions.isEmpty()) {
                            currentQuestionIndex = 0;
                            displayNextValidQuestion();
                        } else {
                            showNoQuestionsMessage("⚠️ No valid multiple-choice questions available.");
                        }
                    } else {
                        Toast.makeText(this, "Quiz not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load quiz.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void displayNextValidQuestion() {
        hasAnswered = false;

        while (currentQuestionIndex < questions.size()) {
            Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);

            String type = currentQuestion.containsKey("type")
                    ? currentQuestion.get("type").toString().toLowerCase()
                    : detectFallbackType(currentQuestion);

            if (type.equals("multiple choice")) {
                displayMultipleChoice(currentQuestion);
                return;
            } else if (type.equals("enumeration")) {
                displayEnumeration(currentQuestion);
                return;
            } else {
                Toast.makeText(this, "Unsupported or missing question type. Skipping...", Toast.LENGTH_SHORT).show();
                currentQuestionIndex++;
            }
        }

        // No more questions
        showNoQuestionsMessage("✅ You've completed the quiz!");
    }


    private String detectFallbackType(Map<String, Object> question) {
        // This is for backward compatibility: guess type if `type` is missing
        if (question.containsKey("correctAnswer")) {
            return "multiple choice";
        } else if (question.containsKey("choices")) {
            return "enumeration";
        } else {
            return "unknown";
        }
    }


    private void displayMultipleChoice(Map<String, Object> questionData) {
        String questionText = questionData.get("question") != null ? questionData.get("question").toString() : "No question text";
        List<String> choices = null;

        try {
            choices = (List<String>) questionData.get("choices");
        } catch (ClassCastException e) {
            Toast.makeText(this, "Invalid choices format.", Toast.LENGTH_SHORT).show();
        }

        String correctAnswer = questionData.get("correctAnswer") != null ? questionData.get("correctAnswer").toString() : null;

        quizQuestionTextView.setText(questionText);
        linearLayoutOptions.removeAllViews();

        if (choices != null && correctAnswer != null) {
            for (String optionText : choices) {
                addOptionView(optionText, correctAnswer);
            }
        } else {
            Toast.makeText(this, "Invalid question format. Skipping...", Toast.LENGTH_SHORT).show();
            currentQuestionIndex++;
            displayNextValidQuestion();
        }
    }

    private void displayEnumeration(Map<String, Object> questionData) {
        String questionText = questionData.get("question") != null ? questionData.get("question").toString() : "No question text";
        quizQuestionTextView.setText(questionText);
        linearLayoutOptions.removeAllViews();

        List<String> answers = null;
        try {
            answers = (List<String>) questionData.get("answers"); // Expecting "answers": [ "one", "two", ... ]
        } catch (ClassCastException e) {
            Toast.makeText(this, "Invalid answers format.", Toast.LENGTH_SHORT).show();
        }

        if (answers != null) {
            for (int i = 0; i < answers.size(); i++) {
                View blankView = LayoutInflater.from(this).inflate(R.layout.item_quiz_enumeration_blanks, linearLayoutOptions, false);
                EditText input = blankView.findViewById(R.id.enum_answer_input);
                input.setHint("Answer " + (i + 1));
                linearLayoutOptions.addView(blankView);
            }
        } else {
            Toast.makeText(this, "Missing enumeration answers", Toast.LENGTH_SHORT).show();
        }

        // Optional: Add "Next" button for enumeration
        Button nextBtn = new Button(this);
        nextBtn.setText("Next");
        nextBtn.setOnClickListener(v -> {
            currentQuestionIndex++;
            displayNextValidQuestion();
        });
        linearLayoutOptions.addView(nextBtn);
    }


    private void addOptionView(String optionText, String correctAnswer) {
        View optionView = LayoutInflater.from(this).inflate(R.layout.item_quiz_options, linearLayoutOptions, false);
        TextView tvOption = optionView.findViewById(R.id.tvOptionText);
        MaterialCardView cardOption = optionView.findViewById(R.id.cardOption);

        if (tvOption == null || cardOption == null) {
            Toast.makeText(this, "Quiz layout error: option views missing", Toast.LENGTH_SHORT).show();
            return;
        }

        tvOption.setText(optionText);

        cardOption.setOnClickListener(v -> {
            if (hasAnswered) return;
            hasAnswered = true;

            resetOptionColors();

            if (optionText.equals(correctAnswer)) {
                cardOption.setCardBackgroundColor(ContextCompat.getColor(this, R.color.progress_green));
                Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show();
            } else {
                cardOption.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning));
                highlightCorrectAnswer(correctAnswer);
                Toast.makeText(this, "Incorrect", Toast.LENGTH_SHORT).show();
            }

            cardOption.postDelayed(() -> {
                currentQuestionIndex++;
                displayNextValidQuestion();
            }, 1000);
        });

        linearLayoutOptions.addView(optionView);
    }

    private void highlightCorrectAnswer(String correctAnswer) {
        for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
            View child = linearLayoutOptions.getChildAt(i);
            TextView tv = child.findViewById(R.id.tvOptionText);
            MaterialCardView card = child.findViewById(R.id.cardOption);
            if (tv.getText().toString().equals(correctAnswer)) {
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.progress_green));
            }
        }
    }

    private void resetOptionColors() {
        for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
            View child = linearLayoutOptions.getChildAt(i);
            MaterialCardView card = child.findViewById(R.id.cardOption);
            card.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void showNoQuestionsMessage(String message) {
        quizQuestionTextView.setText(message);
        linearLayoutOptions.removeAllViews();

        TextView endMessage = new TextView(this);
        endMessage.setText("Return to the previous screen.");
        endMessage.setTextSize(16f);
        endMessage.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
        linearLayoutOptions.addView(endMessage);
    }
}
