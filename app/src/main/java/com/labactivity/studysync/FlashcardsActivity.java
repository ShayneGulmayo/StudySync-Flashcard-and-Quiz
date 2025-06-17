package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class FlashcardsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FlashcardSetAdapter adapter;
    private ArrayList<FlashcardSet> flashcardSets;

    private ImageView backButton, addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcards);

        recyclerView = findViewById(R.id.recycler_flashcards);
        progressBar = findViewById(R.id.progress_bar);
        db = FirebaseFirestore.getInstance();
        flashcardSets = new ArrayList<>();

        adapter = new FlashcardSetAdapter(this, flashcardSets, this::onFlashcardSetClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        backButton = findViewById(R.id.back_button);
        addButton = findViewById(R.id.add_button);

        backButton.setOnClickListener(v -> onBackPressed());
        loadFlashcardSets();

        addButton.setOnClickListener(v -> {
            startActivity(new Intent(FlashcardsActivity.this, CreateFlashcardActivity.class));
        });


    }

    private void loadFlashcardSets() {
        progressBar.setVisibility(View.VISIBLE);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("flashcardSets")
                .whereEqualTo("ownerId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    flashcardSets.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        FlashcardSet set = doc.toObject(FlashcardSet.class);
                        set.setId(doc.getId());
                        flashcardSets.add(set);
                    }
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load flashcards", Toast.LENGTH_SHORT).show();
                });
    }

    private void onFlashcardSetClicked(FlashcardSet set) {
        Intent intent = new Intent(this, FlashcardViewerActivity.class);
        intent.putExtra("setId", set.getId());
        intent.putExtra("setName", set.getName());
        startActivity(intent);
    }

    public void deleteFlashcardSet(FlashcardSet set) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Flashcard Set")
                .setMessage("Are you sure you want to delete this set?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("flashcardSets").document(set.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                flashcardSets.remove(set);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, "Flashcard set deleted", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}