package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.BrowseContentAdapter;
import com.labactivity.studysync.models.BrowseContent;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowseActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BrowseContentAdapter adapter;
    private List<BrowseContent> allItems = new ArrayList<>();
    private List<BrowseContent> filteredItems = new ArrayList<>();
    private Map<String, User> userMap = new HashMap<>();
    private MaterialButton peopleBtn, flashcardsBtn, quizzesBtn;
    private boolean filterPeople = false, filterFlashcards = false, filterQuizzes = false;
    private EditText searchInput;
    private TextView noResultsText;
    private ImageView cancelBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        View layout = findViewById(R.id.browse_layout);
        if (layout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(layout, (v, insets) -> insets);
        }
        cancelBtn = findViewById(R.id.cancelBtn);

        MaterialButton toggleFiltersButton = findViewById(R.id.toggleFiltersButton);
        LinearLayout filterButtonsContainer = findViewById(R.id.filterButtonsContainer);

        toggleFiltersButton.setOnClickListener(v -> {
            boolean visible = filterButtonsContainer.getVisibility() == View.VISIBLE;
            filterButtonsContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
            toggleFiltersButton.setText(visible ? R.string.show_filter : R.string.hide_filter);
            toggleFiltersButton.setIconResource(visible ? R.drawable.drop_down_icon : R.drawable.arrow_up);
        });



        recyclerView = findViewById(R.id.browseRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        noResultsText = findViewById(R.id.noResultsText);


        adapter = new BrowseContentAdapter(filteredItems, item -> {
            Intent intent;
            switch (item.getType()) {
                case "user":
                    intent = new Intent(this, UserProfileActivity.class);
                    intent.putExtra("userId", item.getUser().getUid());
                    break;
                case "flashcard":
                    intent = new Intent(this, FlashcardPreviewActivity.class);
                    intent.putExtra("setId", item.getFlashcard().getId());
                    break;
                case "quiz":
                    intent = new Intent(this, QuizPreviewActivity.class);
                    intent.putExtra("quizId", item.getQuiz().getQuizId());
                    break;
                default:
                    return;
            }
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        peopleBtn = findViewById(R.id.btnUsers);
        flashcardsBtn = findViewById(R.id.btnFlashcards);
        quizzesBtn = findViewById(R.id.btnQuizzes);
        searchInput = findViewById(R.id.searchEditText);

        peopleBtn.setOnClickListener(v -> {
            filterPeople = !filterPeople;
            filterBy();
        });

        flashcardsBtn.setOnClickListener(v -> {
            filterFlashcards = !filterFlashcards;
            filterBy();
        });

        quizzesBtn.setOnClickListener(v -> {
            filterQuizzes = !filterQuizzes;
            filterBy();
        });

        cancelBtn.setOnClickListener(view -> finish());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBy();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        fetchEverything();
    }

    private void fetchEverything() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        allItems.clear();

        // Step 1: Load all users
        db.collection("users").get().addOnSuccessListener(userSnap -> {
            for (DocumentSnapshot doc : userSnap) {
                User user = doc.toObject(User.class);
                if (user != null) {
                    Boolean isDeleted = doc.getBoolean("isDeleted");
                    String username = doc.getString("username");

                    // ✅ Skip deleted or "User Not Found" users
                    if (Boolean.TRUE.equals(isDeleted) || "User Not Found".equalsIgnoreCase(username)) {
                        continue;
                    }

                    user.setUid(doc.getId());
                    user.setUsername(username);
                    user.setPhotoUrl(doc.getString("photoUrl"));
                    user.setFirstName(doc.getString("firstName"));
                    user.setLastName(doc.getString("lastName"));

                    allItems.add(BrowseContent.fromUser(user));
                    userMap.put(user.getUid(), user);
                }
            }


            // Step 2: Load flashcards (after users are cached)
            db.collection("flashcards").get().addOnSuccessListener(flashcardSnap -> {
                for (DocumentSnapshot doc : flashcardSnap) {
                    String privacy = doc.getString("privacy");
                    if (!"public".equalsIgnoreCase(privacy)) continue;

                    final String flashcardId = doc.getId();
                    final String title = doc.getString("title");
                    final String ownerUid = doc.getString("owner_uid");
                    final int termCount = doc.contains("terms") ? ((Map<?, ?>) doc.get("terms")).size() : 0;

                    if (flashcardId == null || flashcardId.trim().isEmpty()) continue;
                    if (ownerUid == null || ownerUid.trim().isEmpty()) continue;

                    String ownerUsername = getUsernameFromUsers(ownerUid);
                    Flashcard fc = new Flashcard();
                    fc.setId(flashcardId);
                    fc.setTitle(title);
                    fc.setOwnerUid(ownerUid);
                    fc.setTermCount(termCount);
                    fc.setPrivacy(privacy);

                    if (!"unknown".equals(ownerUsername)) {
                        fc.setOwnerUsername(ownerUsername);
                        allItems.add(BrowseContent.fromFlashcard(fc));
                    } else {
                        // Fetch user from Firestore and update map
                        db.collection("users").document(ownerUid).get().addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                User fetchedUser = userDoc.toObject(User.class);
                                if (fetchedUser != null) {
                                    fetchedUser.setUid(userDoc.getId());
                                    fetchedUser.setUsername(userDoc.getString("username"));
                                    fetchedUser.setPhotoUrl(userDoc.getString("photoUrl"));
                                    fetchedUser.setFirstName(userDoc.getString("firstName"));
                                    fetchedUser.setLastName(userDoc.getString("lastName"));

                                    userMap.put(ownerUid, fetchedUser);

                                    fc.setOwnerUsername(fetchedUser.getUsername());
                                    allItems.add(BrowseContent.fromFlashcard(fc));
                                    adapter.notifyItemInserted(allItems.size() - 1);
                                }
                            }
                        });
                    }
                }

                // Step 3: Load quizzes
                db.collection("quiz").get().addOnSuccessListener(quizSnap -> {
                    for (DocumentSnapshot doc : quizSnap) {
                        String privacy = doc.getString("privacy");
                        if (!"public".equalsIgnoreCase(privacy)) continue;

                        String title = doc.getString("title");
                        String quizId = doc.getId();
                        String ownerUid = doc.getString("owner_uid");
                        String ownerUsername = doc.getString("owner_username");
                        int questionCount = doc.contains("questions") ? ((List<?>) doc.get("questions")).size() : 0;

                        Quiz quiz = new Quiz();
                        quiz.setQuizId(quizId);
                        quiz.setTitle(title);
                        quiz.setOwner_uid(ownerUid);
                        quiz.setOwnerUsername(ownerUsername);
                        quiz.setNumber_of_items(questionCount);
                        quiz.setPrivacy(privacy);

                        allItems.add(BrowseContent.fromQuiz(quiz));
                    }

                    // Step 4: Display filtered list
                    filterBy();
                });
            });
        });
    }

    private String getUsernameFromUsers(String uid) {
        User user = userMap.get(uid);
        return (user != null) ? user.getUsername() : "unknown";
    }

    private void filterBy() {
        String query = searchInput.getText().toString().toLowerCase().trim();
        filteredItems.clear();

        if (query.isEmpty()) {
            adapter.notifyDataSetChanged();
            noResultsText.setVisibility(View.GONE); // 🚫 Hide if nothing typed
            return;
        }

        for (BrowseContent item : allItems) {
            boolean matchesType =
                    (filterPeople && "user".equals(item.getType())) ||
                            (filterFlashcards && "flashcard".equals(item.getType())) ||
                            (filterQuizzes && "quiz".equals(item.getType()));

            boolean matchesQuery = false;

            if ("user".equals(item.getType())) {
                User u = item.getUser();
                if (u == null || "User Not Found".equalsIgnoreCase(u.getUsername()) || Boolean.TRUE.equals(u.isDeleted())) {
                    continue; // ✅ Skip deleted users
                }

                String username = u.getUsername().toLowerCase();
                String fullName = u.getFullName().toLowerCase();
                matchesQuery = username.contains(query) || fullName.contains(query);
            }
            else if ("flashcard".equals(item.getType())) {
                matchesQuery = item.getFlashcard().getTitle().toLowerCase().contains(query);

            } else if ("quiz".equals(item.getType())) {
                matchesQuery = item.getQuiz().getTitle().toLowerCase().contains(query);
            }

            if ((filterPeople || filterFlashcards || filterQuizzes) && matchesType && matchesQuery) {
                filteredItems.add(item);
            } else if (!filterPeople && !filterFlashcards && !filterQuizzes && matchesQuery) {
                filteredItems.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        if (!query.isEmpty() && filteredItems.isEmpty()) {
            noResultsText.setVisibility(View.VISIBLE);
        } else {
            noResultsText.setVisibility(View.GONE);
        }
    }
}
