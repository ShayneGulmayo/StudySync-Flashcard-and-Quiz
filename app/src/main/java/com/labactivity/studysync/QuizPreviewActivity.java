package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.receivers.ReminderReceiver;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import com.google.firebase.firestore.DocumentReference;
import java.util.HashMap;
import java.util.Iterator;


import androidx.viewpager2.widget.ViewPager2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizPreviewActivity extends AppCompatActivity {

    private TextView quizTitleTxt, ownerUsernameTxt, itemTxt;
    private ImageView ownerProfileImage, backButton, moreButton, saveQuizBtn;
    private boolean isSaved = false;
    private FirebaseFirestore db;
    private String quizId, photoUrl, currentReminder, accessLevel = "owner";
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private List<Quiz.Question> quizQuestions = new ArrayList<>();
    private Switch shuffleSwitch, shuffleOptionsSwitch;
    private Button startQuizBtn;
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

        db = FirebaseFirestore.getInstance();
        quizId = getIntent().getStringExtra("quizId");
        photoUrl = getIntent().getStringExtra("photoUrl");

        saveQuizBtn.setOnClickListener(v -> toggleSaveState());


        shareToChatBtn.setOnClickListener(view -> {
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", quizId);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });
        convertBtn.setOnClickListener(view -> {
            Intent intent = new Intent(QuizPreviewActivity.this, LoadingSetActivity.class);
            intent.putExtra("convertFromId", quizId);
            intent.putExtra("originalType", "quiz");
            startActivity(intent);
        });
        downloadBtn.setOnClickListener(v -> {
            //TODO add download function
        });

        setReminderBtn.setOnClickListener(v -> {
            //TODO add reminders function
        });

        if (quizId != null) {
            checkIfSaved();
            checkIfSaved();
            loadQuizData(quizId);
        } else {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

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
                            saveQuizBtn.setVisibility(View.GONE); // hide save button for owner
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
            bottomSheetDialog.dismiss();
        });

        sendToChatBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", quizId);
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

