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
            // 🔹 OFFLINE MODE
            String quizTitle = getIntent().getStringExtra("quizTitle");
            if (quizTitle != null && !quizTitle.trim().isEmpty()) {
                quizTitleText.setText(quizTitle);
            } else {
                quizTitleText.setText("Untitled Quiz");
            }

            String userAnswersJson = getIntent().getStringExtra("userAnswers");
            if (userAnswersJson != null) {
                try {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("userAnswers", new JSONArray(userAnswersJson));

                    File file = new File(getCacheDir(), "offline_quiz_attempt.json");
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(wrapper.toString().getBytes());
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to cache offline answers", Toast.LENGTH_SHORT).show();
                }

                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> userAnswersList = new Gson().fromJson(userAnswersJson, listType);

                if (userAnswersList != null) {
                    displayOfflineQuizProgress(userAnswersList, quizTitle); // ✅ Progress only shown in offline
                    List<Map<String, Object>> loadedAnswers = loadAnsweredQuestionsFromFile();
                    displayOfflineAnsweredQuestions(loadedAnswers);// ✅ Answers shown offline only
                }
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

                    File file = new File(getCacheDir(), "offline_quiz_attempt.json");
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

        // Get UI elements
        TextView quizTitleText = findViewById(R.id.txtView_quiz_title);
        TextView correctText = findViewById(R.id.know_items);
        TextView incorrectText = findViewById(R.id.still_learning_items);
        TextView progressPercentageText = findViewById(R.id.progress_percentage);
        ProgressBar progressBar = findViewById(R.id.stats_progressbar);

        int totalQuestions = userAnswersList.size();
        int correctCount = 0;

        for (Map<String, Object> q : userAnswersList) {
            if ((boolean) q.getOrDefault("isCorrect", false)) {
                correctCount++;
            }
        }

        int incorrectCount = totalQuestions - correctCount;
        int percentage = (int) ((correctCount / (double) totalQuestions) * 100);

        quizTitleText.setText(quizTitle != null ? quizTitle : "Untitled Quiz");
        correctText.setText("Correct: " + correctCount);
        incorrectText.setText("Incorrect: " + incorrectCount);
        progressPercentageText.setText(percentage + "%");
        progressBar.setProgress(percentage);
    }


    private List<Map<String, Object>> loadAnsweredQuestionsFromFile() {
        File file = new File(getCacheDir(), "quiz_progress_data.json");
        if (!file.exists()) {
            Log.e("QUIZ_LOAD", "Progress file not found.");
            return Collections.emptyList();
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonStr = new String(data);
            Log.d("QUIZ_LOAD", "Raw file content: " + jsonStr);

            JSONObject json = new JSONObject(jsonStr);
            JSONArray answeredQuestionsArray = json.optJSONArray("answeredQuestions");
            if (answeredQuestionsArray == null) {
                Log.e("QUIZ_LOAD", "answeredQuestions array is missing");
                return Collections.emptyList();
            }

            List<Map<String, Object>> questions = new ArrayList<>();

            for (int i = 0; i < answeredQuestionsArray.length(); i++) {
                JSONObject qObj = answeredQuestionsArray.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();

                map.put("number", qObj.optInt("number"));
                map.put("question", qObj.optString("question"));
                map.put("photoUrl", qObj.optString("photoUrl", null));
                map.put("photoPath", qObj.optString("photoPath", null));
                map.put("isCorrect", qObj.optBoolean("isCorrect", false));

                // Parse correctAnswers
                List<String> correctList = new ArrayList<>();
                Object correctAns = qObj.opt("correctAnswers");
                if (correctAns instanceof JSONArray) {
                    JSONArray arr = (JSONArray) correctAns;
                    for (int j = 0; j < arr.length(); j++) {
                        correctList.add(arr.optString(j, ""));
                    }
                } else if (correctAns != null) {
                    correctList.add(correctAns.toString());
                }
                map.put("correctAnswers", correctList);

                // Parse userAnswer
                List<String> userList = new ArrayList<>();
                Object userAns = qObj.opt("userAnswer");
                if (userAns instanceof JSONArray) {
                    JSONArray arr = (JSONArray) userAns;
                    for (int j = 0; j < arr.length(); j++) {
                        userList.add(arr.optString(j, ""));
                    }
                } else if (userAns != null) {
                    userList.add(userAns.toString());
                }
                map.put("userAnswer", userList);

                questions.add(map);
            }

            return questions;

        } catch (Exception e) {
            Log.e("QUIZ_LOAD", "Error parsing saved progress", e);
            return Collections.emptyList();
        }
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
            boolean isCorrect = (boolean) q.getOrDefault("isCorrect", false);
            String question = (String) q.getOrDefault("question", "No question text");
            String photoPath = (String) q.get("photoPath");

            // Safely cast lists
            List<String> correctAnswers = (List<String>) q.getOrDefault("correctAnswers", new ArrayList<>());
            List<String> userAnswers = (List<String>) q.getOrDefault("userAnswer", new ArrayList<>());

            View answerView = inflater.inflate(R.layout.item_quiz_attempt_view, answersLayout, false);

            TextView questionText = answerView.findViewById(R.id.question_text);
            TextView correctAnswerText = answerView.findViewById(R.id.correct_answer_text);
            TextView selectedWrongAnswerText = answerView.findViewById(R.id.selected_wrong_answer_text);
            View wrongAnswerContainer = answerView.findViewById(R.id.selected_wrong_answer_container);
            ImageView questionImageView = answerView.findViewById(R.id.question_image);
            View questionImgCard = answerView.findViewById(R.id.question_img_card);
            TextView statusLabel = answerView.findViewById(R.id.status_label);

            // Question number and text
            questionText.setText(number + ". " + question);
            number++;

            // Correct/Incorrect UI
            if (isCorrect) {
                statusLabel.setText("Correct");
                statusLabel.setBackgroundColor(Color.parseColor("#00BF63"));
                wrongAnswerContainer.setVisibility(View.GONE);
            } else {
                statusLabel.setText("Incorrect");
                statusLabel.setBackgroundColor(Color.parseColor("#F24F4F"));
                wrongAnswerContainer.setVisibility(View.VISIBLE);
            }

            // Correct answers
            if (!correctAnswers.isEmpty()) {
                correctAnswerText.setText(TextUtils.join(", ", correctAnswers));
            } else {
                correctAnswerText.setText("No correct answer");
            }

            // User's wrong answers
            if (!isCorrect && !userAnswers.isEmpty()) {
                selectedWrongAnswerText.setText(TextUtils.join(", ", userAnswers));
            } else {
                selectedWrongAnswerText.setText("");
            }

            // Image
            if (photoPath != null && !photoPath.isEmpty()) {
                File imageFile = new File(photoPath);
                if (imageFile.exists()) {
                    questionImageView.setImageURI(Uri.fromFile(imageFile));
                    questionImageView.setVisibility(View.VISIBLE);
                    questionImgCard.setVisibility(View.VISIBLE);
                } else {
                    questionImgCard.setVisibility(View.GONE);
                }
            } else {
                questionImgCard.setVisibility(View.GONE);
            }

            answersLayout.addView(answerView);
        }
    }

    private void renderAnswerViews(List<Map<String, Object>> userAnswersList, boolean isOffline) {
        if (!isOffline) return;
        LinearLayout answersLayout = findViewById(R.id.answers_linear_layout);
        LayoutInflater inflater = LayoutInflater.from(this);
        int number = 1;

        for (Map<String, Object> answer : userAnswersList) {
            View answerView = inflater.inflate(R.layout.item_quiz_attempt_view, answersLayout, false);

            TextView questionText = answerView.findViewById(R.id.question_text);
            TextView correctAnswerText = answerView.findViewById(R.id.correct_answer_text);
            TextView selectedWrongAnswerText = answerView.findViewById(R.id.selected_wrong_answer_text);
            View wrongAnswerContainer = answerView.findViewById(R.id.selected_wrong_answer_container);
            TextView statusLabel = answerView.findViewById(R.id.status_label);
            ImageView questionImageView = answerView.findViewById(R.id.question_image);

            questionImageView.setVisibility(View.GONE); // Offline has no image

            String question = (String) answer.get("question");
            Object correct = answer.get("correctAnswers");
            Object userAnswer = answer.get("userAnswer");
            boolean isCorrect = Boolean.TRUE.equals(answer.get("isCorrect"));

            questionText.setText(number + ". " + question);
            number++;

            if (isCorrect) {
                statusLabel.setText("Correct");
                statusLabel.setBackgroundColor(Color.parseColor("#00BF63"));
                wrongAnswerContainer.setVisibility(View.GONE);
            } else {
                statusLabel.setText("Incorrect");
                statusLabel.setBackgroundColor(Color.parseColor("#F24F4F"));
                wrongAnswerContainer.setVisibility(View.VISIBLE);
            }

            if (correct instanceof List && userAnswer instanceof List) {
                String correctStr = TextUtils.join(", ", (List<?>) correct);
                String selectedStr = TextUtils.join(", ", (List<?>) userAnswer);

                correctAnswerText.setText(correctStr);
                selectedWrongAnswerText.setText(selectedStr);
            } else if (correct instanceof String && userAnswer instanceof String) {
                correctAnswerText.setText((String) correct);
                selectedWrongAnswerText.setText((String) userAnswer);
            }

            answersLayout.addView(answerView);
        }
    }

}
