package com.labactivity.studysync;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.adapters.SendSetAdapter;
import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SetPickerActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private RecyclerView recyclerOwnedSets, recyclerPublicSets;
    private SendSetAdapter ownedAdapter, publicAdapter;
    private final List<Object> ownedSets = new ArrayList<>();
    private final List<Object> publicSets = new ArrayList<>();
    private String chatRoomId;
    private boolean flashcardsLoaded = false;
    private boolean quizzesLoaded = false;
    private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_picker);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        chatRoomId = getIntent().getStringExtra("roomId");

        recyclerOwnedSets = findViewById(R.id.recyclerOwnedSets);
        recyclerPublicSets = findViewById(R.id.recyclerPublicSets);
        recyclerOwnedSets.setLayoutManager(new LinearLayoutManager(this));
        recyclerPublicSets.setLayoutManager(new LinearLayoutManager(this));
        searchView = findViewById(R.id.searchView);

        ImageView backBtn = findViewById(R.id.back_button);
        backBtn.setOnClickListener(v -> finish());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterSets(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSets(newText);
                return true;
            }
        });

        fetchFlashcards();
        fetchQuizzes();

    }

    private void filterSets(String query) {
        if (ownedAdapter != null) {
            ownedAdapter.filter(query);
        }
        if (publicAdapter != null) {
            publicAdapter.filter(query);
        }
    }


    private void fetchFlashcards() {
        db.collection("flashcards").get().addOnSuccessListener(query -> {
            for (var doc : query) {
                Flashcard card = doc.toObject(Flashcard.class);
                card.setId(doc.getId());
                if (currentUser.getUid().equals(card.getOwnerUid())) {
                    ownedSets.add(card);
                } else if ("public".equalsIgnoreCase(card.getPrivacy())) {
                    publicSets.add(card);
                }
            }
            flashcardsLoaded = true;
            if (quizzesLoaded) setupAdapters();
        });
    }

    private void fetchQuizzes() {
        db.collection("quiz").get().addOnSuccessListener(query -> {
            for (var doc : query) {
                Quiz quiz = doc.toObject(Quiz.class);
                quiz.setQuizId(doc.getId());
                if (currentUser.getUid().equals(quiz.getOwner_uid())) {
                    ownedSets.add(quiz);
                } else if ("public".equalsIgnoreCase(quiz.getPrivacy())) {
                    publicSets.add(quiz);
                }
            }
            quizzesLoaded = true;
            if (flashcardsLoaded) setupAdapters();
        });
    }

    private void setupAdapters() {
        if (ownedAdapter == null) {
            ownedAdapter = new SendSetAdapter(ownedSets, this::sendSet);
            recyclerOwnedSets.setAdapter(ownedAdapter);
        } else {
            ownedAdapter.notifyDataSetChanged();
        }

        if (publicAdapter == null) {
            publicAdapter = new SendSetAdapter(publicSets, this::sendSet);
            recyclerPublicSets.setAdapter(publicAdapter);
        } else {
            publicAdapter.notifyDataSetChanged();
        }
    }

    private void sendSet(Object item) {
        String setId, setType;

        if (item instanceof Flashcard) {
            setId = ((Flashcard) item).getId();
            setType = "flashcard";
        } else if (item instanceof Quiz) {
            setId = ((Quiz) item).getQuizId();
            setType = "quiz";
        } else {
            return;
        }

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {
                    String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                    String photoUrl = userDoc.getString("photoUrl");

                    ChatMessage message = new ChatMessage();
                    message.setSenderId(currentUser.getUid());
                    message.setSenderName(senderName);
                    message.setSenderPhotoUrl(photoUrl);
                    message.setTimestamp(new Date());
                    message.setType("set");
                    message.setSetId(setId);
                    message.setSetType(setType);

                    db.collection("chat_rooms")
                            .document(chatRoomId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> {
                                db.collection("chat_rooms")
                                        .document(chatRoomId)
                                        .update(
                                                "lastMessage", "Shared a set",
                                                "lastMessageSender", senderName,
                                                "type", "set"
                                        );

                                Toast.makeText(this, "Set sent!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to send set: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
    }


}
