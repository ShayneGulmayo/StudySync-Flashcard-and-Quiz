package com.labactivity.studysync;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateFlashcardActivity extends AppCompatActivity {

    private EditText setNameEditText;
    private LinearLayout flashcardContainer;
    private FloatingActionButton floatingAddButton;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ImageView backButton, saveButton;
    private String username;
    private String setId = null; // holds setId if in edit mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_flashcard);

        setNameEditText = findViewById(R.id.set_name);
        flashcardContainer = findViewById(R.id.flashcard_container);
        floatingAddButton = findViewById(R.id.floating_add_btn);
        backButton = findViewById(R.id.back_button);
        saveButton = findViewById(R.id.save_button);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        backButton.setOnClickListener(v -> onBackPressed());
        floatingAddButton.setOnClickListener(v -> addFlashcardView());
        saveButton.setOnClickListener(v -> {
            if (setId != null) {
                updateFlashcardSet();
            } else {
                saveFlashcardSet();
            }
        });

        addFlashcardView(); // initial card

        // fetch current user's username
        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        username = documentSnapshot.getString("username");
                    }
                });

        // Check if this is an edit operation
        if (getIntent().hasExtra("setId")) {
            setId = getIntent().getStringExtra("setId");
            loadFlashcardSetForEditing(setId);
        }
    }

    private void loadFlashcardSetForEditing(String setId) {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        setNameEditText.setText(title);

                        Map<String, Object> terms = (Map<String, Object>) documentSnapshot.get("terms");
                        if (terms != null) {
                            flashcardContainer.removeAllViews(); // remove initial empty card
                            for (int i = 0; i < terms.size(); i++) {
                                Map<String, Object> termEntry = (Map<String, Object>) terms.get(String.valueOf(i));
                                String term = (String) termEntry.get("term");
                                String definition = (String) termEntry.get("definition");

                                addFlashcardView(term, definition);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load flashcard set", Toast.LENGTH_SHORT).show());
    }

    private void saveFlashcardSet() {
        String setName = setNameEditText.getText().toString().trim();

        if (TextUtils.isEmpty(setName)) {
            setNameEditText.setError("Set name is required");
            return;
        }

        int numberOfItems = 0;
        Map<String, Object> termsMap = new HashMap<>();

        for (int i = 0; i < flashcardContainer.getChildCount(); i++) {
            View view = flashcardContainer.getChildAt(i);
            EditText termEditText = view.findViewById(R.id.flashcard_term);
            EditText definitionEditText = view.findViewById(R.id.flashcard_definition);

            String term = termEditText.getText().toString().trim();
            String definition = definitionEditText.getText().toString().trim();

            if (!TextUtils.isEmpty(term) && !TextUtils.isEmpty(definition)) {
                Map<String, Object> termEntry = new HashMap<>();
                termEntry.put("term", term);
                termEntry.put("definition", definition);

                termsMap.put(String.valueOf(numberOfItems), termEntry);
                numberOfItems++;
            }
        }

        if (numberOfItems == 0) {
            Toast.makeText(this, "Add at least one flashcard", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> flashcardSet = new HashMap<>();
        flashcardSet.put("title", setName);
        flashcardSet.put("number_of_items", numberOfItems);
        flashcardSet.put("owner_username", username);
        flashcardSet.put("owner_uid", uid);
        flashcardSet.put("private", "public"); // or "private"
        flashcardSet.put("progress", 0);
        flashcardSet.put("terms", termsMap);

        db.collection("flashcards")
                .add(flashcardSet)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Flashcard set saved", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save flashcard set", Toast.LENGTH_SHORT).show());
    }

    private void updateFlashcardSet() {
        String setName = setNameEditText.getText().toString().trim();

        if (TextUtils.isEmpty(setName)) {
            setNameEditText.setError("Set name is required");
            return;
        }

        int numberOfItems = 0;
        Map<String, Object> termsMap = new HashMap<>();

        for (int i = 0; i < flashcardContainer.getChildCount(); i++) {
            View view = flashcardContainer.getChildAt(i);
            EditText termEditText = view.findViewById(R.id.flashcard_term);
            EditText definitionEditText = view.findViewById(R.id.flashcard_definition);

            String term = termEditText.getText().toString().trim();
            String definition = definitionEditText.getText().toString().trim();

            if (!TextUtils.isEmpty(term) && !TextUtils.isEmpty(definition)) {
                Map<String, Object> termEntry = new HashMap<>();
                termEntry.put("term", term);
                termEntry.put("definition", definition);

                termsMap.put(String.valueOf(numberOfItems), termEntry);
                numberOfItems++;
            }
        }

        if (numberOfItems == 0) {
            Toast.makeText(this, "Add at least one flashcard", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updatedData = new HashMap<>();
        updatedData.put("title", setName);
        updatedData.put("number_of_items", numberOfItems);
        updatedData.put("terms", termsMap);

        db.collection("flashcards").document(setId)
                .update(updatedData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Flashcard set updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update flashcard set", Toast.LENGTH_SHORT).show());
    }

    private void addFlashcardView() {
        View flashcardView = getLayoutInflater().inflate(R.layout.item_flashcard_input, null);
        flashcardContainer.addView(flashcardView);

        ImageButton deleteButton = flashcardView.findViewById(R.id.delete_btn);
        deleteButton.setOnClickListener(v -> flashcardContainer.removeView(flashcardView));
    }

    private void addFlashcardView(String term, String definition) {
        View flashcardView = getLayoutInflater().inflate(R.layout.item_flashcard_input, null);
        EditText termEditText = flashcardView.findViewById(R.id.flashcard_term);
        EditText definitionEditText = flashcardView.findViewById(R.id.flashcard_definition);

        termEditText.setText(term);
        definitionEditText.setText(definition);

        ImageButton deleteButton = flashcardView.findViewById(R.id.delete_btn);
        deleteButton.setOnClickListener(v -> flashcardContainer.removeView(flashcardView));

        flashcardContainer.addView(flashcardView);
    }
}
