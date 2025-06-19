package com.labactivity.studysync;


import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.UserAdapter;
import com.labactivity.studysync.User;

import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.HashMap;
import java.util.Map;


import java.util.ArrayList;
import java.util.List;

public class CreateChatRoomActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private EditText chatRoomNameEdttxt;
    private SearchView searchView;

    private RecyclerView usersRecyclerView;
    private RecyclerView selectedUsersRecyclerView;

    private UserAdapter allUsersAdapter;
    private UserAdapter selectedUsersAdapter;

    private List<User> allUsers = new ArrayList<>();
    private List<User> selectedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_chat_room);

        db = FirebaseFirestore.getInstance();

        chatRoomNameEdttxt = findViewById(R.id.chatRoomNameEdttxt);
        searchView = findViewById(R.id.search_view);
        usersRecyclerView = findViewById(R.id.users_recyclerview);
        selectedUsersRecyclerView = findViewById(R.id.selected_users_recyclerview);
        TextView saveTxt = findViewById(R.id.save_txt);
        saveTxt.setOnClickListener(v -> saveChatRoom());


        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        selectedUsersRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Set up adapters
        allUsersAdapter = new UserAdapter(allUsers, selectedUsers, this::onUserSelected);
        selectedUsersAdapter = new UserAdapter(selectedUsers, selectedUsers, (user, selected) -> {}); // No interaction for selected list

        usersRecyclerView.setAdapter(allUsersAdapter);
        selectedUsersRecyclerView.setAdapter(selectedUsersAdapter);

        loadUsersFromFirestore();
        setupSearchView();
    }

    private void loadUsersFromFirestore() {
        db.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allUsers.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            allUsers.add(user);
                        }
                    }
                    allUsersAdapter.notifyDataSetChanged();
                });
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterUsers(newText);
                return true;
            }
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
        allUsersAdapter = new UserAdapter(filtered, selectedUsers, this::onUserSelected);
        usersRecyclerView.setAdapter(allUsersAdapter);
    }

    private void onUserSelected(User user, boolean isSelected) {
        if (isSelected && !selectedUsers.contains(user)) {
            selectedUsers.add(user);
        } else if (!isSelected && selectedUsers.contains(user)) {
            selectedUsers.remove(user);
        }
        selectedUsersAdapter.notifyDataSetChanged();
    }
    private void saveChatRoom() {
        String chatRoomName = chatRoomNameEdttxt.getText().toString().trim();

        if (chatRoomName.isEmpty()) {
            chatRoomNameEdttxt.setError("Chat room name is required");
            chatRoomNameEdttxt.requestFocus();
            return;
        }

        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare member UID list
        List<String> memberIds = new ArrayList<>();
        for (User user : selectedUsers) {
            memberIds.add(user.getUid());
        }
        memberIds.add(currentUser.getUid()); // Include owner as member too

        // Generate document ID first
        String chatRoomId = db.collection("chat_rooms").document().getId();

        Map<String, Object> chatRoomData = new HashMap<>();
        chatRoomData.put("chatRoomId", chatRoomId);
        chatRoomData.put("chatRoomName", chatRoomName);
        chatRoomData.put("createdAt", Timestamp.now());
        chatRoomData.put("ownerId", currentUser.getUid());
        chatRoomData.put("members", memberIds);

        db.collection("chat_rooms")
                .document(chatRoomId)
                .set(chatRoomData)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Chat room created", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create chat room: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

}
