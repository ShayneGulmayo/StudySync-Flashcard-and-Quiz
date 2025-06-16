package com.labactivity.studysync;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.AlphaAnimation;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeUsernameActivity extends AppCompatActivity {

    private EditText changeUsernameEditText;
    private ImageView usernameStatusIcon, backButton;
    private TextView usernameWarningText;

    private FirebaseFirestore db;
    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9._]{4,}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username);

        changeUsernameEditText = findViewById(R.id.change_username);
        usernameStatusIcon = findViewById(R.id.username_status_icon);
        usernameWarningText = findViewById(R.id.username_warning);
        backButton = findViewById(R.id.backButton);

        db = FirebaseFirestore.getInstance();
        backButton.setOnClickListener(v -> finish());


        changeUsernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateUsername(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) { }
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
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        showInvalid("Username is already taken.");
                    } else {
                        showValid();
                    }
                })
                .addOnFailureListener(e -> {
                    showInvalid("Failed to check username.");
                });
    }

    private void showValid() {
        usernameStatusIcon.setImageResource(R.drawable.check_circle);
        usernameStatusIcon.setVisibility(View.VISIBLE);
        usernameWarningText.setVisibility(View.GONE);
    }

    private void showInvalid(String message) {
        usernameStatusIcon.setImageResource(R.drawable.x_circle);
        usernameStatusIcon.setVisibility(View.VISIBLE);
        usernameWarningText.setText(message);
        fadeInWarning();
    }

    private void fadeInWarning() {
        usernameWarningText.setVisibility(View.VISIBLE);
        AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
        animation.setDuration(300);
        usernameWarningText.startAnimation(animation);
    }
}
