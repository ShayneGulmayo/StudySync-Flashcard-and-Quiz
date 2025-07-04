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
    private ImageView more_button;
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
        more_button = findViewById(R.id.more_button);
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
            db.collection("quiz_attempts")
                    .document(quizId)
                    .collection("users")
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

        more_button.setOnClickListener(v -> showMoreBottomSheet());
    }

    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView privacyOption = view.findViewById(R.id.privacy);

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

    private void displayQuizProgress(DocumentSnapshot doc) {
        String title = doc.getString("title");
        if (title != null && !title.trim().isEmpty()) {
            quizTitleText.setText(title);
        } else {
            quizTitleText.setText("Untitled Quiz");
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("quiz_attempts")
                .document(quizId)
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(attemptDoc -> {
                    if (attemptDoc.exists()) {
                        Long score = attemptDoc.getLong("score");
                        Long total = attemptDoc.getLong("total");

                        if (score != null && total != null && total > 0) {
                            int correct = score.intValue();
                            int totalItems = total.intValue();

                            Long originalCount = doc.getLong("number_of_items");
                            if (originalCount != null && originalCount > 0) {
                                totalItems = originalCount.intValue();
                            }

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

        db.collection("quiz_attempts")
                .document(quizId)
                .collection("users")
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
