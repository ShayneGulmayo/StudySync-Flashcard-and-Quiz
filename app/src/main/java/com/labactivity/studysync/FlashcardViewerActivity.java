package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class FlashcardViewerActivity extends AppCompatActivity {

    private String setId;
    private FirebaseFirestore db;
    private ArrayList<Flashcard> flashcards;
    private ArrayList<Flashcard> dontKnowFlashcards;
    private boolean isReviewingOnlyDontKnow = false;

    private TextView frontCard, backCard, items;
    private ImageView backButton, moreButton, knowBtn, dontKnowBtn;
    private int knowCount;
    private int dontKnowCount;
    private int currentIndex = 0;
    private boolean showingFront = true;
    private String ownerUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_viewer);

        setId = getIntent().getStringExtra("setId");
        isReviewingOnlyDontKnow = getIntent().getBooleanExtra("isReviewingOnlyDontKnow", false);
        db = FirebaseFirestore.getInstance();
        flashcards = new ArrayList<>();
        dontKnowFlashcards = new ArrayList<>();

        frontCard = findViewById(R.id.front_card);
        backCard = findViewById(R.id.back_card);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        knowBtn = findViewById(R.id.know_btn);
        dontKnowBtn = findViewById(R.id.dont_know_btn);
        items = findViewById(R.id.txtView_items);

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());
        frontCard.setOnClickListener(v -> flipCard());
        backCard.setOnClickListener(v -> flipCard());

        knowBtn.setOnClickListener(v -> {
            knowCount++;
            animateCardSwipe(true);
        });

        dontKnowBtn.setOnClickListener(v -> {
            dontKnowCount++;
            dontKnowFlashcards.add(flashcards.get(currentIndex));
            animateCardSwipe(false);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFlashcards();
    }

    @SuppressLint("MissingInflatedId")
    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_viewer, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.shuffle).setOnClickListener(v -> {
            Toast.makeText(this, "Shuffle clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.restart).setOnClickListener(v -> {
            Toast.makeText(this, "Restart clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void loadFlashcards() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = snapshot.getData();
                    if (data != null && data.containsKey("terms")) {
                        Map<String, Object> terms = (Map<String, Object>) data.get("terms");
                        flashcards.clear();
                        ArrayList<String> dontKnowTerms = getIntent().getStringArrayListExtra("dontKnowTerms");

                        for (Map.Entry<String, Object> entry : terms.entrySet()) {
                            Map<String, Object> termEntry = (Map<String, Object>) entry.getValue();
                            String term = (String) termEntry.get("term");
                            String definition = (String) termEntry.get("definition");
                            Flashcard card = new Flashcard(term, definition);

                            if (isReviewingOnlyDontKnow) {
                                if (dontKnowTerms != null && dontKnowTerms.contains(term)) {
                                    flashcards.add(card);
                                }
                            } else {
                                flashcards.add(card);
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
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show());
    }

    private void showCard(int index) {
        if (index < flashcards.size()) {
            Flashcard card = flashcards.get(index);
            frontCard.setText(card.getTerm());
            backCard.setText(card.getDefinition());
            showingFront = true;
            frontCard.setVisibility(View.VISIBLE);
            backCard.setVisibility(View.GONE);
            items.setText((index + 1) + " / " + flashcards.size());
        } else {
            openFlashcardProgressActivity();
        }
    }

    private void flipCard() {
        View visible = showingFront ? frontCard : backCard;
        View hidden = showingFront ? backCard : frontCard;

        visible.animate()
                .rotationY(90f)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    visible.setVisibility(View.GONE);
                    visible.setRotationY(0f);

                    hidden.setVisibility(View.VISIBLE);
                    hidden.setRotationY(-90f);

                    hidden.animate()
                            .rotationY(0f)
                            .setDuration(200)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

                    showingFront = !showingFront;
                })
                .start();
    }

    private void animateCardSwipe(boolean isRightSwipe) {
        if (currentIndex == flashcards.size() - 1) {
            openFlashcardProgressActivity();
            return;
        }

        View cardView = showingFront ? frontCard : backCard;
        View hiddenCard = showingFront ? backCard : frontCard;

        float toX = isRightSwipe ? cardView.getWidth() * 1.5f : -cardView.getWidth() * 1.5f;
        float rotation = isRightSwipe ? 15f : -15f;

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

                    nextCard(isRightSwipe);
                })
                .start();
    }

    private void nextCard(boolean previousWasRightSwipe) {
        currentIndex++;
        if (currentIndex < flashcards.size()) {
            showCard(currentIndex);

            View newCardView = showingFront ? frontCard : backCard;
            float fromX = previousWasRightSwipe ? -newCardView.getWidth() * 1.5f : newCardView.getWidth() * 1.5f;

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
        Intent intent = new Intent(this, FlashcardProgressActivity.class);
        intent.putExtra("knowCount", knowCount);
        intent.putExtra("stillLearningCount", dontKnowCount);
        intent.putExtra("totalItems", flashcards.size());
        intent.putExtra("setId", setId);

        ArrayList<String> dontKnowTerms = new ArrayList<>();
        for (Flashcard card : dontKnowFlashcards) {
            dontKnowTerms.add(card.getTerm());
        }
        intent.putStringArrayListExtra("dontKnowTerms", dontKnowTerms);

        startActivity(intent);
        finish();
    }
}
