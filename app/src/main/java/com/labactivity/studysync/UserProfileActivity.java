package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.SetCardAdapter;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserProfileActivity extends AppCompatActivity {

    private ImageView profileImage, backButton;
    private TextView userFullName, usernameTxt;
    private RecyclerView recyclerView;
    private SetCardAdapter adapter;

    private final List<Flashcard> flashcardList = new ArrayList<>();
    private final List<Quiz> quizList = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        String userId = getIntent().getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        profileImage = findViewById(R.id.profileImage);
        userFullName = findViewById(R.id.userFullName);
        usernameTxt = findViewById(R.id.usernameTxt);
        recyclerView = findViewById(R.id.recyclerView);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        fetchUserData(userId);
        loadPublicOwnedSets(userId);
    }

    private void fetchUserData(String userId) {
        db.collection("users").document(userId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                User user = snapshot.toObject(User.class);
                if (user != null) {
                    userFullName.setText(user.getFullName());
                    usernameTxt.setText("@" + user.getUsername());

                    Glide.with(this)
                            .load(user.getPhotoUrl())
                            .placeholder(R.drawable.user_profile)
                            .circleCrop()
                            .into(profileImage);
                }
            } else {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadPublicOwnedSets(String userId) {
        db.collection("users").document(userId).get().addOnSuccessListener(snapshot -> {
            List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) snapshot.get("owned_sets");
            if (ownedSets == null || ownedSets.isEmpty()) return;

            List<Object> combinedSets = new ArrayList<>();
            int totalSets = ownedSets.size();
            final int[] loadedCount = {0}; // counter

            for (Map<String, Object> map : ownedSets) {
                String id = (String) map.get("id");
                String type = (String) map.get("type");

                if ("flashcard".equals(type)) {
                    db.collection("flashcards").document(id).get().addOnSuccessListener(doc -> {
                        Flashcard flashcard = doc.toObject(Flashcard.class);
                        if (flashcard != null && "public".equals(flashcard.getPrivacy())) {
                            flashcard.setId(doc.getId());
                            combinedSets.add(flashcard);
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == totalSets) {
                            setupRecyclerView(combinedSets);
                        }
                    });
                } else if ("quiz".equals(type)) {
                    db.collection("quiz").document(id).get().addOnSuccessListener(doc -> {
                        Quiz quiz = doc.toObject(Quiz.class);
                        if (quiz != null && "public".equals(quiz.getPrivacy())) {
                            quiz.setQuizId(doc.getId());
                            combinedSets.add(quiz);
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == totalSets) {
                            setupRecyclerView(combinedSets);
                        }
                    });
                } else {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalSets) {
                        setupRecyclerView(combinedSets);
                    }
                }
            }
        });
    }

    private void setupRecyclerView(List<Object> combinedSets) {
        if (combinedSets.isEmpty()) {
            findViewById(R.id.recyclerView).setVisibility(View.GONE);
            findViewById(R.id.noSetsText).setVisibility(View.VISIBLE);
            return;
        }

        adapter = new SetCardAdapter(this, combinedSets);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

}
