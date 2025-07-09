package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.opengl.Visibility;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.CarouselAdapter;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.receivers.ReminderReceiver;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.firestore.ListenerRegistration;


public class FlashcardPreviewActivity extends AppCompatActivity {

    private TextView titleTextView, ownerTextView, createdAtTextView, numberOfItemsTextView, privacyText, reminderTextView;
    private ImageView ownerPhotoImageView, backButton, moreButton, privacyIcon, reminderIcon, saveSetBtn;
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private Button startFlashcardBtn;
    private FirebaseFirestore db;
    private String currentPrivacy, setId, currentReminder;
    private final ArrayList<Flashcard> flashcards = new ArrayList<>();
    private ListenerRegistration reminderListener;
    private FirebaseAuth auth;
    private boolean isSaved = false;
    private BottomSheetDialog bottomSheetDialog;
    private AlertDialog deleteConfirmationDialog;
    private DatePickerDialog datePickerDialog;
    private TimePickerDialog timePickerDialog;
    private String accessLevel = "none";



    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_preview);
        auth = FirebaseAuth.getInstance();

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

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reminderListener != null) reminderListener.remove();
        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) bottomSheetDialog.dismiss();
        if (deleteConfirmationDialog != null && deleteConfirmationDialog.isShowing()) deleteConfirmationDialog.dismiss();
        if (datePickerDialog != null && datePickerDialog.isShowing()) datePickerDialog.dismiss();
        if (timePickerDialog != null && timePickerDialog.isShowing()) timePickerDialog.dismiss();
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
        saveSetBtn = findViewById(R.id.saveSetBtn);

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
            if ("owner".equals(accessLevel) || "edit".equals(accessLevel) || "view".equals(accessLevel)) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, FlashcardViewerActivity.class);
                intent.putExtra("setId", setId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "You don't have access to start this flashcard set.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void showMoreBottomSheet() {
        if (isFinishing() || isDestroyed()) return;

        bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.download);
        TextView copyBtn = view.findViewById(R.id.copy);
        TextView privacyBtn = view.findViewById(R.id.privacy);
        TextView reminderBtn = view.findViewById(R.id.reminder);
        TextView sendToChatBtn = view.findViewById(R.id.sendToChat);
        TextView editBtn = view.findViewById(R.id.edit);
        TextView deleteBtn = view.findViewById(R.id.delete);
        TextView reqAccessBtn = view.findViewById(R.id.reqAccess);
        TextView reqEditBtn = view.findViewById(R.id.reqEdit);

        // ✅ Show/hide buttons based on access level
        switch (accessLevel) {
            case "owner":
                // Owner sees everything
                break;

            case "edit":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                reqAccessBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.VISIBLE);
                editBtn.setVisibility(View.VISIBLE);
                break;

            case "view":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                editBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
                reqAccessBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                reqEditBtn.setVisibility(View.VISIBLE);
                break;

            default:
                // No access, allow only Request Access
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                editBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.GONE);
                reqAccessBtn.setVisibility(View.VISIBLE);
                break;
        }

        // ✅ Click listeners
        downloadBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        copyBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Copy clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        privacyBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new Intent(this, PrivacyActivity.class).putExtra("setId", setId));
        });

        reminderBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showReminderDialog();
        });

        sendToChatBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        editBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateFlashcardActivity.class);
            intent.putExtra("setId", setId);
            startActivity(intent);
        });

        deleteBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDeleteConfirmationDialog();
        });

        bottomSheetDialog.show();
    }

    private void showDeleteConfirmationDialog() {
        if (isFinishing() || isDestroyed()) return;

        deleteConfirmationDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Flashcard Set")
                .setMessage("Are you sure you want to delete this flashcard set? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteFlashcardSet())
                .setNegativeButton("No", null)
                .create();

        deleteConfirmationDialog.show();
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
                                // Now remove it from the user's owned_sets
                                db.collection("users").document(auth.getCurrentUser().getUid())
                                        .update("owned_sets", com.google.firebase.firestore.FieldValue.arrayRemove(
                                                // Remove by matching the owned_set object structure
                                                new HashMap<String, Object>() {{
                                                    put("id", setId);
                                                    put("type", "flashcard");
                                                }}
                                        ))
                                        .addOnSuccessListener(unused -> {
                                            Toast.makeText(this, "Flashcard set deleted.", Toast.LENGTH_SHORT).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Deleted set but failed to update owned_sets.", Toast.LENGTH_LONG).show();
                                            finish();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete flashcard set.", Toast.LENGTH_SHORT).show();
                            });

                });
    }

    private void showReminderDialog() {
        if (isFinishing() || isDestroyed()) return;

        Calendar calendar = Calendar.getInstance();

        datePickerDialog = new DatePickerDialog(this, R.style.DialogTheme, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            timePickerDialog = new TimePickerDialog(this, R.style.DialogTheme, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                setReminder(calendar);

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

            if (!isFinishing() && !isDestroyed()) timePickerDialog.show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        if (!isFinishing() && !isDestroyed()) datePickerDialog.show();
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

                    currentPrivacy = documentSnapshot.getString("privacy") != null ? documentSnapshot.getString("privacy") : "Public";
                    currentReminder = documentSnapshot.getString("reminder");

                    if (numberOfItems != null) {
                        numberOfItemsTextView.setText(numberOfItems + (numberOfItems == 1 ? " item" : " items"));
                    } else {
                        numberOfItemsTextView.setText("0 items");
                    }

                    if (currentReminder != null && !currentReminder.isEmpty()) {
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

                    if (ownerUid != null) {
                        db.collection("users").document(ownerUid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        String username = userDoc.getString("username");
                                        ownerTextView.setText(username != null ? username : "Unknown");
                                    } else {
                                        ownerTextView.setText("Unknown");
                                    }
                                });
                    } else {
                        ownerTextView.setText("Unknown");
                    }

                    loadOwnerProfile(ownerUid);

                    String currentUserId = auth.getCurrentUser().getUid();

                    // ✅ Determine access level
                    if (ownerUid != null && ownerUid.equals(currentUserId)) {
                        accessLevel = "owner";
                    } else if ("public".equals(currentPrivacy)) {
                        String privacyRole = documentSnapshot.getString("privacyRole");
                        if ("edit".equalsIgnoreCase(privacyRole)) {
                            accessLevel = "edit";
                        } else if ("view".equalsIgnoreCase(privacyRole)) {
                            accessLevel = "view";
                        } else {
                            accessLevel = "none";
                        }
                    } else {
                        // ✅ Private: check accessUsers map
                        Map<String, String> accessUsers = (Map<String, String>) documentSnapshot.get("accessUsers");
                        if (accessUsers != null && accessUsers.containsKey(currentUserId)) {
                            String userRole = accessUsers.get(currentUserId);
                            if ("edit".equalsIgnoreCase(userRole)) {
                                accessLevel = "edit";
                            } else if ("view".equalsIgnoreCase(userRole)) {
                                accessLevel = "view";
                            } else {
                                accessLevel = "none";
                            }
                        } else {
                            accessLevel = "none";
                        }
                    }

                    // 🚨 Redirect if no access
                    if ("none".equals(accessLevel)) {
                        Intent intent = new Intent(this, NoAccessActivity.class);
                        intent.putExtra("setId", setId);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    // ✅ Load flashcards if access granted
                    loadFlashcards();

                    Object createdAtObj = documentSnapshot.get("createdAt");
                    if (createdAtObj instanceof Timestamp) {
                        Timestamp createdAtTimestamp = (Timestamp) createdAtObj;
                        String formattedDate = new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault()).format(createdAtTimestamp.toDate());
                        createdAtTextView.setText(formattedDate);
                    } else if (createdAtObj instanceof String) {
                        createdAtTextView.setText((String) createdAtObj);
                    } else {
                        createdAtTextView.setText("Unknown");
                    }
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
    private void updateSaveIcon() {
        if (isSaved) {
            saveSetBtn.setImageResource(R.drawable.bookmark_filled);
        } else {
            saveSetBtn.setImageResource(R.drawable.bookmark);
        }
    }

    private void toggleSaveState() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Object> setData = new HashMap<>();
        setData.put("id", setId);
        setData.put("type", "flashcard");

        if (isSaved) {
            db.collection("users").document(userId)
                    .update("saved_sets", com.google.firebase.firestore.FieldValue.arrayRemove(setData))
                    .addOnSuccessListener(unused -> {
                        isSaved = false;
                        updateSaveIcon();
                        Toast.makeText(this, "Set unsaved.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to unsave.", Toast.LENGTH_SHORT).show());
        } else {
            db.collection("users").document(userId)
                    .update("saved_sets", com.google.firebase.firestore.FieldValue.arrayUnion(setData))
                    .addOnSuccessListener(unused -> {
                        isSaved = true;
                        updateSaveIcon();
                        Toast.makeText(this, "Set saved!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show());
        }
    }


}
