package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;

public class FlashcardProgressActivity extends AppCompatActivity {

    ProgressBar backgroundProgressBar, statsProgressBar;
    TextView progressPercentage, knowItems, stillLearningItems, flashcardTitleTxt;
    Button reviewQuestionsBtn;
    TextView retakeFlashcardBtn;
    ImageView backButton, moreButton;

    int knowCount, stillLearningCount, totalItems;
    String setId;

    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flashcard_progress);

        db = FirebaseFirestore.getInstance();

        // Window insets fix
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.review_terms_txt), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        backgroundProgressBar = findViewById(R.id.background_progressbar);
        statsProgressBar = findViewById(R.id.stats_progressbar);
        progressPercentage = findViewById(R.id.progress_percentage);
        knowItems = findViewById(R.id.know_items);
        stillLearningItems = findViewById(R.id.still_learning_items);
        reviewQuestionsBtn = findViewById(R.id.review_questions_btn);
        retakeFlashcardBtn = findViewById(R.id.retake_quiz_btn);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        flashcardTitleTxt = findViewById(R.id.txtView_flashcard_title);

        // Get data from intent
        knowCount = getIntent().getIntExtra("knowCount", 0);
        stillLearningCount = getIntent().getIntExtra("stillLearningCount", 0);
        totalItems = getIntent().getIntExtra("totalItems", 1); // avoid divide by zero
        setId = getIntent().getStringExtra("setId");

        // Update UI
        int progressValue = (int) (((float) knowCount / totalItems) * 100);
        statsProgressBar.setProgress(progressValue);
        progressPercentage.setText(progressValue + "%");
        knowItems.setText(knowCount + " items");
        stillLearningItems.setText(stillLearningCount + " items");

// Show or hide the Keep Reviewing button
        if (stillLearningCount > 0) {
            reviewQuestionsBtn.setVisibility(View.VISIBLE);
        } else {
            reviewQuestionsBtn.setVisibility(View.GONE);
        }


        // Save progress to Firestore
        updateProgressInFirestore(setId, progressValue);

        // Fetch title from Firestore
        loadFlashcardTitle(setId);

        // Handle buttons
        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());


        retakeFlashcardBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardViewerActivity.class);
            intent.putExtra("setId", setId);  // only setId
            startActivity(intent);
            finish();
        });
        reviewQuestionsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardViewerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("isReviewingOnlyDontKnow", true);
            intent.putStringArrayListExtra("dontKnowTerms", getIntent().getStringArrayListExtra("dontKnowTerms"));
            startActivity(intent);
            finish();
        });
    }

    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.more_bottom_sheet_menu, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.privacy).setOnClickListener(v -> {
            Toast.makeText(this, "Privacy clicked", Toast.LENGTH_SHORT).show();
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
            Intent intent = new Intent(this, CreateFlashcardActivity.class);
            intent.putExtra("setId", setId);
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
                .setTitle("Delete Flashcard Set")
                .setMessage("Are you sure you want to delete this flashcard set? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteFlashcardSet())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteFlashcardSet() {
        db.collection("flashcards").document(setId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Flashcard set deleted.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete flashcard set.", Toast.LENGTH_SHORT).show());
    }
    private void updateProgressInFirestore(String setId, int progressValue) {
        if (setId == null) return;

        db.collection("flashcards").document(setId)
                .update("progress", progressValue)
                .addOnSuccessListener(aVoid -> {
                    // Optional log or toast
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save progress", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadFlashcardTitle(String setId) {
        if (setId == null) return;

        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            flashcardTitleTxt.setText(title);
                        } else {
                            flashcardTitleTxt.setText("Untitled Set");
                        }
                    } else {
                        flashcardTitleTxt.setText("Flashcard set not found.");
                    }
                })
                .addOnFailureListener(e -> {
                    flashcardTitleTxt.setText("Failed to load title.");
                });
    }
}
