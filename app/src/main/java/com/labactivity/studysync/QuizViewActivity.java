    package com.labactivity.studysync;

    import android.annotation.SuppressLint;
    import android.content.Intent;
    import android.graphics.Color;
    import android.graphics.drawable.ColorDrawable;
    import android.os.Build;
    import android.os.Bundle;
    import android.text.TextUtils;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.annotation.Nullable;
    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.cardview.widget.CardView;
    import androidx.core.content.ContextCompat;

    import com.bumptech.glide.Glide;
    import com.google.android.material.bottomsheet.BottomSheetDialog;
    import com.google.android.material.card.MaterialCardView;
    import com.google.common.reflect.TypeToken;
    import com.google.firebase.Timestamp;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.firestore.FieldValue;
    import com.google.firebase.firestore.FirebaseFirestore;
    import com.google.gson.Gson;
    import com.google.gson.GsonBuilder;
    import com.google.gson.JsonElement;
    import com.google.gson.JsonParser;

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileOutputStream;
    import java.lang.reflect.Type;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.Files;
    import java.util.Arrays;
    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.HashMap;
    import java.util.HashSet;
    import java.util.Iterator;
    import java.util.LinkedHashMap;
    import java.util.List;
    import java.util.Map;
    import java.util.Objects;
    import java.util.Set;

    import org.json.JSONArray;
    import org.json.JSONObject;

    public class QuizViewActivity extends AppCompatActivity {

        private FirebaseFirestore db;

        private TextView quizQuestionTextView, chooseAnswerLabel, txtViewItems;
        private ImageView questionImage;

        private LinearLayout linearLayoutOptions;
        private CardView questionCard;

        private String progressFileName, quizId;
        private String mode = "normal";
        private String selectedAnswer = null;
        private String correctAnswer = null;

        private int score = 0;
        private int currentQuestionIndex = 0;
        private int originalQuestionCount = 0;

        private boolean hasAnswered = false;
        private boolean shouldShuffle = false;
        private boolean isOffline = false;

        private List<Map<String, Object>> questions;
        private List<Map<String, Object>> userAnswersList = new ArrayList<>();
        private List<Map<String, Object>> incorrectQuestions = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_quiz_viewer);

            initializeViews();
            db = FirebaseFirestore.getInstance();

            Intent intent = getIntent();
            quizId = intent.getStringExtra("quizId");
            mode = intent.getStringExtra("mode");
            if (mode == null) mode = "normal";
            isOffline = intent.getBooleanExtra("isOffline", false);

            if (quizId == null || quizId.trim().isEmpty()) {
                Toast.makeText(this, "Quiz ID not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            shouldShuffle = intent.getBooleanExtra("shuffle", false);
            isOffline = getIntent().getBooleanExtra("isOffline", false);
            progressFileName = getIntent().getStringExtra("progressFileName");

            if (isOffline) {
                String fileName = "quiz_" + quizId + ".json";

                if ("retake_incorrect_only".equals(mode)) {
                    String userAnswersJson = intent.getStringExtra("userAnswersList");

                    if (!TextUtils.isEmpty(userAnswersJson)) {
                        try {
                            Type listType = new TypeToken<List<Map<String, Object>>>() {
                            }.getType();
                            List<Map<String, Object>> incorrectAnswers = new Gson().fromJson(userAnswersJson, listType);

                            if (incorrectAnswers != null && !incorrectAnswers.isEmpty()) {
                                loadOfflineQuiz(fileName, incorrectAnswers);
                            } else {
                                Toast.makeText(this, "No incorrect answers found.", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Failed to parse offline review data.", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "No offline review data found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    loadOfflineQuiz(fileName, null);
                }
            } else {
                if ("retake_incorrect_only".equals(mode)) {
                    loadIncorrectQuestions();
                } else {
                    loadQuizFromFirestore();
                }
            }
        }

        private void initializeViews() {
            quizQuestionTextView = findViewById(R.id.quiz_question_txt_view);
            txtViewItems = findViewById(R.id.txt_view_items);
            linearLayoutOptions = findViewById(R.id.linear_layout_options);
            chooseAnswerLabel = findViewById(R.id.choose_answer_label);
            questionImage = findViewById(R.id.question_image);
            questionCard = findViewById(R.id.question_img_card);


            Button btnCheck = findViewById(R.id.btn_check_answer);
            ImageView moreButton = findViewById(R.id.more_button);
            ImageView backButton = findViewById(R.id.back_button);

            btnCheck.setOnClickListener(v -> handleAnswerCheck());
            moreButton.setOnClickListener(v -> showQuizMoreBottomSheet());
            backButton.setOnClickListener(v -> showExitConfirmationDialog());
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

                int correctCount = 0;
                int incorrectCount = 0;

                for (Map<String, Object> answer : userAnswersList) {
                    boolean isCorrect = Boolean.TRUE.equals(answer.get("isCorrect"));
                    if (isCorrect) correctCount++;
                    else incorrectCount++;
                }

                int total = correctCount + incorrectCount;
                int percentage = total > 0 ? (int) ((correctCount * 100.0f) / total) : 0;

                if (!userAnswersList.isEmpty()) {
                    if (isOffline) {
                        goToOfflineQuizProgressActivity();
                    } else {
                        saveQuizAttempt(userAnswersList, correctCount);
                    }
                }
                Intent intent = new Intent(this, QuizProgressActivity.class);
                intent.putExtra("quizId", quizId);
                intent.putExtra("quizTitle", getIntent().getStringExtra("quizTitle"));
                intent.putExtra("isOffline", isOffline);
                intent.putExtra("score", correctCount);
                intent.putExtra("incorrect", incorrectCount);
                intent.putExtra("percentage", percentage);
                intent.putExtra("userAnswers", new Gson().toJson(userAnswersList));
                startActivity(intent);
                finish();
                return;
            }

            txtViewItems.setText((currentQuestionIndex + 1) + "/" + questions.size());

            Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);
            String quizType = currentQuestion.containsKey("quizType")
                    ? currentQuestion.get("quizType").toString().toLowerCase()
                    : detectFallbackType(currentQuestion);

            if (quizType.equals("multiple choice")) {
                displayMultipleChoice(currentQuestion);
            } else if (quizType.equals("enumeration")) {
                displayEnumeration(currentQuestion);
            } else if (quizType.equals("true or false")) {
                displayTrueOrFalse(currentQuestion);
            }
            else {
                Toast.makeText(this, "Unsupported or missing question type. Skipping...", Toast.LENGTH_SHORT).show();
                currentQuestionIndex++;
                displayNextValidQuestion();
            }
        }

        private void resetOptionColors() {
            for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                View child = linearLayoutOptions.getChildAt(i);
                MaterialCardView card = child.findViewById(R.id.cardOption);
                card.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
            }
        }

        private String detectFallbackType(Map<String, Object> question) {

            if (question.containsKey("choices")) {
                Object rawChoices = question.get("choices");

                if (rawChoices instanceof List) {
                    List<?> choicesList = (List<?>) rawChoices;

                    if (choicesList.size() == 2) {
                        String c1 = choicesList.get(0).toString().toLowerCase();
                        String c2 = choicesList.get(1).toString().toLowerCase();

                        // If choices are exactly true/false → TRUE OR FALSE
                        if ((c1.equals("true") && c2.equals("false")) ||
                                (c1.equals("false") && c2.equals("true"))) {
                            return "true or false";
                        }
                    }
                }
            }
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

            if (questionData.containsKey("photoUrl") && questionData.get("photoUrl") != null) {
                String photoUrl = questionData.get("photoUrl").toString();

                if (!photoUrl.trim().isEmpty()) {
                    questionCard.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(photoUrl)
                            .centerCrop()
                            .into(questionImage);
                } else {
                    questionCard.setVisibility(View.GONE);
                }
            } else {
                questionCard.setVisibility(View.GONE);
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

                cardOption.setCardBackgroundColor(ContextCompat.getColor(this, R.color.medium_gray));

                selectedAnswer = optionText;
            });

            linearLayoutOptions.addView(optionView);
        }

        private void displayEnumeration(Map<String, Object> questionData) {
            chooseAnswerLabel.setText("Type your answers");
            selectedAnswer = null;
            hasAnswered = false;

            String questionText = questionData.get("question") != null
                    ? questionData.get("question").toString()
                    : "No question text";

            quizQuestionTextView.setText(questionText);

            if (questionData.containsKey("photoUrl") && questionData.get("photoUrl") != null) {
                String photoUrl = questionData.get("photoUrl").toString();

                if (!photoUrl.trim().isEmpty()) {
                    questionCard.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(photoUrl)
                            .centerCrop()
                            .into(questionImage);
                } else {
                    questionCard.setVisibility(View.GONE);
                }
            } else {
                questionCard.setVisibility(View.GONE);
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

            for (int i = 0; i < correctAnswers.size(); i++) {
                View inputView = LayoutInflater.from(this)
                        .inflate(R.layout.item_quiz_enumeration_blanks, linearLayoutOptions, false);
                EditText input = inputView.findViewById(R.id.enum_answer_input);


                input.setHint("Enter answer " + (i + 1));
                linearLayoutOptions.addView(inputView);
            }
        }

        private void displayTrueOrFalse(Map<String, Object> questionData) {

            chooseAnswerLabel.setText("Choose your answer");
            selectedAnswer = null;
            hasAnswered = false;

            // 1. Display question text
            String questionText = questionData.get("question") != null
                    ? questionData.get("question").toString()
                    : "No question text";

            quizQuestionTextView.setText(questionText);

            // 2. Display image if present
            if (questionData.containsKey("photoUrl") && questionData.get("photoUrl") != null) {
                String photoUrl = questionData.get("photoUrl").toString();

                if (!photoUrl.trim().isEmpty()) {
                    questionCard.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(photoUrl)
                            .centerCrop()
                            .into(questionImage);
                } else {
                    questionCard.setVisibility(View.GONE);
                }
            } else {
                questionCard.setVisibility(View.GONE);
            }

            // 3. Clear previous options
            linearLayoutOptions.removeAllViews();

            // 4. Validate correctAnswer exists
            if (!questionData.containsKey("correctAnswer") || questionData.get("correctAnswer") == null) {
                Toast.makeText(this, "Missing correct answer. Skipping question.", Toast.LENGTH_SHORT).show();
                currentQuestionIndex++;
                displayNextValidQuestion();
                return;
            }

            correctAnswer = questionData.get("correctAnswer").toString().trim().toLowerCase();

            // 5. Create the two TRUE/FALSE choices
            List<String> tfChoices = Arrays.asList("true", "false");

            // Optional shuffle if enabled
            if (shouldShuffle) {
                Collections.shuffle(tfChoices);
            }

            // 6. Add the two choice buttons to the UI
            for (String optionText : tfChoices) {
                addOptionView(optionText, correctAnswer);
            }
        }


        private void handleAnswerCheck() {
            Button btnCheck = findViewById(R.id.btn_check_answer);
            btnCheck.setEnabled(false);
            btnCheck.postDelayed(() -> btnCheck.setEnabled(true), 1000);

            if (hasAnswered || currentQuestionIndex >= questions.size()) return;

            Map<String, Object> currentQuestion = questions.get(currentQuestionIndex);
            String quizType = currentQuestion.containsKey("quizType")
                    ? currentQuestion.get("quizType").toString().toLowerCase()
                    : detectFallbackType(currentQuestion);

            if ("multiple choice".equals(quizType)) {
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
                answer.put("quizType", "multiple choice");
                answer.put("selected", selectedAnswer);
                answer.put("correct", correctAnswer);
                answer.put("isCorrect", isCorrect);
                answer.put("choices", currentQuestion.get("choices"));
                answer.put("order", currentQuestionIndex);
                answer.put("photoUrl", currentQuestion.get("photoUrl"));

                userAnswersList.add(answer);
                questions.get(currentQuestionIndex).put("selectedAnswer", selectedAnswer);

                for (int i = 0; i < questions.size(); i++) {
                    Log.d("CHECK_SELECTED", "Q" + (i + 1) + " selectedAnswer = " + questions.get(i).get("selectedAnswer"));
                }

                saveUserAnswersOffline(); // <-- Add this line


                if (isCorrect) score++;

                linearLayoutOptions.postDelayed(() -> {
                    currentQuestionIndex++;
                    displayNextValidQuestion();
                    selectedAnswer = null;
                }, 1000);

            } else if ("true or false".equals(quizType)) {

                if (selectedAnswer == null || selectedAnswer.trim().isEmpty()) {
                    Toast.makeText(this, "Please select True or False.", Toast.LENGTH_SHORT).show();
                    return;
                }

                hasAnswered = true;

                boolean isCorrect = selectedAnswer.equalsIgnoreCase(correctAnswer);

                // Color options
                for (int i = 0; i < linearLayoutOptions.getChildCount(); i++) {
                    View child = linearLayoutOptions.getChildAt(i);
                    TextView tv = child.findViewById(R.id.tvOptionText);
                    MaterialCardView card = child.findViewById(R.id.cardOption);
                    String option = tv.getText().toString().toLowerCase();

                    if (option.equals(correctAnswer.toLowerCase())) {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.vibrant_green));
                    } else if (option.equals(selectedAnswer.toLowerCase())) {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_red));
                    } else {
                        card.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
                    }
                }

                // Save answer
                Map<String, Object> answer = new HashMap<>();
                answer.put("question", currentQuestion.get("question"));
                answer.put("quizType", "true or false");
                answer.put("selected", selectedAnswer);
                answer.put("correct", correctAnswer);
                answer.put("isCorrect", isCorrect);
                answer.put("choices", Arrays.asList("true", "false"));
                answer.put("order", currentQuestionIndex);
                answer.put("photoUrl", currentQuestion.get("photoUrl"));

                userAnswersList.add(answer);
                questions.get(currentQuestionIndex).put("selectedAnswer", selectedAnswer);

                saveUserAnswersOffline();

                if (isCorrect) score++;

                // Move to next question
                linearLayoutOptions.postDelayed(() -> {
                    currentQuestionIndex++;
                    displayNextValidQuestion();
                    selectedAnswer = null;
                }, 1000);
            }
            else if ("enumeration".equals(quizType)) {
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
                    answer.put("quizType", "enumeration");
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
                    questions.get(currentQuestionIndex).put("selectedAnswer", rawUserAnswers);

                    for (int i = 0; i < questions.size(); i++) {
                        Log.d("CHECK_SELECTED", "Q" + (i + 1) + " selectedAnswer = " + questions.get(i).get("selectedAnswer"));
                    }

                    saveUserAnswersOffline(); // <-- Add this line

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
                            Object rawType = q.get("quizType");

                            if (isCorrect == null || isCorrect || rawQuestion == null || rawType == null) {
                                continue;
                            }

                            String quizType = rawType.toString().toLowerCase().trim();
                            Map<String, Object> reconstructed = new HashMap<>();
                            reconstructed.put("question", rawQuestion);
                            reconstructed.put("quizType", quizType);
                            reconstructed.put("correct", q.get("correct"));
                            reconstructed.put("selected", q.get("selected"));

                            if ("multiple choice".equals(quizType)) {
                                if (q.get("choices") instanceof List) {
                                    reconstructed.put("correctAnswer", q.get("correct"));
                                    reconstructed.put("choices", q.get("choices"));
                                } else {
                                    continue;
                                }
                            } else if ("enumeration".equals(quizType)) {
                                if (q.get("correct") instanceof List) {
                                    reconstructed.put("correctAnswer", q.get("correct"));
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
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
                            if ("retake_incorrect_only".equals(mode)) {
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

        private void saveUserAnswersOffline() {
            Log.d("QUIZ_OFFLINE", ">>> saveUserAnswersOffline CALLED <<<");

            if (!isOffline || quizId == null || questions == null || questions.isEmpty()) return;

            try {
                if (progressFileName == null) {
                    progressFileName = "quiz_" + quizId + ".json";
                }

                File file = new File(getFilesDir(), progressFileName);
                if (!file.exists()) {
                    Log.e("QUIZ_OFFLINE", "Quiz JSON file not found.");
                    return;
                }

                String fileContent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fileContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } else {
                    Log.e("QUIZ_OFFLINE", "Unsupported Android version.");
                    return;
                }

                JSONObject quizJson = new JSONObject(fileContent);
                JSONArray newAttemptsArray = new JSONArray();

                int correctCount = 0;
                int incorrectCount = 0;

                for (int i = 0; i < questions.size(); i++) {
                    Map<String, Object> q = questions.get(i);
                    Object selected = q.get("selectedAnswer");
                    Object correctRaw = q.get("correctAnswer");
                    String quizType = String.valueOf(q.get("quizType"));
                    if (quizType == null || quizType.equals("null") || quizType.trim().isEmpty()) {
                        quizType = "multiple choice"; // default type
                    }

                    List<String> correctAnswers = new ArrayList<>();
                    if (correctRaw instanceof List) {
                        for (Object c : (List<?>) correctRaw) correctAnswers.add(String.valueOf(c).trim());
                    } else if (correctRaw instanceof String) {
                        correctAnswers.add(((String) correctRaw).trim());
                    }

                    // Determine correctness
                    boolean isCorrect = false;
                    boolean hasAnswer = false;

                    if ("multiple choice".equalsIgnoreCase(quizType)) {
                        String selectedStr = selected != null ? selected.toString().trim().toLowerCase() : "";
                        hasAnswer = !selectedStr.isEmpty();

                        // Convert correct answers to lowercase
                        List<String> lowerCorrectAnswers = new ArrayList<>();
                        for (String c : correctAnswers) {
                            lowerCorrectAnswers.add(c.toLowerCase());
                        }

                        isCorrect = hasAnswer && lowerCorrectAnswers.contains(selectedStr);

                    } else if ("enumeration".equalsIgnoreCase(quizType)) {
                        List<String> selectedList = new ArrayList<>();
                        if (selected instanceof List<?>) {
                            for (Object s : (List<?>) selected) {
                                selectedList.add(String.valueOf(s).trim().toLowerCase());
                            }
                        }
                        hasAnswer = !selectedList.isEmpty();

                        Set<String> selSet = new HashSet<>(selectedList);
                        Set<String> corrSet = new HashSet<>();
                        for (String c : correctAnswers) {
                            corrSet.add(c.toLowerCase());
                        }

                        isCorrect = hasAnswer && selSet.equals(corrSet);
                    }

                    if (isCorrect) correctCount++;
                    else incorrectCount++;

                    // Build attempt object
                    JSONObject answerObj = new JSONObject();
                    answerObj.put("quizType", quizType);
                    answerObj.put("question", q.get("question"));
                    answerObj.put("choices", new JSONArray((List<?>) q.get("choices")));

                    // photo fields
                    answerObj.put("photoUrl", q.get("photoUrl") != null ? q.get("photoUrl") : "");
                    String photoPath = q.get("photoPath") != null ? q.get("photoPath").toString().trim() : "";
                    answerObj.put("photoPath", photoPath.isEmpty() ? "Add Image" : photoPath);

                    // selected answer
                    if ("enumeration".equalsIgnoreCase(quizType)) {
                        JSONArray selectedList = new JSONArray();
                        if (selected instanceof List<?>) {
                            for (Object s : (List<?>) selected) {
                                selectedList.put(String.valueOf(s).trim().toLowerCase());
                            }
                        }
                        answerObj.put("selectedAnswer", selectedList);
                    } else {
                        answerObj.put("selectedAnswer", selected != null ? selected.toString().trim().toLowerCase() : "");
                    }

                    // ✅ Add correctAnswer
                    if ("enumeration".equalsIgnoreCase(quizType)) {
                        JSONArray correctList = new JSONArray();
                        for (String c : correctAnswers) {
                            correctList.put(c);
                        }
                        answerObj.put("correctAnswer", correctList);
                    } else {
                        String singleCorrect = correctAnswers.isEmpty() ? "" : correctAnswers.get(0);
                        answerObj.put("correctAnswer", singleCorrect);
                    }

                    // correctness and order
                    answerObj.put("isCorrect", isCorrect);
                    answerObj.put("order", i + 1);

                    JSONObject wrapped = new JSONObject();
                    wrapped.put(String.valueOf(i), answerObj);
                    newAttemptsArray.put(wrapped);
                }

                int total = correctCount + incorrectCount;
                int percentage = (total > 0) ? (correctCount * 100 / total) : 0;

                // Update only the required fields
                quizJson.put("attempts", newAttemptsArray);
                quizJson.put("correctCount", correctCount);
                quizJson.put("incorrectCount", incorrectCount);
                quizJson.put("percentage", percentage);

                // Save back with pretty print
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement parsed = JsonParser.parseString(quizJson.toString());
                String prettyJson = gson.toJson(parsed);

                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(prettyJson.getBytes(StandardCharsets.UTF_8));
                }

                Log.d("QUIZ_OFFLINE", "Quiz progress updated in file: " + file.getAbsolutePath());

            } catch (Exception e) {
                Log.e("QUIZ_OFFLINE", "Failed to save user answers offline", e);
            }
        }

        @SuppressLint("NewApi")
        private void loadOfflineQuiz(String fileName, @Nullable List<Map<String, Object>> incorrectAnswers) {
            try {
                File file = new File(getFilesDir(), fileName);
                if (!file.exists()) {
                    Toast.makeText(this, "Offline quiz not found.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String json;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } else {
                    FileInputStream fis = new FileInputStream(file);
                    json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                    fis.close();
                }

                // Parse the JSON into a Map
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> quizMap = new Gson().fromJson(json, type);

                if (quizMap == null || !quizMap.containsKey("questions")) {
                    showNoQuestionsMessage("⚠️ No questions found in offline quiz.");
                    return;
                }

                Object questionsObj = quizMap.get("questions");
                if (!(questionsObj instanceof Map)) {
                    showNoQuestionsMessage("⚠️ Invalid question format.");
                    return;
                }

                // 👇 Cast and prepare
                Map<String, Object> questionMap = (Map<String, Object>) questionsObj;
                questions = new ArrayList<>();

                for (Object value : questionMap.values()) {
                    if (!(value instanceof Map)) continue;

                    Map<String, Object> original = (Map<String, Object>) value;
                    Map<String, Object> question = new HashMap<>();

                    for (Map.Entry<String, Object> entry : original.entrySet()) {
                        question.put(entry.getKey(), entry.getValue());
                    }

                    // ✳️ Ensure selectedAnswer is always present ("" or [])
                    String quizType = (String) question.getOrDefault("quizType", "multiple choice");
                    if (!question.containsKey("selectedAnswer")) {
                        if ("enumeration".equalsIgnoreCase(quizType)) {
                            question.put("selectedAnswer", new ArrayList<>());
                        } else {
                            question.put("selectedAnswer", "");
                        }
                    }

                    boolean shouldAdd = true;

                    // 🔁 Filter to show only incorrect answers if provided
                    if (incorrectAnswers != null && !incorrectAnswers.isEmpty()) {
                        shouldAdd = false;
                        for (Map<String, Object> incorrect : incorrectAnswers) {
                            boolean matched;
                            if (question.containsKey("questionId") && incorrect.containsKey("questionId")) {
                                matched = Objects.equals(question.get("questionId"), incorrect.get("questionId"));
                            } else {
                                matched = Objects.equals(question.get("question"), incorrect.get("question"));
                            }
                            if (matched) {
                                shouldAdd = true;
                                break;
                            }
                        }
                    }

                    if (shouldAdd) {
                        questions.add(question);
                    }
                }

                // 🔀 Shuffle if needed
                if (shouldShuffle && incorrectAnswers == null) {
                    Collections.shuffle(questions);
                }

                originalQuestionCount = questions.size();

                if (!questions.isEmpty()) {
                    currentQuestionIndex = 0;
                    displayNextValidQuestion();
                } else {
                    showNoQuestionsMessage("⚠️ No matching questions to display.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading offline quiz.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        private void goToOfflineQuizProgressActivity() {
            try {
                String quizTitle = getIntent().getStringExtra("title");
                String ownerUid = getIntent().getStringExtra("owner_uid");
                String username = getIntent().getStringExtra("username");
                String photoUrl = getIntent().getStringExtra("photoUrl");
                String privacy = getIntent().getStringExtra("privacy");
                String fileName = "quiz_" + quizId + ".json";
                File file = new File(getFilesDir(), fileName);

                if (userAnswersList == null || userAnswersList.isEmpty()) {
                    Toast.makeText(this, "No answers to save.", Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONArray previousArray = new JSONArray();
                Map<String, JSONObject> combinedAnswers = new LinkedHashMap<>();
                int originalQuestionCount = userAnswersList.size(); // fallback

                if (file.exists()) {
                    String prevJson;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        prevJson = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    } else {
                        FileInputStream fis = new FileInputStream(file);
                        byte[] buffer = new byte[(int) file.length()];
                        fis.read(buffer);
                        fis.close();
                        prevJson = new String(buffer, StandardCharsets.UTF_8);
                    }

                    JSONObject prevData = new JSONObject(prevJson);
                    previousArray = prevData.optJSONArray("attempts");

                    if (previousArray != null) {
                        for (int i = 0; i < previousArray.length(); i++) {
                            JSONObject wrapped = previousArray.getJSONObject(i);
                            Iterator<String> keys = wrapped.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                JSONObject prevQ = wrapped.getJSONObject(key);
                                String questionText = prevQ.optString("question", "");
                                combinedAnswers.put(questionText, prevQ);
                            }
                        }
                    }

                    originalQuestionCount = prevData.optInt("number_of_items", combinedAnswers.size());
                }

                for (int i = 0; i < userAnswersList.size(); i++) {
                    Map<String, Object> q = userAnswersList.get(i);
                    if (q == null || !q.containsKey("question")) continue;

                    String questionText = String.valueOf(q.get("question"));
                    if (questionText == null || questionText.trim().isEmpty() || questionText.equals("null")) continue;

                    JSONObject newQ = new JSONObject();
                    newQ.put("question", questionText);
                    String quizTypeStr = q.get("quizType") != null ? q.get("quizType").toString() : "";
                    newQ.put("quizType", quizTypeStr);

                    try {
                        Object choicesObj = q.get("choices");
                        newQ.put("choices", choicesObj instanceof List ? new JSONArray((List<?>) choicesObj) : new JSONArray());
                    } catch (Exception ignored) {
                        newQ.put("choices", new JSONArray());
                    }

                    // correctAnswer may be String or JSONArray
                    try {
                        Object correctObj = q.get("correctAnswer");
                        if (correctObj instanceof List) {
                            newQ.put("correctAnswer", new JSONArray((List<?>) correctObj));
                        } else {
                            newQ.put("correctAnswer", String.valueOf(correctObj));
                        }
                    } catch (Exception ignored) {
                        newQ.put("correctAnswer", "");
                    }

                    // selectedAnswer may be String or JSONArray
                    try {
                        Object selectedObj = q.get("selectedAnswer");
                        if (selectedObj instanceof List) {
                            newQ.put("selectedAnswer", new JSONArray((List<?>) selectedObj));
                        } else {
                            newQ.put("selectedAnswer", String.valueOf(selectedObj));
                        }
                    } catch (Exception ignored) {
                        newQ.put("selectedAnswer", "");
                    }

                    newQ.put("photoUrl", q.getOrDefault("photoUrl", ""));
                    String localPath = String.valueOf(q.getOrDefault("localPhotoPath", ""));
                    newQ.put("photoPath", localPath.isEmpty() ? "Add Image" : localPath);

                    boolean isCorrect = Boolean.TRUE.equals(q.get("isCorrect"));
                    newQ.put("isCorrect", isCorrect);
                    newQ.put("order", i + 1);

                    if (combinedAnswers.containsKey(questionText)) {
                        JSONObject prevQ = combinedAnswers.get(questionText);
                        boolean wasCorrect = prevQ.optBoolean("isCorrect", false);
                        if (!wasCorrect || isCorrect) {
                            combinedAnswers.put(questionText, newQ);
                        }
                    } else {
                        combinedAnswers.put(questionText, newQ);
                    }
                }

                // Final formatting
                int correct = 0;
                int questionIndex = 0;
                JSONArray finalAttemptsArray = new JSONArray();
                JSONObject questionsObject = new JSONObject();   // Map of {"0": {...}, "1": {...}}

                for (Map.Entry<String, JSONObject> entry : combinedAnswers.entrySet()) {
                    JSONObject mergedQ = entry.getValue();
                    mergedQ.put("order", questionIndex + 1);

                    // ✅ Wrap in an object with the index key
                    JSONObject wrapped = new JSONObject();
                    wrapped.put(String.valueOf(questionIndex), mergedQ);

                    // ✅ Add to both final arrays
                    finalAttemptsArray.put(wrapped);
                    questionsObject.put(String.valueOf(questionIndex), new JSONObject(mergedQ.toString()));

                    if (mergedQ.optBoolean("isCorrect", false)) {
                        correct++;
                    }

                    questionIndex++;
                }

                int incorrect = originalQuestionCount - correct;
                if (incorrect < 0) incorrect = 0;

                JSONObject createdAt = new JSONObject();
                Timestamp now = Timestamp.now();
                createdAt.put("seconds", now.getSeconds());
                createdAt.put("nanoseconds", now.getNanoseconds());

                JSONObject accessUsers = new JSONObject();
                accessUsers.put(ownerUid, "Owner");
                if (ownerUid != null && !ownerUid.isEmpty()) {
                    accessUsers.put(ownerUid, "Owner");
                } else {
                    Log.e("QUIZ_SAVE", "❌ ownerUid is NULL — skipping Owner assignment");
                }

                JSONObject data = new JSONObject();
                data.put("quizId", quizId);
                data.put("title", quizTitle);
                data.put("questions", questionsObject);
                data.put("attempts", finalAttemptsArray);
                data.put("number_of_items", originalQuestionCount);
                data.put("correctCount", correct);
                data.put("incorrectCount", incorrect);
                data.put("percentage", originalQuestionCount == 0 ? 0 : (int) (((double) correct / originalQuestionCount) * 100));
                data.put("created_at", createdAt);
                data.put("privacy", privacy != null ? privacy : "Private");
                data.put("owner_uid", ownerUid);
                data.put("username", username);
                data.put("photoUrl", photoUrl != null ? photoUrl : "");
                data.put("accessUsers", accessUsers);
                data.put("id", quizId);
                data.put("fileName", fileName);

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data.toString().getBytes(StandardCharsets.UTF_8));
                fos.close();

                Log.d("QUIZ_SAVE", "Offline quiz progress saved successfully.");
                Log.d("QUIZ_SAVE_JSON", data.toString(2));  // Pretty print JSON

                Intent intent = new Intent(this, QuizProgressActivity.class);
                intent.putExtra("progressFileName", fileName);
                startActivity(intent);

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("QUIZ_SAVE", "Error saving offline quiz progress", e);
                Toast.makeText(this, "Failed to save offline quiz progress: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                                    Map<String, Object> percentData = new HashMap<>();
                                    percentData.put("percentage", percentage);

                                });

                        db.collection("users").document(userId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (!userDoc.exists()) return;

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
                                                item.put("progress", percentage);
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
                if (!"retake_incorrect_only".equals(mode)) {
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
            dialog.setCancelable(false);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.show();

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
