package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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

public class SignupActivity extends AppCompatActivity {

    private EditText editTxtEmail, editTxtPassword;
    private TextView loginRedirect;
    private Button btnSignup, googleSignupBtn;
    private FirebaseAuth mAuth;

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private static final int REQ_ONE_TAP = 101; // different from login
    private boolean showOneTapUI = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

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
                        Toast.makeText(SignupActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                        finish();
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
                                    Toast.makeText(this, "Signed up as " + user.getEmail(), Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(SignupActivity.this, LoginActivity.class));
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
}
