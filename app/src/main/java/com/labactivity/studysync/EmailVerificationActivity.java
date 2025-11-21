package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationActivity extends AppCompatActivity {
    private Button procBtn;
    private TextView resendBtn;
    private FirebaseAuth  mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_email_verification);

        procBtn = findViewById(R.id.procLogin);
        resendBtn = findViewById(R.id.resendBtn);
        mAuth = FirebaseAuth.getInstance();

        procBtn.setOnClickListener(v -> {
            startActivity(new Intent(EmailVerificationActivity.this, LoginActivity.class));
            finish();

        });
        resendBtn.setOnClickListener(v -> {
            resendVerificationEmail();
        });




    }
    private void resendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(EmailVerificationActivity.this,
                                    "Verification email resent. Check your inbox or spam.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Log.e("EmailVerifActivity", "Failed to resend verification email: " + task.getException().getMessage());
                            Toast.makeText(EmailVerificationActivity.this,
                                    "Failed to resend email. Try again later.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            Toast.makeText(EmailVerificationActivity.this,
                    "No active user session. Please try logging in again.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(EmailVerificationActivity.this, LoginActivity.class));

        }
    }
}