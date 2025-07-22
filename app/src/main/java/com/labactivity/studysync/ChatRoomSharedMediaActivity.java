package com.labactivity.studysync;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.labactivity.studysync.adapters.SharedMediaAdapter;
import com.labactivity.studysync.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomSharedMediaActivity extends AppCompatActivity {

    private ImageView backBtn;
    private RecyclerView recyclerView;
    private SharedMediaAdapter adapter;
    private List<ChatMessage> mediaMessages;
    private FirebaseFirestore db;
    private String chatRoomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_room_shared_media);
        backBtn = findViewById(R.id.backButton);
        chatRoomId = getIntent().getStringExtra("roomId");

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mediaMessages = new ArrayList<>();
        adapter = new SharedMediaAdapter(this, mediaMessages);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        loadMediaMessages();

        backBtn.setOnClickListener(view -> finish());

    }
    private void loadMediaMessages() {
        CollectionReference messagesRef = db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages");

        messagesRef.orderBy("timestamp").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    mediaMessages.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ChatMessage message = doc.toObject(ChatMessage.class);
                        if (message.getImageUrl() != null || message.getVideoUrl() != null || message.getFileUrl() != null) {
                            mediaMessages.add(message);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("SharedMediaActivity", "Error loading media messages", e));
    }
}