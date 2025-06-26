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
import java.util.HashSet;
import java.util.Set;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizViewActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ImageView back_button;
    private ImageView more_button;
    private ImageView privacyIcon;
    private TextView quizTitleView, quizOwnerView, quizQuestionTextView;
    private TextView chooseAnswerLabel;
    private String selectedAnswer = null;
    private String correctAnswer = null; // store for checking later
    private LinearLayout linearLayoutOptions;
    private List<Map<String, Object>> questions;
    private int currentQuestionIndex = 0;
    private String quizId;
    private boolean hasAnswered = false;
    private ImageView ownerProfile;
    private int score = 0;
    private List<Map<String, Object>> userAnswersList = new ArrayList<>();





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_viewer);


        Button btnCheck = findViewById(R.id.btn_check_answer);
        btnCheck.setOnClickListener(v -> {
            if (hasAnswered) return;

            Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);
            String type = currentQuestion.containsKey("type")
                    ? currentQuestion.get("type").toString().toLowerCase()
                    : detectFallbackType(currentQuestion);

            if (type.equals("multiple choice")) {
                if (selectedAnswer == null) {
                    Toast.makeText(this, "Please select an answer.", Toast.LENGTH_SHORT).show();
                    return;
                }

                hasAnswered = true;
                boolean isCorrect = selectedAnswer.equals(correctAnswer);

                for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                    View child = linearLayoutOptions.getChildAt(i);
                    TextView tv = child.findViewById(R.id.tvOptionText);
                    MaterialCardView card = child.findViewById(R.id.cardOption);

                    String option = tv.getText().toString();

                    if (option.equals(correctAnswer)) {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.vibrant_green));
                    } else if (option.equals(selectedAnswer)) {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_red));
                    } else {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
                    }
                }

                // Save result
                Map<String, Object> answer = new HashMap<>();
                answer.put("question", currentQuestion.get("question"));
                answer.put("type", "multiple choice");
                answer.put("selected", selectedAnswer);
                answer.put("correct", correctAnswer);
                answer.put("isCorrect", isCorrect);
                userAnswersList.add(answer);
                if (isCorrect) score++;

                linearLayoutOptions.postDelayed(() -> {
                    currentQuestionIndex++;
                    displayNextValidQuestion();
                    selectedAnswer = null;
                }, 3000);

            } else if (type.equals("enumeration")) {
                List<String> userAnswers = new ArrayList<>();
                for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                    View child = linearLayoutOptions.getChildAt(i);
                    EditText input = child.findViewById(R.id.enum_answer_input);
                    if (input != null) {
                        userAnswers.add(input.getText().toString().trim().toLowerCase());
                    }
                }

                List<String> correctAnswers;
                try {
                    correctAnswers = (List<String>) currentQuestion.get("choices");
                } catch (ClassCastException e) {
                    correctAnswers = new ArrayList<>();
                }

                List<String> correctLower = new ArrayList<>();
                for (String ans : correctAnswers) {
                    correctLower.add(ans.trim().toLowerCase());
                }

                Set<String> userSet = new HashSet<>(userAnswers);
                Set<String> correctSet = new HashSet<>(correctLower);

                Set<String> correctMatched = new HashSet<>(userSet);
                correctMatched.retainAll(correctSet); // only correct

                Set<String> missedAnswers = new HashSet<>(correctSet);
                missedAnswers.removeAll(userSet); // not given by user

                boolean isCorrect = correctMatched.size() == correctSet.size();
                if (isCorrect) score++;

                Map<String, Object> answer = new HashMap<>();
                answer.put("question", currentQuestion.get("question"));
                answer.put("type", "enumeration");
                answer.put("selected", userAnswers);
                answer.put("correct", correctLower);
                answer.put("isCorrect", isCorrect);
                userAnswersList.add(answer);

                hasAnswered = true;

                // ✅ Show feedback in a popup
                new AlertDialog.Builder(this)
                        .setTitle(isCorrect ? "✅ Correct!" : "❌ Not Quite")
                        .setMessage("You answered: " + userAnswers + "\n\n" +
                                "Correct answers: " + correctLower + "\n\n" +
                                "Matched: " + correctMatched + "\n" +
                                "Missed: " + missedAnswers)
                        .setPositiveButton("Next", (dialog, which) -> {
                            currentQuestionIndex++;
                            displayNextValidQuestion();
                        })
                        .setCancelable(false)
                        .show();
            }

        });




        db = FirebaseFirestore.getInstance();
        back_button = findViewById(R.id.back_button);
        quizTitleView = findViewById(R.id.quiz_title);
        quizOwnerView = findViewById(R.id.owner_username);
        quizQuestionTextView = findViewById(R.id.quiz_question_txt_view);
        linearLayoutOptions = findViewById(R.id.linear_layout_options);
        ownerProfile = findViewById(R.id.owner_profile);
        more_button = findViewById(R.id.more_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        more_button.setOnClickListener(v -> showMoreBottomSheet());
        chooseAnswerLabel = findViewById(R.id.choose_answer_label);





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
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
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

                            if ("private".equalsIgnoreCase(newPrivacy)) {
                                privacyIcon.setImageResource(R.drawable.lock);
                            } else {
                                privacyIcon.setImageResource(R.drawable.public_icon);
                            }

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

                        String currentPrivacy = documentSnapshot.getString("privacy");

                        if ("private".equalsIgnoreCase(currentPrivacy)) {
                            privacyIcon.setImageResource(R.drawable.lock);
                        } else {
                            privacyIcon.setImageResource(R.drawable.public_icon);
                        }

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

        // 🔴 This block runs when all questions are done!
        // 💾 You should save attempt, show result, or redirect

        Toast.makeText(this, "🎉 Quiz Completed!", Toast.LENGTH_LONG).show();

        saveQuizAttempt(userAnswersList, score);

        // Optionally collect final results here (score, answers)
        // For now, just go back to progress screen:
        Intent intent = new Intent(this, QuizProgressActivity.class);
        intent.putExtra("quizId", quizId);
        startActivity(intent);
        finish();
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
        chooseAnswerLabel.setText("Choose your answer");
        selectedAnswer = null;
        hasAnswered = false;

        String questionText = questionData.get("question") != null
                ? questionData.get("question").toString()
                : "No question text";

        quizQuestionTextView.setText(questionText);
        linearLayoutOptions.removeAllViews();

        correctAnswer = questionData.get("correctAnswer") != null
                ? questionData.get("correctAnswer").toString()
                : null;

        List<String> choices = null;
        try {
            choices = (List<String>) questionData.get("choices");
        } catch (ClassCastException e) {
            Toast.makeText(this, "Invalid choices format.", Toast.LENGTH_SHORT).show();
        }

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
        if (chooseAnswerLabel != null) {
            chooseAnswerLabel.setText("Type your answer"); // ✅ Changes label
        }

        String questionText = questionData.get("question") != null
                ? questionData.get("question").toString()
                : "No question text";

        quizQuestionTextView.setText(questionText);
        linearLayoutOptions.removeAllViews(); // ✅ Clears previous options

        List<String> answers = null;
        try {
            answers = (List<String>) questionData.get("choices"); // ✅ Comes from Firestore
        } catch (ClassCastException e) {
            Toast.makeText(this, "Invalid answers format.", Toast.LENGTH_SHORT).show();
        }

        if (answers != null) {
            for (int i = 0; i < answers.size(); i++) {
                View blankView = LayoutInflater.from(this)
                        .inflate(R.layout.item_quiz_enumeration_blanks, linearLayoutOptions, false); // ✅ Inflates your card

                EditText input = blankView.findViewById(R.id.enum_answer_input); // ✅ Finds EditText
                input.setHint("Answer " + (i + 1)); // ✅ Sets hint
                linearLayoutOptions.addView(blankView); // ✅ Adds to layout
            }
        } else {
            Toast.makeText(this, "Missing enumeration answers", Toast.LENGTH_SHORT).show();
        }
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

            // Reset all option colors
            resetOptionColors();

            // Highlight selected card
            cardOption.setCardBackgroundColor(ContextCompat.getColor(this, R.color.pale_green)); // choose your highlight color

            // Store selected answer
            selectedAnswer = optionText;
        });


        // Add the option view to the layout (this must be outside the click listener)
        linearLayoutOptions.addView(optionView);
    }


    private void highlightCorrectAnswer(String correctAnswer) {
        for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
            View child = linearLayoutOptions.getChildAt(i);
            TextView tv = child.findViewById(R.id.tvOptionText);
            MaterialCardView card = child.findViewById(R.id.cardOption);
            if (tv.getText().toString().equals(correctAnswer)) {
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.vibrant_green));
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

    private void saveQuizAttempt(List<Map<String, Object>> answeredQuestions, int score) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "anonymous";

        // Structure: quiz_attempts/{quizId}/users/{userId}
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("quizId", quizId);
        resultData.put("userId", userId);
        resultData.put("score", score);
        resultData.put("total", questions.size());
        resultData.put("answeredQuestions", answeredQuestions); // each question includes selected, correct, question, type
        resultData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("quiz_attempts")
                .document(quizId)
                .collection("users")
                .document(userId)
                .set(resultData) // This overwrites previous result (latest attempt only)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Quiz result saved.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save result.", Toast.LENGTH_SHORT).show());
    }

// YOU MUST CALL THIS WHEN QUIZ IS DONE
// Example usage after final question:
// saveQuizAttempt(userAnswersList, userScore);
// Each item in userAnswersList:
// Map<String, Object> answer = new HashMap<>();
// answer.put("question", questionText);
// answer.put("selected", selectedAnswer);
// answer.put("correct", correctAnswer);
// answer.put("isCorrect", selectedAnswer.equals(correctAnswer));
// answer.put("type", questionType);

}
