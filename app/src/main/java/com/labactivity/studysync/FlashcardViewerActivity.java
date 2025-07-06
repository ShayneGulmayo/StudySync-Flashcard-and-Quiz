package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
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
import com.labactivity.studysync.models.Flashcard;
import java.util.ArrayList;
import java.util.Map;

public class FlashcardViewerActivity extends AppCompatActivity {

    private String setId;
    private FirebaseFirestore db;
    private ArrayList<Flashcard> flashcards, dontKnowFlashcards;
    private TextView frontCard, backCard, items;
    private ImageView backButton, moreButton, knowBtn, dontKnowBtn, flashcardImage;
    private View cardFront, cardBack;
    private String cardOrientation = "term";
    private int knowCount;
    private int dontKnowCount;
    private int currentIndex = 0;
    private boolean isReviewingOnlyDontKnow = false;
    private boolean showingFront = true;

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
            animateCardSwipe();
        });

        dontKnowBtn.setOnClickListener(v -> {
            dontKnowCount++;
            dontKnowFlashcards.add(flashcards.get(currentIndex));
            animateCardSwipe();
        });

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
                            String photoUrl = (String) termEntry.get("photoUrl");
                            String photoPath = (String) termEntry.get("photoPath");

                            Flashcard card = new Flashcard(term, definition, photoUrl, photoPath);

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
                Glide.with(this).load(card.getPhotoUrl()).transform(new RoundedCorners(dpToPx(20))).into(flashcardImage);

                int paddingLeftRightBottom = dpToPx(24);
                int paddingTop = dpToPx(40);
                backCard.setPadding(paddingLeftRightBottom, paddingTop, paddingLeftRightBottom, paddingLeftRightBottom);

            } else {
                flashcardImage.setVisibility(View.GONE);
                int defaultPadding = dpToPx(24);
                backCard.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding);
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
