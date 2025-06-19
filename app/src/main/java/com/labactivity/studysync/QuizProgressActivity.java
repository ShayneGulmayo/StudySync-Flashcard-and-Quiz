package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class QuizProgressActivity extends AppCompatActivity {

    private TextView quizTitleText, correctText, incorrectText;
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
        more_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Future implementation for "more" button
            }
        });
    }

    private void displayQuizProgress(DocumentSnapshot doc) {
        String title = doc.getString("quizName");
        Long progress = doc.getLong("progress");
        Long total = doc.getLong("numberOfItems");

        if (title != null) quizTitleText.setText(title);
        else quizTitleText.setText("Untitled Quiz");

        int percentage = progress != null ? progress.intValue() : 0;
        int totalItems = total != null ? total.intValue() : 0;
        int correct = Math.round((percentage / 100f) * totalItems);
        int incorrect = totalItems - correct;

        correctText.setText(correct);
        incorrectText.setText(incorrect);
        progressCircle.setProgress(percentage);
    }
}
