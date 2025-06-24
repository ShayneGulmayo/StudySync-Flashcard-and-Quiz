package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class SetFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private FlashcardSetAdapter adapter;
    private ArrayList<FlashcardSet> allSets;
    private ArrayList<FlashcardSet> displayedSets;
    private FirebaseFirestore db;
    private ImageView addButton, privacyIcon;
    private MaterialButtonToggleGroup toggleGroup;
    private TextView noSetsText;

    private int totalCollectionsToLoad = 2;
    private int collectionsLoaded = 0;
    private String currentUserPhotoUrl;

    private final ActivityResultLauncher<Intent> createFlashcardLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> loadAllSets()
    );

    public SetFragment() {}

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_set, container, false);

        recyclerView = view.findViewById(R.id.recycler_items);
        progressBar = view.findViewById(R.id.progress_bar);
        addButton = view.findViewById(R.id.add_btn);
        toggleGroup = view.findViewById(R.id.toggle_group);
        noSetsText = view.findViewById(R.id.no_sets_text);
        db = FirebaseFirestore.getInstance();
        allSets = new ArrayList<>();
        displayedSets = new ArrayList<>();
        adapter = new FlashcardSetAdapter(getContext(), displayedSets, this::onFlashcardSetClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> showAddSet());

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_flashcards) {
                    filterByCollection("flashcard");
                } else if (checkedId == R.id.btn_quizzes) {
                    filterByCollection("quiz");
                } else if (checkedId == R.id.btn_all) {
                    filterByCollection("all");
                }
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAllSets();
    }

    @SuppressLint("MissingInflatedId")
    private void showAddSet() {
        View view = getLayoutInflater().inflate(R.layout.add_set_menu, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        view.findViewById(R.id.add_flashcard).setOnClickListener(v -> {
            dialog.dismiss();
            showAddBottomSheetFlashcard();
        });

        view.findViewById(R.id.add_quiz).setOnClickListener(v -> {
            dialog.dismiss();
            showAddBottomSheetQuiz();

        });
    }

    private void loadAllSets() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);
        allSets.clear();
        displayedSets.clear();
        collectionsLoaded = 0;

        db.collection("users").document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserPhotoUrl = documentSnapshot.getString("photoUrl");
                    } else {
                        currentUserPhotoUrl = null;
                    }
                    // Load flashcards & quizzes after fetching photo
                    loadFlashcardsAndQuizzes(currentUid);
                })
                .addOnFailureListener(e -> {
                    currentUserPhotoUrl = null;
                    loadFlashcardsAndQuizzes(currentUid);
                });
    }
    private void loadFlashcardsAndQuizzes(String currentUid) {
        db.collection("flashcards")
                .whereEqualTo("owner_uid", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        FlashcardSet set = parseSet(doc);
                        set.setType("flashcard");
                        set.setPhotoUrl(currentUserPhotoUrl);

                        // Get reminder from document
                        String reminder = doc.getString("reminder");
                        if (reminder != null) {
                            set.setReminder(reminder);
                        }

                        allSets.add(set);
                    }
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                })
                .addOnFailureListener(e -> {
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                });

        db.collection("quiz")
                .whereEqualTo("owner_uid", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        FlashcardSet set = parseSet(doc);
                        set.setType("quiz");
                        set.setPhotoUrl(currentUserPhotoUrl);
                        allSets.add(set);
                    }
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                })
                .addOnFailureListener(e -> {
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                });
    }

    private FlashcardSet parseSet(QueryDocumentSnapshot doc) {
        String id = doc.getId();
        String title = doc.getString("title");
        long numberOfItems = doc.getLong("number_of_items") != null ? doc.getLong("number_of_items") : 0;
        long progress = doc.getLong("progress") != null ? doc.getLong("progress") : 0;
        String ownerUsername = doc.getString("owner_username");
        String privacy = doc.getString("privacy");

        FlashcardSet set = new FlashcardSet(id, title, (int) numberOfItems, ownerUsername, (int) progress, null);
        set.setPrivacy(privacy);
        return set;
    }


    private void checkAndApplyInitialFilter() {
        if (collectionsLoaded >= totalCollectionsToLoad) {
            progressBar.setVisibility(View.GONE);
            int checkedId = toggleGroup.getCheckedButtonId();

            if (checkedId == -1) {
                toggleGroup.check(R.id.btn_all);
            } else if (checkedId == R.id.btn_flashcards) {
                filterByCollection("flashcard");
            } else if (checkedId == R.id.btn_quizzes) {
                filterByCollection("quiz");
            } else {
                filterByCollection("all");
            }
        }
    }

    private void filterByCollection(String typeFilter) {
        displayedSets.clear();
        for (FlashcardSet set : allSets) {
            if (typeFilter.equals("all") || set.getType().equalsIgnoreCase(typeFilter)) {
                displayedSets.add(set);
            }
        }

        // 🔍 DEBUG: Log what’s being displayed
        android.util.Log.d("SetFragment", "Filtered sets count: " + displayedSets.size());
        for (FlashcardSet set : displayedSets) {
            android.util.Log.d("SetFragment", "Type: " + set.getType() + ", Title: " + set.getTitle());
        }

        adapter.notifyDataSetChanged();
        noSetsText.setVisibility(displayedSets.isEmpty() ? View.VISIBLE : View.GONE);
    }


    private void showAddBottomSheetFlashcard() {
        View view = getLayoutInflater().inflate(R.layout.add_bottom_sheet_menu, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        view.findViewById(R.id.generate_from_image).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Generate from image coming soon!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.generate_from_pdf).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Generate from PDF coming soon!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.add_new_set).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), CreateFlashcardActivity.class);
            createFlashcardLauncher.launch(intent);
        });
    }

    private void showAddBottomSheetQuiz() {
        View view = getLayoutInflater().inflate(R.layout.add_bottom_sheet_menu, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        view.findViewById(R.id.generate_from_image).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Generate from image coming soon!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.generate_from_pdf).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Generate from PDF coming soon!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.add_new_set).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), CreateQuizActivity.class);
            createFlashcardLauncher.launch(intent);
        });
    }

    private void onFlashcardSetClicked(FlashcardSet set) {
        Intent intent = new Intent(getContext(), FlashcardViewerActivity.class);
        intent.putExtra("setId", set.getId());
        intent.putExtra("setName", set.getTitle());
        startActivity(intent);
    }
}
