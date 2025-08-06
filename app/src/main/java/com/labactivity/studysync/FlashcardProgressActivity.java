package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashcardProgressActivity extends AppCompatActivity {

    FirebaseFirestore db;

    TextView progressPercentage, knowItems, stillLearningItems, flashcardTitleTxt, retakeFlashcardBtn;
    ImageView backButton;
    Button reviewQuestionsBtn;
    ProgressBar backgroundProgressBar, statsProgressBar;

    String setId, offlineFileName, flashcardId;
    int knowCount, totalItems;
    private boolean isOffline;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flashcard_progress);

        isOffline = getIntent().getBooleanExtra("isOffline", false);
        offlineFileName = getIntent().getStringExtra("offlineFileName");

        db = FirebaseFirestore.getInstance();

        setId = getIntent().getStringExtra("setId");

        if (!isOffline && (setId == null || setId.isEmpty())) {
            Toast.makeText(this, "No flashcard ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        backgroundProgressBar = findViewById(R.id.background_progressbar);
        statsProgressBar = findViewById(R.id.stats_progressbar);
        progressPercentage = findViewById(R.id.progress_percentage);
        knowItems = findViewById(R.id.know_items);
        stillLearningItems = findViewById(R.id.still_learning_items);
        reviewQuestionsBtn = findViewById(R.id.review_terms_btn);
        retakeFlashcardBtn = findViewById(R.id.retake_flashcard_btn);
        backButton = findViewById(R.id.back_button);
        flashcardTitleTxt = findViewById(R.id.flashcard_title);

        knowCount = getIntent().getIntExtra("knowCount", 0);
        totalItems = getIntent().getIntExtra("totalItems", 1);

        int cappedKnowCount = Math.min(knowCount, totalItems);
        int stillLearningCount = Math.max(0, totalItems - cappedKnowCount);
        int progressValue = (int) (((float) cappedKnowCount / totalItems) * 100);

        statsProgressBar.setProgress(progressValue);
        progressPercentage.setText(progressValue + "%");
        knowItems.setText(cappedKnowCount + " items");
        stillLearningItems.setText(stillLearningCount + " items");

        if (stillLearningCount > 0) {
            reviewQuestionsBtn.setVisibility(View.VISIBLE);
        } else {
            reviewQuestionsBtn.setVisibility(View.GONE);
        }

        if (!isOffline) {
            db.collection("flashcards").document(setId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            displayFlashcardAttempts();
                        } else {
                            Toast.makeText(this, "Flashcard not found", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to fetch flashcard", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        } else {
            displayFlashcardAttempts();
        }

        updateProgressInFirestore(setId, progressValue);

        loadFlashcardTitle(setId);

        backButton.setOnClickListener(v -> finish());

        retakeFlashcardBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardViewerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("isOffline", isOffline);
            if (isOffline) intent.putExtra("offlineFileName", offlineFileName);
            intent.putExtra("totalItems", totalItems);
            intent.putExtra("knowCount", 0);
            startActivity(intent);
            finish();
        });

        reviewQuestionsBtn.setOnClickListener(v -> {
            ArrayList<String> dontKnowTerms = getIntent().getStringArrayListExtra("dontKnowTerms");
            if (dontKnowTerms == null || dontKnowTerms.isEmpty()) {
                Toast.makeText(this, "No attempt data to review.", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<Map<String, Object>> offlineAttempts = null;

            if (isOffline) {
                try {
                    File file = new File(getFilesDir(), offlineFileName);
                    if (file.exists()) {
                        StringBuilder jsonBuilder = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            jsonBuilder.append(line);
                        }
                        reader.close();

                        String json = jsonBuilder.toString();
                        Map<String, Object> setData = new Gson().fromJson(json, Map.class);
                        offlineAttempts = (ArrayList<Map<String, Object>>) setData.get("attempts");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Intent intent = new Intent(FlashcardProgressActivity.this, FlashcardViewerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("photoUrl", getIntent().getStringExtra("photoUrl"));
            intent.putExtra("mode", "review_only_incorrect");
            intent.putExtra("isOffline", isOffline);
            if (isOffline) intent.putExtra("offlineFileName", offlineFileName);
            intent.putExtra("totalItems", totalItems);
            intent.putExtra("knowCount", knowCount);
            intent.putExtra("isReviewingOnlyDontKnow", true);
            intent.putStringArrayListExtra("dontKnowTerms", dontKnowTerms);

            if (isOffline && offlineAttempts != null) {
                intent.putExtra("offlineAttemptsJson", new Gson().toJson(offlineAttempts));
            }
            startActivity(intent);
            finish();
        });

    }

    private void updateProgressInFirestore(String setId, int progressValue) {
        if (isOffline || setId == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    String collectionField;
                    if (documentSnapshot.contains("owner_uid")) {
                        String ownerId = documentSnapshot.getString("owner_uid");
                        collectionField = ownerId != null && ownerId.equals(currentUserId) ? "owned_sets" : "saved_sets";
                    } else {
                        collectionField = "saved_sets";
                    }

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
                                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to save progress.", Toast.LENGTH_SHORT).show());
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch set owner.", Toast.LENGTH_SHORT).show());
    }

    @SuppressLint("SetTextI18n")
    private void loadFlashcardTitle(String setId) {
        if (isOffline) {
            if (offlineFileName == null) {
                flashcardTitleTxt.setText("Untitled Set");
                return;
            }

            File file = new File(getFilesDir(), offlineFileName);
            if (!file.exists()) {
                flashcardTitleTxt.setText("Untitled Set");
                return;
            }

            try {
                StringBuilder jsonBuilder = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file)));
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                reader.close();

                String json = jsonBuilder.toString();
                Map<String, Object> setData = new com.google.gson.Gson().fromJson(json, Map.class);
                String title = (String) setData.get("title");

                if (title != null && !title.isEmpty()) {
                    flashcardTitleTxt.setText(title.length() > 20 ? title.substring(0, 20) + "..." : title);
                } else {
                    flashcardTitleTxt.setText("Untitled Set");
                }

            } catch (Exception e) {
                e.printStackTrace();
                flashcardTitleTxt.setText("Failed to load title.");
            }

        } else {
            if (setId == null) return;

            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String title = documentSnapshot.getString("title");
                            if (title != null && !title.isEmpty()) {
                                flashcardTitleTxt.setText(title.length() > 20 ? title.substring(0, 20) + "..." : title);
                            } else {
                                flashcardTitleTxt.setText("Untitled Set");
                            }
                        } else {
                            flashcardTitleTxt.setText("Flashcard set not found.");
                        }
                    })
                    .addOnFailureListener(e -> flashcardTitleTxt.setText("Failed to load title."));
        }
    }

    private void displayFlashcardAttempts() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        LinearLayout answersLayout = findViewById(R.id.item_flashcard_container);
        LayoutInflater inflater = LayoutInflater.from(this);

        if (isOffline) {
            if (offlineFileName == null) {
                Toast.makeText(this, "No offline data available.", Toast.LENGTH_SHORT).show();
                return;
            }

            File file = new File(getFilesDir(), offlineFileName);
            if (!file.exists()) {
                Toast.makeText(this, "Offline flashcard data not found.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                StringBuilder jsonBuilder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                reader.close();

                String json = jsonBuilder.toString();

                Map<String, Object> setData = new Gson().fromJson(json, Map.class);

                List<Map<String, Object>> attempts = (List<Map<String, Object>>) setData.get("attempts");

                if (attempts == null || attempts.isEmpty()) {
                    Toast.makeText(this, "No offline attempts found.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Collections.sort(attempts, (a, b) -> {
                    int orderA = a.get("order") != null ? ((Number) a.get("order")).intValue() : 0;
                    int orderB = b.get("order") != null ? ((Number) b.get("order")).intValue() : 0;
                    return Integer.compare(orderA, orderB);
                });

                for (Map<String, Object> t : attempts) {

                    View answerView = inflater.inflate(R.layout.item_flashcard_attempt_view, answersLayout, false);

                    TextView termText = answerView.findViewById(R.id.term_text);
                    TextView definitionText = answerView.findViewById(R.id.definition_text);
                    TextView statusLabel = answerView.findViewById(R.id.status_label);
                    LinearLayout linearLayout = answerView.findViewById(R.id.linear_layout);
                    ImageView termIcon = answerView.findViewById(R.id.check_term);
                    ImageView definitionImage = answerView.findViewById(R.id.definition_image);

                    String term = t.get("term") != null ? t.get("term").toString() : "No term";
                    String definition = t.get("definition") != null ? t.get("definition").toString() : "No definition";
                    String photoUrl = t.get("photoUrl") != null ? t.get("photoUrl").toString() : null;
                    boolean isCorrect = t.get("isCorrect") != null && (boolean) t.get("isCorrect");

                    Log.d("FLASHCARD_ATTEMPT", "Term: " + term + ", Definition: " + definition + ", PhotoURL: " + photoUrl + ", isCorrect: " + isCorrect);

                    termText.setText(term);
                    definitionText.setText(definition);

                    if (isCorrect) {
                        statusLabel.setText(R.string.know_term_text);
                        statusLabel.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_message_current_user));
                    } else {
                        statusLabel.setText(R.string.dont_know_term_text);
                        statusLabel.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_light_red));
                        linearLayout.setBackground(ContextCompat.getDrawable(this, R.drawable.light_red_stroke_bg));
                        termIcon.setImageResource(R.drawable.x_circle);
                        termIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
                    }

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        definitionImage.setVisibility(View.VISIBLE);
                        Glide.with(this)
                                .load(photoUrl)
                                .transform(new RoundedCorners(dpToPx(15)))
                                .into(definitionImage);
                    } else {
                        definitionImage.setVisibility(View.GONE);
                    }

                    answersLayout.addView(answerView);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load offline attempts.", Toast.LENGTH_SHORT).show();
            }

            return;
        }

        db.collection("flashcards")
                .document(setId)
                .collection("flashcard_attempt")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<Map<String, Object>> knowCount = (List<Map<String, Object>>) doc.get("knowCount");
                        if (knowCount != null && !knowCount.isEmpty()) {
                            Collections.sort(knowCount, (a, b) -> {
                                int orderA = a.get("order") != null ? ((Number) a.get("order")).intValue() : 0;
                                int orderB = b.get("order") != null ? ((Number) b.get("order")).intValue() : 0;
                                return Integer.compare(orderA, orderB);
                            });

                            for (Map<String, Object> t : knowCount) {
                                View answerView = inflater.inflate(R.layout.item_flashcard_attempt_view, answersLayout, false);

                                TextView termText = answerView.findViewById(R.id.term_text);
                                TextView definitionText = answerView.findViewById(R.id.definition_text);
                                TextView statusLabel = answerView.findViewById(R.id.status_label);
                                LinearLayout linearLayout = answerView.findViewById(R.id.linear_layout);
                                ImageView termIcon = answerView.findViewById(R.id.check_term);
                                ImageView definitionImage = answerView.findViewById(R.id.definition_image);

                                String term = t.get("term") != null ? t.get("term").toString() : "No term";
                                String definition = t.get("definition") != null ? t.get("definition").toString() : "No definition";
                                boolean isCorrect = t.get("isCorrect") != null && (boolean) t.get("isCorrect");

                                termText.setText(term);
                                definitionText.setText(definition);

                                if (isCorrect) {
                                    statusLabel.setText(R.string.know_term_text);
                                    statusLabel.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_message_current_user));
                                } else {
                                    statusLabel.setText(R.string.dont_know_term_text);
                                    statusLabel.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_light_red));
                                    linearLayout.setBackground(ContextCompat.getDrawable(this, R.drawable.light_red_stroke_bg));
                                    termIcon.setImageResource(R.drawable.x_circle);
                                    termIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
                                }

                                String photoUrl = t.get("photoUrl") != null ? t.get("photoUrl").toString() : null;
                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    definitionImage.setVisibility(View.VISIBLE);
                                    Glide.with(this)
                                            .load(photoUrl)
                                            .transform(new RoundedCorners(dpToPx(15)))
                                            .into(definitionImage);
                                } else {
                                    definitionImage.setVisibility(View.GONE);
                                }

                                answersLayout.addView(answerView);
                            }

                        } else {
                            Toast.makeText(this, "No attempt data found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "No attempt record found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load answers", Toast.LENGTH_SHORT).show();
                });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}