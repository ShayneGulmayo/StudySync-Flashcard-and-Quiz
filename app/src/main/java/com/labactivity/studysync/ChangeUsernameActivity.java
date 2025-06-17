package com.labactivity.studysync;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeUsernameActivity extends AppCompatActivity {

    private EditText changeUsernameEditText;
    private ImageView usernameStatusIcon, backButton;
    private TextView usernameWarningText, saveButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9._]{4,}$";
    private String validatedUsername = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username);

        changeUsernameEditText = findViewById(R.id.change_username);
        usernameStatusIcon = findViewById(R.id.username_status_icon);
        usernameWarningText = findViewById(R.id.username_warning);
        backButton = findViewById(R.id.backButton);
        saveButton = findViewById(R.id.save_txt);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        backButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> {
            if (validatedUsername != null) {
                disableSaveButton();
                saveUsername(validatedUsername);
            } else {
                Toast.makeText(this, "Please enter a valid username.", Toast.LENGTH_SHORT).show();
            }
        });

        changeUsernameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatedUsername = null;
                disableSaveButton();
                validateUsername(s.toString().trim());
            }

            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void validateUsername(String username) {
        if (!username.matches(USERNAME_PATTERN)) {
            showInvalid("Username must be at least 4 characters and can only contain letters, numbers, underscores, and periods.");
            return;
        }

        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        showInvalid("Username is already taken.");
                    } else {
                        validatedUsername = username;
                        showValid();
                    }
                })
                .addOnFailureListener(e -> showInvalid("Failed to check username."));
    }

    private void saveUsername(String username) {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .update("username", username)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Username updated successfully!", Toast.LENGTH_SHORT).show();
                    enableSaveButton();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update username.", Toast.LENGTH_SHORT).show();
                    enableSaveButton();
                });
    }

    private void showValid() {
        usernameStatusIcon.setImageResource(R.drawable.check_circle);
        usernameStatusIcon.setVisibility(View.VISIBLE);
        usernameWarningText.setVisibility(View.GONE);
        enableSaveButton();
    }

    private void showInvalid(String message) {
        usernameStatusIcon.setImageResource(R.drawable.x_circle);
        usernameStatusIcon.setVisibility(View.VISIBLE);
        usernameWarningText.setText(message);
        fadeInWarning();
        disableSaveButton();
    }

    private void fadeInWarning() {
        usernameWarningText.setVisibility(View.VISIBLE);
        AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(300);
        usernameWarningText.startAnimation(anim);
    }

    private void disableSaveButton() {
        saveButton.setAlpha(0.4f);
        saveButton.setEnabled(false);
    }

    private void enableSaveButton() {
        saveButton.setAlpha(1f);
        saveButton.setEnabled(true);
    }
}
