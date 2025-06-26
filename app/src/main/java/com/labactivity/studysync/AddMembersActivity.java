package com.labactivity.studysync;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddMembersActivity extends AppCompatActivity {

    private ImageView backButton;
    private TextView addButton;
    private TextView noUsersMessage;
    private SearchView searchInput;
    private RecyclerView recyclerView, selectedRecyclerView;

    private UserAdapter allUsersAdapter;
    private UserAdapter selectedUsersAdapter;
    private FirebaseFirestore db;

    private List<User> allUsers = new ArrayList<>();
    private List<User> selectedUsers = new ArrayList<>();
    private Set<String> existingMemberIds = new HashSet<>();

    private String chatRoomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_members);

        db = FirebaseFirestore.getInstance();
        chatRoomId = getIntent().getStringExtra("chatRoomId");

        backButton = findViewById(R.id.back_button);
        addButton = findViewById(R.id.add_txt);
        noUsersMessage = findViewById(R.id.no_users_msg);
        searchInput = findViewById(R.id.search_view);
        recyclerView = findViewById(R.id.users_recyclerview);
        selectedRecyclerView = findViewById(R.id.selected_users_recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        selectedRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        setAddButtonState(false);
        updateSelectedUsersVisibility();

        backButton.setOnClickListener(v -> finish());

        addButton.setOnClickListener(v -> {
            for (User user : selectedUsers) {
                db.collection("chat_rooms").document(chatRoomId)
                        .update("members", FieldValue.arrayUnion(user.getUid()));
            }
            finish();
        });

        searchInput.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                filterUsers(query.trim());
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                filterUsers(newText.trim());
                return true;
            }
        });

        fetchExistingMembersAndUsers();
    }

    private void fetchExistingMembersAndUsers() {
        db.collection("chat_rooms").document(chatRoomId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> members = (List<String>) snapshot.get("members");
                    if (members != null) {
                        existingMemberIds.addAll(members);
                    }
                    fetchUsers();
                });
    }

    private void fetchUsers() {
        db.collection("users").get().addOnSuccessListener(querySnapshot -> {
            allUsers.clear();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                User user = doc.toObject(User.class);
                if (user != null && !existingMemberIds.contains(user.getUid())) {
                    user.setUid(doc.getId());
                    allUsers.add(user);
                }
            }
            filterUsers(searchInput.getQuery().toString().trim());
        });
    }

    private void filterUsers(String query) {
        List<User> filtered = new ArrayList<>();
        for (User user : allUsers) {
            if (user.getFullName().toLowerCase().contains(query.toLowerCase()) ||
                    user.getUsername().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(user);
            }
        }

        List<User> ordered = new ArrayList<>();
        for (User selected : selectedUsers) {
            if (filtered.contains(selected)) {
                ordered.add(selected);
            }
        }
        for (User user : filtered) {
            if (!selectedUsers.contains(user)) {
                ordered.add(user);
            }
        }

        noUsersMessage.setVisibility(ordered.isEmpty() ? View.VISIBLE : View.GONE);

        allUsersAdapter = new UserAdapter(ordered, selectedUsers, true, (user, selected, position) -> {
            if (selected) {
                if (!selectedUsers.contains(user)) selectedUsers.add(user);
            } else {
                selectedUsers.remove(user);
            }
            selectedUsersAdapter.notifyDataSetChanged();
            setAddButtonState(!selectedUsers.isEmpty());
            updateSelectedUsersVisibility();
            filterUsers(searchInput.getQuery().toString().trim());
        });

        selectedUsersAdapter = new UserAdapter(selectedUsers, selectedUsers, false, (user, selected, position) -> {
            if (!selected) {
                selectedUsers.remove(user);
                allUsersAdapter.notifyDataSetChanged();
                selectedUsersAdapter.notifyDataSetChanged();
                setAddButtonState(!selectedUsers.isEmpty());
                updateSelectedUsersVisibility();
            }
        });

        recyclerView.setAdapter(allUsersAdapter);
        selectedRecyclerView.setAdapter(selectedUsersAdapter);
    }

    private void setAddButtonState(boolean enabled) {
        addButton.setEnabled(enabled);
        addButton.setTextColor(enabled ? getResources().getColor(R.color.primary) : Color.GRAY);
    }

    private void updateSelectedUsersVisibility() {
        selectedRecyclerView.setVisibility(selectedUsers.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
