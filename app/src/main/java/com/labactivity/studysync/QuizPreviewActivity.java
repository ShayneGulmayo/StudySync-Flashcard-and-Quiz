package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.QuizCarouselAdapter;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.receivers.ReminderReceiver;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;

import androidx.viewpager2.widget.ViewPager2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizPreviewActivity extends AppCompatActivity {

    private TextView quizTitleTxt, ownerUsernameTxt, itemTxt, createdAtTxt, privacyTxt, reminderTxt;
    private ImageView privacyIcon, ownerProfileImage, backButton, moreButton, reminderIcon;
    private FirebaseFirestore db;
    private String quizId, photoUrl, currentReminder;
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private List<Quiz.Question> quizQuestions = new ArrayList<>();
    private Switch shuffleSwitch, shuffleOptionsSwitch;

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
        createdAtTxt = findViewById(R.id.createdAt_txt);
        privacyTxt = findViewById(R.id.privacy_txt);
        privacyIcon = findViewById(R.id.privacy_icon);
        ownerProfileImage = findViewById(R.id.owner_profile);
        backButton = findViewById(R.id.back_button);
        moreButton = findViewById(R.id.more_button);
        reminderTxt = findViewById(R.id.reminder_txt);
        reminderIcon = findViewById(R.id.reminder_icon);
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);
        shuffleSwitch = findViewById(R.id.shuffle_switch);
        shuffleOptionsSwitch = findViewById(R.id.shuffle_options_switch);
        TextView startQuizBtn = findViewById(R.id.start_quiz_btn);

        db = FirebaseFirestore.getInstance();
        quizId = getIntent().getStringExtra("quizId");
        photoUrl = getIntent().getStringExtra("photoUrl");

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        if (quizId != null) {
            loadQuizData(quizId);
        } else {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

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
                    itemTxt.setText((items != null ? items : 0) + ((items != null && items == 1) ? " item" : " items"));


                    Timestamp createdAt = doc.getTimestamp("created_at");
                    if (createdAt != null) {
                        createdAtTxt.setText(new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault())
                                .format(createdAt.toDate()));
                    }

                    updatePrivacyUI(doc.getString("privacy"));

                    String reminder = doc.getString("reminder");
                    if (reminder != null && !reminder.isEmpty()) {
                        currentReminder = reminder;
                        reminderTxt.setText("Reminder: " + reminder);
                        reminderIcon.setImageResource(R.drawable.notifications);
                    } else {
                        reminderTxt.setText("Reminder: None");
                        reminderIcon.setImageResource(R.drawable.off_notifications);
                    }

                    loadOwnerProfile(doc.getString("owner_uid"));

                    List<Map<String, Object>> questionList = (List<Map<String, Object>>) doc.get("questions");
                    if (questionList != null) {
                        quizQuestions.clear();
                        for (Map<String, Object> q : questionList) {
                            Quiz.Question question = new Quiz.Question();
                            question.setQuestion((String) q.get("question"));
                            question.setType((String) q.get("type"));
                            question.setChoices((List<String>) q.get("choices"));

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

    private void updatePrivacyUI(String privacy) {
        if ("private".equalsIgnoreCase(privacy)) {
            privacyTxt.setText("Private");
            privacyIcon.setImageResource(R.drawable.lock);
        } else {
            privacyTxt.setText("Public");
            privacyIcon.setImageResource(R.drawable.public_icon);
        }
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
                })
                .addOnFailureListener(e -> {
                    ownerProfileImage.setImageResource(R.drawable.user_profile);
                    ownerUsernameTxt.setText("Unknown user");
                });
    }


    private void showMoreBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        dialog.setContentView(view);

        //TextView privacyOption = view.findViewById(R.id.privacy);
        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        view.findViewById(R.id.sendToChat).setOnClickListener(v -> {
            String setType = "quiz";
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", quizId);
            intent.putExtra("setType", setType);
            startActivity(intent);
        });

        view.findViewById(R.id.edit).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, CreateQuizActivity.class);
            intent.putExtra("quizId", quizId);
            startActivity(intent);
        });

        view.findViewById(R.id.delete).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmationDialog();
        });
        view.findViewById(R.id.reminder).setOnClickListener(v -> {
            dialog.dismiss();
            showReminderDialog();
        });
        view.findViewById(R.id.privacy).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, PrivacyActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });


        dialog.show();
    }


    private void showReminderDialog() {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, R.style.DialogTheme, (view, year, month, day) -> {
            calendar.set(year, month, day);
            TimePickerDialog timePickerDialog = new TimePickerDialog(this, R.style.DialogTheme, (timeView, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                setReminder(calendar);
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
            timePickerDialog.show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setReminder(Calendar calendar) {
        String formattedDateTime = new SimpleDateFormat("MMMM dd, yyyy | hh:mm a", Locale.getDefault())
                .format(calendar.getTime());

        db.collection("quiz").document(quizId)
                .update("reminder", formattedDateTime)
                .addOnSuccessListener(aVoid -> {
                    reminderTxt.setText("Reminder: " + formattedDateTime);
                    reminderIcon.setImageResource(R.drawable.notifications);
                    Toast.makeText(this, "Reminder set for " + formattedDateTime, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to set reminder.", Toast.LENGTH_SHORT).show());

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("quizId", quizId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7, pendingIntent);
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
                                Toast.makeText(this, "Quiz deleted.", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete quiz.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch quiz attempts.", Toast.LENGTH_SHORT).show());
    }

}
