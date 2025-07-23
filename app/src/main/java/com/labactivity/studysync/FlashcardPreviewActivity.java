package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.labactivity.studysync.adapters.FlashcardCarouselAdapter;
import com.labactivity.studysync.helpers.AlarmHelper;
import com.labactivity.studysync.models.Flashcard;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlashcardPreviewActivity extends AppCompatActivity {

    private TextView titleTextView, ownerTextView, numberOfItemsTextView, setReminderTxt;
    private ImageView ownerPhotoImageView, backButton, moreButton, saveSetBtn;
    private Button startFlashcardBtn, cancelReminderBtn, convertBtn;
    private MaterialButton downloadBtn, setReminderBtn, shareToChatBtn;
    private LinearLayout linearLayout;
    private ViewPager2 carouselViewPager;
    private String currentPrivacy, setId, offlineFileName, ownerUid, userId, title;
    private String accessLevel = "none";
    private SpringDotsIndicator dotsIndicator;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private BottomSheetDialog bottomSheetDialog;
    private AlertDialog deleteConfirmationDialog;
    private boolean isSaved = false;
    private boolean isDownloaded = false;
    private boolean isRedirecting = false;
    private boolean isOffline;
    private final ArrayList<Flashcard> flashcards = new ArrayList<>();
    private Map<String, Object> setData;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_preview);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        initializeViews();
        title = "Review Set";

        isOffline = getIntent().getBooleanExtra("isOffline", false);
        offlineFileName = getIntent().getStringExtra("offlineFileName");
        setId = getIntent().getStringExtra("setId");

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            Log.d("Auth", "Current user ID: " + userId);
        } else {
            userId = null;
            Log.d("Auth", "No user is currently signed in.");
        }

        if (isOffline) {
            if (offlineFileName != null) {
                loadOfflineSet(offlineFileName);
            } else {
                Toast.makeText(this, "No offline file specified.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            if (setId != null) {
                fetchSetFromFirestore(setId);
                loadFlashcardSet();
                loadFlashcards();

                if (userId != null) {
                    checkIfSaved();
                }
            } else {
                Toast.makeText(this, "No flashcard set ID provided.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        if (setId != null) {
            db.collection("flashcards")
                    .document(setId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            title = documentSnapshot.getString("title");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to fetch set title.", Toast.LENGTH_SHORT).show();
                    });
        }

        if (setId != null && !isOffline) {
            if (!AlarmHelper.isReminderSet(this, setId)) {
                cancelReminderBtn.setVisibility(View.GONE);
            } else {
                cancelReminderBtn.setVisibility(View.VISIBLE);
            }
        }

        loadReminderText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReminderText();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) bottomSheetDialog.dismiss();
        if (deleteConfirmationDialog != null && deleteConfirmationDialog.isShowing()) deleteConfirmationDialog.dismiss();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        titleTextView = findViewById(R.id.flashcard_title);
        ownerTextView = findViewById(R.id.owner_username);
        numberOfItemsTextView = findViewById(R.id.item_txt);
        ownerPhotoImageView = findViewById(R.id.owner_profile);
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);
        startFlashcardBtn = findViewById(R.id.start_flashcard_btn);
        moreButton = findViewById(R.id.more_button);
        saveSetBtn = findViewById(R.id.saveQuizBtn);
        setReminderTxt = findViewById(R.id.setRemindersTxt);
        cancelReminderBtn = findViewById(R.id.cancelReminderBtn);
        convertBtn = findViewById(R.id.convertToQuizBtn);
        shareToChatBtn = findViewById(R.id.shareToChat);
        downloadBtn = findViewById(R.id.downloadBtn);
        setReminderBtn = findViewById(R.id.setReminderBtn);
        linearLayout = findViewById(R.id.reminder_layout);

        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        shareToChatBtn.setOnClickListener(view -> {
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        downloadBtn.setOnClickListener(v -> {
            showDownloadOptionsDialog();
        });

        setReminderBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("To schedule reminders exactly on time, you need to allow this app to set exact alarms in your system settings.")
                            .setPositiveButton("Allow", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }
            showDateTimePicker();
        });

        ownerTextView.setOnClickListener(v -> openUserProfile());
        ownerPhotoImageView.setOnClickListener(v -> openUserProfile());

        backButton.setOnClickListener(v -> { finish(); });

        saveSetBtn.setOnClickListener(view -> toggleSaveState());

        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        startFlashcardBtn.setOnClickListener(v -> {
            if ("owner".equals(accessLevel) || "edit".equals(accessLevel) || "view".equals(accessLevel)) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, FlashcardViewerActivity.class);
                intent.putExtra("setId", setId);

                if (isOffline) {
                    intent.putExtra("isOffline", true);
                    intent.putExtra("offlineFileName", offlineFileName);
                }

                startActivity(intent);
            } else {
                Toast.makeText(this, "You don't have access to start this flashcard set.", Toast.LENGTH_SHORT).show();
            }
        });

        convertBtn.setOnClickListener(view -> {
            new AlertDialog.Builder(FlashcardPreviewActivity.this)
                    .setTitle("Choose Question Format")
                    .setMessage("Which part should be used as the question?")
                    .setPositiveButton("Definition", (dialog, which) -> {
                        Intent intent = new Intent(FlashcardPreviewActivity.this, LoadingSetActivity.class);
                        intent.putExtra("convertFromId", setId);
                        intent.putExtra("originalType", "flashcard");
                        intent.putExtra("flashToQuizQuestionIsDefinition", true); // Definition as question
                        startActivity(intent);
                    })
                    .setNegativeButton("Term", (dialog, which) -> {
                        Intent intent = new Intent(FlashcardPreviewActivity.this, LoadingSetActivity.class);
                        intent.putExtra("convertFromId", setId);
                        intent.putExtra("originalType", "flashcard");
                        intent.putExtra("flashToQuizQuestionIsDefinition", false); // Term as question
                        startActivity(intent);
                    })
                    .show();
        });


        cancelReminderBtn.setOnClickListener(v -> {
            AlarmHelper.cancelAlarm(this, setId);
            setReminderTxt.setText("No reminder set");
            cancelReminderBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Reminder canceled.", Toast.LENGTH_SHORT).show();
        });

    }

    private void showDateTimePicker() {
        final Calendar calendar = Calendar.getInstance();

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.reminder_picker, null);
        DatePicker datePicker = dialogView.findViewById(R.id.datePicker);
        CheckBox repeatDailyCheckBox = dialogView.findViewById(R.id.repeatDailyCheckBox);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setTitle("Select Date")
                .setPositiveButton("Next", (dialog, which) -> {
                    calendar.set(Calendar.YEAR, datePicker.getYear());
                    calendar.set(Calendar.MONTH, datePicker.getMonth());
                    calendar.set(Calendar.DAY_OF_MONTH, datePicker.getDayOfMonth());

                    TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                            (view1, hour, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hour);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);

                                boolean isRepeating = repeatDailyCheckBox.isChecked();
                                long currentTimeMillis = System.currentTimeMillis();


                                if (isRepeating) {
                                    while (calendar.getTimeInMillis() <= currentTimeMillis) {
                                        calendar.add(Calendar.DAY_OF_YEAR, 1);
                                    }
                                } else {
                                    if (calendar.getTimeInMillis() <= currentTimeMillis) {
                                        Toast.makeText(this, "Time has already passed.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                }

                                AlarmHelper.setAlarm(this, calendar, setId, title, isRepeating);

                                SharedPreferences prefs = getSharedPreferences("ReminderPrefs", MODE_PRIVATE);
                                prefs.edit()
                                        .putLong("reminderTime", calendar.getTimeInMillis())
                                        .putBoolean("isRepeating", isRepeating)
                                        .apply();

                                String ampm = (hour >= 12) ? "PM" : "AM";
                                int displayHour = (hour % 12 == 0) ? 12 : hour % 12;
                                String display = String.format("%02d:%02d %s on %d/%d/%d%s",
                                        displayHour, minute, ampm,
                                        calendar.get(Calendar.MONTH) + 1,
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                        calendar.get(Calendar.YEAR),
                                        isRepeating ? " (Daily)" : "");

                                setReminderTxt.setText(display);
                                cancelReminderBtn.setVisibility(View.VISIBLE);

                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

                    timePickerDialog.show();

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadReminderText() {
        SharedPreferences prefs = getSharedPreferences("ReminderPrefs", MODE_PRIVATE);
        long reminderTime = prefs.getLong("reminderTime", -1);
        boolean isRepeating = prefs.getBoolean("isRepeating", false);

        if (reminderTime != -1) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(reminderTime);

            long currentTimeMillis = System.currentTimeMillis();

            if (isRepeating && reminderTime <= currentTimeMillis) {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                } while (calendar.getTimeInMillis() <= currentTimeMillis);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong("reminderTime", calendar.getTimeInMillis());
                editor.apply();
            }

            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            String ampm = (hour >= 12) ? "PM" : "AM";
            int displayHour = (hour % 12 == 0) ? 12 : hour % 12;

            String display = String.format("%02d:%02d %s on %d/%d/%d%s",
                    displayHour, minute, ampm,
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.YEAR),
                    isRepeating ? " (Daily)" : "");

            setReminderTxt.setText(display);
            cancelReminderBtn.setVisibility(View.VISIBLE);
        } else {
            setReminderTxt.setText("");
            cancelReminderBtn.setVisibility(View.GONE);
        }
    }

    private void openUserProfile() {
        if (ownerUid != null) {
            Intent intent = new Intent(FlashcardPreviewActivity.this, UserProfileActivity.class);
            intent.putExtra("userId", ownerUid);
            startActivity(intent);
        } else if (isOffline) {
            Toast.makeText(this, "You are Offline.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "User Not Found.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean canNavigate() {
        return !isRedirecting && !isFinishing() && !isDestroyed();
    }

    private void showMoreBottomSheet() {
        if (isRedirecting || isFinishing() || isDestroyed()) return;

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

        switch (accessLevel) {
            case "Owner":
                break;

            case "Editor":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                reqAccessBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.VISIBLE);
                editBtn.setVisibility(View.VISIBLE);
                break;

            case "Viewer":
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

        downloadBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            showDownloadOptionsDialog();
            bottomSheetDialog.dismiss();
        });

        copyBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            makeCopy();
            bottomSheetDialog.dismiss();
        });

        privacyBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            bottomSheetDialog.dismiss();
            startActivity(new Intent(this, PrivacyActivity.class).putExtra("setId", setId));
        });

        reminderBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("To schedule reminders exactly on time, you need to allow this app to set exact alarms in your system settings.")
                            .setPositiveButton("Allow", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }
            showDateTimePicker();
        });

        sendToChatBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        editBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateFlashcardActivity.class);
            intent.putExtra("setId", setId);
            startActivity(intent);
        });

        deleteBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            bottomSheetDialog.dismiss();
            showDeleteConfirmationDialog();
        });

        reqEditBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            Toast.makeText(this, "Request Edit clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        reqAccessBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            Toast.makeText(this, "Request Access clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void showDownloadOptionsDialog() {
        String[] options = {"Download as PDF", "Download for Offline Use"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Download Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        downloadOfflinePdf(setId);
                    } else if (which == 1) {
                        downloadSet();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchSetFromFirestore(String setId) {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        setData = documentSnapshot.getData();
                        if (setData != null) {
                            setData.put("id", setId);
                            setData.put("type", "flashcard");
                            String ownerUid = documentSnapshot.getString("owner_uid");

                            if (ownerUid != null) {
                                db.collection("users").document(ownerUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                String username = userDoc.getString("username");
                                                setData.put("username", username != null ? username : "Unknown User");
                                                String photoUrl = userDoc.getString("photoUrl");
                                                setData.put("photoUrl", photoUrl != null ? photoUrl : "");
                                            } else {
                                                setData.put("username", "Unknown User");
                                                setData.put("photoUrl", "");
                                            }
                                            loadFlashcardSet();
                                        })
                                        .addOnFailureListener(e -> {
                                            setData.put("username", "Unknown User");
                                            setData.put("photoUrl", "");
                                            loadFlashcardSet();
                                        });
                            } else {
                                setData.put("username", "Unknown User");
                                setData.put("photoUrl", "");
                                loadFlashcardSet();
                            }
                        } else {
                            Toast.makeText(this, "Set data is empty.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Set not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to fetch set.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void downloadSet() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> setData = documentSnapshot.getData();
                        if (setData != null) {
                            setData.put("id", setId);

                            String ownerUid = documentSnapshot.getString("owner_uid");
                            if (ownerUid != null) {
                                db.collection("users").document(ownerUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                String username = userDoc.getString("username");
                                                String photoUrl = userDoc.getString("photoUrl");

                                                setData.put("username", username != null ? username : "Unknown User");
                                                setData.put("photoUrl", photoUrl != null ? photoUrl : "");

                                            } else {
                                                setData.put("username", "Unknown User");
                                                setData.put("photoUrl", "");
                                            }

                                            saveSetOffline(setData, setId, "flashcard");


                                            Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                            startActivity(intent);
                                            if (bottomSheetDialog != null) bottomSheetDialog.dismiss();

                                        })
                                        .addOnFailureListener(e -> {
                                            setData.put("username", "Unknown User");
                                            setData.put("photoUrl", "");
                                            saveSetOffline(setData, setId, "flashcard");


                                            Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                            startActivity(intent);
                                            if (bottomSheetDialog != null) bottomSheetDialog.dismiss();
                                        });
                            } else {
                                setData.put("username", "Unknown User");
                                setData.put("photoUrl", "");
                                saveSetOffline(setData, setId, "flashcard");


                                Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                startActivity(intent);
                                if (bottomSheetDialog != null) bottomSheetDialog.dismiss();
                            }
                        } else {
                            Toast.makeText(this, "Set data is empty.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Set not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to fetch set data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveSetOffline(Map<String, Object> setData, String setId, String type) {
        File dir = getFilesDir();
        File file = new File(dir, "set_" + setId + ".json");

        if (file.exists()) {
            Toast.makeText(this, "Set already downloaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            setData.put("type", type);
            setData.put("fileName", "set_" + setId + ".json");

            String json = new Gson().toJson(setData);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.getBytes());
            fos.close();
            Toast.makeText(this, "Set downloaded successfully.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to download set.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadOfflineSet(String fileName) {
        File dir = getFilesDir();
        File file = new File(dir, fileName);

        if (!file.exists()) {
            Toast.makeText(this, "Offline file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String json = new String(data);

            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            setData = new Gson().fromJson(json, type);

            loadFlashcardSet();
            loadFlashcards();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load offline set.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void downloadOfflinePdf(String setId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title == null || title.isEmpty()) {
                            title = "Flashcard_" + setId;
                        }

                        Object termsObj = documentSnapshot.get("terms");

                        if (termsObj == null) {
                            Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<Object> rawTerms;

                        if (termsObj instanceof Map) {
                            Map<String, Object> termsMap = (Map<String, Object>) termsObj;
                            rawTerms = new ArrayList<>(termsMap.values());
                        } else if (termsObj instanceof List) {
                            rawTerms = (List<Object>) termsObj;
                        } else {
                            Toast.makeText(this, "Terms data is invalid format.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (rawTerms.isEmpty()) {
                            Toast.makeText(this, "No flashcards in this set.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        writePdfFile(title, rawTerms);

                    } else {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch flashcards: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    private void writePdfFile(String title, List<Object> termsList) {
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            PdfDocument pdfDocument = new PdfDocument();
            Paint paint = new Paint();
            paint.setTextSize(14f);
            paint.setAntiAlias(true);

            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 40;
            int y = margin;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint titlePaint = new Paint();
            titlePaint.setTextSize(18f);
            titlePaint.setFakeBoldText(true);
            titlePaint.setAntiAlias(true);
            canvas.drawText(title, margin, y, titlePaint);
            y += 40;

            for (Object termItem : termsList) {
                if (termItem instanceof Map) {
                    Map<String, Object> termMap = (Map<String, Object>) termItem;
                    String term = (String) termMap.get("term");
                    String definition = (String) termMap.get("definition");
                    String photoUrl = (String) termMap.get("photoUrl");

                    if (term != null) {
                        y = drawWrappedText(canvas, "Term: " + term, paint, margin, y, pageWidth - margin);
                        y += 10;
                    }

                    if (definition != null) {
                        y = drawWrappedText(canvas, "Definition: " + definition, paint, margin + 20, y, pageWidth - margin);
                        y += 10;
                    }

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        try {
                            URL url = new URL(photoUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setDoInput(true);
                            connection.connect();

                            InputStream input = connection.getInputStream();
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            byte[] data = new byte[4096];
                            int n;
                            while ((n = input.read(data)) != -1) {
                                buffer.write(data, 0, n);
                            }
                            input.close();
                            byte[] imageBytes = buffer.toByteArray();

                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            if (bitmap == null) {
                                Log.e("PDF", "Failed to decode bitmap from URL: " + photoUrl);
                                continue;
                            }

                            bitmap = getRoundedCornerBitmap(bitmap, dpToPx(20));

                            int imageSize = 2 * 72;

                            if (y + imageSize > pageHeight - margin) {
                                pdfDocument.finishPage(page);
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.getPages().size() + 1).create();
                                page = pdfDocument.startPage(pageInfo);
                                canvas = page.getCanvas();
                                y = margin;
                            }

                            Rect destRect = new Rect(margin, y, margin + imageSize, y + imageSize);
                            canvas.drawBitmap(bitmap, null, destRect, null);
                            y += imageSize + 20;

                        } catch (Exception e) {
                            Log.e("PDF", "Error downloading image: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (y > pageHeight - margin) {
                        pdfDocument.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.getPages().size() + 1).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin;
                    }
                }
            }

            pdfDocument.finishPage(page);

            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = safeTitle + ".pdf";

            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }

            if (uri != null) {
                OutputStream outputStream = resolver.openOutputStream(uri);
                pdfDocument.writeTo(outputStream);
                pdfDocument.close();
                outputStream.close();

                Toast.makeText(this, "PDF downloaded to Downloads/" + fileName, Toast.LENGTH_LONG).show();
                openPdfFile(uri);
            } else {
                Toast.makeText(this, "Failed to create PDF file.", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int cornerRadius) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int drawWrappedText(Canvas canvas, String text, Paint paint, int x, int y, int rightMargin) {
        int maxWidth = rightMargin - x;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (paint.measureText(line + word + " ") > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint);
                y += 20;
                line = new StringBuilder();
            }
            line.append(word).append(" ");
        }
        if (!line.toString().isEmpty()) {
            canvas.drawText(line.toString(), x, y, paint);
            y += 20;
        }
        return y;
    }

    private void openPdfFile(Uri uri) throws ActivityNotFoundException {
        if (uri == null) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Open PDF File"));
    }
    private void makeCopy() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get()
                .addOnSuccessListener(userSnapshot -> {
                    if (!userSnapshot.exists()) {
                        Toast.makeText(this, "User record not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("flashcards").document(setId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Map<String, Object> originalData = documentSnapshot.getData();
                                if (originalData == null) {
                                    Toast.makeText(this, "No data to copy.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Map<String, Object> copyData = new HashMap<>(originalData);
                                copyData.remove("reminder");
                                copyData.put("owner_uid", userId);
                                copyData.put("createdAt", Timestamp.now());
                                copyData.put("privacy", "private");
                                copyData.put("privacyRole", "view");
                                copyData.put("accessUsers", new HashMap<String, Object>());

                                String originalTitle = (String) originalData.get("title");
                                if (originalTitle == null) originalTitle = "Untitled Set";
                                copyData.put("title", originalTitle + " (Copy)");

                                Map<String, Object> originalTerms = (Map<String, Object>) originalData.get("terms");
                                Map<String, Object> copiedTerms = new HashMap<>();

                                if (originalTerms == null || originalTerms.isEmpty()) {
                                    copyData.put("terms", copiedTerms);
                                    saveCopiedFlashcardSet(copyData, userId);
                                    return;
                                }

                                final int[] remainingUploads = {originalTerms.size()};

                                for (Map.Entry<String, Object> entry : originalTerms.entrySet()) {
                                    String key = entry.getKey();
                                    Map<String, Object> originalCard = (Map<String, Object>) entry.getValue();
                                    Map<String, Object> copiedCard = new HashMap<>(originalCard);

                                    String oldPhotoPath = (String) originalCard.get("photoPath");

                                    if (oldPhotoPath != null && !oldPhotoPath.isEmpty()) {
                                        StorageReference oldRef = FirebaseStorage.getInstance().getReference("flashcard-images/" + oldPhotoPath);

                                        oldRef.getBytes(5 * 1024 * 1024)
                                                .addOnSuccessListener(bytes -> {
                                                    String newFileName = "flashcard_" + UUID.randomUUID() + ".jpg";
                                                    StorageReference newRef = FirebaseStorage.getInstance().getReference("flashcard-images/" + newFileName);

                                                    newRef.putBytes(bytes)
                                                            .addOnSuccessListener(taskSnapshot -> newRef.getDownloadUrl()
                                                                    .addOnSuccessListener(newUri -> {
                                                                        copiedCard.put("photoUrl", newUri.toString());
                                                                        copiedCard.put("photoPath", newFileName);
                                                                        copiedTerms.put(key, copiedCard);

                                                                        remainingUploads[0]--;
                                                                        if (remainingUploads[0] == 0) {
                                                                            copyData.put("terms", copiedTerms);
                                                                            saveCopiedFlashcardSet(copyData, userId);
                                                                        }
                                                                    }))
                                                            .addOnFailureListener(e -> {
                                                                Log.e("CopyFlashcard", "Failed to upload copied image: " + oldPhotoPath);
                                                                copiedCard.remove("photoUrl");
                                                                copiedCard.remove("photoPath");
                                                                copiedTerms.put(key, copiedCard);
                                                                remainingUploads[0]--;
                                                                if (remainingUploads[0] == 0) {
                                                                    copyData.put("terms", copiedTerms);
                                                                    saveCopiedFlashcardSet(copyData, userId);
                                                                }
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e("CopyFlashcard", "Failed to download original image bytes: " + oldPhotoPath);
                                                    copiedCard.remove("photoUrl");
                                                    copiedCard.remove("photoPath");
                                                    copiedTerms.put(key, copiedCard);
                                                    remainingUploads[0]--;
                                                    if (remainingUploads[0] == 0) {
                                                        copyData.put("terms", copiedTerms);
                                                        saveCopiedFlashcardSet(copyData, userId);
                                                    }
                                                });

                                    } else {
                                        copiedTerms.put(key, copiedCard);
                                        remainingUploads[0]--;
                                        if (remainingUploads[0] == 0) {
                                            copyData.put("terms", copiedTerms);
                                            saveCopiedFlashcardSet(copyData, userId);
                                        }
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error fetching original flashcard set.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching user data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveCopiedFlashcardSet(Map<String, Object> copyData, String userId) {
        db.collection("flashcards").add(copyData)
                .addOnSuccessListener(newDocRef -> {
                    long timestamp = System.currentTimeMillis();
                    int progress = 0; // Default value, adjust if you track specific progress

                    Map<String, Object> ownedSetData = new HashMap<>();
                    ownedSetData.put("id", newDocRef.getId());
                    ownedSetData.put("type", "flashcard");
                    ownedSetData.put("progress", progress);
                    ownedSetData.put("lastAccessed", timestamp);

                    // Optional: track where it was copied from
                    // ownedSetData.put("copiedFrom", originalSetId); // only if you store the original ID

                    db.collection("users").document(userId)
                            .update("owned_sets", FieldValue.arrayUnion(ownedSetData))
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Flashcard set copied!", Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(this, FlashcardPreviewActivity.class);
                                intent.putExtra("setId", newDocRef.getId());
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Copy succeeded but failed to update owned sets.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to copy flashcard set.", Toast.LENGTH_SHORT).show();
                });
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
                                        StorageReference photoRef = FirebaseStorage.getInstance()
                                                .getReference("flashcard-images/" + photoPath);

                                        photoRef.delete()
                                                .addOnSuccessListener(aVoid -> Log.d("FirebaseStorage", "Deleted image: " + photoPath))
                                                .addOnFailureListener(e -> Log.e("FirebaseStorage", "Failed to delete image: " + photoPath + " Reason: " + e.getMessage()));
                                    }
                                }
                            }
                        }
                    }

                    db.collection("flashcards").document(setId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                String userId = auth.getCurrentUser().getUid();
                                DocumentReference userRef = db.collection("users").document(userId);

                                db.runTransaction(transaction -> {
                                    DocumentSnapshot snapshot = transaction.get(userRef);
                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) snapshot.get("owned_sets");

                                    if (ownedSets != null) {
                                        List<Map<String, Object>> updatedOwnedSets = new ArrayList<>();
                                        for (Map<String, Object> item : ownedSets) {
                                            if (!item.get("id").equals(setId)) {
                                                updatedOwnedSets.add(item);
                                            }
                                        }
                                        transaction.update(userRef, "owned_sets", updatedOwnedSets);
                                    }
                                    return null;
                                }).addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Flashcard set deleted.", Toast.LENGTH_SHORT).show();
                                    finish();
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to clean owned_sets.", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete flashcard set.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching flashcard set.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadFlashcardSet() {
        if (isOffline) {
            String title = (String) setData.get("title");
            titleTextView.setText(title != null ? title : "Untitled");

            Long numberOfItems = setData.get("number_of_items") instanceof Number
                    ? ((Number) setData.get("number_of_items")).longValue() : 0;
            numberOfItemsTextView.setText("| " + numberOfItems + (numberOfItems == 1 ? " term" : " terms"));

            String ownerName = (String) setData.get("username");
            ownerTextView.setText(ownerName != null ? ownerName : "Unknown");

            String photoUrl = (String) setData.get("photoUrl");
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.user_profile)
                        .circleCrop()
                        .into(ownerPhotoImageView);
            } else {
                ownerPhotoImageView.setImageResource(R.drawable.user_profile);
            }

            // Hide buttons for offline mode
            moreButton.setVisibility(View.GONE);
            saveSetBtn.setVisibility(View.GONE);
            convertBtn.setVisibility(View.GONE);
            downloadBtn.setVisibility(View.GONE);
            shareToChatBtn.setVisibility(View.GONE);
            setReminderBtn.setVisibility(View.GONE);
            cancelReminderBtn.setVisibility(View.GONE);
            linearLayout.setVisibility(View.GONE);

            accessLevel = "Viewer";
            loadFlashcards();
        } else {
            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!documentSnapshot.exists()) {
                            Toast.makeText(this, "Flashcard not found", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        ownerUid = documentSnapshot.getString("owner_uid");
                        String title = documentSnapshot.getString("title");
                        Long numberOfItems = documentSnapshot.getLong("number_of_items");
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        currentPrivacy = documentSnapshot.getString("privacy") != null ? documentSnapshot.getString("privacy") : "Public";

                        titleTextView.setText(title != null ? title : "Untitled");

                        String currentUserId = currentUser != null ? currentUser.getUid() : null;

                        if (ownerUid != null && currentUserId != null && ownerUid.equals(currentUserId)) {
                            accessLevel = "Owner";
                            saveSetBtn.setVisibility(View.GONE);
                        } else if ("public".equalsIgnoreCase(currentPrivacy)) {
                            String privacyRole = documentSnapshot.getString("privacyRole");
                            if ("Editor".equalsIgnoreCase(privacyRole)) {
                                accessLevel = "Editor";
                            } else {
                                accessLevel = "Viewer";
                            }
                            saveSetBtn.setVisibility(View.VISIBLE);
                        } else {
                            Map<String, String> accessUsers = (Map<String, String>) documentSnapshot.get("accessUsers");
                            if (accessUsers != null && currentUserId != null && accessUsers.containsKey(currentUserId)) {
                                String userRole = accessUsers.get(currentUserId);
                                if ("Editor".equalsIgnoreCase(userRole)) {
                                    accessLevel = "Editor";
                                } else if ("Viewer".equalsIgnoreCase(userRole)) {
                                    accessLevel = "Viewer";
                                } else {
                                    accessLevel = "none";
                                }
                            } else {
                                accessLevel = "none";
                            }
                            saveSetBtn.setVisibility(View.VISIBLE);
                        }

                        if (numberOfItems != null) {
                            numberOfItemsTextView.setText("| " + numberOfItems + (numberOfItems == 1 ? " term" : " terms"));
                        } else {
                            numberOfItemsTextView.setText("| 0 terms");
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

                        // Redirect if access is not granted
                        if ("none".equalsIgnoreCase(accessLevel)) {
                            if (isRedirecting) return;
                            isRedirecting = true;
                            Intent intent = new Intent(this, NoAccessActivity.class);
                            intent.putExtra("setId", setId);
                            startActivity(intent);
                            finish();
                            return;
                        }

                        loadFlashcards();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load flashcard", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }
    }

    private void loadFlashcards() {
        if (isOffline) {
            Object termsObj = setData.get("terms");
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
                    }
                }
            }

            Log.d("Flashcards", "Loaded flashcards offline: " + flashcards.size());
            if (!flashcards.isEmpty()) {
                setupCarousel();
            } else {
                Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
            }
        } else {
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
    }

    private void loadOwnerProfile(String ownerUid) {
        if (ownerUid == null) {
            ownerPhotoImageView.setImageResource(R.drawable.user_profile);
            return;
        }

        db.collection("users").document(ownerUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (userDoc.exists()) {
                        String photoUrl = userDoc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(FlashcardPreviewActivity.this)
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
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    ownerPhotoImageView.setImageResource(R.drawable.user_profile);
                });
    }

    private void setupCarousel() {
        FlashcardCarouselAdapter carouselAdapter = new FlashcardCarouselAdapter(flashcards);
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
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
            if (savedSets == null) {
                savedSets = new ArrayList<>();
            }

            Map<String, Object> setData = new HashMap<>();
            setData.put("id", setId);
            setData.put("type", "flashcard");

            if (isSaved) {
                Iterator<Map<String, Object>> iterator = savedSets.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> item = iterator.next();
                    if (setId.equals(item.get("id"))) {
                        iterator.remove();
                        break;
                    }
                }

                userRef.update("saved_sets", savedSets)
                        .addOnSuccessListener(unused -> {
                            isSaved = false;
                            updateSaveIcon();
                            Toast.makeText(this, "Set unsaved.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to unsave.", Toast.LENGTH_SHORT).show());

            } else {
                boolean alreadySaved = false;
                for (Map<String, Object> item : savedSets) {
                    if (setId.equals(item.get("id"))) {
                        alreadySaved = true;
                        break;
                    }
                }

                if (!alreadySaved) {
                    savedSets.add(setData);
                }

                userRef.update("saved_sets", savedSets)
                        .addOnSuccessListener(unused -> {
                            isSaved = true;
                            updateSaveIcon();
                            Toast.makeText(this, "Set saved!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkIfSaved() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
                    isSaved = false;
                    if (savedSets != null) {
                        for (Map<String, Object> item : savedSets) {
                            if (setId.equals(item.get("id"))) {
                                isSaved = true;
                                break;
                            }
                        }
                    }
                    updateSaveIcon();
                });
    }
}