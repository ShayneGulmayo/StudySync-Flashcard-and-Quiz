package com.labactivity.studysync;

import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Context;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;



import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.QuizCarouselAdapter;
import com.labactivity.studysync.helpers.AlarmHelper;
import com.labactivity.studysync.models.Quiz;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import com.google.firebase.firestore.DocumentReference;

import java.util.Calendar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;


import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuizPreviewActivity extends AppCompatActivity {

    private TextView quizTitleTxt, ownerUsernameTxt, itemTxt, setReminderTxt;
    private ImageView ownerProfileImage, backButton, moreButton, saveQuizBtn;
    private boolean isSaved = false;
    private FirebaseFirestore db;
    private String quizId, photoUrl, currentReminder, accessLevel = "owner", title;
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private List<Quiz.Question> quizQuestions = new ArrayList<>();
    private Switch shuffleSwitch, shuffleOptionsSwitch;
    private Button startQuizBtn, cancelReminderBtn;
    private MaterialButton downloadBtn, setReminderBtn, convertBtn, shareToChatBtn;


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
        setReminderTxt = findViewById(R.id.setRemindersTxt);
        cancelReminderBtn = findViewById(R.id.cancelReminderBtn);

        db = FirebaseFirestore.getInstance();
        photoUrl = getIntent().getStringExtra("photoUrl");
        title = "Review set";
        quizId = getIntent().getStringExtra("quizId");

        boolean isOffline = getIntent().getBooleanExtra("isOffline", false);
        String fileName = getIntent().getStringExtra("offlineFileName");

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

        if (!AlarmHelper.isReminderSet(this, quizId)) {
            cancelReminderBtn.setVisibility(View.GONE);
        } else {
            cancelReminderBtn.setVisibility(View.VISIBLE);
        }

        cancelReminderBtn.setOnClickListener(v -> {
            AlarmHelper.cancelAlarm(this, quizId);
            setReminderTxt.setText("No reminder set");
            cancelReminderBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Reminder canceled.", Toast.LENGTH_SHORT).show();
        });

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        startQuizBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizViewActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("photoUrl", photoUrl);
            intent.putExtra("shuffle", shuffleSwitch.isChecked());
            intent.putExtra("shuffleOptions", shuffleOptionsSwitch.isChecked());
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

                                AlarmHelper.setAlarm(this, calendar, quizId, title, isRepeating);

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



    private void loadQuizData(String quizId) {
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


                    loadOwnerProfile(doc.getString("owner_uid"));

                    String ownerUid = doc.getString("owner_uid");
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                    if (currentUser != null && ownerUid != null) {
                        if (ownerUid.equals(currentUser.getUid())) {
                            accessLevel = "owner";
                            saveQuizBtn.setVisibility(View.GONE);
                            saveQuizBtn.setVisibility(View.GONE);
                        } else {
                            accessLevel = "view";
                            saveQuizBtn.setVisibility(View.VISIBLE); // show save button for non-owner
                            saveQuizBtn.setVisibility(View.VISIBLE);
                        }
                    } else {
                        accessLevel = "view";
                        saveQuizBtn.setVisibility(View.VISIBLE);
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
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load quiz data", Toast.LENGTH_SHORT).show());
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
        TextView reqAccessBtn = view.findViewById(R.id.reqAccess);
        TextView reqEditBtn = view.findViewById(R.id.reqEdit);

        switch (accessLevel) {
            case "owner":
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
                reqAccessBtn.setVisibility(View.GONE);
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
                reqAccessBtn.setVisibility(View.VISIBLE);
                break;
        }

        downloadBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        copyBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "You must be signed in to copy this quiz.", Toast.LENGTH_SHORT).show();
                return;
            }

            String newQuizId = db.collection("quiz").document().getId(); // generate new ID
            String userId = currentUser.getUid();

            db.collection("quiz").document(quizId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> originalData = doc.getData();
                        if (originalData == null) {
                            Toast.makeText(this, "Failed to copy quiz data.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Fetch username from users collection
                        db.collection("users").document(userId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    String username = userDoc.getString("username");
                                    originalData.put("owner_uid", userId);
                                    originalData.put("owner_username", username != null ? username : "Unknown");
                                    originalData.put("created_at", Timestamp.now());
                                    originalData.put("reminder", ""); // reset reminder
                                    originalData.put("privacy", "private"); // optional: set to private
                                    originalData.put("id", newQuizId); // optional: helpful for future reference
                                    originalData.put("reminder", "");
                                    originalData.put("privacy", "private");
                                    originalData.put("id", newQuizId);
                                    originalData.put("privacyRole", "view");

                                    Map<String, Object> accessUsers = new HashMap<>();
                                    accessUsers.put(userId, "Owner");
                                    originalData.put("accessUsers", accessUsers);

                                    // Upload new quiz document
                                    db.collection("quiz").document(newQuizId)
                                            .set(originalData)
                                            .addOnSuccessListener(aVoid -> {
                                                // Add to owned_sets
                                                DocumentReference userRef = db.collection("users").document(userId);
                                                userRef.get().addOnSuccessListener(userSnapshot -> {
                                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userSnapshot.get("owned_sets");
                                                    if (ownedSets == null) ownedSets = new ArrayList<>();

                                                    Map<String, Object> newSet = new HashMap<>();
                                                    newSet.put("id", newQuizId);
                                                    newSet.put("type", "quiz");

                                                    ownedSets.add(newSet);

                                                    userRef.update("owned_sets", ownedSets)
                                                            .addOnSuccessListener(unused -> Toast.makeText(this, "Quiz copied successfully!", Toast.LENGTH_SHORT).show())
                                                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to add to owned sets.", Toast.LENGTH_SHORT).show());
                                                });
                                            })
                                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to copy quiz.", Toast.LENGTH_SHORT).show());
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch user info.", Toast.LENGTH_SHORT).show());
                    });
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
            Toast.makeText(this, "Request Edit clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        reqAccessBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Request Access clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
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
                    // Delete all user attempts under quiz_attempt
                    for (DocumentSnapshot userAttempt : userAttempts.getDocuments()) {
                        userAttempt.getReference().delete();
                    }

                    // Delete the quiz document itself
                    db.collection("quiz").document(quizId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                // 🔄 Remove from owned_sets under user document
                                DocumentReference userRef = db.collection("users").document(userId);

                                userRef.get().addOnSuccessListener(userDoc -> {
                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");
                                    if (ownedSets != null) {
                                        Iterator<Map<String, Object>> iterator = ownedSets.iterator();
                                        while (iterator.hasNext()) {
                                            Map<String, Object> item = iterator.next();
                                            if (quizId.equals(item.get("id"))) {
                                                iterator.remove(); // delete match
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
                                        // No owned_sets field found, just finish
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
                        downloadOfflinePdf(quizId);  // Implement this method
                    } else if (which == 1) {
                        downloadQuiz();              // Implement this method
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void downloadQuiz() {
        db.collection("quiz").document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> setData = documentSnapshot.getData();
                        if (setData != null) {
                            setData.put("id", quizId);

                            String ownerUid = documentSnapshot.getString("owner_uid");
                            if (ownerUid != null) {
                                db.collection("users").document(ownerUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            String username = userDoc.getString("username");
                                            String photoUrl = userDoc.getString("photoUrl");

                                            setData.put("username", username != null ? username : "Unknown User");
                                            setData.put("photoUrl", photoUrl != null ? photoUrl : "");

                                            saveSetOffline(setData, quizId);
                                            startActivity(new Intent(this, DownloadedSetsActivity.class));

                                        })
                                        .addOnFailureListener(e -> {
                                            setData.put("username", "Unknown User");
                                            setData.put("photoUrl", "");
                                            saveSetOffline(setData, quizId);
                                            startActivity(new Intent(this, DownloadedSetsActivity.class));
                                        });
                            } else {
                                setData.put("username", "Unknown User");
                                setData.put("photoUrl", "");
                                saveSetOffline(setData, quizId);
                                startActivity(new Intent(this, DownloadedSetsActivity.class));
                            }
                        } else {
                            Toast.makeText(this, "Quiz data is empty.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Quiz not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch quiz.", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }


    private void saveSetOffline(Map<String, Object> setData, String id) {
        File dir = getFilesDir();
        File file = new File(dir, "set_" + id + ".json");

        if (file.exists()) {
            Toast.makeText(this, "Set already downloaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
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

    private void loadOfflineQuiz(String fileName) {
        File file = new File(getFilesDir(), fileName); // ✅ Initialize file first

        if (!file.exists()) {
            Toast.makeText(this, "Quiz file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        quizId = file.getName().replace("set_", "").replace(".json", ""); // ✅ Now this works

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

            if (usernameTextView != null && quizMap.get("owner_username") != null)
                usernameTextView.setText(quizMap.get("owner_username").toString());

            if (itemCountTextView != null && quizMap.get("questions") != null)
                itemCountTextView.setText(((List<?>) quizMap.get("questions")).size() + " items");

            // ✅ Load owner profile image from 'owner_photo'
            if (quizMap.get("owner_photo") != null) {
                String photoUrl = quizMap.get("owner_photo").toString();
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.user_profile)
                        .error(R.drawable.user_profile)
                        .into(ownerProfileImage);
            } else {
                ownerProfileImage.setImageResource(R.drawable.user_profile);
            }

        } catch (IOException e) {
            Toast.makeText(this, "Error reading quiz file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void downloadOfflinePdf(String quizId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference quizRef = db.collection("quiz").document(quizId);
        quizRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
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

                // Now generate the PDF
                PdfDocument document = new PdfDocument();
                Paint paint = new Paint();
                paint.setColor(Color.BLACK);
                paint.setTextSize(14);

                int pageNumber = 1;
                int yPosition = 50;
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                canvas.drawText("Quiz Title: " + quizTitle, 40, yPosition, paint);
                yPosition += 40;

                for (int i = 0; i < questions.size(); i++) {
                    Quiz.Question q = questions.get(i);
                    if (yPosition > 750) {
                        document.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        yPosition = 50;
                    }

                    canvas.drawText("Q" + (i + 1) + ": " + q.getQuestion(), 40, yPosition, paint);
                    yPosition += 20;

                    if (q.getChoices() != null && !q.getChoices().isEmpty()) {
                        for (String choice : q.getChoices()) {
                            canvas.drawText(" - " + choice, 60, yPosition, paint);
                            yPosition += 20;
                        }
                    }

                    canvas.drawText("Answer: " + q.getCorrectAnswerAsString(), 60, yPosition, paint);
                    yPosition += 40;
                }

                document.finishPage(page);

                File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "StudySync");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                String fileName = quizTitle.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
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
            } else {
                Toast.makeText(this, "Quiz does not exist.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to fetch quiz: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
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

