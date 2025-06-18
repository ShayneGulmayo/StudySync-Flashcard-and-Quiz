package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private String currentUid;

    private final ActivityResultLauncher<Intent> createFlashcardLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> loadFlashcardSets()
    );

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
        addButton.setOnClickListener(v -> showAddBottomSheet());

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        loadFlashcardSets();
    }

    private void loadFlashcardSets() {
        progressBar.setVisibility(View.VISIBLE);
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("flashcards")
                .whereEqualTo("owner_uid", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    flashcardSets.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String id = doc.getId();
                        String title = doc.getString("title");
                        long numberOfItems = doc.getLong("number_of_items") != null ? doc.getLong("number_of_items") : 0;
                        long progress = doc.getLong("progress") != null ? doc.getLong("progress") : 0;
                        String ownerUsername = doc.getString("owner_username");
                        String ownerUid = doc.getString("owner_uid");

                        // Fetch the owner's photoUrl from "users" collection
                        db.collection("users").document(ownerUid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String photoUrl = userDoc.getString("photoUrl");

                                    FlashcardSet set = new FlashcardSet(
                                            id,
                                            title,
                                            (int) numberOfItems,
                                            ownerUsername,
                                            (int) progress,
                                            photoUrl
                                    );

                                    flashcardSets.add(set);
                                    adapter.notifyDataSetChanged();
                                    progressBar.setVisibility(View.GONE);
                                })
                                .addOnFailureListener(e -> {
                                    // Fallback: create set without photo if user data fails
                                    FlashcardSet set = new FlashcardSet(
                                            id,
                                            title,
                                            (int) numberOfItems,
                                            ownerUsername,
                                            (int) progress,
                                            null
                                    );

                                    flashcardSets.add(set);
                                    adapter.notifyDataSetChanged();
                                    progressBar.setVisibility(View.GONE);
                                });
                    }

                    // If no flashcard sets found
                    if (queryDocumentSnapshots.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load flashcards", Toast.LENGTH_SHORT).show();
                });
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadFlashcardSets();
    }

    private void showAddBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.add_bottom_sheet_menu, null);

        TextView generateFromImage = view.findViewById(R.id.generate_from_image);
        TextView generateFromPdf = view.findViewById(R.id.generate_from_pdf);
        TextView addNewSet = view.findViewById(R.id.add_new_set);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        generateFromImage.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Generate from image coming soon!", Toast.LENGTH_SHORT).show();
        });

        generateFromPdf.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Generate from PDF coming soon!", Toast.LENGTH_SHORT).show();
        });

        addNewSet.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(FlashcardsActivity.this, CreateFlashcardActivity.class);
            createFlashcardLauncher.launch(intent);
        });
    }

    private void onFlashcardSetClicked(FlashcardSet set) {
        Intent intent = new Intent(this, FlashcardViewerActivity.class);
        intent.putExtra("setId", set.getId());
        intent.putExtra("setName", set.getTitle());
        startActivity(intent);
    }
}
