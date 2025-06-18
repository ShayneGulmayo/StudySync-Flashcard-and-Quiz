package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserSettingsActivity extends AppCompatActivity {

    private TextView usernameValue, fullnameValue, emailValue;
    private ImageView backButton;

    private CardView cardViewUsername, cardViewFullname, cardViewEmail, cardViewPassword;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private boolean isGoogleUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_user_settings);


        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        usernameValue = findViewById(R.id.username_value);
        fullnameValue = findViewById(R.id.fullname_value);
        emailValue = findViewById(R.id.email_value);
        backButton = findViewById(R.id.back_button);

        cardViewUsername = findViewById(R.id.cardView_username);
        cardViewFullname = findViewById(R.id.cardView_fullname);
        cardViewEmail = findViewById(R.id.cardView_email);
        cardViewPassword = findViewById(R.id.cardView_password);

        backButton.setOnClickListener(v -> finish());



        if (currentUser != null) {
            loadUserData(currentUser.getUid());
            for (com.google.firebase.auth.UserInfo userInfo : currentUser.getProviderData()) {
                if ("google.com".equals(userInfo.getProviderId())) {
                    isGoogleUser = true;
                    break;
                }
            }
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }

        cardViewUsername.setOnClickListener(v ->
                startActivity(new Intent(UserSettingsActivity.this, ChangeUsernameActivity.class)));

        cardViewFullname.setOnClickListener(v ->
                startActivity(new Intent(UserSettingsActivity.this, ChangeFullnameActivity.class)));

        cardViewEmail.setOnClickListener(v -> {
            if (isGoogleUser) {
                Snackbar.make(v, "Looks like you signed in with Google.", Snackbar.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(UserSettingsActivity.this, ChangeEmailActivity.class));
            }
        });

        cardViewPassword.setOnClickListener(v -> {
            if (isGoogleUser) {
                Snackbar.make(v, "Looks like you signed in with Google.", Snackbar.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(UserSettingsActivity.this, ChangePasswordActivity.class));
            }
        });
    }

    private void loadUserData(String uid) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");

                        usernameValue.setText(username != null ? username : "username");
                        fullnameValue.setText((firstName != null && lastName != null) ?
                                firstName + " " + lastName : "-");

                        // ✅ Get email directly from Firebase Authentication
                        emailValue.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "email");

                    } else {
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
                );
    }

}
