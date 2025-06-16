package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private EditText editTxtEmail, editTxtPassword;
    private TextView loginRedirect;
    private Button btnSignup, googleSignupBtn;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private static final int REQ_ONE_TAP = 101;
    private boolean showOneTapUI = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTxtEmail = findViewById(R.id.editTxtEmail);
        editTxtPassword = findViewById(R.id.editTxtPassword);
        btnSignup = findViewById(R.id.btnSignup);
        googleSignupBtn = findViewById(R.id.btnGoogleSignup);
        loginRedirect = findViewById(R.id.loginRedirect);

        btnSignup.setOnClickListener(v -> registerUser());
        loginRedirect.setOnClickListener(v ->
                startActivity(new Intent(SignupActivity.this, LoginActivity.class)));

        googleSignupBtn.setOnClickListener(v -> signUpWithGoogle());

        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .build();
    }

    private void registerUser() {
        String email = editTxtEmail.getText().toString().trim();
        String password = editTxtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTxtEmail.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTxtPassword.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            editTxtPassword.setError("Password must be at least 6 characters");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(SignupActivity.this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkUserProfileAndRedirect(user);
                            Toast.makeText(SignupActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(SignupActivity.this, "This email is already registered", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SignupActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void signUpWithGoogle() {
        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(
                                result.getPendingIntent().getIntentSender(),
                                REQ_ONE_TAP,
                                null,
                                0, 0, 0
                        );
                    } catch (Exception e) {
                        Log.e("SignupActivity", "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.d("SignupActivity", "Google Sign-Up failed: " + e.getLocalizedMessage());
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ONE_TAP) {
            try {
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(data);
                String idToken = credential.getGoogleIdToken();
                if (idToken != null) {
                    AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                    mAuth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener(this, task -> {
                                if (task.isSuccessful()) {
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    if (user != null) {
                                        checkUserProfileAndRedirect(user);
                                        Toast.makeText(this, "Signed up as " + user.getEmail(), Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(this, "Google Sign-Up failed.", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            } catch (Exception e) {
                Log.e("SignupActivity", "Google Sign-Up Exception: " + e.getLocalizedMessage());
            }
        }
    }

    private void checkUserProfileAndRedirect(FirebaseUser user) {
        String uid = user.getUid();
        String email = user.getEmail();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null &&
                                data.containsKey("username") &&
                                data.containsKey("firstName") &&
                                data.containsKey("lastName")) {
                            // Profile complete
                            startActivity(new Intent(SignupActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Profile incomplete
                            startActivity(new Intent(SignupActivity.this, UserSetUpProfileActivity.class));
                            finish();
                        }
                    } else {
                        // Create initial document with email only
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);

                        db.collection("users").document(uid).set(userData)
                                .addOnSuccessListener(unused -> {
                                    startActivity(new Intent(SignupActivity.this, UserSetUpProfileActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(SignupActivity.this, "Error creating user profile", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(SignupActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                });
    }
}
