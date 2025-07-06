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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FlashcardProgressActivity extends AppCompatActivity {

    ProgressBar backgroundProgressBar, statsProgressBar;
    TextView progressPercentage, knowItems, stillLearningItems, flashcardTitleTxt;
    Button reviewQuestionsBtn;
    TextView retakeFlashcardBtn;
    ImageView backButton;
    int knowCount, stillLearningCount, totalItems;
    String setId;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flashcard_progress);

        db = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.review_terms_txt), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        backgroundProgressBar = findViewById(R.id.background_progressbar);
        statsProgressBar = findViewById(R.id.stats_progressbar);
        progressPercentage = findViewById(R.id.progress_percentage);
        knowItems = findViewById(R.id.know_items);
        stillLearningItems = findViewById(R.id.still_learning_items);
        reviewQuestionsBtn = findViewById(R.id.review_questions_btn);
        retakeFlashcardBtn = findViewById(R.id.retake_quiz_btn);
        backButton = findViewById(R.id.back_button);
        flashcardTitleTxt = findViewById(R.id.flashcard_title);

        knowCount = getIntent().getIntExtra("knowCount", 0);
        stillLearningCount = getIntent().getIntExtra("stillLearningCount", 0);
        totalItems = getIntent().getIntExtra("totalItems", 1);
        setId = getIntent().getStringExtra("setId");

        int progressValue = (int) (((float) knowCount / totalItems) * 100);
        statsProgressBar.setProgress(progressValue);
        progressPercentage.setText(progressValue + "%");
        knowItems.setText(knowCount + " items");
        stillLearningItems.setText(stillLearningCount + " items");

        if (stillLearningCount > 0) {
            reviewQuestionsBtn.setVisibility(View.VISIBLE);
        } else {
            reviewQuestionsBtn.setVisibility(View.GONE);
        }

        updateProgressInFirestore(setId, progressValue);

        loadFlashcardTitle(setId);

        backButton.setOnClickListener(v -> finish());

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

    private void updateProgressInFirestore(String setId, int progressValue) {
        if (setId == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Get flashcard set to check owner
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    String collectionField;

                    if (documentSnapshot.contains("owner_uid")) {
                        String ownerId = documentSnapshot.getString("owner_uid");
                        collectionField = ownerId != null && ownerId.equals(currentUserId) ? "owned_sets" : "saved_sets";
                    } else {
                        // fallback: if no ownerId — you decide how to handle this
                        collectionField = "saved_sets";
                    }

                    // now proceed to fetch user's sets and update progress
                    db.collection("users").document(currentUserId)
                            .get()
                            .addOnSuccessListener(userSnapshot -> {
                                if (!userSnapshot.exists()) return;

                                ArrayList<Map<String, Object>> setList = (ArrayList<Map<String, Object>>) userSnapshot.get(collectionField);
                                if (setList == null) setList = new ArrayList<>();

                                boolean found = false;

                                for (Map<String, Object> item : setList) {
                                    if (setId.equals(item.get("id"))) {
                                        item.put("progress", progressValue);
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found) {
                                    Map<String, Object> newEntry = new HashMap<>();
                                    newEntry.put("id", setId);
                                    newEntry.put("type", "flashcard");
                                    newEntry.put("progress", progressValue);
                                    setList.add(newEntry);
                                }

                                db.collection("users").document(currentUserId)
                                        .update(collectionField, setList)
                                        .addOnSuccessListener(aVoid -> {
                                            // success
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Failed to save progress.", Toast.LENGTH_SHORT).show();
                                        });

                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch set owner.", Toast.LENGTH_SHORT).show();
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
                            if (title.length() > 20) {
                                String shortTitle = title.substring(0, 20) + "...";
                                flashcardTitleTxt.setText(shortTitle);
                            } else {
                                flashcardTitleTxt.setText(title);
                            }
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
