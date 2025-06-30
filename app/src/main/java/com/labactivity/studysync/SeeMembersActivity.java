package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.MemberAdapter;
import com.labactivity.studysync.models.ChatRoom;
import com.labactivity.studysync.models.User;
import com.labactivity.studysync.models.UserWithRole;

import java.util.ArrayList;
import java.util.List;

public class SeeMembersActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private MemberAdapter adapter;
    private String chatRoomId, currentUserId;
    private FirebaseFirestore db;
    private String ownerId;
    private List<String> admins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_see_members);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatRoomId = getIntent().getStringExtra("chatRoomId");

        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.add_txt).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddMembersActivity.class);
            intent.putExtra("chatRoomId", chatRoomId);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter(this, new ArrayList<>(), currentUserId, chatRoomId, this);
        recyclerView.setAdapter(adapter);

        fetchChatRoomMetadata();
    }

    private void fetchChatRoomMetadata() {
        db.collection("chat_rooms").document(chatRoomId).get().addOnSuccessListener(doc -> {
            ChatRoom chatRoom = doc.toObject(ChatRoom.class);
            if (chatRoom == null) return;

            ownerId = chatRoom.getOwnerId();
            admins = chatRoom.getAdmins();
            fetchMembers(chatRoom.getMembers());
        });
    }

    public void refreshView() {
        fetchChatRoomMetadata();
    }

    private void fetchMembers(List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return;

        db.collection("users")
                .whereIn("uid", memberIds)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<UserWithRole> users = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user == null) continue;

                        String role = "Member";
                        if (user.getUid().equals(ownerId)) {
                            role = "Owner";
                        } else if (admins != null && admins.contains(user.getUid())) {
                            role = "Admin";
                        }

                        users.add(new UserWithRole(user, role));
                    }
                    adapter.updateData(users, ownerId, admins);
                });


    }

}

