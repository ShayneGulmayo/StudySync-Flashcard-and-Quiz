package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

        quizId = getIntent().getStringExtra("quizId");
        if (quizId == null || quizId.isEmpty()) {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Fetch data first before assigning button click behaviors
        db.collection("quiz").document(quizId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                displayQuizProgress(documentSnapshot);
            } else {
                Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to fetch quiz", Toast.LENGTH_SHORT).show();
            finish();
        });

        // back button goes to previous activity
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // retake quiz goes to QuizViewActivity
        retake_quiz_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizProgressActivity.this, QuizViewActivity.class);
                intent.putExtra("quizId", quizId);
                startActivity(intent);
                finish();
            }
        });

        // review questions goes to ReviewQuestionsActivity
        review_questions_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizProgressActivity.this, ReviewQuestionsActivity.class);
                intent.putExtra("quizId", quizId);
                startActivity(intent);
                finish();
            }
        });

        // more button – placeholder
        more_button.setOnClickListener(v -> showMoreBottomSheet());
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


    private void displayQuizProgress(DocumentSnapshot doc) {
        String title = doc.getString("title");
        Long progress = doc.getLong("progress");
        Long total = doc.getLong("numberOfItems");

        if (title != null && !title.trim().isEmpty()) {
            quizTitleText.setText(title);
        } else {
            quizTitleText.setText("Untitled Quiz");
        }

        int percentage = progress != null ? progress.intValue() : 0;
        int totalItems = total != null ? total.intValue() : 0;
        int correct = Math.round((percentage / 100f) * totalItems);
        int incorrect = totalItems - correct;

        correctText.setText(correct + " Items");
        incorrectText.setText(incorrect + " Items");

        progressCircle.setProgress(percentage);
        progressPercentageText.setText(percentage + "%"); // ✅ THIS FIXES THE ZERO %
    }



}
