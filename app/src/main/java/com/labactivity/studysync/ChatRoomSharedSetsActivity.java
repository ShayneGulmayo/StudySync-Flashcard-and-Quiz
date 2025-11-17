package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomSharedSetsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SharedSetAdapter adapter;
    private List<SharedSet> sharedSetList = new ArrayList<>();
    private FirebaseFirestore db;
    private String chatRoomId;

    private ImageView backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_room_shared_sets);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SharedSetAdapter(sharedSetList);
        recyclerView.setAdapter(adapter);
        backBtn = findViewById(R.id.backButton);
        db = FirebaseFirestore.getInstance();
        chatRoomId = getIntent().getStringExtra("roomId");

        loadSharedSets();

        backBtn.setOnClickListener(v -> finish());
    }

    private void loadSharedSets() {
        db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages")
                .whereEqualTo("type", "set")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    sharedSetList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String setId = doc.getString("setId");
                        String setType = doc.getString("setType");
                        String senderId = doc.getString("senderId");
                        String senderName = doc.getString("senderName");

                        if (setId == null || setType == null || senderId == null) continue;

                        db.collection(setType.equals("quiz") ? "quiz" : "flashcards")
                                .document(setId)
                                .get()
                                .addOnSuccessListener(setDoc -> {
                                    if (!setDoc.exists()) return;

                                    String title = setDoc.getString("title");
                                    String ownerUid = setDoc.getString("owner_uid");
                                    Long itemCount = setDoc.getLong("number_of_items");

                                    if (title == null || ownerUid == null || itemCount == null) return;

                                    db.collection("users")
                                            .document(ownerUid)
                                            .get()
                                            .addOnSuccessListener(ownerDoc -> {
                                                String ownerName = ownerDoc.exists() ? ownerDoc.getString("username") : "Unknown";
                                                sharedSetList.add(new SharedSet(setId, title, setType, itemCount.intValue(), ownerName, senderName));                                                adapter.notifyItemInserted(sharedSetList.size() - 1);
                                            });
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ChatRoomSharedSetsActivity.this, "Failed to load shared sets", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                    Log.e("ChatRoomSharedSets", "Error loading sets: ", e);
                });
    }

    static class SharedSet {
        String setId, title, type, ownerName, senderName;
        int count;

        public SharedSet(String setId, String title, String type, int count, String ownerName, String senderName) {
            this.setId = setId;
            this.title = title;
            this.type = type;
            this.count = count;
            this.ownerName = ownerName;
            this.senderName = senderName;
        }
    }

    class SharedSetAdapter extends RecyclerView.Adapter<SharedSetAdapter.SetViewHolder> {

        List<SharedSet> setList;

        SharedSetAdapter(List<SharedSet> setList) {
            this.setList = setList;
        }

        @NonNull
        @Override
        public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set, parent, false);
            return new SetViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
            SharedSet set = setList.get(position);
            holder.setTitle.setText(set.title);
            String description = set.type + " · " + set.count + " items · by " + set.ownerName + " · shared by " + set.senderName;
            holder.description.setText(description);

            holder.itemView.setOnClickListener(v-> {
                Intent intent;
                if ("quiz".equals(set.type)) {
                    intent = new Intent(ChatRoomSharedSetsActivity.this, QuizPreviewActivity.class);
                } else if ("flashcard".equals(set.type)) {
                    intent = new Intent(ChatRoomSharedSetsActivity.this, FlashcardPreviewActivity.class);
                } else {
                    Toast.makeText(ChatRoomSharedSetsActivity.this, "Unknown set type: " + set.type, Toast.LENGTH_SHORT).show();
                    return;
                }

                intent.putExtra("setId", set.setId);
                startActivity(intent);
            });
        }


        @Override
        public int getItemCount() {
            return setList.size();
        }

        class SetViewHolder extends RecyclerView.ViewHolder {
            TextView setTitle, description;
            CardView cardView;

            SetViewHolder(@NonNull View itemView) {
                super(itemView);
                setTitle = itemView.findViewById(R.id.setTitle);
                description = itemView.findViewById(R.id.description);
                cardView = itemView.findViewById(R.id.card_view);
            }
        }
    }
}