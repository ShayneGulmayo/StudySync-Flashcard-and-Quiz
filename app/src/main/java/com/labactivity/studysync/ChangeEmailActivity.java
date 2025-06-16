package com.labactivity.studysync;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeEmailActivity extends AppCompatActivity {

    private ImageView backButton;
    private TextView saveText;
    private EditText changeEmailEditText;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_email);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        backButton = findViewById(R.id.back_button);
        saveText = findViewById(R.id.save_txt);
        changeEmailEditText = findViewById(R.id.change_email);
        ImageView emailStatusIcon = findViewById(R.id.email_status_icon);
        TextView emailWarning = findViewById(R.id.email_warning);

        changeEmailEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String email = s.toString().trim();

                if (email.isEmpty()) {
                    emailStatusIcon.setVisibility(View.GONE);
                    emailWarning.setVisibility(View.GONE);
                    return;
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailStatusIcon.setImageResource(R.drawable.x_circle);
                    emailStatusIcon.setVisibility(View.VISIBLE);
                    emailWarning.setText("Invalid email format.");
                    emailWarning.setVisibility(View.VISIBLE);
                } else {
                    emailStatusIcon.setImageResource(R.drawable.check_circle);
                    emailStatusIcon.setVisibility(View.VISIBLE);
                    emailWarning.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (currentUser != null && currentUser.getEmail() != null) {
            changeEmailEditText.setText(currentUser.getEmail());
        }

        backButton.setOnClickListener(v -> finish());

        saveText.setOnClickListener(v -> {
            String newEmail = changeEmailEditText.getText().toString().trim();
            String currentEmail = currentUser != null ? currentUser.getEmail() : "";

            if (newEmail.isEmpty()) {
                Toast.makeText(this, "Please enter a new email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newEmail.equals(currentEmail)) {
                Toast.makeText(this, "New email is the same as the current email", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check if user is Google user (provider is "google.com")
            if (currentUser.getProviderData().stream().anyMatch(
                    userInfo -> "google.com".equals(userInfo.getProviderId()))) {
                Toast.makeText(this, "Cannot change email for Google Sign-In users", Toast.LENGTH_LONG).show();
                return;
            }

            currentUser.updateEmail(newEmail)
                    .addOnSuccessListener(unused -> {
                        db.collection("users").document(currentUser.getUid())
                                .update("email", newEmail)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Email updated successfully", Toast.LENGTH_SHORT).show();
                                    finish(); // Close activity
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to update Firestore", Toast.LENGTH_SHORT).show()
                                );
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to update email: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        });

    }
}
