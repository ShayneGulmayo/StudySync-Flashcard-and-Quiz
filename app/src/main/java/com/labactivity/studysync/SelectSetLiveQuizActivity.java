package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.labactivity.studysync.adapters.SharedSetAdapter;
import com.labactivity.studysync.models.SharedSet;

import java.util.ArrayList;
import java.util.List;

public class SelectSetLiveQuizActivity extends AppCompatActivity {

    private Spinner spinner;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private ImageView backButton;

    private FirebaseFirestore db;
    private SharedSetAdapter adapter;
    private final List<SharedSet> allSets = new ArrayList<>();
    private final List<SharedSet> filteredSets = new ArrayList<>();

    private String chatRoomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_set_live_quiz);

        db = FirebaseFirestore.getInstance();
        chatRoomId = getIntent().getStringExtra("roomId");

        spinner = findViewById(R.id.spinnerTimePerQuestion);
        searchView = findViewById(R.id.search_set);
        recyclerView = findViewById(R.id.recyclerView);
        backButton = findViewById(R.id.back_button);

        setupSpinner();
        setupSearch();
        setupRecyclerView();
        loadSharedSets();

        backButton.setOnClickListener(v -> finish());
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this, R.array.time_per_question_options, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(spinnerAdapter.getPosition("30s"));
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                filter(query);
                return false;
            }
            @Override public boolean onQueryTextChange(String newText) {
                filter(newText);
                return false;
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SharedSetAdapter(this, filteredSets, getSelectedSeconds());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private String getSelectedSeconds() {
        String selected = spinner.getSelectedItem().toString();
        switch (selected) {
            case "10s":
                return "10";
            case "20s":
                return "20";
            case "1 minute":
                return "60";
            default:
                return "30";
        }
    }

    private void filter(String query) {
        filteredSets.clear();
        for (SharedSet set : allSets) {
            if (set.getTitle().toLowerCase().contains(query.toLowerCase())) {
                filteredSets.add(set);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadSharedSets() {
        db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages")
                .whereEqualTo("type", "set")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    allSets.clear();
                    filteredSets.clear();
                    for (QueryDocumentSnapshot doc : querySnapshots) {
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
                                                SharedSet newSet = new SharedSet(setId, title, setType, itemCount.intValue(), ownerName, senderName, chatRoomId);
                                                allSets.add(newSet);
                                                filteredSets.add(newSet);
                                                adapter.notifyItemInserted(filteredSets.size() - 1);
                                            });
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load shared sets", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }
}
