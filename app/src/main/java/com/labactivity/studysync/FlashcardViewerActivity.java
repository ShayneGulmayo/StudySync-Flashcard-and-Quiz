package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.labactivity.studysync.models.Flashcard;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FlashcardViewerActivity extends AppCompatActivity {

    private String setId;
    private FirebaseFirestore db;
    private ArrayList<Flashcard> flashcards, dontKnowFlashcards;
    private TextView frontCard, backCard, items;
    private ImageView backButton, moreButton, knowBtn, dontKnowBtn, flashcardImage;
    private View cardFront, cardBack;
    private String cardOrientation = "term";
    private String offlineFileName;
    private int knowCount, dontKnowCount, totalItems;
    private int currentIndex = 0;
    private boolean isReviewingOnlyDontKnow = false;
    private boolean showingFront = true;
    private boolean isOffline = false;
    private Map<String, Object> setData;
    private ArrayList<Map<String, Object>> flashcardAttempts = new ArrayList<>();
    private ArrayList<Map<String, Object>> offlineAttempts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_viewer);
        knowCount = getIntent().getIntExtra("knowCount", 0);

        setId = getIntent().getStringExtra("setId");
        isReviewingOnlyDontKnow = getIntent().getBooleanExtra("isReviewingOnlyDontKnow", false);

        db = FirebaseFirestore.getInstance();
        flashcards = new ArrayList<>();
        dontKnowFlashcards = new ArrayList<>();
        isOffline = getIntent().getBooleanExtra("isOffline", false);
        offlineFileName = getIntent().getStringExtra("offlineFileName");

        if (isOffline && getIntent().hasExtra("offlineAttemptsJson")) {
            String offlineAttemptsJson = getIntent().getStringExtra("offlineAttemptsJson");
            Type listType = new TypeToken<ArrayList<Map<String, Object>>>() {}.getType();
            offlineAttempts = new Gson().fromJson(offlineAttemptsJson, listType);
        }

        frontCard = findViewById(R.id.front_card);
        backCard = findViewById(R.id.back_card);
        flashcardImage = findViewById(R.id.flashcard_image);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        knowBtn = findViewById(R.id.know_btn);
        dontKnowBtn = findViewById(R.id.dont_know_btn);
        items = findViewById(R.id.txtView_items);
        cardFront = findViewById(R.id.card_front);
        cardBack = findViewById(R.id.card_back);

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());
        frontCard.setOnClickListener(v -> flipCard());
        backCard.setOnClickListener(v -> flipCard());

        knowBtn.setOnClickListener(v -> {
            knowCount++;
            addAttempt(true);
            animateCardSwipe();

        });

        dontKnowBtn.setOnClickListener(v -> {
            dontKnowCount++;
            dontKnowFlashcards.add(flashcards.get(currentIndex));
            addAttempt(false);
            animateCardSwipe();

        });

        if (isOffline) {
            loadOfflineSet(offlineFileName);
        } else {
            loadFlashcards();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFlashcards();
    }

    @SuppressLint("MissingInflatedId")
    private void showMoreBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_viewer, null);
        dialog.setContentView(view);

        view.findViewById(R.id.shuffle).setOnClickListener(v -> {
            shuffleFlashcards();
            dialog.dismiss();
        });

        view.findViewById(R.id.restart).setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardViewerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("isOffline", isOffline);
            if (isOffline) intent.putExtra("offlineFileName", offlineFileName);
            startActivity(intent);
            finish();
        });

        TextView cardOrientationTxt = view.findViewById(R.id.card_orientation_txt);
        TextView cardOrientationOption = view.findViewById(R.id.card_orientation_option);

        cardOrientationTxt.setVisibility(View.VISIBLE);
        cardOrientationOption.setVisibility(View.VISIBLE);

        cardOrientationOption.setText(cardOrientation.equals("term") ? "Term First" : "Definition First");

        cardOrientationOption.setOnClickListener(v -> {
            if (cardOrientation.equals("term")) {
                cardOrientation = "definition";
                cardOrientationOption.setText("Definition First");
            } else {
                cardOrientation = "term";
                cardOrientationOption.setText("Term First");
            }
            showCard(currentIndex);
        });

        dialog.show();
    }

    private void addAttempt(boolean isCorrect) {
        Flashcard card = flashcards.get(currentIndex);
        String term = card.getTerm();

        boolean replaced = false;
        for (int i = 0; i < flashcardAttempts.size(); i++) {
            if (flashcardAttempts.get(i).get("term").equals(term)) {
                flashcardAttempts.get(i).put("isCorrect", isCorrect);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            Map<String, Object> attempt = new HashMap<>();
            attempt.put("term", term);
            attempt.put("definition", card.getDefinition());
            attempt.put("photoUrl", card.getPhotoUrl());
            attempt.put("isCorrect", isCorrect);
            attempt.put("order", currentIndex + 1);
            flashcardAttempts.add(attempt);
        }
    }

    private void loadOfflineSet(String fileName) {
        File file = new File(getFilesDir(), fileName);
        if (!file.exists()) {
            Toast.makeText(this, "Offline file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] dataBytes = new byte[(int) file.length()];
            fis.read(dataBytes);
            fis.close();

            String json = new String(dataBytes);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            setData = new Gson().fromJson(json, type);

            loadOfflineFlashcards();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load offline set.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadOfflineFlashcards() {
        if (setData != null && setData.containsKey("terms")) {
            Map<String, Object> terms = (Map<String, Object>) setData.get("terms");
            flashcards.clear();

            for (Map.Entry<String, Object> entry : terms.entrySet()) {
                Map<String, Object> termEntry = (Map<String, Object>) entry.getValue();
                flashcards.add(new Flashcard(
                        (String) termEntry.get("term"),
                        (String) termEntry.get("definition"),
                        (String) termEntry.get("photoUrl"),
                        (String) termEntry.get("photoPath")));
            }

            totalItems = flashcards.size();

            if (isReviewingOnlyDontKnow) {
                ArrayList<String> dontKnowTerms = getIntent().getStringArrayListExtra("dontKnowTerms");
                if (dontKnowTerms != null) {
                    ArrayList<Flashcard> filtered = new ArrayList<>();
                    for (Flashcard card : flashcards) {
                        if (dontKnowTerms.contains(card.getTerm())) {
                            filtered.add(card);
                        }
                    }
                    flashcards = filtered;
                }
            }

            if (!offlineAttempts.isEmpty()) {
                flashcardAttempts.clear();
                flashcardAttempts.addAll(offlineAttempts);

                knowCount = 0;
                for (Map<String, Object> attempt : offlineAttempts) {
                    if (Boolean.TRUE.equals(attempt.get("isCorrect"))) {
                        knowCount++;
                    }
                }
            }

            if (!flashcards.isEmpty()) {
                currentIndex = 0;
                showCard(0);
            } else {
                frontCard.setText("No flashcards found.");
            }
        } else {
            frontCard.setText("No flashcards data.");
        }
    }

    private void saveOfflineAttempts() {
        if (!isOffline || offlineFileName == null) return;

        try {
            File file = new File(getFilesDir(), offlineFileName);
            if (!file.exists()) return;

            FileInputStream fis = new FileInputStream(file);
            byte[] dataBytes = new byte[(int) file.length()];
            fis.read(dataBytes);
            fis.close();

            String json = new String(dataBytes);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> setData = new Gson().fromJson(json, type);

            setData.put("attempts", flashcardAttempts); // save all attempts

            String updatedJson = new Gson().toJson(setData);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(updatedJson.getBytes());
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void shuffleFlashcards() {
        if (flashcards.isEmpty()) {
            Toast.makeText(this, "No flashcards to shuffle.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentIndex >= flashcards.size() - 1) {
            Toast.makeText(this, "No more flashcards to shuffle.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Flashcard> remainingFlashcards = new ArrayList<>(flashcards.subList(currentIndex, flashcards.size()));

        java.util.Collections.shuffle(remainingFlashcards);

        for (int i = currentIndex; i < flashcards.size(); i++) {
            flashcards.set(i, remainingFlashcards.get(i - currentIndex));
        }

        showCard(currentIndex);

        Toast.makeText(this, "Remaining flashcards shuffled!", Toast.LENGTH_SHORT).show();
    }

    private void loadFlashcards() {
        if (isOffline) {
            loadOfflineFlashcards();
        } else {
            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.exists()) {
                            Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> data = snapshot.getData();
                        Map<String, Object> terms = (Map<String, Object>) data.get("terms");
                        flashcards.clear();
                        for (Map.Entry<String, Object> entry : terms.entrySet()) {
                            Map<String, Object> termEntry = (Map<String, Object>) entry.getValue();
                            flashcards.add(new Flashcard(
                                    (String) termEntry.get("term"),
                                    (String) termEntry.get("definition"),
                                    (String) termEntry.get("photoUrl"),
                                    (String) termEntry.get("photoPath")));
                        }
                        totalItems = flashcards.size();
                        currentIndex = 0;

                        if (isReviewingOnlyDontKnow) {
                            ArrayList<String> dontKnowTerms = getIntent().getStringArrayListExtra("dontKnowTerms");
                            if (dontKnowTerms != null) {
                                ArrayList<Flashcard> filtered = new ArrayList<>();
                                for (Flashcard card : flashcards) {
                                    if (dontKnowTerms.contains(card.getTerm())) {
                                        filtered.add(card);
                                    }
                                }
                                flashcards = filtered;
                            }
                        }

                        if (!flashcards.isEmpty()) showCard(0);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show());
        }
            }

    private void showCard(int index) {
        if (index < flashcards.size()) {
            Flashcard card = flashcards.get(index);
            if (cardOrientation.equals("term")) {
                frontCard.setText(card.getTerm());
                backCard.setText(card.getDefinition());
            } else {
                frontCard.setText(card.getDefinition());
                backCard.setText(card.getTerm());
            }

            items.setText((index + 1) + " / " + flashcards.size());

            if (card.getPhotoUrl() != null && !card.getPhotoUrl().isEmpty()) {
                flashcardImage.setVisibility(View.VISIBLE);
                Glide.with(this).load(card.getPhotoUrl()).transform(new RoundedCorners(dpToPx(15))).into(flashcardImage);

            } else {
                flashcardImage.setVisibility(View.GONE);
                backCard.setPadding(20,20,20,20);
                backCard.setGravity(Gravity.CENTER);
            }
            showingFront = true;
            cardFront.setVisibility(View.VISIBLE);
            cardBack.setVisibility(View.GONE);

        } else {
            openFlashcardProgressActivity();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void flipCard() {
        View visibleCard = showingFront ? cardFront : cardBack;
        View hiddenCard = showingFront ? cardBack : cardFront;

        visibleCard.animate()
                .rotationY(90f)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    visibleCard.setVisibility(View.GONE);
                    visibleCard.setRotationY(0f);

                    hiddenCard.setVisibility(View.VISIBLE);
                    hiddenCard.setRotationY(-90f);
                    hiddenCard.animate()
                            .rotationY(0f)
                            .setDuration(200)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

                    showingFront = !showingFront;
                }).start();
    }

    private void animateCardSwipe() {
        if (currentIndex == flashcards.size() - 1) {
            openFlashcardProgressActivity();
            return;
        }

        View cardView = showingFront ? cardFront : cardBack;
        View hiddenCard = showingFront ? cardBack : cardFront;

        float toX = cardView.getWidth() * 1.5f;
        float rotation = 15f;

        cardView.animate()
                .translationX(toX)
                .rotation(rotation)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    cardView.setTranslationX(0f);
                    cardView.setRotation(0f);
                    cardView.setAlpha(1f);
                    hiddenCard.setTranslationX(0f);
                    hiddenCard.setRotation(0f);
                    hiddenCard.setAlpha(1f);

                    nextCard();
                }).start();
    }

    private void nextCard() {
        currentIndex++;
        if (currentIndex < flashcards.size()) {
            showCard(currentIndex);

            View newCardView = showingFront ? cardFront : cardBack;
            float fromX = -newCardView.getWidth() * 1.5f;

            newCardView.setTranslationX(fromX);
            newCardView.setAlpha(0f);
            newCardView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            openFlashcardProgressActivity();
        }
    }

    private void openFlashcardProgressActivity() {
        if (!isOffline) {
            String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();

            db.collection("flashcards")
                    .document(setId)
                    .collection("flashcard_attempt")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        ArrayList<Map<String, Object>> existingAttempts = new ArrayList<>();
                        if (snapshot.exists() && snapshot.contains("knowCount")) {
                            existingAttempts = (ArrayList<Map<String, Object>>) snapshot.get("knowCount");
                        }

                        for (Map<String, Object> newAttempt : flashcardAttempts) {
                            String term = newAttempt.get("term").toString();
                            boolean replaced = false;

                            for (int i = 0; i < existingAttempts.size(); i++) {
                                Map<String, Object> existingAttempt = existingAttempts.get(i);
                                if (existingAttempt.get("term").equals(term)) {
                                    existingAttempts.set(i, newAttempt);
                                    replaced = true;
                                    break;
                                }
                            }

                            if (!replaced) {
                                existingAttempts.add(newAttempt);
                            }
                        }

                        Map<String, Object> data = new HashMap<>();
                        data.put("knowCount", existingAttempts);

                        db.collection("flashcards")
                                .document(setId)
                                .collection("flashcard_attempt")
                                .document(userId)
                                .set(data)
                                .addOnSuccessListener(unused -> {
                                    proceedToProgressScreen();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to save attempts.", Toast.LENGTH_SHORT).show();
                                    proceedToProgressScreen();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to fetch existing attempts.", Toast.LENGTH_SHORT).show();
                        proceedToProgressScreen();
                    });
        } else {
            saveOfflineAttempts();
            proceedToProgressScreen();
        }
    }

    private void proceedToProgressScreen() {
        Intent intent = new Intent(this, FlashcardProgressActivity.class);
        intent.putExtra("knowCount", knowCount);
        intent.putExtra("totalItems", totalItems);
        intent.putExtra("setId", setId);
        intent.putExtra("isOffline", isOffline);
        if (isOffline) intent.putExtra("offlineFileName", offlineFileName);
        ArrayList<String> dontKnowTerms = new ArrayList<>();
        for (Flashcard card : dontKnowFlashcards) {
            dontKnowTerms.add(card.getTerm());
        }
        intent.putStringArrayListExtra("dontKnowTerms", dontKnowTerms);
        startActivity(intent);
        finish();
    }
}