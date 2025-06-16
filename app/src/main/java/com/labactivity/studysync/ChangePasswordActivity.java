package com.labactivity.studysync;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText currentPass, newPass, confirmPass;
    private TextView saveTxt;

    private boolean isCurrentVisible = false;
    private boolean isNewVisible = false;
    private boolean isConfirmVisible = false;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        currentPass = findViewById(R.id.current_pass);
        newPass = findViewById(R.id.set_pass);
        confirmPass = findViewById(R.id.confirm_pass);
        saveTxt = findViewById(R.id.save_txt);

        setupPasswordToggle(currentPass, () -> isCurrentVisible = !isCurrentVisible, () -> isCurrentVisible);
        setupPasswordToggle(newPass, () -> isNewVisible = !isNewVisible, () -> isNewVisible);
        setupPasswordToggle(confirmPass, () -> isConfirmVisible = !isConfirmVisible, () -> isConfirmVisible);

        saveTxt.setOnClickListener(view -> validateAndChangePassword());
    }

    private void setupPasswordToggle(EditText editText, Runnable toggleState, Supplier<Boolean> isVisibleSupplier) {
        editText.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editText.getRight() - editText.getCompoundDrawables()[DRAWABLE_END].getBounds().width())) {
                    toggleState.run();
                    if (isVisibleSupplier.get()) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.eye, 0);
                    } else {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.eye_closed, 0);
                    }
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
        editText.setHapticFeedbackEnabled(false);

    }

    private void validateAndChangePassword() {
        String current = currentPass.getText().toString().trim();
        String newP = newPass.getText().toString().trim();
        String confirm = confirmPass.getText().toString().trim();

        if (TextUtils.isEmpty(current)) {
            currentPass.setError("Current password is required");
            return;
        }

        if (TextUtils.isEmpty(newP)) {
            newPass.setError("New password is required");
            return;
        }

        if (newP.length() < 6) {
            newPass.setError("Password must be at least 6 characters");
            return;
        }

        if (!newP.matches(".*[A-Za-z].*") || !newP.matches(".*\\d.*")) {
            newPass.setError("Password must contain letters and numbers");
            return;
        }

        if (!newP.equals(confirm)) {
            confirmPass.setError("Passwords do not match");
            return;
        }

        if (currentUser != null && currentUser.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), current);

            currentUser.reauthenticate(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    currentUser.updatePassword(newP).addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(this, "Failed to update password. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    currentPass.setError("Incorrect current password");
                }
            });
        }
    }


    interface Supplier<T> {
        T get();
    }
}
