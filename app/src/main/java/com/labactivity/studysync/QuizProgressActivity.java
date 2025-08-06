package com.labactivity.studysync;

import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.common.reflect.TypeToken;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuizProgressActivity extends AppCompatActivity {

    private TextView quizTitleText, correctText, incorrectText, progressPercentageText, retake_quiz_btn;
    private ImageView back_button;
    private Button review_questions_btn;

    private String quizId;

    private ProgressBar progressCircle;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_progress);
        boolean isOffline = getIntent().getBooleanExtra("isOffline", false);

        quizTitleText = findViewById(R.id.txtView_quiz_title);
        correctText = findViewById(R.id.know_items);
        incorrectText = findViewById(R.id.still_learning_items);
        progressPercentageText = findViewById(R.id.progress_percentage);
        progressCircle = findViewById(R.id.stats_progressbar);

        db = FirebaseFirestore.getInstance();

        back_button = findViewById(R.id.back_button);
        review_questions_btn = findViewById(R.id.review_questions_btn);
        retake_quiz_btn = findViewById(R.id.retake_quiz_btn);

        quizTitleText.setText("Loading...");
        correctText.setText("-");
        incorrectText.setText("-");
        progressPercentageText.setText("...");
        progressCircle.setProgress(0);

        quizId = getIntent().getStringExtra("quizId");

        if (quizId == null || quizId.isEmpty()) {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (isOffline) {
            String quizTitle = getIntent().getStringExtra("quizTitle");
            if (quizTitle != null && !quizTitle.trim().isEmpty()) {
                quizTitleText.setText(quizTitle);
            } else {
                quizTitleText.setText("Untitled Quiz");
            }

            File file = new File(getFilesDir(), "quiz_" + quizId + ".json");

            if (file.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String json = new String(data);
                    JSONObject obj = new JSONObject(json);
                    String title = obj.optString("quizTitle", "Untitled Quiz");

                    JSONArray questionsArray = obj.getJSONArray("attempts");
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    List<Map<String, Object>> userAnswersList = new Gson().fromJson(questionsArray.toString(), listType);

                    displayQuizProgressOffline();
                    displayOfflineAnsweredQuestions((List<Map<String, Object>>) userAnswersList);

                } catch (Exception e) {
                    Log.e("OFFLINE_LOAD", "Failed to read offline quiz data", e);
                    Toast.makeText(this, "Failed to load offline data", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Offline quiz data not found", Toast.LENGTH_SHORT).show();
            }

            int correct = getIntent().getIntExtra("score", 0);
            int incorrect = getIntent().getIntExtra("incorrect", 0);
            int percentage = getIntent().getIntExtra("percentage", 0);

            correctText.setText(correct + " Items");
            incorrectText.setText(incorrect + " Items");
            progressCircle.setProgress(percentage);
            progressPercentageText.setText(percentage + "%");

            if (percentage == 100) {
                review_questions_btn.setVisibility(View.GONE);
            }
        } else {
            db.collection("quiz").document(quizId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    displayQuizProgress(documentSnapshot); // ✅ Only when online
                    displayAnsweredQuestions(); // ✅ Answers from Firestore only
                } else {
                    Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to fetch quiz", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        back_button.setOnClickListener(v -> onBackPressed());

        retake_quiz_btn.setOnClickListener(v -> {
            Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);

            if (isOffline) {
                intent.putExtra("isOffline", true);
                intent.putExtra("mode", "normal");
                intent.putExtra("retakeFull", true);

                if (quizId != null) {
                    intent.putExtra("quizId", quizId);
                }

                if (getIntent().hasExtra("questionsJson")) {
                    intent.putExtra("questionsJson", getIntent().getStringExtra("questionsJson"));
                }

                String photoUrl = getIntent().getStringExtra("photoUrl");

                if (photoUrl != null) {
                    intent.putExtra("photoUrl", photoUrl);
                }
            } else {
                intent.putExtra("isOffline", false);
                intent.putExtra("quizId", quizId);
                intent.putExtra("mode", "normal");
            }

            startActivity(intent);
            finish();
        });

        review_questions_btn.setOnClickListener(v -> {
            if (isOffline) {
                try {
                    Log.d("QUIZ_REVIEW", "Opening offline file...");

                    String quizId = getIntent().getStringExtra("quizId");
                    String fileName = getIntent().getStringExtra("progressFileName");

                    if (fileName == null && quizId != null) {
                        fileName = "quiz_" + quizId + ".json";
                    }

                    if (fileName == null) {
                        Log.e("QUIZPROGRESS", "Missing file name for progress!");
                        Toast.makeText(this, "No offline progress file specified.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File file = new File(getFilesDir(), fileName);
                    if (!file.exists()) {
                        Toast.makeText(this, "No offline quiz data to review.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String json = new String(data);
                    Log.d("QUIZ_REVIEW", "Offline JSON loaded: " + json);

                    JSONObject obj = new JSONObject(json);
                    if (!obj.has("attempts")) {
                        Toast.makeText(this, "No answers found in offline file.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    JSONArray answersArray = obj.getJSONArray("attempts");
                    ArrayList<Map<String, Object>> incorrectAnswersOnly = new ArrayList<>();

                    for (int i = 0; i < answersArray.length(); i++) {
                        JSONObject wrapped = answersArray.getJSONObject(i); // e.g., { "0": { ... } }
                        Iterator<String> keys = wrapped.keys();

                        if (keys.hasNext()) {
                            String key = keys.next();
                            JSONObject a = wrapped.getJSONObject(key);

                            if (a.has("isCorrect") && !a.getBoolean("isCorrect")) {
                                Map<String, Object> map = new HashMap<>();
                                map.put("question", a.optString("question", "No question"));
                                map.put("quizType", a.optString("quizType", ""));

                                // Handle selectedAnswer
                                if (a.has("selectedAnswer")) {
                                    Object selected = a.get("selectedAnswer");
                                    if (selected instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) selected;
                                        List<String> selectedList = new ArrayList<>();
                                        for (int j = 0; j < arr.length(); j++) {
                                            selectedList.add(arr.getString(j));
                                        }
                                        map.put("userAnswer", selectedList);
                                    } else {
                                        map.put("userAnswer", a.optString("selectedAnswer", ""));
                                    }
                                }

                                // Handle correctAnswer
                                if (a.has("correctAnswer")) {
                                    Object correct = a.get("correctAnswer");
                                    if (correct instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) correct;
                                        List<String> correctList = new ArrayList<>();
                                        for (int j = 0; j < arr.length(); j++) {
                                            correctList.add(arr.getString(j));
                                        }
                                        map.put("correctAnswers", correctList);
                                    } else {
                                        map.put("correctAnswers", a.optString("correctAnswer", ""));
                                    }
                                }

                                if (a.has("photoUrl")) {
                                    map.put("photoUrl", a.optString("photoUrl", ""));
                                }

                                map.put("isCorrect", false);
                                incorrectAnswersOnly.add(map);
                            }
                        }
                    }

                    if (incorrectAnswersOnly.isEmpty()) {
                        Toast.makeText(this, "All answers were correct. Nothing to review.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                    intent.putExtra("quizId", quizId);
                    intent.putExtra("mode", "retake_incorrect_only");
                    intent.putExtra("isOffline", true);
                    intent.putExtra("userAnswersList", new Gson().toJson(incorrectAnswersOnly));
                    intent.putExtra("progressFileName", fileName);

                    startActivity(intent);
                    finish();

                } catch (Exception e) {
                    Log.e("QUIZ_REVIEW", "Exception occurred", e);
                    Toast.makeText(this, "Failed to prepare offline review data.", Toast.LENGTH_SHORT).show();
                }
            }
            else {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                db.collection("quiz")
                        .document(quizId)
                        .collection("quiz_attempt")
                        .document(userId)
                        .get()
                        .addOnSuccessListener(attemptDoc -> {
                            if (attemptDoc.exists()) {
                                List<Map<String, Object>> answered = (List<Map<String, Object>>) attemptDoc.get("answeredQuestions");

                                if (answered != null && !answered.isEmpty()) {
                                    ArrayList<Map<String, Object>> incorrectOnly = new ArrayList<>();
                                    for (Map<String, Object> ans : answered) {
                                        Boolean isCorrect = (Boolean) ans.get("isCorrect");
                                        if (isCorrect != null && !isCorrect) {
                                            incorrectOnly.add(ans);
                                        }
                                    }

                                    if (incorrectOnly.isEmpty()) {
                                        Toast.makeText(this, "All answers were correct. Nothing to review.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                                    intent.putExtra("quizId", quizId);
                                    intent.putExtra("photoUrl", getIntent().getStringExtra("photoUrl"));
                                    intent.putExtra("mode", "retake_incorrect_only");
                                    intent.putExtra("userAnswersList", new Gson().toJson(incorrectOnly));
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(this, "No questions to review.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(this, "No quiz attempt found.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to load review data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    private void displayQuizProgress(DocumentSnapshot doc) {
        String title = doc.getString("title");
        if (title != null && !title.trim().isEmpty()) {
            quizTitleText.setText(title);
        } else {
            quizTitleText.setText("Untitled Quiz");
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("quiz")
                .document(quizId)
                .collection("quiz_attempt")
                .document(userId)
                .get()
                .addOnSuccessListener(attemptDoc -> {
                    if (attemptDoc.exists()) {
                        Long score = attemptDoc.getLong("score");
                        Long total = attemptDoc.getLong("total");

                        if (score != null && total != null && total > 0) {
                            int correct = score.intValue();
                            int totalItems = total.intValue();

                            int incorrect = totalItems - correct;
                            int percentage = Math.round((correct / (float) totalItems) * 100);

                            correctText.setText(correct + " Items");
                            incorrectText.setText(incorrect + " Items");
                            progressCircle.setProgress(percentage);
                            progressPercentageText.setText(percentage + "%");

                            if (percentage == 100) {
                                review_questions_btn.setVisibility(View.GONE);
                            }
                        } else {
                            Toast.makeText(this, "Invalid attempt data", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        correctText.setText("0 Items");
                        incorrectText.setText("0 Items");
                        progressCircle.setProgress(0);
                        progressPercentageText.setText("0%");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load attempt data", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayAnsweredQuestions() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        LinearLayout answersLayout = findViewById(R.id.answers_linear_layout);
        LayoutInflater inflater = LayoutInflater.from(this);

        db.collection("quiz")
                .document(quizId)
                .collection("quiz_attempt")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<Map<String, Object>> answeredQuestions = (List<Map<String, Object>>) doc.get("answeredQuestions");
                        if (answeredQuestions != null) {
                            Collections.sort(answeredQuestions, (a, b) -> {
                                int orderA = a.get("order") != null ? ((Number) a.get("order")).intValue() : 0;
                                int orderB = b.get("order") != null ? ((Number) b.get("order")).intValue() : 0;
                                return Integer.compare(orderA, orderB);
                            });
                        }
                        if (answeredQuestions != null) {

                            int number = 1;
                            for (Map<String, Object> q : answeredQuestions) {
                                View answerView = inflater.inflate(R.layout.item_quiz_attempt_view, answersLayout, false);
                                LinearLayout answerLinearLayout = answerView.findViewById(R.id.answer_linear_layout);


                                TextView questionText = answerView.findViewById(R.id.question_text);
                                TextView correctAnswerText = answerView.findViewById(R.id.correct_answer_text);
                                TextView selectedWrongAnswerText = answerView.findViewById(R.id.selected_wrong_answer_text);
                                View wrongAnswerContainer = answerView.findViewById(R.id.selected_wrong_answer_container);
                                CardView questionImageCard = answerView.findViewById(R.id.question_img_card);

                                ImageView questionImageView = answerView.findViewById(R.id.question_image); // must exist in XML
                                String photoUrl = (String) q.get("photoUrl");

                                if (photoUrl != null && !photoUrl.trim().isEmpty() && !photoUrl.equals("Add Image")) {
                                    questionImageCard.setVisibility(VISIBLE);
                                    questionImageView.setVisibility(VISIBLE);

                                    Glide.with(this)
                                            .load(photoUrl)
                                            .into(questionImageView);
                                } else {
                                    questionImageCard.setVisibility(View.GONE);
                                }

                                String question = (String) q.get("question");
                                Object correctObj = q.get("correct");
                                Object selectedObj = q.get("selected");
                                boolean isCorrect = Boolean.TRUE.equals(q.get("isCorrect"));

                                questionText.setText(number + ". " + question);
                                number++;

                                TextView statusLabel = answerView.findViewById(R.id.status_label);
                                if (isCorrect) {
                                    statusLabel.setText("Correct");
                                    statusLabel.setBackgroundColor(Color.parseColor("#00BF63"));
                                    wrongAnswerContainer.setVisibility(View.GONE);
                                } else {
                                    statusLabel.setText("Incorrect");
                                    statusLabel.setBackgroundColor(Color.parseColor("#F24F4F"));
                                    wrongAnswerContainer.setVisibility(VISIBLE);
                                    answerLinearLayout.setBackgroundResource(R.drawable.light_red_stroke_bg);
                                }

                                if (correctObj instanceof String && selectedObj instanceof String) {
                                    String correct = (String) correctObj;
                                    String selected = (String) selectedObj;

                                    correctAnswerText.setText(correct);

                                    if (!isCorrect && !correct.equals(selected)) {
                                        selectedWrongAnswerText.setText(selected);
                                        wrongAnswerContainer.setVisibility(VISIBLE);
                                    } else {
                                        wrongAnswerContainer.setVisibility(View.GONE);
                                    }

                                } else if (correctObj instanceof List && selectedObj instanceof List) {
                                    List<String> correctList = (List<String>) correctObj;
                                    List<String> selectedList = (List<String>) selectedObj;

                                    String correctStr = android.text.TextUtils.join(", ", correctList);
                                    String selectedStr = android.text.TextUtils.join(", ", selectedList);

                                    correctAnswerText.setText(correctStr);

                                    if (!isCorrect && !correctStr.equals(selectedStr)) {
                                        selectedWrongAnswerText.setText(selectedStr);
                                        wrongAnswerContainer.setVisibility(VISIBLE);
                                    } else {
                                        wrongAnswerContainer.setVisibility(View.GONE);
                                    }
                                }
                                answersLayout.addView(answerView);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load answers", Toast.LENGTH_SHORT).show());
    }

    private void displayQuizProgressOffline() {
        try {
            File file = new File(getFilesDir(), "quiz_" + quizId + ".json");
            if (!file.exists()) {
                quizTitleText.setText("Untitled Quiz");
                correctText.setText("0 Items");
                incorrectText.setText("0 Items");
                progressCircle.setProgress(0);
                progressPercentageText.setText("0%");
                return;
            }

            String json = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                json = new String(Files.readAllBytes(file.toPath()));
            }

            JSONObject jsonObject = new JSONObject(json);

            // ✅ Update title from Firestore if missing
            String quizTitle = jsonObject.optString("title", "");
            if (quizTitle.isEmpty()) {
                FirebaseFirestore.getInstance()
                        .collection("quiz")
                        .document(quizId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            String title = doc.getString("title");
                            if (title == null || title.trim().isEmpty()) title = "Untitled Quiz";
                            try {
                                jsonObject.put("title", title);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Files.write(file.toPath(), jsonObject.toString().getBytes());
                                }
                                quizTitleText.setText(title);
                            } catch (JSONException | IOException e) {
                                e.printStackTrace();
                                quizTitleText.setText("Untitled Quiz");
                            }
                        })
                        .addOnFailureListener(e -> quizTitleText.setText("Untitled Quiz"));
            } else {
                quizTitleText.setText(quizTitle);
            }

            // ✅ Cumulative Progress Logic
            int correctCount = 0;
            int totalItems = 0;

            JSONArray questionsArray = jsonObject.optJSONArray("questions");
            if (questionsArray != null) {
                for (int i = 0; i < questionsArray.length(); i++) {
                    JSONObject questionObj = questionsArray.getJSONObject(i);

                    String quizType = questionObj.optString("quizType", "");
                    String selected = questionObj.optString("selectedAnswer", "");
                    JSONArray choices = questionObj.optJSONArray("choices");

                    // Only check if type is multiple choice and valid choice list
                    if ("multiple choice".equals(quizType) && choices != null && selected != null && !selected.isEmpty()) {
                        int correctIndex = questionObj.optInt("correctAnswerIndex", -1);
                        int selectedIndex = -1;

                        // Get index of selected answer in choices
                        for (int j = 0; j < choices.length(); j++) {
                            if (selected.equals(choices.getString(j))) {
                                selectedIndex = j;
                                break;
                            }
                        }

                        if (correctIndex != -1 && selectedIndex != -1) {
                            if (selectedIndex == correctIndex) {
                                correctCount++;
                            }
                            totalItems++;
                        }
                    }
                }
            }

            int incorrectCount = totalItems - correctCount;
            int percentage = totalItems == 0 ? 0 : (int) (((double) correctCount / totalItems) * 100);

            correctText.setText(correctCount + " Items");
            incorrectText.setText(incorrectCount + " Items");
            progressCircle.setProgress(percentage);
            progressPercentageText.setText(percentage + "%");

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            quizTitleText.setText("Untitled Quiz");
            correctText.setText("0 Items");
            incorrectText.setText("0 Items");
            progressCircle.setProgress(0);
            progressPercentageText.setText("0%");
        }
    }


    @SuppressLint("ResourceAsColor")
    private void displayOfflineAnsweredQuestions(List<Map<String, Object>> userAnswersList) {
        if (userAnswersList == null || userAnswersList.isEmpty()) {
            Log.w("QUIZ_DISPLAY", "No answers to display.");
            return;
        }

        LinearLayout answersLayout = findViewById(R.id.answers_linear_layout);
        LayoutInflater inflater = LayoutInflater.from(this);
        int number = 1;
        for (Map<String, Object> wrapped : userAnswersList) {
            for (Map.Entry<String, Object> entry : wrapped.entrySet()) {
                Map<String, Object> q = (Map<String, Object>) entry.getValue();

                View view = inflater.inflate(R.layout.item_quiz_attempt_view, answersLayout, false);

            // View bindings
            TextView questionText = view.findViewById(R.id.question_text);
            TextView statusLabel = view.findViewById(R.id.status_label);
            TextView correctAnswerText = view.findViewById(R.id.correct_answer_text);
            TextView selectedWrongAnswerText = view.findViewById(R.id.selected_wrong_answer_text);
            LinearLayout selectedWrongAnswerContainer = view.findViewById(R.id.selected_wrong_answer_container);
            ImageView imageView = view.findViewById(R.id.question_image);
            View imageCard = view.findViewById(R.id.question_img_card);
            LinearLayout containerLayout = view.findViewById(R.id.answer_linear_layout);
            ImageView checkIconCorrect = view.findViewById(R.id.correct_answer_container).findViewById(R.id.checkIcon);
            ImageView checkIconWrong = selectedWrongAnswerContainer.findViewById(R.id.wrongIcon);

            // Extract data
            String questionTextStr = (String) q.getOrDefault("question", "No question");
            Object correctObj = q.get("correctAnswer");
            Object selectedObj = q.get("selectedAnswer");
            String photoUrl = (String) q.getOrDefault("photoUrl", "");
            boolean isCorrect = Boolean.TRUE.equals(q.get("isCorrect"));

            // Parse correct answers
            List<String> correctAnswers = new ArrayList<>();
            if (correctObj instanceof List) {
                for (Object obj : (List<?>) correctObj) {
                    correctAnswers.add(String.valueOf(obj).trim());
                }
            } else if (correctObj instanceof String) {
                correctAnswers.add(((String) correctObj).trim());
            }

            // Parse selected answers
            List<String> selectedAnswers = new ArrayList<>();
            if (selectedObj instanceof List) {
                for (Object obj : (List<?>) selectedObj) {
                    selectedAnswers.add(String.valueOf(obj).trim());
                }
            } else if (selectedObj instanceof String) {
                String sel = ((String) selectedObj).trim();
                if (!sel.isEmpty()) selectedAnswers.add(sel);
            }

            // Set question text
            questionText.setText(number + ". " + questionTextStr);
            number++;

            // Correctness UI
            if (isCorrect) {
                statusLabel.setText("Correct");
                statusLabel.setBackgroundColor(Color.parseColor("#00BF63"));
                containerLayout.setBackgroundResource(R.drawable.green_stroke_bg);

                checkIconCorrect.setVisibility(View.VISIBLE);

                selectedWrongAnswerContainer.setVisibility(View.GONE); // hide wrong answer container
            } else {
                statusLabel.setText("Incorrect");
                statusLabel.setBackgroundColor(Color.parseColor("#F24F4F"));
                containerLayout.setBackgroundResource(R.drawable.light_red_stroke_bg);

                checkIconWrong.setVisibility(View.VISIBLE);

                selectedWrongAnswerContainer.setVisibility(View.VISIBLE);
            }

            // Set correct answer text
            if (!correctAnswers.isEmpty()) {
                correctAnswerText.setText(
                        TextUtils.join(", ", correctAnswers.stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toList()))
                );
            } else {
                correctAnswerText.setText("No correct answer");
            }

            // Set selected wrong answer text (only if incorrect)
            if (!isCorrect && !selectedAnswers.isEmpty()) {
                selectedWrongAnswerText.setText(TextUtils.join(", ", selectedAnswers.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList())));
            }

            if (!TextUtils.isEmpty(photoUrl) && !"".equals(photoUrl)) {
                imageCard.setVisibility(View.VISIBLE);

                Glide.with(this)
                        .load(photoUrl.trim())
                        .placeholder(R.drawable.image)
                        .error(R.drawable.image)
                        .into(imageView);
            } else {
                imageCard.setVisibility(View.GONE);
            }

            // Add view to parent layout
                answersLayout.addView(view);
                number++;
            }
        }
    }

}