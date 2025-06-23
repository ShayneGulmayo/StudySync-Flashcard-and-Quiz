package com.labactivity.studysync;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.Date;
import java.util.List;

public class ChatRoomActivity extends AppCompatActivity {

    private String roomId;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private EditText messageEditText;
    private RecyclerView recyclerView;
    private ChatMessageAdapter adapter;
    private CollectionReference messagesRef;
    private List<String> memberUids;
    private TextView chatRoomNameText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        roomId = getIntent().getStringExtra("roomId");
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        messageEditText = findViewById(R.id.messageEditText);
        recyclerView = findViewById(R.id.recyclerView);
        chatRoomNameText = findViewById(R.id.txtChatRoomName);

        ImageButton sendButton = findViewById(R.id.sendButton);
        ImageView backBtn = findViewById(R.id.back_button);
        ImageView moreBtn = findViewById(R.id.chatRoomSettings);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // ✅ Messages start from bottom
        recyclerView.setLayoutManager(layoutManager);

        messagesRef = db.collection("chat_rooms").document(roomId).collection("messages");

        fetchChatRoomDetails(() -> {
            if (!memberUids.contains(currentUser.getUid())) {
                Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            setUpRecyclerView();
        });

        sendButton.setOnClickListener(v -> sendMessage());
        backBtn.setOnClickListener(v -> finish());
        moreBtn.setOnClickListener(v -> showPopupMenu(moreBtn));
    }

    private void fetchChatRoomDetails(Runnable onSuccess) {
        db.collection("chat_rooms").document(roomId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        memberUids = (List<String>) doc.get("members");

                        // ✅ Set chat room name
                        String roomName = doc.getString("chatRoomName");
                        if (roomName != null && !roomName.trim().isEmpty()) {
                            chatRoomNameText.setText(roomName);
                        }

                        onSuccess.run();
                    } else {
                        Toast.makeText(this, "Chat room not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load chat room", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setUpRecyclerView() {
        Query query = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING);
        FirestoreRecyclerOptions<ChatMessage> options = new FirestoreRecyclerOptions.Builder<ChatMessage>()
                .setQuery(query, ChatMessage.class)
                .build();

        adapter = new ChatMessageAdapter(options, currentUser.getUid());
        recyclerView.setAdapter(adapter);
        adapter.startListening();
    }

    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) return;

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {
                    String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                    String photoUrl = userDoc.getString("photoUrl");

                    ChatMessage message = new ChatMessage(currentUser.getUid(), senderName, photoUrl, text, new Date());
                    messagesRef.add(message);
                    messageEditText.setText("");

                    recyclerView.scrollToPosition(adapter.getItemCount() - 1); // ✅ Scroll to new message
                });
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.chat_room_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_live_quiz) {
                // Handle live quiz
                return true;
            } else if (id == R.id.menu_reminder) {
                // Handle reminder
                return true;
            } else if (id == R.id.menu_settings) {
                Intent intent = new Intent(ChatRoomActivity.this, EditChatRoomActivity.class);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
                return true;
            }

            return false;
        });
        popup.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) adapter.stopListening();
    }
}
