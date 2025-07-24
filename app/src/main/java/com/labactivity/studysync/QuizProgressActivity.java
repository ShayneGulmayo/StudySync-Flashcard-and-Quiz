package com.labactivity.studysync;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.common.reflect.TypeToken;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizProgressActivity extends AppCompatActivity {

    private TextView quizTitleText, correctText, incorrectText, progressPercentageText;
    private ProgressBar progressCircle;
    private FirebaseFirestore db;

    private ImageView back_button;
    private Button review_questions_btn;
    private TextView retake_quiz_btn;

    private String quizId;

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

            // ✅ Build correct progress file path using quizId
            File file = new File(getCacheDir(), "progress_" + quizId + ".json");

            if (file.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String json = new String(data);
                    JSONObject obj = new JSONObject(json);
                    String title = obj.optString("quizTitle", "Untitled Quiz");

                    JSONArray questionsArray = obj.getJSONArray("answeredQuestions");
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    List<Map<String, Object>> userAnswersList = new Gson().fromJson(questionsArray.toString(), listType);

                    displayOfflineQuizProgress(userAnswersList, title);
                    displayOfflineAnsweredQuestions(userAnswersList);

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
            // 🔹 ONLINE MODE
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
            Intent intent;
            if (isOffline) {
                // ✅ Retake OFFLINE quiz
                intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                intent.putExtra("mode", "normal");
                intent.putExtra("isOffline", true);  // Important
                intent.putExtra("retakeFull", true);

                String photoUrl = getIntent().getStringExtra("photoUrl");
                if (photoUrl != null) {
                    intent.putExtra("photoUrl", photoUrl);
                }
            } else {
                // ✅ Retake ONLINE quiz
                intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                intent.putExtra("quizId", quizId);
                intent.putExtra("mode", "normal");
            }

            startActivity(intent);
            finish();
        });


        review_questions_btn.setOnClickListener(v -> {
            if (isOffline) {
                // ✅ OFFLINE REVIEW FLOW
                try {
                    Log.d("QUIZ_REVIEW", "Opening offline file...");

                    String fileName = getIntent().getStringExtra("progressFileName");
                    if (fileName == null) {
                        Log.e("QUIZPROGRESS", "Missing file name for progress!");
                        return;
                    }
                    File file = new File(getCacheDir(), fileName);

                    if (!file.exists()) {
                        Toast.makeText(this, "No offline quiz data to review.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String json = new String(data);
                    Log.d("QUIZ_REVIEW", "JSON loaded: " + json);

                    JSONObject obj = new JSONObject(json);
                    JSONArray answersArray = obj.getJSONArray("userAnswers");

                    ArrayList<Map<String, Object>> incorrectAnswersOnly = new ArrayList<>();
                    for (int i = 0; i < answersArray.length(); i++) {
                        JSONObject a = answersArray.getJSONObject(i);

                        if (!a.optBoolean("isCorrect", true)) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("question", a.getString("question"));

                            if (a.get("selected") instanceof JSONArray) {
                                JSONArray selectedArray = a.getJSONArray("selected");
                                List<String> selectedList = new ArrayList<>();

                                for (int k = 0; k < selectedArray.length(); k++) {
                                    selectedList.add(selectedArray.getString(k));
                                }
                                map.put("userAnswer", selectedList);
                            } else {
                                map.put("userAnswer", a.getString("selected"));
                            }

                            map.put("correctAnswers", a.get("correctAnswers"));
                            map.put("isCorrect", false);
                            incorrectAnswersOnly.add(map);
                        }
                    }
                    Log.d("QUIZ_REVIEW", "Incorrect answers count: " + incorrectAnswersOnly.size());
                    if (incorrectAnswersOnly.isEmpty()) {
                        Toast.makeText(this, "All answers were correct. Nothing to review.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String listJson = new Gson().toJson(incorrectAnswersOnly);
                    Log.d("QUIZ_REVIEW", "Launching QuizViewActivity with data: " + listJson);
                    Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                    intent.putExtra("quizId", quizId);
                    intent.putExtra("mode", "retake_incorrect_only");
                    intent.putExtra("isOffline", true);
                    intent.putExtra("userAnswersList", new Gson().toJson(incorrectAnswersOnly));
                    startActivity(intent);
                    finish();

                } catch (Exception e) {
                    Log.e("QUIZ_REVIEW", "Exception occurred", e);
                    Toast.makeText(this, "Failed to prepare review data.", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

            } else {
                // ✅ ONLINE REVIEW FLOW
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
                                    questionImageCard.setVisibility(View.VISIBLE);
                                    questionImageView.setVisibility(View.VISIBLE);

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
                                    wrongAnswerContainer.setVisibility(View.VISIBLE);
                                    answerLinearLayout.setBackgroundResource(R.drawable.light_red_stroke_bg);
                                }

                                if (correctObj instanceof String && selectedObj instanceof String) {
                                    String correct = (String) correctObj;
                                    String selected = (String) selectedObj;

                                    correctAnswerText.setText(correct);

                                    if (!isCorrect && !correct.equals(selected)) {
                                        selectedWrongAnswerText.setText(selected);
                                        wrongAnswerContainer.setVisibility(View.VISIBLE);
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
                                        wrongAnswerContainer.setVisibility(View.VISIBLE);
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


    private void displayOfflineQuizProgress(List<Map<String, Object>> userAnswersList, String quizTitle) {
        if (userAnswersList == null || userAnswersList.isEmpty()) return;

        TextView quizTitleText = findViewById(R.id.txtView_quiz_title);
        TextView correctText = findViewById(R.id.know_items);
        TextView incorrectText = findViewById(R.id.still_learning_items);
        TextView progressPercentageText = findViewById(R.id.progress_percentage);
        ProgressBar progressBar = findViewById(R.id.stats_progressbar);

        int correctCount = 0;
        for (Map<String, Object> q : userAnswersList) {
            if (Boolean.TRUE.equals(q.get("isCorrect"))) {
                correctCount++;
            }
        }

        int total = userAnswersList.size();
        int incorrectCount = total - correctCount;
        int percentage = total == 0 ? 0 : (int) ((correctCount / (double) total) * 100);

        quizTitleText.setText(quizTitle != null ? quizTitle : "Untitled Quiz");
        correctText.setText("Correct: " + correctCount);
        incorrectText.setText("Incorrect: " + incorrectCount);
        progressPercentageText.setText(percentage + "%");
        progressBar.setProgress(percentage);
    }


    private void displayOfflineAnsweredQuestions(List<Map<String, Object>> userAnswersList) {
        if (userAnswersList == null || userAnswersList.isEmpty()) {
            Log.w("QUIZ_DISPLAY", "No answers to display.");
            return;
        }

        LinearLayout answersLayout = findViewById(R.id.answers_linear_layout);
        LayoutInflater inflater = LayoutInflater.from(this);

        int number = 1;
        for (Map<String, Object> q : userAnswersList) {
            View view = inflater.inflate(R.layout.item_quiz_attempt_view, answersLayout, false);

            String questionTextStr = (String) q.getOrDefault("question", "No question");
            List<String> correctAnswers = (List<String>) q.getOrDefault("correctAnswers", new ArrayList<>());
            List<String> userAnswers = (List<String>) q.getOrDefault("userAnswer", new ArrayList<>());
            String photoPath = (String) q.getOrDefault("photoPath", "");
            boolean isCorrect = Boolean.TRUE.equals(q.get("isCorrect"));

            TextView questionText = view.findViewById(R.id.question_text);
            TextView statusLabel = view.findViewById(R.id.status_label);
            LinearLayout correctAnswerContainer = view.findViewById(R.id.correct_answer_container);
            LinearLayout selectedWrongAnswerContainer = view.findViewById(R.id.selected_wrong_answer_container);
            ImageView imageView = view.findViewById(R.id.question_image);
            View imageCard = view.findViewById(R.id.question_img_card);

            questionText.setText(number + ". " + questionTextStr);
            number++;

            // Set status
            if (isCorrect) {
                statusLabel.setText("Correct");
                statusLabel.setBackgroundColor(Color.parseColor("#00BF63"));
                selectedWrongAnswerContainer.setVisibility(View.GONE);
            } else {
                statusLabel.setText("Incorrect");
                statusLabel.setBackgroundColor(Color.parseColor("#F24F4F"));
                selectedWrongAnswerContainer.setVisibility(View.VISIBLE);
            }

            // Correct answers
            correctAnswerContainer.removeAllViews();
            for (String ans : correctAnswers) {
                if (!TextUtils.isEmpty(ans.trim())) {
                    TextView tv = new TextView(this);
                    tv.setText("✓ " + ans);
                    tv.setTextColor(Color.parseColor("#006400"));
                    tv.setTextSize(16);
                    correctAnswerContainer.addView(tv);
                }
            }

            // User's wrong answers
            selectedWrongAnswerContainer.removeAllViews();
            if (!isCorrect && userAnswers != null) {
                for (String ans : userAnswers) {
                    if (!TextUtils.isEmpty(ans.trim())) {
                        TextView tv = new TextView(this);
                        tv.setText("✗ " + ans);
                        tv.setTextColor(Color.parseColor("#B22222"));
                        tv.setTextSize(16);
                        selectedWrongAnswerContainer.addView(tv);
                    }
                }
            }

            // Load local image if present
            if (!TextUtils.isEmpty(photoPath)) {
                File imgFile = new File(photoPath);
                if (imgFile.exists()) {
                    imageView.setImageURI(Uri.fromFile(imgFile));
                    imageCard.setVisibility(View.VISIBLE);
                } else {
                    imageCard.setVisibility(View.GONE);
                }
            } else {
                imageCard.setVisibility(View.GONE);
            }

            answersLayout.addView(view);
        }
    }
}
