package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class DeleteAccountActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button confirmDeleteBtn;
    private ImageView backButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String currentUid, currentEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUid = currentUser.getUid();
        currentEmail = currentUser.getEmail();

        emailInput = findViewById(R.id.delete_account);
        confirmDeleteBtn = findViewById(R.id.confirm_delete_account_btn);
        backButton = findViewById(R.id.backButton);

        confirmDeleteBtn.setOnClickListener(v -> confirmDeletion());
        backButton.setOnClickListener(v -> finish());
    }

    private void confirmDeletion() {
        String enteredEmail = emailInput.getText().toString().trim();

        if (enteredEmail.isEmpty()) {
            Toast.makeText(this, "Please enter your email to confirm.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!enteredEmail.equals(currentEmail)) {
            Toast.makeText(this, "Email doesn't match your account email.", Toast.LENGTH_SHORT).show();
            return;
        }

        deleteUserData();
    }

    private void deleteUserData() {
        // Delete user document in 'users' collection
        db.collection("users").document(currentUid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Delete other collections related to this user
                    deleteUserCollections("owned_sets");
                    deleteUserCollections("saved_sets");
                    deleteUserCollections("group_chats");
                    deleteUserCollections("quizzes");
                    deleteUserCollections("messages");

                    // Delete user files from Firebase Storage
                    deleteUserStorageFiles();

                    // Delete Firebase Authentication account
                    deleteFirebaseAccount();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete user data.", Toast.LENGTH_SHORT).show());
    }

    private void deleteUserCollections(String collectionName) {
        CollectionReference collectionRef = db.collection(collectionName);
        collectionRef.whereEqualTo("ownerUid", currentUid).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }
                });
    }

    private void deleteUserStorageFiles() {
        StorageReference userFilesRef = storage.getReference().child("user-files/" + currentUid);
        userFilesRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference fileRef : listResult.getItems()) {
                        fileRef.delete();
                    }
                });

        // Also delete images in flashcard-images if applicable
        StorageReference flashcardImagesRef = storage.getReference().child("flashcard-images");
        flashcardImagesRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference imageRef : listResult.getItems()) {
                        if (imageRef.getName().startsWith(currentUid + "_")) {
                            imageRef.delete();
                        }
                    }
                });
    }

    private void deleteFirebaseAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Account successfully deleted.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(DeleteAccountActivity.this, LoginActivity.class));
                        finishAffinity();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete account.", Toast.LENGTH_SHORT).show());
        }
    }
}
