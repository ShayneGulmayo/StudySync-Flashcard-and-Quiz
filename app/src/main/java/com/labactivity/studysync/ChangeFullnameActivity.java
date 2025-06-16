package com.labactivity.studysync;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeFullnameActivity extends AppCompatActivity {

    private EditText firstNameEditText, lastNameEditText;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private TextView saveText;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_fullname);

        firstNameEditText = findViewById(R.id.change_firstname);
        lastNameEditText = findViewById(R.id.change_lastname);
        saveText = findViewById(R.id.save_txt);
        backButton = findViewById(R.id.back_button);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        if (currentUser != null) {
            DocumentReference userRef = db.collection("users").document(currentUser.getUid());
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String firstName = documentSnapshot.getString("firstName");
                    String lastName = documentSnapshot.getString("lastName");

                    firstNameEditText.setText(firstName != null ? firstName : "");
                    lastNameEditText.setText(lastName != null ? lastName : "");
                }
            }).addOnFailureListener(e ->
                    Toast.makeText(ChangeFullnameActivity.this, "Failed to load user info.", Toast.LENGTH_SHORT).show()
            );
        }

        saveText.setOnClickListener(v -> saveFullName());
        backButton.setOnClickListener(v -> finish());
    }

    private void saveFullName() {
        String newFirstName = firstNameEditText.getText().toString().trim();
        String newLastName = lastNameEditText.getText().toString().trim();

        if (newFirstName.isEmpty() || newLastName.isEmpty()) {
            Toast.makeText(this, "Please fill out both names.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(currentUser.getUid());

        userRef.update("firstName", newFirstName, "lastName", newLastName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Name updated successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update name.", Toast.LENGTH_SHORT).show()
                );
    }
}
