package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;


public class UserSetUpProfileActivity extends AppCompatActivity {

    private EditText usernameEditText, firstNameEditText, lastNameEditText;
    private ImageView usernameCheckIcon, usernameXIcon;
    private TextView usernameWarningText;
    private Button saveButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_set_up_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        usernameEditText = findViewById(R.id.set_username);
        firstNameEditText = findViewById(R.id.set_firstname);
        lastNameEditText = findViewById(R.id.set_lastname);
        usernameCheckIcon = findViewById(R.id.username_check_icon);
        usernameXIcon = findViewById(R.id.username_x_icon);
        usernameWarningText = findViewById(R.id.username_warning_text);
        saveButton = findViewById(R.id.button);

        animateViewEntry(usernameEditText, 0);
        animateViewEntry(firstNameEditText, 100);
        animateViewEntry(lastNameEditText, 200);
        animateViewEntry(saveButton, 300);

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);

                final String username = s.toString().trim();

                debounceRunnable = () -> validateUsername(username);
                debounceHandler.postDelayed(debounceRunnable, 500); // 500ms delay
            }
        });


        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void animateViewEntry(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(30);
        view.animate().alpha(1f).translationY(0).setDuration(500).setStartDelay(delay).start();
    }

    private void validateUsername(String username) {
        usernameCheckIcon.setVisibility(View.GONE);
        usernameXIcon.setVisibility(View.GONE);

        if (TextUtils.isEmpty(username)) {
            fadeOutWarning();
            return;
        }

        if (!username.matches("^[a-zA-Z0-9._]+$")) {
            showWarning("Only letters, numbers, underscores, and periods allowed.");
            return;
        }

        if (username.length() < 4) {
            showWarning("Username must be at least 4 characters long.");
            return;
        }

        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snapshot -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    boolean taken = snapshot.getDocuments().stream()
                            .anyMatch(doc -> !doc.getId().equals(currentUser.getUid()));

                    if (taken) {
                        showWarning("This username is already taken.");
                        showIcon(usernameXIcon, false);
                    } else {
                        fadeOutWarning();
                        showIcon(usernameCheckIcon, true);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error checking username", Toast.LENGTH_SHORT).show());
    }



    private void showIcon(ImageView icon, boolean bounce) {
        icon.setVisibility(View.VISIBLE);
        ScaleAnimation scale = new ScaleAnimation(
                0f, 1f, 0f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(300);
        if (bounce) scale.setInterpolator(new BounceInterpolator());
        icon.startAnimation(scale);
    }

    private void saveProfile() {
        String username = usernameEditText.getText().toString().trim();
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();

        if (username.length() < 4 || !username.matches("^[a-zA-Z0-9._]+$")) {
            Toast.makeText(this, "Please fix the username format before saving.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snapshot -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    boolean taken = snapshot.getDocuments().stream()
                            .anyMatch(doc -> !doc.getId().equals(currentUser.getUid()));

                    if (taken) {
                        showIcon(usernameXIcon, false);
                        Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show();
                    } else {
                        showIcon(usernameCheckIcon, true);
                        saveUserToFirestore(currentUser.getUid(), username, firstName, lastName);
                    }
                });
    }

    private void saveUserToFirestore(String uid, String username, String firstName, String lastName) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", username);
        data.put("firstName", firstName);
        data.put("lastName", lastName);

        db.collection("users").document(uid)
                .set(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show());
    }

    private void showWarning(String message) {
        usernameWarningText.setText(message);
        if (usernameWarningText.getVisibility() != View.VISIBLE) {
            usernameWarningText.setAlpha(0f);
            usernameWarningText.setVisibility(View.VISIBLE);
            usernameWarningText.animate().alpha(1f).setDuration(300).start();
        }
    }

    private void fadeOutWarning() {
        if (usernameWarningText.getVisibility() == View.VISIBLE) {
            usernameWarningText.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> usernameWarningText.setVisibility(View.GONE))
                    .start();
        }
    }

}
