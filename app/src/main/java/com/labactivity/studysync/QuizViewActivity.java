package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
    private String correctAnswer = null;
    private LinearLayout linearLayoutOptions;
    private List<Map<String, Object>> questions;
    private int currentQuestionIndex = 0;
    private String quizId;
    private boolean hasAnswered = false;
    private ImageView ownerProfile;
    private int score = 0;
    private TextView txtViewItems;
    private List<Map<String, Object>> userAnswersList = new ArrayList<>();
    private String mode = "normal";
    private List<Map<String, Object>> incorrectQuestions = new ArrayList<>();
    private int originalQuestionCount = 0;






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_viewer);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Get quizId FIRST before anything else
        quizId = getIntent().getStringExtra("quizId");
        if (quizId == null || quizId.trim().isEmpty()) {
            Toast.makeText(this, "Quiz ID not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Views
        quizTitleView = findViewById(R.id.quiz_title);
        quizOwnerView = findViewById(R.id.owner_username);
        quizQuestionTextView = findViewById(R.id.quiz_question_txt_view);
        txtViewItems = findViewById(R.id.txt_view_items);
        linearLayoutOptions = findViewById(R.id.linear_layout_options);
        ownerProfile = findViewById(R.id.owner_profile);
        more_button = findViewById(R.id.more_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        chooseAnswerLabel = findViewById(R.id.choose_answer_label);
        back_button = findViewById(R.id.back_button);

        // Setup profile image if passed
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

        // Setup button
        Button btnCheck = findViewById(R.id.btn_check_answer);
        btnCheck.setOnClickListener(v -> handleAnswerCheck());

        // Back button behavior
        back_button.setOnClickListener(v -> onBackPressed());

        // Determine mode (default is normal)
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "normal";


        // Load appropriate quiz content
        if ("review_only_incorrect".equals(mode)) {
            loadIncorrectQuestions(); // this calls loadQuizMetaInfo internally
        } else {
            loadQuizFromFirestore(); // this also loads meta and full quiz
        }

        // (Optional) More options button if needed later
        // more_button.setOnClickListener(v -> showMoreBottomSheet());
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
                        String ownerUid = documentSnapshot.getString("owner_uid");

                        if (ownerUid != null && !ownerUid.isEmpty()) {
                            db.collection("users").document(ownerUid).get()
                                    .addOnSuccessListener(userDoc -> {
                                        String latestUsername = userDoc.getString("username");
                                        if (latestUsername != null && !latestUsername.isEmpty()) {
                                            quizOwnerView.setText(latestUsername);
                                        } else {
                                            quizOwnerView.setText("Unknown User");
                                        }

                                        // Optional: update profile photo if needed
                                        String photoUrl = userDoc.getString("photoUrl");
                                        if (photoUrl != null && !photoUrl.isEmpty()) {
                                            Glide.with(this)
                                                    .load(photoUrl)
                                                    .placeholder(R.drawable.user_profile)
                                                    .circleCrop()
                                                    .into(ownerProfile);
                                        } else {
                                            ownerProfile.setImageResource(R.drawable.user_profile);
                                        }

                                    })
                                    .addOnFailureListener(e -> {
                                        quizOwnerView.setText("Failed to load user");
                                        ownerProfile.setImageResource(R.drawable.user_profile);
                                    });
                        } else {
                            quizOwnerView.setText("Unknown User");
                            ownerProfile.setImageResource(R.drawable.user_profile);
                        }


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
                        originalQuestionCount = questions.size();


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

        // Check if all questions have been shown first
        if (currentQuestionIndex >= questions.size()) {
            // ✅ Do NOT update txtViewItems here — quiz is done
            Toast.makeText(this, "🎉 Quiz Completed!", Toast.LENGTH_LONG).show();
            saveQuizAttempt(userAnswersList, score);

            Intent intent = new Intent(this, QuizProgressActivity.class);
            intent.putExtra("quizId", quizId);
            startActivity(intent);
            finish();
            return;
        }

        txtViewItems.setText((currentQuestionIndex + 1) + "/" + questions.size());

        Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);
        String type = currentQuestion.containsKey("type")
                ? currentQuestion.get("type").toString().toLowerCase()
                : detectFallbackType(currentQuestion);

        if (type.equals("multiple choice")) {
            displayMultipleChoice(currentQuestion);
        } else if (type.equals("enumeration")) {
            displayEnumeration(currentQuestion);
        } else {
            Toast.makeText(this, "Unsupported or missing question type. Skipping...", Toast.LENGTH_SHORT).show();
            currentQuestionIndex++;
            displayNextValidQuestion();  // 🔁 Try next one recursively
        }
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

    private void loadQuizMetaInfo() {
        db.collection("quiz").document(quizId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String title = doc.getString("title");
                quizTitleView.setText(title != null ? title : "Untitled");

                String ownerUid = doc.getString("owner_uid");
                if (ownerUid != null) {
                    db.collection("users").document(ownerUid).get().addOnSuccessListener(userDoc -> {
                        String username = userDoc.getString("username");
                        quizOwnerView.setText(username != null ? username : "Unknown User");

                        String photoUrl = userDoc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this).load(photoUrl).placeholder(R.drawable.user_profile).circleCrop().into(ownerProfile);
                        } else {
                            ownerProfile.setImageResource(R.drawable.user_profile);
                        }
                    }).addOnFailureListener(e -> {
                        quizOwnerView.setText("Failed to load user");
                        ownerProfile.setImageResource(R.drawable.user_profile);
                    });
                }

                String privacy = doc.getString("privacy");
                if ("private".equalsIgnoreCase(privacy)) {
                    privacyIcon.setImageResource(R.drawable.lock);
                } else {
                    privacyIcon.setImageResource(R.drawable.public_icon);
                }

            }
        });
    }


    private void loadIncorrectQuestions() {
        loadQuizMetaInfo();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("quiz_attempts")
                .document(quizId)
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    List<Map<String, Object>> answered = (List<Map<String, Object>>) doc.get("answeredQuestions");
                    if (answered != null) {
                        originalQuestionCount = answered.size(); // ✅ Save total questions originally answered
                    }
                    incorrectQuestions.clear();
                    for (Map<String, Object> q : answered) {
                        Boolean isCorrect = (Boolean) q.get("isCorrect");
                        if (isCorrect != null && !isCorrect) {
                            String type = q.get("type").toString();
                            Map<String, Object> reconstructed = new HashMap<>();
                            reconstructed.put("question", q.get("question"));
                            reconstructed.put("type", type);
                            reconstructed.put("correct", q.get("correct"));
                            reconstructed.put("selected", q.get("selected"));

                            if ("multiple choice".equals(type)) {
                                reconstructed.put("correctAnswer", q.get("correct"));
                                reconstructed.put("choices", q.get("choices"));
                            } else if ("enumeration".equals(type)) {
                                reconstructed.put("choices", q.get("correct"));
                            }

                            incorrectQuestions.add(reconstructed);
                        }
                    }
                    questions = incorrectQuestions;
                    if (!questions.isEmpty()) {
                        currentQuestionIndex = 0;
                        displayNextValidQuestion();
                    } else {
                        // Only exit if the user actually pressed "Review Incorrect Questions"
                        String mode = getIntent().getStringExtra("mode");
                        if ("review_only_incorrect".equals(mode)) {
                            Toast.makeText(this, "🎉 All questions were answered correctly!", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(this, QuizProgressActivity.class);
                            intent.putExtra("quizId", quizId);
                            startActivity(intent);
                            finish();
                        } else {
                            // Otherwise just load the full quiz
                            loadQuizFromFirestore(); // fallback to normal
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load previous attempt.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void handleAnswerCheck() {
        Button btnCheck = findViewById(R.id.btn_check_answer);
        btnCheck.setEnabled(false);
        btnCheck.postDelayed(() -> btnCheck.setEnabled(true), 1000);

        if (hasAnswered || currentQuestionIndex >= questions.size()) return;

        Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);
        String type = currentQuestion.containsKey("type")
                ? currentQuestion.get("type").toString().toLowerCase()
                : detectFallbackType(currentQuestion);

        if (type.equals("multiple choice")) {
            if (selectedAnswer == null || selectedAnswer.trim().isEmpty()) {
                Toast.makeText(this, "Please select an answer.", Toast.LENGTH_SHORT).show();
                return;
            }

            hasAnswered = true;
            boolean isCorrect = selectedAnswer.equals(correctAnswer);

            // Highlight options
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

            // Save answer
            Map<String, Object> answer = new HashMap<>();
            answer.put("question", currentQuestion.get("question"));
            answer.put("type", "multiple choice");
            answer.put("selected", selectedAnswer);
            answer.put("correct", correctAnswer);
            answer.put("isCorrect", isCorrect);
            answer.put("choices", currentQuestion.get("choices"));
            userAnswersList.add(answer);
            if (isCorrect) score++;

            linearLayoutOptions.postDelayed(() -> {
                currentQuestionIndex++;
                displayNextValidQuestion();
                selectedAnswer = null;
            }, 1000);

        } else if (type.equals("enumeration")) {
            List<String> userAnswers = new ArrayList<>();
            boolean hasEmptyBlanks = false;

            for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                View child = linearLayoutOptions.getChildAt(i);
                EditText input = child.findViewById(R.id.enum_answer_input);
                if (input != null) {
                    String answer = input.getText().toString().trim().toLowerCase();
                    if (!answer.isEmpty()) {
                        userAnswers.add(answer);
                    } else {
                        hasEmptyBlanks = true;
                    }
                }
            }

            if (userAnswers.isEmpty()) {
                Toast.makeText(this, "Please fill in at least one answer.", Toast.LENGTH_SHORT).show();
                return;
            }

            Runnable proceedWithCheck = () -> {
                List<String> correctAnswers = new ArrayList<>();
                try {
                    correctAnswers = (List<String>) currentQuestion.get("choices");
                } catch (ClassCastException e) {
                    Toast.makeText(this, "Invalid choices for enumeration.", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<String> correctLower = new ArrayList<>();
                for (String ans : correctAnswers) {
                    correctLower.add(ans.trim().toLowerCase());
                }

                Set<String> userSet = new HashSet<>(userAnswers);
                Set<String> correctSet = new HashSet<>(correctLower);

                Set<String> correctMatched = new HashSet<>(userSet);
                correctMatched.retainAll(correctSet);

                Set<String> missedAnswers = new HashSet<>(correctSet);
                missedAnswers.removeAll(userSet);

                boolean isCorrect = correctMatched.size() == correctSet.size();
                if (isCorrect) score++;

                Map<String, Object> answer = new HashMap<>();
                answer.put("question", currentQuestion.get("question"));
                answer.put("type", "enumeration");
                answer.put("selected", userAnswers);
                answer.put("correct", correctLower);
                answer.put("isCorrect", isCorrect);
                answer.put("choices", correctLower);
                userAnswersList.add(answer);

                hasAnswered = true;

                new AlertDialog.Builder(this)
                        .setTitle(isCorrect ? "✅ Correct!" : "❌ Not Quite")
                        .setMessage("You answered: " + TextUtils.join(", ", userAnswers) + "\n\n" +
                                "Correct answers: " + TextUtils.join(", ", correctLower) + "\n\n" +
                                "Matched: " + TextUtils.join(", ", correctMatched) + "\n" +
                                "Missed: " + TextUtils.join(", ", missedAnswers))
                        .setPositiveButton("Next", (dialog, which) -> {
                            currentQuestionIndex++;
                            displayNextValidQuestion();
                        })
                        .setCancelable(false)
                        .show();
            };

            if (hasEmptyBlanks) {
                new AlertDialog.Builder(this)
                        .setTitle("Some blanks are empty")
                        .setMessage("You still have blanks unanswered. Are you sure you want to check?")
                        .setPositiveButton("Yes", (dialog, which) -> proceedWithCheck.run())
                        .setNegativeButton("No, Go Back", null)
                        .show();
            } else {
                proceedWithCheck.run();
            }
        }
    }

        private void resetQuizState() {
        currentQuestionIndex = 0;
        hasAnswered = false;
        selectedAnswer = null;
        correctAnswer = null;
        userAnswersList.clear();
        incorrectQuestions.clear();
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

    private void saveQuizAttempt(List<Map<String, Object>> newAnswers, int newScore) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "anonymous";

        db.collection("quiz_attempts")
                .document(quizId)
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    Map<String, Map<String, Object>> mergedAnswers = new HashMap<>();

                    // 1. Load previous answers if they exist
                    List<Map<String, Object>> previousAnswers = (List<Map<String, Object>>) doc.get("answeredQuestions");
                    if (previousAnswers != null) {
                        for (Map<String, Object> prev : previousAnswers) {
                            String question = prev.get("question").toString();
                            mergedAnswers.put(question, prev);
                        }
                    }

                    // 2. Overwrite with new answers (fixes incorrect ones)
                    for (Map<String, Object> current : newAnswers) {
                        String question = current.get("question").toString();
                        mergedAnswers.put(question, current);
                    }

                    // 3. Count how many are now correct
                    int finalScore = 0;
                    for (Map<String, Object> answer : mergedAnswers.values()) {
                        Boolean isCorrect = (Boolean) answer.get("isCorrect");
                        if (isCorrect != null && isCorrect) finalScore++;
                    }

                    // 4. Final save
                    Map<String, Object> resultData = new HashMap<>();
                    resultData.put("quizId", quizId);
                    resultData.put("userId", userId);
                    resultData.put("score", finalScore);
                    resultData.put("total", originalQuestionCount);
                    resultData.put("answeredQuestions", new ArrayList<>(mergedAnswers.values()));
                    resultData.put("timestamp", FieldValue.serverTimestamp());

                    db.collection("quiz_attempts")
                            .document(quizId)
                            .collection("users")
                            .document(userId)
                            .set(resultData);
                });
    }


}
