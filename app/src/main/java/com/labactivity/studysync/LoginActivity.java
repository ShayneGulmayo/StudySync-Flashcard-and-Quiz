package com.labactivity.studysync;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private TextView signUpRedirect, forgotPassRedirect;
    private Button loginButton, googleButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ProgressBar loginProgressBar;

    private boolean isPasswordVisible = false;

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private static final int REQ_ONE_TAP = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailEditText = findViewById(R.id.editTxtEmail);
        passwordEditText = findViewById(R.id.editTxtPassword);
        loginButton = findViewById(R.id.btnLogin);
        googleButton = findViewById(R.id.googleLogin);
        forgotPassRedirect = findViewById(R.id.txtForgotPass);
        signUpRedirect = findViewById(R.id.txtSignupRedirect);
        loginProgressBar = findViewById(R.id.login_progress_bar);
        loginProgressBar.setVisibility(View.GONE);

        setupPasswordToggle(passwordEditText, () -> isPasswordVisible = !isPasswordVisible, () -> isPasswordVisible);

        forgotPassRedirect.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, EmailResetPasswordActivity.class));
            finish();
        });

        signUpRedirect.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
            finish();
        });

        loginButton.setOnClickListener(view -> loginWithEmailPassword());
        googleButton.setOnClickListener(view -> signInWithGoogle());

        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .build();
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            loginProgressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            googleButton.setEnabled(false);
            emailEditText.setEnabled(false);
            passwordEditText.setEnabled(false);
        } else {
            loginProgressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            googleButton.setEnabled(true);
            emailEditText.setEnabled(true);
            passwordEditText.setEnabled(true);
        }
    }

    private void setupPasswordToggle(EditText editText, Runnable toggleState, Supplier<Boolean> isVisibleSupplier) {
        editText.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editText.getRight() - editText.getCompoundDrawables()[DRAWABLE_END].getBounds().width())) {
                    toggleState.run();
                    if (isVisibleSupplier.get()) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.eye_resized, 0);
                    } else {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.eye_closed_resized, 0);
                    }
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
        editText.setHapticFeedbackEnabled(false);

    }

    private void loginWithEmailPassword() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }

        setLoadingState(true);
        loginProgressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    loginProgressBar.setVisibility(View.GONE);
                    setLoadingState(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified()) {
                                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
                                checkUserProfileCompletion(user);
                            } else {
                                Toast.makeText(this, "Please verify your email address to continue.", Toast.LENGTH_LONG).show();
                                user.sendEmailVerification()
                                        .addOnCompleteListener(sendTask -> {
                                            if (sendTask.isSuccessful()) {
                                                Toast.makeText(this, "Verification email sent again.", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                mAuth.signOut();
                                setLoadingState(false);
                            }
                        }                    } else {
                        setLoadingState(false);
                        String errorMessage;
                        try {
                            throw task.getException();
                        } catch (Exception e) {
                            errorMessage = e.getMessage();
                            if (errorMessage != null) {
                                if (errorMessage.contains("There is no user record")) {
                                    emailEditText.setError("No account found with this email");
                                    emailEditText.requestFocus();
                                } else if (errorMessage.contains("The password is invalid")) {
                                    passwordEditText.setError("Incorrect password");
                                    passwordEditText.requestFocus();
                                } else {
                                    Toast.makeText(this, "Login failed: " + errorMessage, Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(this, "Login failed: An unknown error occurred.", Toast.LENGTH_LONG).show();
                            }
                        }

                    }
                });
    }

    private void signInWithGoogle() {
        setLoadingState(true);
        oneTapClient = Identity.getSignInClient(this);

        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .build();

        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(
                                result.getPendingIntent().getIntentSender(),
                                REQ_ONE_TAP,
                                null, 0, 0, 0
                        );
                    } catch (Exception e) {
                        Log.e("LoginActivity", "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                        Toast.makeText(this, "Error starting Google Sign-In.", Toast.LENGTH_SHORT).show();
                        setLoadingState(false);
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e("LoginActivity", "Google Sign-In failed: " + e.getLocalizedMessage());
                    Toast.makeText(this, "Google Sign-In not available: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    setLoadingState(false);
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
                                    checkUserProfileCompletion(user);
                                } else {
                                    Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show();
                                    setLoadingState(false);
                                }
                            });
                }
            } catch (Exception e) {
                Log.e("LoginActivity", "Google Sign-In Exception: " + e.getLocalizedMessage());
                setLoadingState(false);
            }
        }
    }

    private void checkUserProfileCompletion(FirebaseUser user) {
        if (user == null){
            setLoadingState(false);
            return;
        }

        final Context context = getApplicationContext();
        if (context == null) {
            setLoadingState(false);
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(document -> {
                    setLoadingState(false);
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String username = document.getString("username");

                        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(username)) {
                            startActivity(new Intent(LoginActivity.this, UserSetUpProfileActivity.class));
                            finish();
                        } else {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                        finish();
                    } else {
                        startActivity(new Intent(LoginActivity.this, UserSetUpProfileActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    Toast.makeText(LoginActivity.this, "Error checking profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    interface Supplier<T> {
        T get();
    }
}
