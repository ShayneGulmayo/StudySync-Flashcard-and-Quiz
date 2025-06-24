package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
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

    private TextView frontCard, backCard, flashcardTitle, ownerUsername, items;
    private ImageView backButton, moreButton, knowBtn, dontKnowBtn, privacyIcon, ownerPhoto;
    private int knowCount;
    private int dontKnowCount;
    private int currentIndex = 0;
    private boolean showingFront = true;
    private String currentPrivacy, ownerUid;

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
        flashcardTitle = findViewById(R.id.flashcard_title);
        ownerUsername = findViewById(R.id.owner_username);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        knowBtn = findViewById(R.id.know_btn);
        dontKnowBtn = findViewById(R.id.dont_know_btn);
        items = findViewById(R.id.txtView_items);
        privacyIcon = findViewById(R.id.privacy_icon);
        ownerPhoto = findViewById(R.id.owner_profile);

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
    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.more_bottom_sheet_menu, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.privacy).setOnClickListener(v -> {
            togglePrivacy();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.reminder).setOnClickListener(v -> {
            showReminderDialog();
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

    private String formatDateTime(Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy | hh:mm a", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void togglePrivacy() {
        if (setId == null) return;

        String newPrivacy = "Public".equals(currentPrivacy) ? "Private" : "Public";

        db.collection("flashcards").document(setId)
                .update("privacy", newPrivacy)
                .addOnSuccessListener(aVoid -> {
                    currentPrivacy = newPrivacy;
                    updatePrivacyIcon();
                    Toast.makeText(this, "Privacy set to " + newPrivacy, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update privacy.", Toast.LENGTH_SHORT).show();
                });
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

    private void loadFlashcards() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String title = snapshot.getString("title") != null ? snapshot.getString("title") : "Untitled Set";
                    if (title.length() > 20) {
                        title = title.substring(0, 17) + "...";
                    }
                    flashcardTitle.setText(title);

                    ownerUsername.setText(snapshot.getString("owner_username") != null ? snapshot.getString("owner_username") : "Unknown Owner");
                    currentPrivacy = snapshot.getString("privacy") != null ? snapshot.getString("privacy") : "Public";
                    updatePrivacyIcon();

                    ownerUid = snapshot.getString("owner_uid");
                    if (ownerUid != null) {
                        loadOwnerPhoto(ownerUid);
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

    private void loadOwnerPhoto(String uid) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String photoUrl = documentSnapshot.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.user_profile)
                                    .error(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(ownerPhoto);
                        } else {
                            ownerPhoto.setImageResource(R.drawable.user_profile);
                        }
                    } else {
                        ownerPhoto.setImageResource(R.drawable.user_profile);
                    }
                })
                .addOnFailureListener(e -> {
                    ownerPhoto.setImageResource(R.drawable.user_profile);
                });
    }

    private void updatePrivacyIcon() {
        if ("Private".equals(currentPrivacy)) {
            privacyIcon.setImageResource(R.drawable.lock);  // your private icon
        } else {
            privacyIcon.setImageResource(R.drawable.public_icon);
        }
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

    private void showReminderDialog() {
        Calendar calendar = Calendar.getInstance();

        @SuppressLint("ResourceType") DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                setReminder(calendar);

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

            timePickerDialog.show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setReminder(Calendar calendar) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("setTitle", flashcardTitle.getText().toString());
        intent.putExtra("setId", setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }

        String formattedDateTime = formatDateTime(calendar);

        db.collection("flashcards").document(setId)
                .update("reminder", formattedDateTime)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder set for " + formattedDateTime, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to set reminder.", Toast.LENGTH_SHORT).show();
                });
    }






}
