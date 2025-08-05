package com.labactivity.studysync;

import static android.content.ContentValues.TAG;

import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import com.google.common.reflect.TypeToken;

import com.google.gson.Gson;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;

import com.google.gson.GsonBuilder;
import com.labactivity.studysync.adapters.QuizCarouselAdapter;
import com.labactivity.studysync.helpers.AlarmHelper;
import com.labactivity.studysync.models.Quiz;

import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;

import java.io.FileWriter;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.URL;

import java.lang.reflect.Type;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuizPreviewActivity extends AppCompatActivity {

    private TextView quizTitleTxt, ownerUsernameTxt, itemTxt, setReminderTxt;
    private ImageView ownerProfileImage, backButton, moreButton, saveQuizBtn;
    private Button startQuizBtn, cancelReminderBtn;
    private MaterialButton downloadBtn, setReminderBtn, convertBtn, shareToChatBtn;
    private Switch shuffleSwitch, shuffleOptionsSwitch;
    private SpringDotsIndicator dotsIndicator;
    private TextView reminderTimeFormatted, reminderCountdownTxt;


    private ViewPager2 carouselViewPager;
    private BottomSheetDialog bottomSheetDialog;

    private String currentUserId, quizId, photoUrl, title, ownerUid;
    private String accessLevel = "owner";

    private boolean isRedirecting = false;
    private boolean isSaved = false;
    private boolean isPublic = false;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private List<Quiz.Question> quizQuestions = new ArrayList<>();
    private Map<String, String> accessUsers = new HashMap<>();
    private List<Map<String, Object>> userAnswers;
    private Map<String, Object> offlineQuizMap;
    private LinearLayout reminderLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quiz_preview);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        quizTitleTxt = findViewById(R.id.quiz_title);
        ownerUsernameTxt = findViewById(R.id.owner_username);
        itemTxt = findViewById(R.id.item_txt);
        ownerProfileImage = findViewById(R.id.owner_profile);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);
        shuffleSwitch = findViewById(R.id.shuffle_switch);
        shuffleOptionsSwitch = findViewById(R.id.shuffle_options_switch);
        saveQuizBtn = findViewById(R.id.saveQuizBtn);
        startQuizBtn = findViewById(R.id.start_quiz_btn);
        convertBtn = findViewById(R.id.convertToQuizBtn);
        downloadBtn = findViewById(R.id.downloadBtn);
        setReminderBtn = findViewById(R.id.setReminderBtn);
        shareToChatBtn = findViewById(R.id.shareToChat);
        reminderTimeFormatted = findViewById(R.id.reminderTimeFormatted);
        reminderCountdownTxt = findViewById(R.id.reminderCountdownTxt);
        cancelReminderBtn = findViewById(R.id.cancelReminderBtn);
        reminderLayout = findViewById(R.id.reminder_layout);
        auth = FirebaseAuth.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db = FirebaseFirestore.getInstance();
        photoUrl = getIntent().getStringExtra("photoUrl");
        title = "Review set";
        quizId = getIntent().getStringExtra("quizId");
        accessUsers = new HashMap<>();

        boolean isOffline = getIntent().getBooleanExtra("isOffline", false);
        String fileName = getIntent().getStringExtra("offlineFileName");

        if (isOffline) {
            saveQuizBtn.setVisibility(View.GONE);
            moreButton.setVisibility(View.GONE);
            convertBtn.setVisibility(View.GONE);
            shareToChatBtn.setVisibility(View.GONE);
            downloadBtn.setVisibility(View.GONE);
            setReminderBtn.setVisibility(View.GONE);
            //findViewById(R.id.quizReminderSetFor).setVisibility(View.GONE);
        }

        if (isOffline && fileName != null) {
            loadOfflineQuiz(fileName);
        } else {
            checkIfSaved();
            loadQuizData(quizId);
        }

        if (quizId == null) {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadReminderText();

        FirebaseFirestore.getInstance()
                .collection("quiz")
                .document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        title = documentSnapshot.getString("title");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch set title.", Toast.LENGTH_SHORT).show();
                });

        saveQuizBtn.setOnClickListener(v -> toggleSaveState());

        shareToChatBtn.setOnClickListener(view -> {
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });

        convertBtn.setOnClickListener(view -> {
            Intent intent = new Intent(QuizPreviewActivity.this, LoadingSetActivity.class);
            intent.putExtra("convertFromId", quizId);
            intent.putExtra("originalType", "quiz");
            startActivity(intent);
        });

        downloadBtn.setOnClickListener(v -> showDownloadOptionsDialog());

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

        if (quizId != null && !isOffline) {
            if (!AlarmHelper.isReminderSet(this, currentUserId, quizId)) {
                cancelReminderBtn.setVisibility(View.GONE);
            } else {
                cancelReminderBtn.setVisibility(View.VISIBLE);
            }
        }

        cancelReminderBtn.setOnClickListener(v -> {
            AlarmHelper.cancelAlarm(this, currentUserId, quizId);

            SharedPreferences prefs = this.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .remove(currentUserId + "_" + quizId + "_reminderTime")
                    .remove(currentUserId + "_" + quizId + "_isRepeating")
                    .apply();

            reminderTimeFormatted.setText("No reminder set");
            reminderCountdownTxt.setVisibility(View.GONE);
            cancelReminderBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Reminder canceled.", Toast.LENGTH_SHORT).show();
        });

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        startQuizBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizViewActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("quizTitle", title);
            intent.putExtra("photoUrl", photoUrl);
            intent.putExtra("shuffle", shuffleSwitch.isChecked());
            intent.putExtra("shuffleOptions", shuffleOptionsSwitch.isChecked());
            intent.putExtra("isOffline", isOffline);
            startActivity(intent);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (quizId != null) {
            loadQuizData(quizId);
            loadReminderText();
        }
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

                                AlarmHelper.setAlarm(this, calendar, currentUserId, quizId, title, isRepeating);

                                SharedPreferences prefs = getSharedPreferences("ReminderPrefs", MODE_PRIVATE);
                                prefs.edit()
                                        .putLong(currentUserId + "_" + quizId + "_reminderTime", calendar.getTimeInMillis())
                                        .putBoolean(currentUserId + "_" + quizId + "_isRepeating", isRepeating)
                                        .apply();

                                String ampm = (hour >= 12) ? "PM" : "AM";
                                int displayHour = (hour % 12 == 0) ? 12 : hour % 12;
                                String displayTime = String.format("%02d:%02d %s on %d/%d/%d",
                                        displayHour, minute, ampm,
                                        calendar.get(Calendar.MONTH) + 1,
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                        calendar.get(Calendar.YEAR));

                                reminderTimeFormatted.setText(displayTime);
                                reminderCountdownTxt.setText(getCountdownText(calendar.getTimeInMillis()));
                                reminderLayout.setVisibility(View.VISIBLE);
                                loadReminderText();



                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

                    timePickerDialog.show();

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadReminderText() {
        SharedPreferences prefs = getSharedPreferences("ReminderPrefs", MODE_PRIVATE);
        long reminderTime = prefs.getLong(currentUserId + "_" + quizId + "_reminderTime", -1);
        boolean isRepeating = prefs.getBoolean(currentUserId + "_" + quizId + "_isRepeating", false);

        if (reminderTime != -1) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(reminderTime);

            long currentTimeMillis = System.currentTimeMillis();

            if (isRepeating && reminderTime <= currentTimeMillis) {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                } while (calendar.getTimeInMillis() <= currentTimeMillis);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(currentUserId + "_" + quizId + "_reminderTime", calendar.getTimeInMillis());
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

            reminderTimeFormatted.setText(display);
            reminderCountdownTxt.setText(getCountdownText(calendar.getTimeInMillis()));

            reminderTimeFormatted.setVisibility(View.VISIBLE);
            reminderCountdownTxt.setVisibility(View.VISIBLE);
            cancelReminderBtn.setVisibility(View.VISIBLE);
            reminderLayout.setVisibility(View.VISIBLE);
            setReminderBtn.setVisibility(View.VISIBLE);
        } else {
            reminderTimeFormatted.setText("No reminder set");
            reminderCountdownTxt.setText("");
            cancelReminderBtn.setVisibility(View.GONE);

            reminderLayout.setVisibility(View.VISIBLE);
            setReminderBtn.setVisibility(View.VISIBLE);
        }
    }

    private String getCountdownText(long futureTimeMillis) {
        long now = System.currentTimeMillis();
        long diff = futureTimeMillis - now;

        if (diff <= 0) return "Reminder time passed";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        hours %= 24;
        minutes %= 60;

        StringBuilder builder = new StringBuilder("In ");
        if (days > 0) builder.append(days).append(" day").append(days > 1 ? "s, " : ", ");
        if (hours > 0) builder.append(hours).append(" hour").append(hours > 1 ? "s, " : ", ");
        builder.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");

        return builder.toString();
    }

    private void loadQuizData(String quizId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = currentUser != null ? currentUser.getUid() : null;

        db.collection("quiz").document(quizId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    quizTitleTxt.setText(doc.getString("title"));
                    ownerUsernameTxt.setText(doc.getString("owner_username"));

                    Long items = doc.getLong("number_of_items");
                    itemTxt.setText("|  " + (items != null ? items : 0) + ((items != null && items == 1) ? " item" : " items"));

                    ownerUid = doc.getString("owner_uid");
                    loadOwnerProfile(ownerUid);

                    String currentPrivacy = doc.getString("privacy") != null ? doc.getString("privacy") : "Public";
                    String privacyRole = doc.getString("privacyRole");

                    Map<String, String> accessUsers = (Map<String, String>) doc.get("accessUsers");
                    String userRole = (accessUsers != null && currentUserId != null) ? accessUsers.get(currentUserId) : null;

                    if (ownerUid != null && currentUserId != null && ownerUid.equals(currentUserId)) {
                        accessLevel = "Owner";
                        saveQuizBtn.setVisibility(View.GONE);
                    } else {

                        if ("Public".equalsIgnoreCase(currentPrivacy)) {
                            if ("Editor".equalsIgnoreCase(userRole)) {
                                accessLevel = "Editor";
                            } else if ("Viewer".equalsIgnoreCase(userRole)) {
                                accessLevel = "Viewer";
                            } else {
                                accessLevel = "Editor".equalsIgnoreCase(privacyRole) ? "Editor" : "Viewer";
                            }
                            saveQuizBtn.setVisibility(View.VISIBLE);
                        } else {
                            if (userRole != null) {
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
                            saveQuizBtn.setVisibility(View.VISIBLE);
                        }
                    }

                    List<Map<String, Object>> questionList = (List<Map<String, Object>>) doc.get("questions");
                    if (questionList != null) {
                        quizQuestions.clear();
                        for (Map<String, Object> q : questionList) {
                            Quiz.Question question = new Quiz.Question();
                            question.setQuestion((String) q.get("question"));
                            question.setType((String) q.get("type"));
                            question.setChoices((List<String>) q.get("choices"));
                            question.setPhotoUrl((String) q.get("photoUrl"));

                            Object correctAnsRaw = q.get("correctAnswer");
                            if (correctAnsRaw instanceof String) {
                                question.setCorrectAnswer((String) correctAnsRaw);
                            } else if (correctAnsRaw instanceof List) {
                                List<String> answerList = new ArrayList<>();
                                for (Object a : (List<?>) correctAnsRaw) {
                                    answerList.add(String.valueOf(a));
                                }
                                question.setCorrectAnswer(String.join(", ", answerList));
                            }

                            quizQuestions.add(question);
                        }

                        QuizCarouselAdapter adapter = new QuizCarouselAdapter(quizQuestions);
                        carouselViewPager.setAdapter(adapter);
                        dotsIndicator.setViewPager2(carouselViewPager);
                    }

                    if ("none".equalsIgnoreCase(accessLevel)) {
                        if (isRedirecting) return;
                        isRedirecting = true;
                        Intent intent = new Intent(this, NoAccessActivity.class);
                        intent.putExtra("setId", quizId);
                        intent.putExtra("setType", "quiz");
                        startActivity(intent);
                        finish();
                    }

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load quiz data", Toast.LENGTH_SHORT).show());
    }

    private void loadOwnerProfile(String ownerUid) {
        if (ownerUid == null || ownerUid.isEmpty()) {
            ownerProfileImage.setImageResource(R.drawable.user_profile);
            ownerUsernameTxt.setText("Unknown user");
            return;
        }

        db.collection("users").document(ownerUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String username = userDoc.getString("username");
                    if (username != null && !username.isEmpty()) {
                        ownerUsernameTxt.setText(username);
                    } else {
                        ownerUsernameTxt.setText("Unknown user");
                    }

                    photoUrl = userDoc.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.user_profile)
                                .circleCrop()
                                .into(ownerProfileImage);
                    } else {
                        ownerProfileImage.setImageResource(R.drawable.user_profile);
                    }

                    View.OnClickListener profileClickListener = v -> {
                        Intent intent = new Intent(this, UserProfileActivity.class);
                        intent.putExtra("userId", ownerUid);
                        startActivity(intent);
                    };

                    ownerProfileImage.setOnClickListener(profileClickListener);
                    ownerUsernameTxt.setOnClickListener(profileClickListener);

                })
                .addOnFailureListener(e -> {
                    ownerProfileImage.setImageResource(R.drawable.user_profile);
                    ownerUsernameTxt.setText("Unknown user");
                });
    }

    private void showMoreBottomSheet() {
        if (isFinishing() || isDestroyed()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.download);
        TextView copyBtn = view.findViewById(R.id.copy);
        TextView privacyBtn = view.findViewById(R.id.privacy);
        TextView reminderBtn = view.findViewById(R.id.reminder);
        TextView sendToChatBtn = view.findViewById(R.id.sendToChat);
        TextView editBtn = view.findViewById(R.id.edit);
        TextView deleteBtn = view.findViewById(R.id.delete);
        TextView reqEditBtn = view.findViewById(R.id.reqEdit);

        if ("Viewer".equals(accessLevel) && isPublic && accessUsers != null && currentUserId != null) {
            String roleInMap = accessUsers.get(currentUserId);
            if ("Editor".equals(roleInMap)) {
                accessLevel = "Editor";
            }
        }

        switch (accessLevel) {
            case "Owner":
                break;

            case "Editor":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.VISIBLE);
                editBtn.setVisibility(View.VISIBLE);
                reqEditBtn.setVisibility(View.GONE);
                break;

            case "Viewer":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                editBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
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
                reqEditBtn.setVisibility(View.VISIBLE);
                break;
        }

        downloadBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDownloadOptionsDialog();
        });

        copyBtn.setOnClickListener(v -> {
            copyQuiz(quizId);
        });

        privacyBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new Intent(this, PrivacyActivity.class)
                    .putExtra("quizId", quizId)
                    .putExtra("setType", "quiz"));
        });

        reminderBtn.setOnClickListener(v -> {
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
            bottomSheetDialog.dismiss();
        });

        sendToChatBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });

        editBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateQuizActivity.class);
            intent.putExtra("quizId", quizId);
            startActivity(intent);
        });

        deleteBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDeleteConfirmationDialog();
        });

        reqEditBtn.setOnClickListener(v -> {
            sendAccessRequest("Editor");
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }
    private boolean canNavigate() {
        return !isRedirecting && !isFinishing() && !isDestroyed();
    }

    private void showToast(String msg) {
        if (canNavigate()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAccessRequest(String requestedRole) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || quizId == null || ownerUid == null || !canNavigate()) return;

        String senderUid = currentUser.getUid();

        db.collection("users")
                .document(ownerUid)
                .collection("notifications")
                .whereEqualTo("senderId", senderUid)
                .whereEqualTo("setId", quizId)
                .whereEqualTo("setType", "quiz")
                .whereEqualTo("type", "request")
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!canNavigate()) return;

                    if (!querySnapshot.isEmpty()) {
                        showToast("You already sent a request. Please wait for a response.");
                        return;
                    }

                    db.collection("users").document(senderUid)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (!canNavigate()) return;

                                String firstName = userDoc.getString("firstName");
                                String lastName = userDoc.getString("lastName");
                                String senderPhoto = userDoc.getString("photoUrl") != null ? userDoc.getString("photoUrl") : "";

                                String senderName = "";
                                if (firstName != null) senderName += firstName;
                                if (lastName != null) senderName += (senderName.isEmpty() ? "" : " ") + lastName;
                                if (senderName.isEmpty()) senderName = "Unknown User";

                                String finalSenderName = senderName;

                                db.collection("quiz").document(quizId)
                                        .get()
                                        .addOnSuccessListener(setDoc -> {
                                            if (!canNavigate()) return;

                                            if (!setDoc.exists()) {
                                                showToast("Set not found.");
                                                return;
                                            }

                                            String setTitle = setDoc.getString("title");
                                            if (setTitle == null || setTitle.isEmpty()) setTitle = "Untitled";

                                            String messageText = finalSenderName + " has requested access to your set \"" + setTitle + "\".";

                                            Map<String, Object> requestNotification = new HashMap<>();
                                            requestNotification.put("senderId", senderUid);
                                            requestNotification.put("senderName", finalSenderName);
                                            requestNotification.put("senderPhotoUrl", senderPhoto);
                                            requestNotification.put("setId", quizId);
                                            requestNotification.put("setType", "quiz");
                                            requestNotification.put("requestedRole", requestedRole);
                                            requestNotification.put("text", messageText);
                                            requestNotification.put("type", "request");
                                            requestNotification.put("status", "pending");
                                            requestNotification.put("timestamp", FieldValue.serverTimestamp());
                                            requestNotification.put("read", false);

                                            DocumentReference notifRef = db.collection("users")
                                                    .document(ownerUid)
                                                    .collection("notifications")
                                                    .document();

                                            requestNotification.put("notificationId", notifRef.getId());

                                            notifRef.set(requestNotification)
                                                    .addOnSuccessListener(unused -> {
                                                        showToast("Access request sent!");
                                                        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
                                                            bottomSheetDialog.dismiss();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        showToast("Failed to send request.");
                                                        Log.e(TAG, "Request send failed", e);
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            showToast("Failed to fetch set.");
                                            Log.e(TAG, "Set fetch error", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to fetch user info.");
                                Log.e(TAG, "User fetch error", e);
                            });
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to check existing requests.");
                    Log.e(TAG, "Query error", e);
                });
    }

    private void copyQuiz(String originalQuizId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(userSnapshot -> {
                    if (!userSnapshot.exists()) return;
                    String username = userSnapshot.getString("username");

                    db.collection("quiz").document(originalQuizId).get()
                            .addOnSuccessListener(originalSnapshot -> {
                                if (!originalSnapshot.exists()) return;

                                Map<String, Object> originalData = originalSnapshot.getData();
                                if (originalData == null) return;

                                Map<String, Object> quizCopy = new HashMap<>();
                                quizCopy.put("title", originalData.get("title") + " (Copy)");
                                quizCopy.put("owner_uid", userId);
                                quizCopy.put("owner_username", username);
                                quizCopy.put("created_at", Timestamp.now());
                                quizCopy.put("privacy", "private");
                                quizCopy.put("privacyRole", "Viewer");
                                quizCopy.put("progress", 0);
                                quizCopy.put("number_of_items", originalData.get("number_of_items"));
                                quizCopy.put("accessUsers", Collections.singletonMap(userId, "Owner"));

                                List<Map<String, Object>> originalQuestions = (List<Map<String, Object>>) originalData.get("questions");
                                List<Map<String, Object>> copiedQuestions = new ArrayList<>();

                                db.collection("quiz").add(quizCopy)
                                        .addOnSuccessListener(newQuizRef -> {
                                            String newQuizId = newQuizRef.getId();
                                            final int[] pending = {originalQuestions.size()};

                                            for (Map<String, Object> question : originalQuestions) {
                                                Map<String, Object> copiedQuestion = new HashMap<>(question);

                                                String oldPath = (String) question.get("photoPath");

                                                if (oldPath != null && !oldPath.isEmpty()) {
                                                    String oldOwnerUid = (String) originalData.get("owner_uid");
                                                    StorageReference oldImageRef = storage.getReference("quiz_images/" + oldOwnerUid + "/" + originalQuizId + "/" + oldPath);

                                                    oldImageRef.getBytes(5 * 1024 * 1024)
                                                            .addOnSuccessListener(bytes -> {
                                                                String newFilename = "quiz_" + UUID.randomUUID() + ".jpg";
                                                                StorageReference newRef = storage.getReference("quiz_images/" + userId + "/" + newQuizId + "/" + newFilename);

                                                                newRef.putBytes(bytes)
                                                                        .addOnSuccessListener(taskSnapshot -> newRef.getDownloadUrl()
                                                                                .addOnSuccessListener(uri -> {
                                                                                    copiedQuestion.put("photoPath", newFilename);
                                                                                    copiedQuestion.put("photoUrl", uri.toString());
                                                                                    copiedQuestions.add(copiedQuestion);
                                                                                    checkSaveComplete(newQuizRef, copiedQuestions, pending);
                                                                                }))
                                                                        .addOnFailureListener(e -> {
                                                                            copiedQuestion.remove("photoPath");
                                                                            copiedQuestion.remove("photoUrl");
                                                                            copiedQuestions.add(copiedQuestion);
                                                                            checkSaveComplete(newQuizRef, copiedQuestions, pending);
                                                                        });
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                copiedQuestion.remove("photoPath");
                                                                copiedQuestion.remove("photoUrl");
                                                                copiedQuestions.add(copiedQuestion);
                                                                checkSaveComplete(newQuizRef, copiedQuestions, pending);
                                                            });
                                                } else {
                                                    copiedQuestions.add(copiedQuestion);
                                                    checkSaveComplete(newQuizRef, copiedQuestions, pending);
                                                }
                                            }

                                            Map<String, Object> ownedSetEntry = new HashMap<>();
                                            ownedSetEntry.put("id", newQuizId);
                                            ownedSetEntry.put("type", "quiz");
                                            ownedSetEntry.put("lastAccessed", System.currentTimeMillis());

                                            db.collection("users").document(userId)
                                                    .update("owned_sets", FieldValue.arrayUnion(ownedSetEntry));
                                        });
                            });
                });
    }

    private void checkSaveComplete(DocumentReference newQuizRef, List<Map<String, Object>> copiedQuestions, int[] counter) {
        counter[0]--;
        if (counter[0] == 0) {
            newQuizRef.update("questions", copiedQuestions)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Quiz copied successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, QuizPreviewActivity.class);
                        intent.putExtra("quizId", newQuizRef.getId());
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update quiz questions.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Quiz")
                .setMessage("Are you sure you want to delete this quiz? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteQuiz())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteQuiz() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in to delete the quiz.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();

        db.collection("quiz")
                .document(quizId)
                .collection("quiz_attempt")
                .get()
                .addOnSuccessListener(userAttempts -> {
                    for (DocumentSnapshot userAttempt : userAttempts.getDocuments()) {
                        userAttempt.getReference().delete();
                    }

                    db.collection("quiz").document(quizId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                DocumentReference userRef = db.collection("users").document(userId);

                                userRef.get().addOnSuccessListener(userDoc -> {
                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");
                                    if (ownedSets != null) {
                                        Iterator<Map<String, Object>> iterator = ownedSets.iterator();
                                        while (iterator.hasNext()) {
                                            Map<String, Object> item = iterator.next();
                                            if (quizId.equals(item.get("id"))) {
                                                iterator.remove();
                                                break;
                                            }
                                        }

                                        userRef.update("owned_sets", ownedSets)
                                                .addOnSuccessListener(unused -> {
                                                    Toast.makeText(this, "Quiz deleted.", Toast.LENGTH_SHORT).show();
                                                    finish();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(this, "Quiz deleted but failed to update user data.", Toast.LENGTH_SHORT).show();
                                                    finish();
                                                });
                                    } else {
                                        Toast.makeText(this, "Quiz deleted.", Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete quiz.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch quiz attempts.", Toast.LENGTH_SHORT).show());
    }

    private void checkIfSaved() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in to check saved status.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
                    isSaved = false;
                    if (savedSets != null) {
                        for (Map<String, Object> item : savedSets) {
                            if (quizId.equals(item.get("id"))) {
                                isSaved = true;
                                break;
                            }
                        }
                    }
                    updateSaveIcon();
                });
    }

    private void showDownloadOptionsDialog() {
        String[] options = {"Download as PDF", "Download for Offline Use"};
        new AlertDialog.Builder(this)
                .setTitle("Download Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        downloadOfflinePdf(quizId);
                    } else if (which == 1) {
                        downloadQuiz();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadQuiz() {
        db.collection("quiz").document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Quiz not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> setData = documentSnapshot.getData();
                    if (setData == null) return;

                    setData.put("id", quizId);

                    List<Map<String, Object>> questions = (List<Map<String, Object>>) setData.get("questions");
                    Map<String, Object> questionsMap = new LinkedHashMap<>();
                    List<Map<String, Object>> attemptList = new ArrayList<>();

                    for (int i = 0; i < questions.size(); i++) {
                        Map<String, Object> q = questions.get(i);
                        String qIndex = String.valueOf(i);

                        Map<String, Object> questionEntry = new LinkedHashMap<>();
                        questionEntry.put("quizType", q.get("quizType"));
                        questionEntry.put("question", q.get("question"));
                        questionEntry.put("choices", q.get("choices"));
                        questionEntry.put("correctAnswer", q.get("correctAnswer"));
                        questionEntry.put("selectedAnswer", ""); // will be replaced per type
                        questionEntry.put("photoUrl", q.get("photoUrl"));
                        questionEntry.put("photoPath", q.getOrDefault("localPhotoPath", "")); // temporarily

                        questionsMap.put(qIndex, questionEntry);

                        Map<String, Object> attempt = new LinkedHashMap<>(questionEntry);
                        attempt.put("selectedAnswer", q.get("correctAnswer") instanceof List ? new ArrayList<>() : "");
                        attempt.put("isCorrect", false);
                        attempt.put("order", i + 1);

                        attemptList.add(Collections.singletonMap(qIndex, attempt));
                    }

                    setData.put("questions", questionsMap);
                    setData.put("attempts", attemptList);

                    String ownerUid = documentSnapshot.getString("owner_uid");
                    if (ownerUid != null) {
                        db.collection("users").document(ownerUid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    setData.put("username", userDoc.getString("username") != null ? userDoc.getString("username") : "Unknown User");
                                    setData.put("photoUrl", userDoc.getString("photoUrl") != null ? userDoc.getString("photoUrl") : "");
                                    downloadQuizImages(questions, setData);
                                })
                                .addOnFailureListener(e -> {
                                    setData.put("username", "Unknown User");
                                    setData.put("photoUrl", "");
                                    downloadQuizImages(questions, setData);
                                });
                    } else {
                        setData.put("username", "Unknown User");
                        setData.put("photoUrl", "");
                        downloadQuizImages(questions, setData);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch quiz.", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    private void downloadQuizImages(List<Map<String, Object>> questions, Map<String, Object> setData) {
        List<Task<Void>> imageTasks = new ArrayList<>();

        // ✅ If downloading image files, append to imageTasks here (not shown in your code)

        Tasks.whenAllComplete(imageTasks)
                .addOnSuccessListener(tasks -> {
                    Map<String, Object> questionsMap = new LinkedHashMap<>();
                    List<Map<String, Object>> attemptList = new ArrayList<>();

                    for (int i = 0; i < questions.size(); i++) {
                        Map<String, Object> q = questions.get(i);
                        String qIndex = String.valueOf(i);

                        Map<String, Object> questionEntry = new LinkedHashMap<>();
                        questionEntry.put("quizType", q.get("quizType"));
                        questionEntry.put("question", q.get("question"));
                        questionEntry.put("choices", q.get("choices"));
                        questionEntry.put("correctAnswer", q.get("correctAnswer"));
                        questionEntry.put("selectedAnswer", "");

                        questionEntry.put("photoUrl", q.get("photoUrl"));

                        String fullPath = (String) q.get("photoPath");
                        if (fullPath == null || fullPath.isEmpty()) {
                            fullPath = (String) q.get("localPhotoPath");
                        }

                        if (fullPath != null && !fullPath.trim().isEmpty()) {
                            File file = new File(fullPath);
                            questionEntry.put("photoPath", file.getName()); // ✅ only file name
                        } else {
                            questionEntry.put("photoPath", "Add Image");
                        }

                        questionsMap.put(qIndex, questionEntry);

                        Map<String, Object> attempt = new LinkedHashMap<>(questionEntry);
                        attempt.put("selectedAnswer", q.get("correctAnswer") instanceof List ? new ArrayList<>() : "");
                        attempt.put("isCorrect", false);
                        attempt.put("order", i + 1);

                        attemptList.add(Collections.singletonMap(qIndex, attempt));
                    }

                    setData.put("questions", questionsMap);
                    setData.put("attempts", attemptList);

// ✅ Compute correct, incorrect, percentage
                    int correct = 0;
                    int incorrect = 0;

                    for (Map<String, Object> wrapper : attemptList) {
                        for (Map.Entry<String, Object> entry : wrapper.entrySet()) {
                            Map<String, Object> attempt = (Map<String, Object>) entry.getValue();
                            Boolean isCorrect = (Boolean) attempt.get("isCorrect");
                            if (isCorrect != null && isCorrect) {
                                correct++;
                            } else {
                                incorrect++;
                            }
                        }
                    }

                    int total = correct + incorrect;
                    int percentage = total > 0 ? (int) Math.round((correct * 100.0) / total) : 0;

                    setData.put("correctCount", correct);
                    setData.put("incorrectCount", incorrect);
                    setData.put("percentage", percentage);

// ✅ Now save
                    saveSetOffline(setData, quizId, "quiz");

                    Intent intent = new Intent(QuizPreviewActivity.this, DownloadedSetsActivity.class);
                    startActivity(intent);

                });
    }


    private void saveSetOffline(Map<String, Object> setData, String id, String type) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting() // <-- this is the key
                .create();

        String fileName = type + "_" + id + ".json";
        File file = new File(getFilesDir(), fileName);

        setData.put("fileName", fileName);

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(setData, writer);  // will now be pretty printed
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadOfflineQuiz(String fileName) {
        File file = new File(getFilesDir(), fileName);

        if (!file.exists()) {
            Toast.makeText(this, "Quiz file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        quizId = file.getName().replace("quiz_", "").replace(".json", "");

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String json = new String(data);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> quizMap = new Gson().fromJson(json, type);

            TextView titleTextView = findViewById(R.id.quiz_title);
            TextView usernameTextView = findViewById(R.id.owner_username);
            TextView itemCountTextView = findViewById(R.id.item_txt);

            if (titleTextView != null && quizMap.get("title") != null)
                titleTextView.setText(quizMap.get("title").toString());

            if (usernameTextView != null && quizMap.get("username") != null)
                usernameTextView.setText(quizMap.get("username").toString());

            Map<String, Map<String, Object>> questionsData = null;

            if (quizMap.get("questions") instanceof Map) {
                questionsData = (Map<String, Map<String, Object>>) quizMap.get("questions");

                if (itemCountTextView != null)
                    itemCountTextView.setText(questionsData.size() + " items");

                List<Quiz.Question> questionList = new ArrayList<>();

                for (String key : questionsData.keySet()) {
                    Map<String, Object> q = questionsData.get(key);
                    Quiz.Question question = new Quiz.Question();

                    question.setQuestion((String) q.get("question"));
                    question.setType((String) quizMap.get("type")); // global quiz type
                    question.setQuizType((String) q.get("quizType")); // per-question
                    question.setChoices((List<String>) q.get("choices"));
                    question.setCorrectAnswer(q.get("correctAnswer"));

                    question.setPhotoUrl((String) q.get("photoUrl"));
                    question.setLocalPhotoPath((String) q.get("photoPath"));

                    questionList.add(question);
                }

                this.quizQuestions = questionList;
            }

            // ✅ Load attempts safely
            if (quizMap.containsKey("attempts") && quizMap.get("attempts") instanceof List) {
                this.userAnswers = new ArrayList<>();

                List<?> rawAttemptsList = (List<?>) quizMap.get("attempts");

                for (Object attemptEntry : rawAttemptsList) {
                    if (attemptEntry instanceof Map) {
                        Map<?, ?> single = (Map<?, ?>) attemptEntry;

                        for (Map.Entry<?, ?> entry : single.entrySet()) {
                            String questionKey = entry.getKey().toString();
                            Object rawAnswer = entry.getValue();

                            if (rawAnswer instanceof Map) {
                                Map<String, Object> answerData = (Map<String, Object>) rawAnswer;

                                if (questionsData != null && questionsData.containsKey(questionKey)) {
                                    Map<String, Object> questionData = questionsData.get(questionKey);

                                    // Fill missing photoPath
                                    Object attemptPhotoPath = answerData.get("photoPath");
                                    if (attemptPhotoPath == null || attemptPhotoPath.toString().trim().isEmpty()) {
                                        answerData.put("photoPath", questionData.get("photoPath"));
                                    }

                                    // Fill missing photoUrl
                                    Object attemptPhotoUrl = answerData.get("photoUrl");
                                    if (attemptPhotoUrl == null || attemptPhotoUrl.toString().trim().isEmpty()) {
                                        answerData.put("photoUrl", questionData.get("photoUrl"));
                                    }
                                }

                                this.userAnswers.add(answerData);
                            } else {
                                Log.w("QuizPreview", "Attempt value is not a Map: " + rawAnswer);
                            }
                        }
                    } else {
                        Log.w("QuizPreview", "Attempt entry is not a Map: " + attemptEntry);
                    }
                }
            } else {
                this.userAnswers = new ArrayList<>();
            }

            this.offlineQuizMap = quizMap; // Keep for future updates

            // ✅ Load owner photo (if already downloaded)
            if (quizMap.get("ownerPhotoPath") != null) {
                File localFile = new File(quizMap.get("ownerPhotoPath").toString());
                if (localFile.exists()) {
                    Glide.with(this)
                            .load(Uri.fromFile(localFile))
                            .circleCrop()
                            .error(R.drawable.user_profile)
                            .into(ownerProfileImage);
                } else {
                    ownerProfileImage.setImageResource(R.drawable.user_profile);
                }
            } else {
                ownerProfileImage.setImageResource(R.drawable.user_profile);
            }

        } catch (IOException e) {
            Toast.makeText(this, "Error reading quiz file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading quiz data", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void downloadOfflinePdf(String quizId) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference quizRef = db.collection("quiz").document(quizId);

        quizRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                Toast.makeText(this, "Quiz does not exist.", Toast.LENGTH_SHORT).show();
                return;
            }

            String quizTitle = documentSnapshot.getString("title");
            List<Map<String, Object>> questionList = (List<Map<String, Object>>) documentSnapshot.get("questions");
            if (questionList == null || questionList.isEmpty()) {
                Toast.makeText(this, "Quiz has no questions.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Quiz.Question> questions = new ArrayList<>();
            for (Map<String, Object> questionMap : questionList) {
                Quiz.Question question = new Quiz.Question();
                question.setQuestion((String) questionMap.get("question"));
                question.setType((String) questionMap.get("type"));
                question.setChoices((List<String>) questionMap.get("choices"));
                question.setPhotoUrl((String) questionMap.get("photoUrl"));

                Object correctAnsRaw = questionMap.get("correctAnswer");
                if (correctAnsRaw instanceof String) {
                    question.setCorrectAnswer((String) correctAnsRaw);
                } else if (correctAnsRaw instanceof List) {
                    List<String> answerList = new ArrayList<>();
                    for (Object a : (List<?>) correctAnsRaw) {
                        answerList.add(String.valueOf(a));
                    }
                    question.setCorrectAnswer(String.join(", ", answerList));
                }
                questions.add(question);
            }

            PdfDocument document = new PdfDocument();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(14);

            int pageNumber = 1;
            int yPosition = 50;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            drawWrappedText(canvas, "Quiz Title: " + quizTitle, 40, 500, yPosition, paint);
            yPosition += 40;

            for (int i = 0; i < questions.size(); i++) {
                Quiz.Question q = questions.get(i);

                if (!TextUtils.isEmpty(q.getPhotoUrl())) {
                    try {
                        URL url = new URL(q.getPhotoUrl());
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setDoInput(true);
                        conn.connect();
                        InputStream input = conn.getInputStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        Bitmap rounded = getRoundedCornerBitmap(bitmap, 30);
                        Bitmap scaled = Bitmap.createScaledBitmap(rounded, 180, 180, false);

                        if (yPosition + scaled.getHeight() + 60 > 800) {
                            document.finishPage(page);
                            pageNumber++;
                            pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                            page = document.startPage(pageInfo);
                            canvas = page.getCanvas();
                            yPosition = 50;
                        }

                        canvas.drawBitmap(scaled, 40, yPosition, null);
                        yPosition += scaled.getHeight() + 20;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String questionTypeRaw = q.getType();
                String questionType = questionTypeRaw.substring(0, 1).toUpperCase() + questionTypeRaw.substring(1).toLowerCase();
                String questionLine = "Q" + (i + 1) + ": " + q.getQuestion();
                if ("enumeration".equalsIgnoreCase(questionType)) {
                    int expected = q.getChoices() != null ? q.getChoices().size() : 0;
                    questionLine += " (Enumeration, " + expected + " Items)";
                } else {
                    questionLine += " (" + questionType + ")";
                }

                drawWrappedText(canvas, questionLine, 40, 500, yPosition, paint);
                yPosition += 30;

                if (!"enumeration".equalsIgnoreCase(questionType) && q.getChoices() != null) {
                    drawWrappedText(canvas, "Options:", 40, 500, yPosition, paint);
                    yPosition += 25;

                    for (int j = 0; j < q.getChoices().size(); j++) {
                        char choiceLabel = (char) ('A' + j);
                        drawWrappedText(canvas, choiceLabel + ". " + q.getChoices().get(j), 60, 460, yPosition, paint);
                        yPosition += 25;
                    }
                }

                yPosition += 15;

                if ("enumeration".equalsIgnoreCase(questionType)) {
                    drawWrappedText(canvas, "✔️ Answer:", 40, 500, yPosition, paint);
                    yPosition += 25;
                    for (int j = 0; j < q.getChoices().size(); j++) {
                        drawWrappedText(canvas, (j + 1) + ". " + q.getChoices().get(j), 60, 460, yPosition, paint);
                        yPosition += 25;
                    }
                } else {
                    drawWrappedText(canvas, "✔️ Answer: " + q.getCorrectAnswerAsString(), 40, 500, yPosition, paint);
                    yPosition += 25;
                }

                yPosition += 30;

                if (yPosition > 750) {
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    yPosition = 50;
                }
            }

            document.finishPage(page);

            File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "StudySync");
            if (!downloadDir.exists()) downloadDir.mkdirs();

            String fileName = quizTitle.replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf";
            File file = new File(downloadDir, fileName);

            try {
                FileOutputStream fos = new FileOutputStream(file);
                document.writeTo(fos);
                document.close();
                fos.close();
                Toast.makeText(this, "PDF downloaded successfully!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to save PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to fetch quiz: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int cornerRadius) {
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

    private void drawWrappedText(Canvas canvas, String text, float x, int maxWidth, int startY, Paint paint) {
        TextPaint textPaint = new TextPaint(paint);
        StaticLayout staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1)
                .setIncludePad(false)
                .build();

        canvas.save();
        canvas.translate(x, startY);
        staticLayout.draw(canvas);
        canvas.restore();
    }

    private void toggleSaveState() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in to save or unsave quizzes.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
            if (savedSets == null) {
                savedSets = new ArrayList<>();
            }

            Map<String, Object> setData = new HashMap<>();
            setData.put("id", quizId);
            setData.put("type", "quiz");

            if (isSaved) {
                Iterator<Map<String, Object>> iterator = savedSets.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> item = iterator.next();
                    if (quizId.equals(item.get("id"))) {
                        iterator.remove();
                        break;
                    }
                }

                userRef.update("saved_sets", savedSets)
                        .addOnSuccessListener(unused -> {
                            isSaved = false;
                            updateSaveIcon();
                            Toast.makeText(this, "Quiz unsaved.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to unsave quiz.", Toast.LENGTH_SHORT).show());

            } else {
                boolean alreadySaved = false;
                for (Map<String, Object> item : savedSets) {
                    if (quizId.equals(item.get("id"))) {
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
                            Toast.makeText(this, "Quiz saved!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to save quiz.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateSaveIcon() {
        if (isSaved) {
            saveQuizBtn.setImageResource(R.drawable.bookmark_filled);
        } else {
            saveQuizBtn.setImageResource(R.drawable.bookmark);
        }
    }
}