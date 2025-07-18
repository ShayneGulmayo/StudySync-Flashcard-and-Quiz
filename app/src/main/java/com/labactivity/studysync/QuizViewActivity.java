    package com.labactivity.studysync;

    import android.annotation.SuppressLint;
    import android.content.Intent;
    import android.graphics.Color;
    import android.graphics.drawable.ColorDrawable;
    import android.os.Bundle;
    import android.text.TextUtils;
    import android.text.method.ScrollingMovementMethod;
    import android.util.Log;
    import android.view.Gravity;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.view.Window;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;
    import java.util.Arrays;


    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.content.ContextCompat;

    import com.bumptech.glide.Glide;
    import com.google.android.material.bottomsheet.BottomSheetDialog;
    import com.google.android.material.card.MaterialCardView;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.firestore.FieldValue;
    import com.google.firebase.firestore.FirebaseFirestore;
    import com.google.firebase.firestore.SetOptions;

    public class QuizViewActivity extends AppCompatActivity {

        private FirebaseFirestore db;
        private TextView quizQuestionTextView;
        private TextView chooseAnswerLabel;
        private String selectedAnswer = null;
        private String correctAnswer = null;
        private LinearLayout linearLayoutOptions;
        private List<Map<String, Object>> questions;
        private int currentQuestionIndex = 0;
        private String quizId;
        private boolean hasAnswered = false;
        private int score = 0;
        private TextView txtViewItems;
        private List<Map<String, Object>> userAnswersList = new ArrayList<>();
        private String mode = "normal";
        private List<Map<String, Object>> incorrectQuestions = new ArrayList<>();
        private int originalQuestionCount = 0;
        private boolean shouldShuffle = false;
        private ImageView questionImage;



        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_quiz_viewer);

            db = FirebaseFirestore.getInstance();

            quizId = getIntent().getStringExtra("quizId");
            if (quizId == null || quizId.trim().isEmpty()) {
                Toast.makeText(this, "Quiz ID not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            quizQuestionTextView = findViewById(R.id.quiz_question_txt_view);
            txtViewItems = findViewById(R.id.txt_view_items);
            linearLayoutOptions = findViewById(R.id.linear_layout_options);
            chooseAnswerLabel = findViewById(R.id.choose_answer_label);
            questionImage = findViewById(R.id.question_image);
            Button btnCheck = findViewById(R.id.btn_check_answer);
            ImageView moreButton = findViewById(R.id.more_button);
            btnCheck.setOnClickListener(v -> handleAnswerCheck());

            // Back button
            findViewById(R.id.back_button).setOnClickListener(v -> showExitConfirmationDialog());
            moreButton.setOnClickListener(v -> showQuizMoreBottomSheet());


            mode = getIntent().getStringExtra("mode");
            if (mode == null) mode = "normal";
            shouldShuffle = getIntent().getBooleanExtra("shuffle", false);

            if ("review_only_incorrect".equals(mode)) {
                loadIncorrectQuestions();
            } else {
                loadQuizFromFirestore();
            }
        }

        private void loadQuizFromFirestore() {
            db.collection("quiz").document(quizId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Object raw = documentSnapshot.get("questions");
                            List<?> fullList = (List<?>) documentSnapshot.get("questions");
                            originalQuestionCount = fullList != null ? fullList.size() : 0;
                            questions = new ArrayList<>();

                            if (raw instanceof List<?>) {
                                for (Object item : (List<?>) raw) {
                                    if (item instanceof Map) {
                                        questions.add((Map<String, Object>) item);
                                    }
                                }
                            }
                            if (shouldShuffle) {
                                Collections.shuffle(questions);
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
            correctAnswer = null;
            selectedAnswer = null;

            if (currentQuestionIndex >= questions.size()) {
                Toast.makeText(this, "🎉 Quiz Completed!", Toast.LENGTH_LONG).show();

                if (!userAnswersList.isEmpty()) {
                    saveQuizAttempt(userAnswersList, score);
                }

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
                displayNextValidQuestion();
            }
        }

        private String detectFallbackType(Map<String, Object> question) {
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

            // Handle question image
            if (questionData.containsKey("photoUrl") && questionData.get("photoUrl") != null) {
                Object photoObj = questionData.get("photoUrl");
                String photoUrl = photoObj != null ? photoObj.toString() : "";

                if (!photoUrl.trim().isEmpty()) {
                    questionImage.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(photoUrl)
                            .centerCrop()
                            .into(questionImage);
                } else {
                    questionImage.setVisibility(View.GONE);
                }
            } else {
                questionImage.setVisibility(View.GONE);
            }


            linearLayoutOptions.removeAllViews();

            if (!questionData.containsKey("correctAnswer") || questionData.get("correctAnswer") == null) {
                Toast.makeText(this, "Missing correct answer. Skipping question.", Toast.LENGTH_SHORT).show();
                currentQuestionIndex++;
                displayNextValidQuestion();
                return;
            }
            correctAnswer = questionData.get("correctAnswer").toString();


            List<String> choices = null;
            try {
                choices = (List<String>) questionData.get("choices");
            } catch (ClassCastException e) {
                Toast.makeText(this, "Invalid choices format.", Toast.LENGTH_SHORT).show();
            }

            if (choices != null && correctAnswer != null) {
                if (shouldShuffle) {
                    Collections.shuffle(choices);
                }
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
            chooseAnswerLabel.setText("Type your answers");
            selectedAnswer = null;
            hasAnswered = false;

            String questionText = questionData.get("question") != null
                    ? questionData.get("question").toString()
                    : "No question text";

            quizQuestionTextView.setText(questionText);

            // Handle question image
            if (questionData.containsKey("photoUrl")) {
                Object photoObj = questionData.get("photoUrl");
                String photoUrl = photoObj != null ? photoObj.toString() : "";

                if (!photoUrl.trim().isEmpty()) {
                    questionImage.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(photoUrl)
                            .centerCrop()
                            .into(questionImage);
                } else {
                    questionImage.setVisibility(View.GONE);
                }
            } else {
                questionImage.setVisibility(View.GONE);
            }

            linearLayoutOptions.removeAllViews();

            List<String> correctAnswers;
            try {
                correctAnswers = (List<String>) questionData.get("correctAnswer");
            } catch (ClassCastException e) {
                Toast.makeText(this, "Invalid format for correct answers.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (correctAnswers == null || correctAnswers.isEmpty()) {
                Toast.makeText(this, "No correct answers found for this enumeration.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add one EditText for each correct answer
            for (int i = 0; i < correctAnswers.size(); i++) {
                View inputView = LayoutInflater.from(this)
                        .inflate(R.layout.item_quiz_enumeration_blanks, linearLayoutOptions, false);
                EditText input = inputView.findViewById(R.id.enum_answer_input);


                input.setHint("Enter answer " + (i + 1));
                linearLayoutOptions.addView(inputView);
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

                resetOptionColors();

                cardOption.setCardBackgroundColor(ContextCompat.getColor(this, R.color.pale_green));

                selectedAnswer = optionText;
            });

            linearLayoutOptions.addView(optionView);
        }

        private void loadIncorrectQuestions() {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            db.collection("quiz")
                    .document(quizId)
                    .collection("quiz_attempt")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        List<Map<String, Object>> answered = (List<Map<String, Object>>) doc.get("answeredQuestions");
                        if (answered != null) {
                            originalQuestionCount = answered.size();
                        }
                        incorrectQuestions.clear();

                        for (Map<String, Object> q : answered) {
                            if (q == null) continue;

                            Boolean isCorrect = (Boolean) q.get("isCorrect");
                            Object rawQuestion = q.get("question");
                            Object rawType = q.get("type");

                            // Skip if question or type is null, or if answered correctly
                            if (isCorrect == null || isCorrect || rawQuestion == null || rawType == null) {
                                continue;
                            }

                            String type = rawType.toString().toLowerCase().trim();
                            Map<String, Object> reconstructed = new HashMap<>();
                            reconstructed.put("question", rawQuestion);
                            reconstructed.put("type", type);
                            reconstructed.put("correct", q.get("correct"));
                            reconstructed.put("selected", q.get("selected"));

                            if ("multiple choice".equals(type)) {
                                if (q.get("choices") instanceof List) {
                                    reconstructed.put("correctAnswer", q.get("correct"));
                                    reconstructed.put("choices", q.get("choices"));
                                } else {
                                    continue; // Skip if choices are invalid
                                }
                            } else if ("enumeration".equals(type)) {
                                if (q.get("correct") instanceof List) {
                                    reconstructed.put("correctAnswer", q.get("correct"));
                                } else {
                                    continue; // Skip if correctAnswer is not a list
                                }
                            } else {
                                continue; // Skip unsupported types
                            }

                            if (q.containsKey("photoUrl")) {
                                reconstructed.put("photoUrl", q.get("photoUrl"));
                            }

                            incorrectQuestions.add(reconstructed);
                        }

                        questions = incorrectQuestions;
                        if (!questions.isEmpty()) {
                            currentQuestionIndex = 0;
                            displayNextValidQuestion();
                        } else {
                            if ("review_only_incorrect".equals(mode)) {
                                Toast.makeText(this, "🎉 All questions were answered correctly!", Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(this, QuizProgressActivity.class);
                                intent.putExtra("quizId", quizId);
                                startActivity(intent);
                                finish();
                            } else {
                                loadQuizFromFirestore();
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

            if ("multiple choice".equals(type)) {
                if (selectedAnswer == null || selectedAnswer.trim().isEmpty()) {
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

                Map<String, Object> answer = new HashMap<>();
                answer.put("question", currentQuestion.get("question"));
                answer.put("type", "multiple choice");
                answer.put("selected", selectedAnswer);
                answer.put("correct", correctAnswer);
                answer.put("isCorrect", isCorrect);
                answer.put("choices", currentQuestion.get("choices"));
                answer.put("order", currentQuestionIndex);
                answer.put("photoUrl", currentQuestion.get("photoUrl"));

                userAnswersList.add(answer);

                if (isCorrect) score++;

                linearLayoutOptions.postDelayed(() -> {
                    currentQuestionIndex++;
                    displayNextValidQuestion();
                    selectedAnswer = null;
                }, 1000);

            } else if ("enumeration".equals(type)) {
                List<String> userAnswers = new ArrayList<>();
                List<String> rawUserAnswers = new ArrayList<>();

                int totalInputs = 0;
                int filledInputs = 0;

                for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                    View child = linearLayoutOptions.getChildAt(i);
                    EditText input = child.findViewById(R.id.enum_answer_input);
                    if (input != null) {
                        totalInputs++;
                        String ansRaw = input.getText().toString().trim();
                        if (!ansRaw.isEmpty()) {
                            filledInputs++;
                            rawUserAnswers.add(ansRaw);
                            userAnswers.add(ansRaw.toLowerCase());
                        }
                    }
                }

                if (userAnswers.isEmpty()) {
                    Toast.makeText(this, "Please fill in at least one blank.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Runnable processCheck = () -> {
                    List<String> correctAnswers;
                    try {
                        correctAnswers = (List<String>) currentQuestion.get("correctAnswer");
                    } catch (ClassCastException e) {
                        Toast.makeText(this, "Invalid format for correct answers.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> correctLower = new ArrayList<>();
                    List<String> correctDisplay = new ArrayList<>();
                    for (String ans : correctAnswers) {
                        correctLower.add(ans.trim().toLowerCase());
                        correctDisplay.add(ans.trim());
                    }

                    List<String> matched = new ArrayList<>();
                    List<String> missed = new ArrayList<>(correctLower);
                    List<String> incorrectInputs = new ArrayList<>();

                    for (String userAns : userAnswers) {
                        if (correctLower.contains(userAns)) {
                            matched.add(userAns);
                            missed.remove(userAns);
                        } else {
                            incorrectInputs.add(userAns);
                        }
                    }

                    boolean isCompletelyCorrect = matched.size() == correctLower.size();
                    if (!matched.isEmpty()) score++;

                    Map<String, Object> answer = new HashMap<>();
                    answer.put("question", currentQuestion.get("question"));
                    answer.put("type", "enumeration");
                    answer.put("selected", rawUserAnswers);
                    answer.put("correct", correctDisplay);
                    answer.put("matched", matched);
                    answer.put("missed", missed);
                    answer.put("isCorrect", isCompletelyCorrect);
                    answer.put("choices", correctDisplay);
                    answer.put("order", currentQuestionIndex);
                    answer.put("incorrectInputs", incorrectInputs);
                    answer.put("photoUrl", currentQuestion.get("photoUrl"));
                    userAnswersList.add(answer);

                    hasAnswered = true;

                    StringBuilder feedback = new StringBuilder();
                    feedback.append("Your Answer: ").append(TextUtils.join(", ", rawUserAnswers)).append("\n");

                    if (!matched.isEmpty()) {
                        feedback.append("Matched: ").append(TextUtils.join(", ", matched)).append("\n");
                    }

                    if (!missed.isEmpty()) {
                        feedback.append("Missed: ").append(TextUtils.join(", ", missed)).append("\n");
                    }

                    if (!correctDisplay.isEmpty()) {
                        feedback.append("Correct Answer: ").append(TextUtils.join(", ", correctDisplay));
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(isCompletelyCorrect ? "✅ Correct!" : (!matched.isEmpty() ? "🟠 Partially Correct" : "❌ Incorrect"))
                            .setMessage(feedback.toString())
                            .setPositiveButton("Next", (dialog, which) -> {
                                currentQuestionIndex++;
                                displayNextValidQuestion();
                            })
                            .setCancelable(false)
                            .show();
                };

                if (filledInputs < totalInputs) {
                    new AlertDialog.Builder(this)
                            .setTitle("Incomplete Answer")
                            .setMessage("Some blanks are still unanswered. Do you want to check your answer anyway?")
                            .setPositiveButton("Yes", (dialog, which) -> processCheck.run())
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    processCheck.run();
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

            db.collection("quiz")
                    .document(quizId)
                    .collection("quiz_attempt")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(existingDoc -> {
                        Map<String, Map<String, Object>> answerMap = new HashMap<>();

                        // Merge old answers by question text
                        if (existingDoc.exists()) {
                            List<Map<String, Object>> oldAnswers = (List<Map<String, Object>>) existingDoc.get("answeredQuestions");
                            if (oldAnswers != null) {
                                for (Map<String, Object> ans : oldAnswers) {
                                    String question = (String) ans.get("question");
                                    if (question != null) {
                                        answerMap.put(question.trim().toLowerCase(), ans);
                                    }
                                }
                            }
                        }

                        // Merge/replace new answers
                        for (Map<String, Object> ans : newAnswers) {
                            String question = (String) ans.get("question");
                            if (question != null) {
                                answerMap.put(question.trim().toLowerCase(), ans);
                            }
                        }

                        List<Map<String, Object>> combinedAnswers = new ArrayList<>(answerMap.values());
                        Collections.sort(combinedAnswers, (a, b) -> {
                            Object orderA = a.get("order");
                            Object orderB = b.get("order");
                            if (orderA instanceof Number && orderB instanceof Number) {
                                return Integer.compare(((Number) orderA).intValue(), ((Number) orderB).intValue());
                            }
                            return 0;
                        });

                        for (int i = 0; i < combinedAnswers.size(); i++) {
                            combinedAnswers.get(i).put("order", i);
                        }

                        int finalScore = 0;
                        for (Map<String, Object> ans : combinedAnswers) {
                            Boolean correct = (Boolean) ans.get("isCorrect");
                            if (correct != null && correct) finalScore++;
                        }

                        int percentage = Math.round((finalScore / (float) originalQuestionCount) * 100);

                        Map<String, Object> resultData = new HashMap<>();
                        resultData.put("quizId", quizId);
                        resultData.put("userId", userId);
                        resultData.put("score", finalScore);
                        resultData.put("total", originalQuestionCount);
                        resultData.put("answeredQuestions", combinedAnswers);
                        resultData.put("timestamp", FieldValue.serverTimestamp());
                        resultData.put("percentage", percentage);

                        db.collection("quiz")
                                .document(quizId)
                                .collection("quiz_attempt")
                                .document(userId)
                                .set(resultData)
                                .addOnSuccessListener(unused -> {
                                    // Update percentage to both owned_sets and saved_sets
                                    Map<String, Object> percentData = new HashMap<>();
                                    percentData.put("percentage", percentage);

                                });

                        db.collection("users").document(userId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (!userDoc.exists()) return;

                                    // ✅ Use "progress" instead of "percentage"
                                    Map<String, Object> progressMap = new HashMap<>();
                                    progressMap.put("progress", percentage);

                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");
                                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) userDoc.get("saved_sets");

                                    boolean updated = false;

                                    boolean ownedUpdated = false;
                                    boolean savedUpdated = false;

                                    if (ownedSets != null) {
                                        for (Map<String, Object> item : ownedSets) {
                                            if (quizId.equals(item.get("id"))) {
                                                item.put("progress", percentage);
                                                ownedUpdated = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (savedSets != null) {
                                        for (Map<String, Object> item : savedSets) {
                                            if (quizId.equals(item.get("id"))) {
                                                item.put("progress", percentage);
                                                savedUpdated = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (ownedUpdated) {
                                        db.collection("users").document(userId).update("owned_sets", ownedSets);
                                    }
                                    if (savedUpdated) {
                                        db.collection("users").document(userId).update("saved_sets", savedSets);
                                    }


                                    if (savedSets != null) {
                                        for (Map<String, Object> item : savedSets) {
                                            if (quizId.equals(item.get("id"))) {
                                                item.put("progress", percentage); // ✅ renamed here
                                                updated = true;
                                                break;
                                            }
                                        }
                                        if (updated) {
                                            db.collection("users").document(userId)
                                                    .update("saved_sets", savedSets);
                                        }
                                    }
                                });


                    });
        }

        private void showQuizMoreBottomSheet() {
            View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_quiz_more_viewer, null);

            LinearLayout moreInfo = view.findViewById(R.id.more_info);
            LinearLayout restartQuiz = view.findViewById(R.id.restart_quiz);

            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
            bottomSheetDialog.setContentView(view);
            bottomSheetDialog.show();

            moreInfo.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showMultiPageInfoDialog();
            });

            restartQuiz.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                if (!"review_only_incorrect".equals(mode)) {
                    showRestartConfirmationDialog();
                }
            });
        }

        private void showMultiPageInfoDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.item_view_more_info, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            dialog.setCancelable(false); // Optional: prevent accidental dismissal
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.show();

            // Fix size after showing to avoid auto-resize issues
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            TextView infoText = dialogView.findViewById(R.id.infoText);
            Button nextButton = dialogView.findViewById(R.id.nextButton);
            Button prevButton = dialogView.findViewById(R.id.prevButton);
            ImageButton exitButton = dialogView.findViewById(R.id.exit_button);

            List<String> pages = Arrays.asList(
                    "1️⃣ There are two types of questions:\n\n🟩 Multiple Choice – Select one correct answer\n🟨 Enumeration – Fill in one or more correct answers",
                    "2️⃣ Answers are checked instantly. You must submit an answer to proceed.\n✅ Correct answers are shown immediately after submission.",
                    "3️⃣ Answer colors matter:\n\n🟥 Red = Incorrect\n🟩 Green = Correct",
                    "4️⃣ Enumeration is scored strictly to help reinforce memorization. This approach enhances learners’ ability to retain information more effectively.\n",
                    "5️⃣ This is a one-way quiz — you can’t go back once you answer.\n\n🔁 You may restart anytime, but your progress will reset."
            );

            final int[] currentPage = {0};
            infoText.setText(pages.get(currentPage[0]));

            prevButton.setEnabled(false); // First page

            nextButton.setOnClickListener(v -> {
                if (currentPage[0] < pages.size() - 1) {
                    currentPage[0]++;
                    infoText.setText(pages.get(currentPage[0]));
                    prevButton.setEnabled(true);
                    if (currentPage[0] == pages.size() - 1) nextButton.setEnabled(false);
                }
            });

            prevButton.setOnClickListener(v -> {
                if (currentPage[0] > 0) {
                    currentPage[0]--;
                    infoText.setText(pages.get(currentPage[0]));
                    nextButton.setEnabled(true);
                    if (currentPage[0] == 0) prevButton.setEnabled(false);
                }
            });

            exitButton.setOnClickListener(v -> dialog.dismiss());
        }








        private void showRestartConfirmationDialog() {
            new AlertDialog.Builder(this)
                    .setTitle("Restart Quiz?")
                    .setMessage("Restart from beginning? Your current progress will be lost.")
                    .setPositiveButton("Yes", (dialog, which) -> recreate())
                    .setNegativeButton("Cancel", null)
                    .show();
        }


        private void showExitConfirmationDialog() {
            new AlertDialog.Builder(this)
                    .setTitle("Exit Quiz")
                    .setMessage("Are you sure you want to exit the quiz?\nYour progress will not be saved.")
                    .setPositiveButton("Yes, Exit", (dialog, which) -> {
                        QuizViewActivity.this.finish();
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }
