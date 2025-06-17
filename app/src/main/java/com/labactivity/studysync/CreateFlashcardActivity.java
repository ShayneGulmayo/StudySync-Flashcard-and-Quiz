package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CreateFlashcardActivity extends AppCompatActivity {
    private EditText setNameEditText;
    private LinearLayout flashcardContainer;
    private FloatingActionButton floatingAddButton;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ImageButton backButton, saveButton;

    @SuppressLint("WrongViewCast")
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

        backButton.setOnClickListener(v->onBackPressed());
        addFlashcardView();

        floatingAddButton.setOnClickListener(v -> addFlashcardView());
        saveButton.setOnClickListener(v -> saveFlashcardSet());

    }

    private void saveFlashcardSet() {
        String setName =setNameEditText.getText().toString().trim();

        if (TextUtils.isEmpty(setName)) {
            setNameEditText.setError("Set name is required");
            return;
        }

        ArrayList<Map<String, String>> flashcards = new ArrayList<>();
        for (int i = 0; i < flashcardContainer.getChildCount(); i++) {
            View view = flashcardContainer.getChildAt(i);
            EditText term = view.findViewById(R.id.flashcard_term);
            EditText definition = view.findViewById(R.id.flashcard_definition);

            String t = term.getText().toString().trim();
            String d = definition.getText().toString().trim();

            if (!t.isEmpty() && !d.isEmpty()) {
                Map<String, String> card = new HashMap<>();
                card.put("term", t);
                card.put("definition", d);
                flashcards.add(card);
            }
        }

        if (flashcards.isEmpty()) {
            Toast.makeText(this, "Add at least one flashcard", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> flashcardSet = new HashMap<>();
        flashcardSet.put("title", setName);
        flashcardSet.put("ownerId", uid);
        flashcardSet.put("flashcards", flashcards);
        flashcardSet.put("timestamp", System.currentTimeMillis());

        db.collection("flashcard")
                .add(flashcardSet)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Flashcard set saved", Toast.LENGTH_SHORT).show();
                    finish(); // Go back to previous screen
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save flashcard set", Toast.LENGTH_SHORT).show()
                );
    }

    private void addFlashcardView() {
        View flashcardView =getLayoutInflater().inflate(R.layout.item_flashcard_input, null);
        flashcardContainer.addView(flashcardView);

        ImageButton deleteButton = flashcardView.findViewById(R.id.delete_btn);

        deleteButton.setOnClickListener(v -> {
            flashcardContainer.removeView(flashcardView);
        });

        flashcardContainer.addView(flashcardView);
    }

}