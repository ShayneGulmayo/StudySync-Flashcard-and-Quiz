package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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

import androidx.viewpager2.widget.ViewPager2;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import com.labactivity.studysync.adapters.QuizCarouselAdapter;
import com.labactivity.studysync.models.Quiz;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizPreviewActivity extends AppCompatActivity {

    private TextView quizTitleTxt, ownerUsernameTxt, itemTxt, createdAtTxt, privacyTxt;
    private ImageView privacyIcon, ownerProfileImage, backButton, moreButton;
    private FirebaseFirestore db;
    private String quizId;
    private String photoUrl;
    private ViewPager2 carouselViewPager;
    private SpringDotsIndicator dotsIndicator;
    private List<Quiz.Question> quizQuestions = new ArrayList<>();



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
        TextView startQuizBtn = findViewById(R.id.start_quiz_btn);
        photoUrl = getIntent().getStringExtra("photoUrl");
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);

        db = FirebaseFirestore.getInstance();

        backButton.setOnClickListener(v -> finish());

        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        quizId = getIntent().getStringExtra("quizId");
        if (quizId != null && !quizId.isEmpty()) {
            loadQuizData(quizId);
        } else {
            Toast.makeText(this, "No quiz ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

        startQuizBtn.setOnClickListener(v -> {
            Intent intent = new Intent(QuizPreviewActivity.this, QuizViewActivity.class);
            intent.putExtra("quizId", quizId);
            intent.putExtra("photoUrl", photoUrl);
            startActivity(intent);
        });
    }


    private void loadQuizData(String quizId) {
        db.collection("quiz").document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String title = documentSnapshot.getString("title");
                    String ownerUsername = documentSnapshot.getString("owner_username");
                    String ownerUid = documentSnapshot.getString("owner_uid");
                    Long numberOfItems = documentSnapshot.getLong("number_of_items");
                    Timestamp createdAt = documentSnapshot.getTimestamp("created_at");
                    String privacy = documentSnapshot.getString("privacy");

                    if (title != null) quizTitleTxt.setText(title);
                    if (ownerUsername != null) ownerUsernameTxt.setText(ownerUsername);
                    if (numberOfItems != null) {
                        String label = numberOfItems == 1 ? " item" : " items";
                        itemTxt.setText(numberOfItems + label);
                    } else {
                        itemTxt.setText("0 items");
                    }

                    if (createdAt != null) {
                        String formattedDateTime = new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault())
                                .format(createdAt.toDate());
                        createdAtTxt.setText(formattedDateTime);
                    }

                    if ("private".equalsIgnoreCase(privacy)) {
                        privacyTxt.setText("Private");
                        privacyIcon.setImageResource(R.drawable.lock);
                    } else {
                        privacyTxt.setText("Public");
                        privacyIcon.setImageResource(R.drawable.public_icon);
                    }

                    loadOwnerProfile(ownerUid);

                    // ✅ Load the quiz questions from the 'questions' array field
                    List<Map<String, Object>> questionList = (List<Map<String, Object>>) documentSnapshot.get("questions");
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
                            } else {
                                question.setCorrectAnswer("N/A");
                            }

                            quizQuestions.add(question);
                        }

                        QuizCarouselAdapter adapter = new QuizCarouselAdapter(quizQuestions);
                        carouselViewPager.setAdapter(adapter);
                        dotsIndicator.setViewPager2(carouselViewPager);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load quiz data", Toast.LENGTH_SHORT).show();
                });
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

                    // Load photo
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
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView privacyOption = view.findViewById(R.id.privacy);

        // Update privacy label
        db.collection("quiz").document(quizId).get().addOnSuccessListener(doc -> {
            String currentPrivacy = doc.getString("privacy");
            if ("private".equalsIgnoreCase(currentPrivacy)) {
                privacyOption.setText("Set as Public");
            } else {
                privacyOption.setText("Set as Private");
            }
        });

        view.findViewById(R.id.download).setOnClickListener(v -> {
            Toast.makeText(this, "Download clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        privacyOption.setOnClickListener(v -> {
            db.collection("quiz").document(quizId).get().addOnSuccessListener(doc -> {
                String currentPrivacy = doc.getString("privacy");
                if (currentPrivacy == null) currentPrivacy = "private";
                String newPrivacy = currentPrivacy.equalsIgnoreCase("private") ? "public" : "private";

                db.collection("quiz").document(quizId)
                        .update("privacy", newPrivacy)
                        .addOnSuccessListener(aVoid -> {
                            // 🔁 Update UI immediately
                            if ("public".equalsIgnoreCase(newPrivacy)) {
                                privacyTxt.setText("Public");
                                privacyIcon.setImageResource(R.drawable.public_icon);
                            } else {
                                privacyTxt.setText("Private");
                                privacyIcon.setImageResource(R.drawable.lock);
                            }

                            Toast.makeText(this, "Privacy set to " + newPrivacy, Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to update privacy", Toast.LENGTH_SHORT).show();
                        });
            });
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.reminder).setOnClickListener(v -> {
            Toast.makeText(this, "Reminder clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.sendToChat).setOnClickListener(v -> {
            Toast.makeText(this, "Send to Chat clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.edit).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateQuizActivity.class);
            intent.putExtra("quizId", quizId);
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
                .setTitle("Delete Quiz")
                .setMessage("Are you sure you want to delete this quiz? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteQuiz())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteQuiz() {
        db.collection("quiz").document(quizId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Quiz deleted.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete quiz.", Toast.LENGTH_SHORT).show());
    }
}
