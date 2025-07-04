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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.CarouselAdapter;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.receivers.ReminderReceiver;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.firestore.ListenerRegistration;


public class FlashcardPreviewActivity extends AppCompatActivity {

    private TextView titleTextView, ownerTextView, createdAtTextView, numberOfItemsTextView, privacyText, reminderTextView;
    private ImageView ownerPhotoImageView, backButton, moreButton, privacyIcon, reminderIcon;
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private Button startFlashcardBtn;
    private FirebaseFirestore db;
    private String currentPrivacy, setId, currentReminder;
    private final ArrayList<Flashcard> flashcards = new ArrayList<>();
    private ListenerRegistration reminderListener;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_preview);

        initializeViews();
        db = FirebaseFirestore.getInstance();

        if (getIntent().hasExtra("setId")) {
            setId = getIntent().getStringExtra("setId");
            loadFlashcardSet();
            loadFlashcards();
            listenToReminderUpdates(); // <-- added here
        } else {
            Toast.makeText(this, "No Flashcard ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (setId != null && !setId.isEmpty()) {
            loadFlashcardSet();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reminderListener != null) {
            reminderListener.remove();
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        titleTextView = findViewById(R.id.flashcard_title);
        ownerTextView = findViewById(R.id.owner_username);
        createdAtTextView = findViewById(R.id.createdAt_txt);
        numberOfItemsTextView = findViewById(R.id.item_txt);
        ownerPhotoImageView = findViewById(R.id.owner_profile);
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);
        startFlashcardBtn = findViewById(R.id.start_flashcard_btn);
        moreButton = findViewById(R.id.more_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        privacyText = findViewById(R.id.privacy_txt);
        reminderTextView = findViewById(R.id.reminder_txt);
        reminderIcon = findViewById(R.id.reminder_icon);

        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        backButton.setOnClickListener(v -> {
            if (fromNotification) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
            finish();
        });
        moreButton.setOnClickListener(v -> showMoreBottomSheet());
        startFlashcardBtn.setOnClickListener(v -> {
            if (setId != null && !setId.isEmpty()) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, FlashcardViewerActivity.class);
                intent.putExtra("setId", setId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Unable to start flashcards.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMoreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.privacy).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, PrivacyActivity.class);
            intent.putExtra("setId", setId);
            startActivity(intent);
        });

        view.findViewById(R.id.reminder).setOnClickListener(v -> {
            showReminderDialog();
            bottomSheetDialog.dismiss();
        });


        view.findViewById(R.id.sendToChat).setOnClickListener(v -> {
            String setType = "flashcard";
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("setType", setType);
            startActivity(intent);
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
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = documentSnapshot.getData();
                    if (data != null && data.containsKey("terms")) {
                        Object termsObj = data.get("terms");
                        if (termsObj instanceof Map) {
                            Map<String, Object> terms = (Map<String, Object>) termsObj;

                            for (Map.Entry<String, Object> entry : terms.entrySet()) {
                                Object value = entry.getValue();
                                if (value instanceof Map) {
                                    Map<String, Object> termEntry = (Map<String, Object>) value;
                                    String photoPath = termEntry.get("photoPath") != null ? termEntry.get("photoPath").toString() : null;

                                    if (photoPath != null && !photoPath.isEmpty()) {
                                        SupabaseUploader.deleteFile("flashcard-images", photoPath, new SupabaseUploader.UploadCallback() {
                                            @Override
                                            public void onUploadComplete(boolean success, String message, String publicUrl) {
                                                if (success) {
                                                    Log.d("Supabase", "Deleted image: " + photoPath);
                                                } else {
                                                    Log.e("Supabase", "Failed to delete image: " + photoPath + " Reason: " + message);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }

                    db.collection("flashcards").document(setId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Flashcard set and images deleted.", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete flashcard set.", Toast.LENGTH_SHORT).show();
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch flashcard set.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showReminderDialog() {
        Calendar calendar = Calendar.getInstance();

        @SuppressLint("ResourceType") DatePickerDialog datePickerDialog = new DatePickerDialog(this, R.style.DialogTheme, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, R.style.DialogTheme, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                setReminder(calendar);

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

            timePickerDialog.show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    private void listenToReminderUpdates() {
        if (setId == null) return;

        reminderListener = db.collection("flashcards").document(setId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        Log.e("ReminderListener", "Error or document missing");
                        return;
                    }

                    String reminder = snapshot.getString("reminder");
                    if (reminder != null && !reminder.isEmpty()) {
                        reminderTextView.setText("Reminder: " + reminder);
                        reminderIcon.setImageResource(R.drawable.notifications);
                    } else {
                        reminderTextView.setText("Reminder: None");
                        reminderIcon.setImageResource(R.drawable.off_notifications);
                    }
                });
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setReminder(Calendar calendar) {
        String formattedDateTime = formatDateTime(calendar);

        db.collection("flashcards").document(setId)
                .update("reminder", formattedDateTime)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder set for " + formattedDateTime, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to set reminder.", Toast.LENGTH_SHORT).show();
                });

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("setId", setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, setId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    private String formatDateTime(Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy | hh:mm a", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void loadFlashcardSet() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Flashcard not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String title = documentSnapshot.getString("title");
                    String ownerUid = documentSnapshot.getString("owner_uid");
                    Long numberOfItems = documentSnapshot.getLong("number_of_items");

                    titleTextView.setText(title != null ? title : "Untitled");

                    String privacy = documentSnapshot.getString("privacy");
                    currentPrivacy = privacy != null ? privacy : "Public";

                    String reminder = documentSnapshot.getString("reminder");
                    currentReminder = (reminder != null && !reminder.isEmpty()) ? reminder : null;

                    if (ownerUid != null) {
                        db.collection("users").document(ownerUid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        String latestUsername = userDoc.getString("username");
                                        ownerTextView.setText(latestUsername != null ? latestUsername : "Unknown");
                                    } else {
                                        ownerTextView.setText("Unknown");
                                    }
                                })
                                .addOnFailureListener(e -> ownerTextView.setText("Unknown"));
                    } else {
                        ownerTextView.setText("Unknown");
                    }

                    if (currentReminder != null) {
                        reminderTextView.setText("Reminder: " + currentReminder);
                        reminderIcon.setImageResource(R.drawable.notifications);
                    } else {
                        reminderTextView.setText("Reminder: None");
                        reminderIcon.setImageResource(R.drawable.off_notifications);
                    }

                    if ("private".equals(currentPrivacy)) {
                        privacyIcon.setImageResource(R.drawable.lock);
                        privacyText.setText("Private");
                    } else {
                        privacyIcon.setImageResource(R.drawable.public_icon);
                        privacyText.setText("Public");

                    }

                    if (numberOfItems != null) {
                        String label = numberOfItems == 1 ? " item" : " items";
                        numberOfItemsTextView.setText(numberOfItems + label);
                    } else {
                        numberOfItemsTextView.setText("0 items");
                    }

                    Object createdAtObj = documentSnapshot.get("createdAt");

                    if (createdAtObj instanceof Timestamp) {
                        Timestamp createdAtTimestamp = (Timestamp) createdAtObj;
                        String formattedDate = new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault())
                                .format(createdAtTimestamp.toDate());
                        createdAtTextView.setText(formattedDate);
                    } else if (createdAtObj instanceof String) {
                        // Optional: handle if stored as string
                        createdAtTextView.setText((String) createdAtObj);
                    } else {
                        createdAtTextView.setText("Unknown");
                        Log.w("FlashcardPreview", "createdAt is not a Timestamp: " + createdAtObj);
                    }
                    loadOwnerProfile(ownerUid);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load flashcard", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadOwnerProfile(String ownerUid) {
        if (ownerUid == null) {
            ownerPhotoImageView.setImageResource(R.drawable.user_profile);
            return;
        }

        db.collection("users").document(ownerUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String photoUrl = userDoc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(ownerPhotoImageView);
                        } else {
                            ownerPhotoImageView.setImageResource(R.drawable.user_profile);
                        }
                    } else {
                        ownerPhotoImageView.setImageResource(R.drawable.user_profile);
                    }
                })
                .addOnFailureListener(e -> ownerPhotoImageView.setImageResource(R.drawable.user_profile));
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
                        Object termsObj = data.get("terms");
                        if (termsObj instanceof Map) {
                            Map<String, Object> terms = (Map<String, Object>) termsObj;
                            flashcards.clear();

                            for (Map.Entry<String, Object> entry : terms.entrySet()) {
                                Object value = entry.getValue();
                                if (value instanceof Map) {
                                    Map<String, Object> termEntry = (Map<String, Object>) value;
                                    String term = termEntry.get("term") != null ? termEntry.get("term").toString() : "";
                                    String definition = termEntry.get("definition") != null ? termEntry.get("definition").toString() : "";
                                    String photoUrl = termEntry.get("photoUrl") != null ? termEntry.get("photoUrl").toString() : "";
                                    String photoPath = termEntry.get("photoPath") != null ? termEntry.get("photoPath").toString() : "";
                                    flashcards.add(new Flashcard(term, definition, photoUrl, photoPath));
                                } else {
                                    Log.e("Flashcards", "Skipping invalid term entry: " + entry.getKey() + " -> " + value);
                                }
                            }
                        }
                    }

                    Log.d("Flashcards", "Loaded flashcards: " + flashcards.size());

                    if (!flashcards.isEmpty()) {
                        setupCarousel();
                    } else {
                        Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
                    }

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    private void setupCarousel() {
        CarouselAdapter carouselAdapter = new CarouselAdapter(flashcards);
        carouselViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        carouselViewPager.setAdapter(carouselAdapter);
        dotsIndicator.setViewPager2(carouselViewPager);
    }
}
