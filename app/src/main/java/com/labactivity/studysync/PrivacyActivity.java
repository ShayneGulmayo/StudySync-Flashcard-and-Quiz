package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import com.labactivity.studysync.adapters.PrivacyUserAdapter;
import com.labactivity.studysync.models.User;
import com.labactivity.studysync.models.UserWithRole;

import java.util.*;

@SuppressLint("MissingInflatedId")
public class PrivacyActivity extends AppCompatActivity {

    private ImageView backButton, checkButton, privacyIcon;
    private TextView privacyTxt, roleTxt, titleTxt;
    private SearchView searchView;

    private RecyclerView selectedRecyclerView, searchResultsRecycler;
    private PrivacyUserAdapter selectedAdapter, searchAdapter;

    private String setId, currentUserId, setType;
    private boolean isPublic = true;

    private final List<UserWithRole> selectedUserList = new ArrayList<>();
    private final List<User> allUsers = new ArrayList<>();
    private final List<User> searchResults = new ArrayList<>();

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        initViews();
        initFirebase();
        initAdapters();
        setupListeners();

        loadSetTitle();
        loadSetPrivacy();
        loadOwnerAndUsers();
        updatePrivacyUI();
    }

    private void initViews() {
        backButton = findViewById(R.id.back_button);
        checkButton = findViewById(R.id.check_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        privacyTxt = findViewById(R.id.privacy_txt);
        roleTxt = findViewById(R.id.role_txt);
        searchView = findViewById(R.id.search_user);
        searchResultsRecycler = findViewById(R.id.search_results_recycler);
        selectedRecyclerView = findViewById(R.id.selected_recycler_view);
        titleTxt = findViewById(R.id.title_txt);
    }

    private void initFirebase() {
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setId = getIntent().getStringExtra("setId");
        if (setId == null) setId = getIntent().getStringExtra("quizId");

        setType = getIntent().getStringExtra("setType");
        if (setType == null) setType = "flashcard";

        String notificationId = getIntent().getStringExtra("notificationId");
        if (notificationId != null) {
            markNotificationAsRead();
        }
    }

    private void initAdapters() {
        selectedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        selectedAdapter = new PrivacyUserAdapter(this, selectedUserList, selectedUserList, false, isPublic,
                (user, selected, position) -> {
                    removeUser(user);
                    selectedAdapter.notifyDataSetChanged();
                });
        selectedRecyclerView.setAdapter(selectedAdapter);

        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new PrivacyUserAdapter(this, searchResults, selectedUserList, true, isPublic,
                (user, selected, position) -> {
                    if (selected) {
                        if (!containsUser(user)) selectedUserList.add(new UserWithRole(user, "Viewer"));
                    } else {
                        removeUser(user);
                    }
                    selectedAdapter.notifyDataSetChanged();
                    searchAdapter.notifyDataSetChanged();
                });
        searchResultsRecycler.setAdapter(searchAdapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        checkButton.setOnClickListener(v -> savePrivacySettings());

        roleTxt.setOnClickListener(v -> {
            if (!isPublic || !isActivityAlive()) return;
            PopupMenu roleMenu = new PopupMenu(this, roleTxt);
            roleMenu.getMenu().add("Viewer");
            roleMenu.getMenu().add("Editor");
            roleMenu.setOnMenuItemClickListener(item -> {
                roleTxt.setText(item.getTitle());
                return true;
            });
            roleMenu.show();
        });

        View.OnClickListener privacyMenuClickListener = v -> {
            if (!isActivityAlive()) return;
            PopupMenu privacyMenu = new PopupMenu(this, privacyTxt);
            privacyMenu.getMenu().add(isPublic ? "Private" : "Public");
            privacyMenu.setOnMenuItemClickListener(item -> {
                String selection = item.getTitle().toString();
                if (selection.equals("Private")) {
                    isPublic = false;
                    privacyTxt.setText("Private");
                    roleTxt.setText("");
                    Glide.with(this).load(R.drawable.lock).into(privacyIcon);
                    ((TextView) findViewById(R.id.textView4)).setText("Only people with access can open the set");
                } else {
                    isPublic = true;
                    privacyTxt.setText("Public");
                    if (TextUtils.isEmpty(roleTxt.getText())) roleTxt.setText("Viewer");
                    Glide.with(this).load(R.drawable.public_icon).into(privacyIcon);
                    ((TextView) findViewById(R.id.textView4)).setText("Anyone can view");
                }
                selectedAdapter.setIsPublic(isPublic);
                searchAdapter.setIsPublic(isPublic);
                return true;
            });
            privacyMenu.show();
        };

        privacyTxt.setOnClickListener(privacyMenuClickListener);
        privacyIcon.setOnClickListener(privacyMenuClickListener);
        findViewById(R.id.textView4).setOnClickListener(privacyMenuClickListener);

        searchView.setEnabled(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                searchResultsRecycler.setVisibility(View.GONE);
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                filterUsers(newText.trim());
                return true;
            }
        });
    }

    private String getCollectionName() {
        return "quiz".equals(setType) ? "quiz" : "flashcards";
    }

    private void loadSetTitle() {
        if (setId == null || setType == null) return;

        db.collection(getCollectionName()).document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    String title = doc.getString("title");
                    titleTxt.setText(title != null ? title : "Untitled");
                })
                .addOnFailureListener(e -> titleTxt.setText("Untitled"));
    }

    private void loadSetPrivacy() {
        db.collection(getCollectionName()).document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    String privacy = doc.getString("privacy");
                    String privacyRole = doc.getString("privacyRole");

                    isPublic = "Public".equals(privacy);
                    if (isPublic) {
                        roleTxt.setText(!TextUtils.isEmpty(privacyRole) ? capitalize(privacyRole) : "Viewer");
                    } else {
                        roleTxt.setText("");
                    }

                    updatePrivacyUI();
                    selectedAdapter.setIsPublic(isPublic);
                    searchAdapter.setIsPublic(isPublic);
                });
    }

    private void loadOwnerAndUsers() {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(ownerDoc -> {
            if (ownerDoc.exists()) {
                User owner = ownerDoc.toObject(User.class);
                owner.setUid(currentUserId);
                selectedUserList.add(new UserWithRole(owner, "Owner"));
                selectedAdapter.notifyDataSetChanged();
            }
            loadAccessUsers();
        });
    }

    private void loadAccessUsers() {
        db.collection(getCollectionName()).document(setId).get().addOnSuccessListener(doc -> {
            if (doc.contains("accessUsers")) {
                Map<String, String> accessMap = (Map<String, String>) doc.get("accessUsers");
                for (String uid : accessMap.keySet()) {
                    if (uid.equals(currentUserId)) continue;
                    String role = accessMap.get(uid);
                    db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            User u = userDoc.toObject(User.class);
                            u.setUid(uid);
                            selectedUserList.add(new UserWithRole(u, role));
                            selectedAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
            loadAllOtherUsers();
        });
    }

    private void loadAllOtherUsers() {
        db.collection("users").get().addOnSuccessListener(snap -> {
            allUsers.clear();
            for (QueryDocumentSnapshot doc : snap) {
                User user = doc.toObject(User.class);
                user.setUid(doc.getId());
                if (!user.getUid().equals(currentUserId)) allUsers.add(user);
            }
            searchView.setEnabled(true);
        });
    }

    private void filterUsers(String query) {
        searchResults.clear();
        if (!TextUtils.isEmpty(query)) {
            for (User user : allUsers) {
                String name = user.getFullName() != null ? user.getFullName() : "";
                String uname = user.getUsername() != null ? user.getUsername() : "";
                if ((name.toLowerCase().contains(query.toLowerCase()) || uname.toLowerCase().contains(query.toLowerCase())) && !containsUser(user)) {
                    searchResults.add(user);
                }
            }
        }

        searchAdapter.notifyDataSetChanged();
        searchResultsRecycler.setVisibility(searchResults.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updatePrivacyUI() {
        privacyTxt.setText(isPublic ? "Public" : "Private");
        Glide.with(this).load(isPublic ? R.drawable.public_icon : R.drawable.lock).into(privacyIcon);
        ((TextView) findViewById(R.id.textView4)).setText(isPublic ? "Anyone can view" : "Only people with access can open the set");
    }

    private void savePrivacySettings() {
        db.collection(getCollectionName()).document(setId).get().addOnSuccessListener(doc -> {
            Map<String, String> oldAccessMap = new HashMap<>();
            if (doc.contains("accessUsers")) {
                oldAccessMap.putAll((Map<String, String>) doc.get("accessUsers"));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("privacy", isPublic ? "Public" : "Private");
            data.put("privacyRole", isPublic ? roleTxt.getText().toString().toLowerCase() : null);

            Map<String, String> newAccessMap = new HashMap<>();
            for (UserWithRole uwr : selectedUserList) {
                String role = uwr.getUser().getUid().equals(currentUserId) ? "Owner" : uwr.getRole();
                newAccessMap.put(uwr.getUser().getUid(), role);
            }
            data.put("accessUsers", newAccessMap);

            db.collection(getCollectionName()).document(setId).update(data)
                    .addOnSuccessListener(aVoid -> {
                        for (String uid : newAccessMap.keySet()) {
                            String newRole = newAccessMap.get(uid);
                            String oldRole = oldAccessMap.get(uid);

                            if (!oldAccessMap.containsKey(uid)) {
                                sendAccessNotification(uid, "added", newRole);
                            } else if (!newRole.equals(oldRole)) {
                                sendAccessNotification(uid, "role_changed", newRole);
                            }
                        }

                        for (String uid : oldAccessMap.keySet()) {
                            if (!newAccessMap.containsKey(uid)) {
                                sendAccessNotification(uid, "removed", oldAccessMap.get(uid));
                            }
                        }

                        Toast.makeText(this, "Privacy settings saved.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    private boolean containsUser(User user) {
        for (UserWithRole u : selectedUserList)
            if (u.getUser().getUid().equals(user.getUid())) return true;
        return false;
    }

    private void removeUser(User user) {
        for (int i = 0; i < selectedUserList.size(); i++)
            if (selectedUserList.get(i).getUser().getUid().equals(user.getUid())) {
                selectedUserList.remove(i);
                break;
            }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private boolean isActivityAlive() {
        return !(isFinishing() || isDestroyed());
    }

    private void sendAccessNotification(String toUserId, String action, String role) {
        if (toUserId.equals(currentUserId)) return;

        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String fullName = currentUserId;
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");
                        fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                        fullName = fullName.trim();
                    }

                    String setTitle = titleTxt.getText().toString();
                    String messageText = "";

                    switch (action) {
                        case "added":
                            messageText = fullName + " added you as " + role.toLowerCase() + " to the set \"" + setTitle + "\".";
                            break;
                        case "removed":
                            messageText = fullName + " removed your access to the set \"" + setTitle + "\".";
                            break;
                        case "role_changed":
                            messageText = fullName + " changed your role to " + role.toLowerCase() + " in the set \"" + setTitle + "\".";
                            break;
                    }

                    Map<String, Object> notificationData = new HashMap<>();
                    notificationData.put("senderId", currentUserId);
                    notificationData.put("receiverId", toUserId);
                    notificationData.put("text", messageText);
                    notificationData.put("timestamp", FieldValue.serverTimestamp());
                    notificationData.put("type", "access");
                    notificationData.put("setId", setId);
                    notificationData.put("setType", setType);
                    notificationData.put("action", action);
                    notificationData.put("read", false);

                    DocumentReference notifDoc = db.collection("users")
                            .document(toUserId)
                            .collection("notifications")
                            .document();

                    notificationData.put("notificationId", notifDoc.getId());

                    notifDoc.set(notificationData)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this, "Access notification sent", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to send notification: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to fetch user info: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void markNotificationAsRead() {
        String notificationId = getIntent().getStringExtra("notificationId");
        if (notificationId == null || currentUserId == null) return;

        db.collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document(notificationId)
                .update("read", true);
    }
}