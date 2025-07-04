package com.labactivity.studysync;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.ChatRoomPickerAdapter;
import com.labactivity.studysync.models.ChatRoom;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomPickerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatRoomPickerAdapter adapter;
    private List<ChatRoom> chatRooms = new ArrayList<>();
    private List<ChatRoom> fullChatRooms = new ArrayList<>();
    private SearchView searchView;

    private FirebaseFirestore db;
    private String currentUserId;
    private String setId, setType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room_picker);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setId = getIntent().getStringExtra("setId");
        setType = getIntent().getStringExtra("setType");

        recyclerView = findViewById(R.id.recyclerOwnedSets);
        searchView = findViewById(R.id.searchView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        adapter = new ChatRoomPickerAdapter(this, chatRooms, currentUserId, setId, setType);
        recyclerView.setAdapter(adapter);

        loadChatRooms();
        setupSearchView();
    }

    private void loadChatRooms() {
        db.collection("chat_rooms")
                .whereArrayContains("members", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    chatRooms.clear();
                    fullChatRooms.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        ChatRoom room = doc.toObject(ChatRoom.class);
                        if (room != null) {
                            room.setId(doc.getId());
                            chatRooms.add(room);
                            fullChatRooms.add(room);
                        }
                    }
                    adapter.updateList(chatRooms);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load chat rooms", Toast.LENGTH_SHORT).show()
                );
    }

    private void setupSearchView() {
        searchView.setQueryHint("Search chat rooms...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterChatRooms(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterChatRooms(newText);
                return true;
            }
        });
    }

    private void filterChatRooms(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            adapter.updateList(fullChatRooms);
            return;
        }

        List<ChatRoom> filtered = new ArrayList<>();
        for (ChatRoom room : fullChatRooms) {
            if (room.getChatRoomName() != null &&
                    room.getChatRoomName().toLowerCase().contains(keyword.toLowerCase())) {
                filtered.add(room);
            }
        }

        adapter.updateList(filtered);
    }
}
