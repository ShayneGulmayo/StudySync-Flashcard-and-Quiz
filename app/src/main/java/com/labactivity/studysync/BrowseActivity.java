package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        View layout = findViewById(R.id.browse_layout);
        if (layout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(layout, (v, insets) -> insets);
        }

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
            if ("user".equals(item.getType())) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra("uid", item.getUser().getUid());
                startActivity(intent);
            }
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

        db.collection("users").get().addOnSuccessListener(userSnap -> {
            for (DocumentSnapshot doc : userSnap) {
                User user = doc.toObject(User.class);
                if (user != null) {
                    user.setUid(doc.getId());
                    allItems.add(BrowseContent.fromUser(user));
                    userMap.put(user.getUid(), user);
                }
            }

            db.collection("flashcards").get().addOnSuccessListener(flashcardSnap -> {
                for (DocumentSnapshot doc : flashcardSnap) {
                    String ownerUid = doc.getString("owner_uid");
                    String title = doc.getString("title");
                    String privacy = doc.getString("privacy");
                    int termCount = doc.contains("terms") ? ((Map<?, ?>) doc.get("terms")).size() : 0;
                    String ownerUsername = getUsernameFromUsers(ownerUid);

                    if (!"public".equalsIgnoreCase(privacy)) continue;

                    Flashcard fc = new Flashcard();
                    fc.setTitle(title);
                    fc.setOwnerUid(ownerUid);
                    fc.setTermCount(termCount);
                    fc.setOwnerUsername(ownerUsername);
                    allItems.add(BrowseContent.fromFlashcard(fc));
                }

                db.collection("quiz").get().addOnSuccessListener(quizSnap -> {
                    for (DocumentSnapshot doc : quizSnap) {

                        String title = doc.getString("title");
                        String ownerUid = doc.getString("owner_uid");
                        String ownerUsername = doc.getString("owner_username");
                        String privacy = doc.getString("privacy");

                        if (title == null || ownerUid == null || ownerUsername == null || privacy == null) continue;
                        if (!privacy.equalsIgnoreCase("public")) continue;

                        int questionCount = doc.contains("questions") ? ((List<?>) doc.get("questions")).size() : 0;

                        Quiz quiz = new Quiz();
                        quiz.setTitle(title);
                        quiz.setOwner_uid(ownerUid);
                        quiz.setOwnerUsername(ownerUsername);
                        quiz.setNumber_of_items(questionCount);
                        quiz.setPrivacy(privacy);
                        quiz.setQuizId(doc.getId());

                        allItems.add(BrowseContent.fromQuiz(quiz));
                    }

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
                String username = item.getUser().getUsername().toLowerCase();
                String fullName = item.getUser().getFullName().toLowerCase();
                matchesQuery = username.contains(query) || fullName.contains(query);

            } else if ("flashcard".equals(item.getType())) {
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
