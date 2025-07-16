package com.labactivity.studysync;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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
import java.util.Collections;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

        db.collection("quiz").document(quizId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                displayQuizProgress(documentSnapshot);
                displayAnsweredQuestions();
            } else {
                Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to fetch quiz", Toast.LENGTH_SHORT).show();
            finish();
        });

        back_button.setOnClickListener(v -> onBackPressed());

        retake_quiz_btn.setOnClickListener(v -> {
            Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("mode", "normal");

            String photoUrl = getIntent().getStringExtra("photoUrl");
            if (photoUrl != null) {
                intent.putExtra("photoUrl", photoUrl);
            }

            startActivity(intent);
            finish();
        });

        review_questions_btn.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            db.collection("quiz")
                    .document(quizId)
                    .collection("quiz_attempt")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(attemptDoc -> {
                        if (attemptDoc.exists() && attemptDoc.contains("answeredQuestions")) {
                            Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                            intent.putExtra("quizId", quizId);
                            intent.putExtra("photoUrl", getIntent().getStringExtra("photoUrl"));
                            intent.putExtra("mode", "review_only_incorrect");
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(this, "No attempt data to review.", Toast.LENGTH_SHORT).show();
                        }
                    });
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

                                TextView questionText = answerView.findViewById(R.id.question_text);
                                TextView correctAnswerText = answerView.findViewById(R.id.correct_answer_text);
                                TextView selectedWrongAnswerText = answerView.findViewById(R.id.selected_wrong_answer_text);
                                View wrongAnswerContainer = answerView.findViewById(R.id.selected_wrong_answer_container);

                                ImageView questionImageView = answerView.findViewById(R.id.question_image); // must exist in XML
                                String photoUrl = (String) q.get("photoUrl");

                                if (photoUrl != null && !photoUrl.trim().isEmpty() && !photoUrl.equals("Add Image")) {
                                    questionImageView.setVisibility(View.VISIBLE);
                                    Glide.with(this)
                                            .load(photoUrl)
                                            .into(questionImageView);
                                } else {
                                    questionImageView.setVisibility(View.GONE);
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


}
