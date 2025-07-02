package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.labactivity.studysync.adapters.PrivacyUserAdapter;
import com.labactivity.studysync.models.User;
import com.labactivity.studysync.models.UserWithRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("MissingInflatedId")
public class PrivacyActivity extends AppCompatActivity {

    private ImageView backButton, checkButton, privacyIcon;
    private TextView privacyTxt, roleTxt, titleTxt;
    private SearchView searchView;
    private RecyclerView selectedRecyclerView, searchResultsRecycler;
    private boolean isPublic = true;
    private final List<UserWithRole> selectedUsers = new ArrayList<>();
    private final List<User> allUsers = new ArrayList<>();
    private final List<User> searchResults = new ArrayList<>();
    private final List<User> selectedUserList = new ArrayList<>();
    private PrivacyUserAdapter selectedAdapter, searchAdapter;
    private FirebaseFirestore db;
    private String setId, currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        backButton = findViewById(R.id.back_button);
        checkButton = findViewById(R.id.check_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        privacyTxt = findViewById(R.id.privacy_txt);
        roleTxt = findViewById(R.id.role_txt);
        searchView = findViewById(R.id.search_user);
        searchResultsRecycler = findViewById(R.id.search_results_recycler);
        selectedRecyclerView = findViewById(R.id.selected_recycler_view);
        titleTxt = findViewById(R.id.title_txt);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setId = getIntent().getStringExtra("setId");

        selectedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // inside onCreate()

        selectedAdapter = new PrivacyUserAdapter(selectedUserList, selectedUserList, false, isPublic, new PrivacyUserAdapter.OnUserSelectedListener() {
            @Override
            public void onUserSelected(User user, boolean selected, int position) {
                removeFromSelectedUsers(user);
            }

            @Override
            public void onAccessListEmpty() {
                if (selectedUserList.size() <= 1) {
                    Toast.makeText(PrivacyActivity.this, "No other users selected.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        selectedRecyclerView.setAdapter(selectedAdapter);

        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new PrivacyUserAdapter(searchResults, selectedUserList, true, isPublic, (user, selected, position) -> {
            int index = selectedUserList.indexOf(user);
            if (selected) {
                if (!selectedUserList.contains(user)) {
                    selectedUserList.add(user);
                    selectedUsers.add(new UserWithRole(user, "View"));
                }
            } else {
                if (index != -1) {
                    selectedUserList.remove(user);
                    for (int i = 0; i < selectedUsers.size(); i++) {
                        if (selectedUsers.get(i).getUser().getUid().equals(user.getUid())) {
                            selectedUsers.remove(i);
                            break;
                        }
                    }
                }
            }

            selectedAdapter.notifyDataSetChanged();

            if (searchResults.isEmpty()) {
                searchResultsRecycler.setVisibility(RecyclerView.GONE);
            } else {
                searchAdapter.notifyDataSetChanged();
            }
        });

        searchResultsRecycler.setAdapter(searchAdapter);


        searchView.setEnabled(false);
        loadOwnerAndUsers();
        loadSetTitle();
        loadSetPrivacy();

        backButton.setOnClickListener(v -> finish());
        checkButton.setOnClickListener(v -> savePrivacySettings());
        findViewById(R.id.constraintLayout).setOnClickListener(v -> togglePrivacyMode());

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchResultsRecycler.setVisibility(RecyclerView.GONE);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!allUsers.isEmpty()) {
                    filterUsers(newText.trim());
                }
                return true;
            }
        });

        updatePrivacyUI();
    }

    private void loadSetTitle() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        titleTxt.setText(title != null ? title : "Untitled");
                    } else {
                        titleTxt.setText("Untitled");
                    }
                })
                .addOnFailureListener(e -> {
                    titleTxt.setText("Untitled");
                    Toast.makeText(this, "Failed to load title: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadSetPrivacy() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String privacy = documentSnapshot.getString("privacy");
                        if (privacy != null) {
                            isPublic = privacy.equalsIgnoreCase("public");
                            updatePrivacyUI();
                            selectedAdapter.setIsPublic(isPublic);
                            searchAdapter.setIsPublic(isPublic);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("PrivacyActivity", "Failed to load privacy setting: " + e.getMessage());
                });
    }


    private void loadOwnerAndUsers() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User ownerUser = documentSnapshot.toObject(User.class);
                        ownerUser.setUid(currentUserId);

                        selectedUserList.add(ownerUser);
                        selectedUsers.add(new UserWithRole(ownerUser, "Owner"));

                        selectedAdapter.notifyDataSetChanged();
                    }
                    loadAllOtherUsers();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load owner: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadAllOtherUsers() {
        db.collection("users").get().addOnSuccessListener(querySnapshot -> {
            allUsers.clear();
            for (QueryDocumentSnapshot doc : querySnapshot) {
                if (doc.getId().equals(currentUserId)) {
                    continue;
                }
                User user = doc.toObject(User.class);
                user.setUid(doc.getId());
                allUsers.add(user);
            }
            Log.d("PrivacyActivity", "Loaded " + allUsers.size() + " other users.");
            searchView.setEnabled(true);
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load users: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void filterUsers(String query) {
        searchResults.clear();
        if (!TextUtils.isEmpty(query)) {
            for (User user : allUsers) {
                String fullName = user.getFullName() != null ? user.getFullName() : "";
                String username = user.getUsername() != null ? user.getUsername() : "";

                if ((fullName.toLowerCase().contains(query.toLowerCase())
                        || username.toLowerCase().contains(query.toLowerCase()))
                        && !selectedUserList.contains(user)) {
                    searchResults.add(user);
                }
            }
        }
        searchAdapter.notifyDataSetChanged();
        searchResultsRecycler.setVisibility(searchResults.isEmpty() ? RecyclerView.GONE : RecyclerView.VISIBLE);
    }

    private void removeFromSelectedUsers(User user) {
        if (user.getUid().equals(currentUserId)) {
            Toast.makeText(this, "You cannot remove yourself.", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedUserList.remove(user);
        for (int i = 0; i < selectedUsers.size(); i++) {
            if (selectedUsers.get(i).getUser().getUid().equals(user.getUid())) {
                selectedUsers.remove(i);
                break;
            }
        }

        selectedAdapter.notifyDataSetChanged();
        searchAdapter.notifyDataSetChanged(); // keep search results consistent
    }


    private void togglePrivacyMode() {
        isPublic = !isPublic;
        updatePrivacyUI();
        selectedAdapter.setIsPublic(isPublic);
        searchAdapter.setIsPublic(isPublic);
        selectedAdapter.notifyDataSetChanged();
    }


    private void updatePrivacyUI() {
        if (isPublic) {
            privacyTxt.setText("Public");
            roleTxt.setText("View");
            Glide.with(this).load(R.drawable.public_icon).into(privacyIcon);
            ((TextView) findViewById(R.id.textView4)).setText("Anyone can view");
        } else {
            privacyTxt.setText("Private");
            roleTxt.setText("View / Edit");
            Glide.with(this).load(R.drawable.lock).into(privacyIcon);
            ((TextView) findViewById(R.id.textView4)).setText("Selected people with roles");
        }
    }

    private void savePrivacySettings() {
        Map<String, Object> data = new HashMap<>();
        data.put("privacy", isPublic ? "public" : "private");

        Map<String, String> accessUsers = new HashMap<>();
        for (UserWithRole u : selectedUsers) {
            accessUsers.put(u.getUser().getUid(), u.getRole());
        }
        data.put("accessUsers", accessUsers);

        db.collection("flashcards").document(setId)
                .update(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Privacy settings saved.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
