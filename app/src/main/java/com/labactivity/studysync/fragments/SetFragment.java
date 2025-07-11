package com.labactivity.studysync.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
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

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.labactivity.studysync.CreateFlashcardActivity;
import com.labactivity.studysync.CreateQuizActivity;
import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.GenerateSetActivity;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.R;
import com.labactivity.studysync.adapters.SetAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private SetAdapter adapter;
    private ArrayList<Flashcard> allSets;
    private ArrayList<Flashcard> displayedSets;
    private FirebaseFirestore db;
    private ImageView addButton;
    private MaterialButtonToggleGroup toggleGroup;
    private TextView noSetsText;
    private SearchView searchView;
    private int totalCollectionsToLoad = 3;
    private int collectionsLoaded = 0;
    private String currentUserPhotoUrl;
    private String currentSearchQuery = "";

    private String defaultFilter = null;
    private boolean isReturningFromCreate = false;

    private final ActivityResultLauncher<Intent> createFlashcardLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                isReturningFromCreate = true;
                loadAllSets();
            }
    );

    public SetFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            defaultFilter = getArguments().getString("defaultFilter");
        }
        Intent intent = requireActivity().getIntent();
        if (intent != null && intent.hasExtra("defaultFilter")) {
            defaultFilter = intent.getStringExtra("defaultFilter");
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_set, container, false);

        recyclerView = view.findViewById(R.id.recycler_items);
        progressBar = view.findViewById(R.id.progress_bar);
        addButton = view.findViewById(R.id.add_btn);
        toggleGroup = view.findViewById(R.id.toggle_group);
        noSetsText = view.findViewById(R.id.no_sets_text);
        searchView = view.findViewById(R.id.search_set);

        db = FirebaseFirestore.getInstance();
        allSets = new ArrayList<>();
        displayedSets = new ArrayList<>();

        adapter = new SetAdapter(getContext(), displayedSets, this::onFlashcardSetClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> showAddSet());

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchEditText.setBackground(null);
        View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
        searchPlate.setBackground(null);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query;
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                applyFilters();
                return true;
            }
        });

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyFilters();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isReturningFromCreate) {
            loadAllSets();
        } else {
            isReturningFromCreate = false;
        }
    }

    private void checkAndApplyInitialFilter() {
        if (collectionsLoaded >= totalCollectionsToLoad) {
            progressBar.setVisibility(View.GONE);
            allSets.sort((s1, s2) -> Long.compare(s2.getLastAccessed(), s1.getLastAccessed()));

            if (defaultFilter != null) {
                if (defaultFilter.equalsIgnoreCase("flashcard")) {
                    toggleGroup.check(R.id.btn_flashcards);
                } else if (defaultFilter.equalsIgnoreCase("quiz")) {
                    toggleGroup.check(R.id.btn_quizzes);
                } else {
                    toggleGroup.check(R.id.btn_all);
                }
                defaultFilter = null;
            } else {
                int checkedId = toggleGroup.getCheckedButtonId();
                if (checkedId == -1) {
                    toggleGroup.check(R.id.btn_all);
                } else {
                    applyFilters();
                }
            }
        }
    }

    private void onFlashcardSetClicked(Flashcard set) {
        updateLastAccessed(set);
        Intent intent = new Intent(getContext(), FlashcardPreviewActivity.class);
        intent.putExtra("setId", set.getId());
        intent.putExtra("setName", set.getTitle());
        startActivity(intent);
    }

    private void updateLastAccessed(Flashcard set) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String setId = set.getId();
        String type = set.getType();
        long timestamp = System.currentTimeMillis();

        db.collection("users").document(currentUid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) documentSnapshot.get("owned_sets");
                List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");

                boolean found = false;

                if (ownedSets != null) {
                    for (Map<String, Object> item : ownedSets) {
                        if (setId.equals(item.get("id")) && type.equals(item.get("type"))) {
                            item.put("lastAccessed", timestamp);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found && savedSets != null) {
                    for (Map<String, Object> item : savedSets) {
                        if (setId.equals(item.get("id")) && type.equals(item.get("type"))) {
                            item.put("lastAccessed", timestamp);
                            break;
                        }
                    }
                }

                Map<String, Object> updates = new HashMap<>();
                if (ownedSets != null) updates.put("owned_sets", ownedSets);
                if (savedSets != null) updates.put("saved_sets", savedSets);

                db.collection("users").document(currentUid).update(updates);
            }
        });
    }

    private void loadAllSets() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);
        allSets.clear();
        displayedSets.clear();
        collectionsLoaded = 0;
        totalCollectionsToLoad = 4;

        db.collection("users").document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserPhotoUrl = documentSnapshot.getString("photoUrl");

                        List<Map<String, Object>> owned = (List<Map<String, Object>>) documentSnapshot.get("owned_sets");
                        if (owned != null) {
                            for (Map<String, Object> entry : owned) {
                                loadSingleSet(entry);
                            }
                        }
                        collectionsLoaded++;
                        checkAndApplyInitialFilter();

                        List<Map<String, Object>> saved = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
                        if (saved != null) {
                            for (Map<String, Object> entry : saved) {
                                loadSingleSet(entry);
                            }
                        }
                        collectionsLoaded++;
                        checkAndApplyInitialFilter();

                    } else {
                        currentUserPhotoUrl = null;
                        collectionsLoaded += 2;
                        checkAndApplyInitialFilter();
                    }
                    loadFlashcardsAndQuizzes(currentUid);
                })
                .addOnFailureListener(e -> {
                    currentUserPhotoUrl = null;
                    collectionsLoaded += 2;
                    checkAndApplyInitialFilter();
                    loadFlashcardsAndQuizzes(currentUid);
                });
    }

    private void loadSingleSet(Map<String, Object> entry) {
        if (entry == null) return;

        String id = (String) entry.get("id");
        String type = (String) entry.get("type");
        Long progress = entry.get("progress") != null ? ((Number) entry.get("progress")).longValue() : 0;
        Long lastAccessed = entry.get("lastAccessed") != null ? ((Number) entry.get("lastAccessed")).longValue() : 0;

        if (id == null || type == null) return;

        db.collection(type.equals("quiz") ? "quiz" : "flashcards")
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        if (!setAlreadyExists(doc.getId())) {
                            Flashcard set = parseSet(doc);
                            set.setType(type);
                            set.setPhotoUrl(doc.getString("photoUrl"));
                            set.setReminder(doc.getString("reminder"));
                            set.setProgress(progress.intValue());
                            set.setLastAccessed(lastAccessed);
                            allSets.add(set);
                        }
                    }
                });
    }



    private void loadFlashcardsAndQuizzes(String currentUid) {
        db.collection("flashcards")
                .whereEqualTo("owner_uid", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        if (!setAlreadyExists(doc.getId())) {
                            Flashcard set = parseSet(doc);
                            set.setType("flashcard");
                            set.setPhotoUrl(currentUserPhotoUrl);
                            set.setReminder(doc.getString("reminder"));
                            allSets.add(set);
                        }
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
                        if (!setAlreadyExists(doc.getId())) {
                            Flashcard set = parseSet(doc);
                            set.setType("quiz");
                            set.setPhotoUrl(currentUserPhotoUrl);
                            set.setReminder(doc.getString("reminder"));
                            allSets.add(set);
                        }
                    }
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                })
                .addOnFailureListener(e -> {
                    collectionsLoaded++;
                    checkAndApplyInitialFilter();
                });
    }

    private boolean setAlreadyExists(String id) {
        for (Flashcard set : allSets) {
            if (set.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private Flashcard parseSet(DocumentSnapshot doc) {
        String id = doc.getId();
        String title = doc.getString("title");
        long numberOfItems = doc.getLong("number_of_items") != null ? doc.getLong("number_of_items") : 0;
        String ownerUsername = doc.getString("owner_username");
        String privacy = doc.getString("privacy");

        Flashcard set = new Flashcard(id, title, (int) numberOfItems, ownerUsername, 0, null);
        set.setPrivacy(privacy);
        set.setOwnerUid(doc.getString("owner_uid"));

        return set;
    }

    private void applyFilters() {
        int checkedId = toggleGroup.getCheckedButtonId();
        String typeFilter;

        if (checkedId == R.id.btn_flashcards) {
            typeFilter = "flashcard";
        } else if (checkedId == R.id.btn_quizzes) {
            typeFilter = "quiz";
        } else {
            typeFilter = "all";
        }

        displayedSets.clear();
        for (Flashcard set : allSets) {
            boolean matchesType = typeFilter.equals("all") || set.getType().equalsIgnoreCase(typeFilter);
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    (set.getTitle() != null && set.getTitle().toLowerCase().contains(currentSearchQuery.toLowerCase()));

            if (matchesType && matchesSearch) {
                displayedSets.add(set);
            }
        }

        adapter.notifyDataSetChanged();
        noSetsText.setVisibility(displayedSets.isEmpty() ? View.VISIBLE : View.GONE);
    }


    private void showAddSet() {
        View view = getLayoutInflater().inflate(R.layout.add_set_menu, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent); // Keep your background styling

            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 25;
            params.y = 895;
            window.setAttributes(params);
        }

        view.findViewById(R.id.add_flashcard).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), GenerateSetActivity.class);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        view.findViewById(R.id.add_quiz).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), GenerateSetActivity.class);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });
    }



    private void showAddBottomSheetFlashcard() {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_add, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);

            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 25;
            params.y = 910;
            window.setAttributes(params);
        }

        view.findViewById(R.id.generate_from_camera).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Generate from camera coming soon!", Toast.LENGTH_SHORT).show();
        });

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
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_add, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);

            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 25;
            params.y = 910;
            window.setAttributes(params);
        }

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

}
