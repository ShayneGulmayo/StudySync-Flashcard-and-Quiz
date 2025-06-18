
package com.labactivity.studysync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserSetUpProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 101;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private EditText usernameEditText, firstNameEditText, lastNameEditText;
    private ImageView usernameCheckIcon, usernameXIcon;
    private CircleImageView profileImageView;
    private TextView usernameWarningText;
    private Button saveButton;
    private Uri selectedImageUri;

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
        profileImageView = findViewById(R.id.user_photo);
        saveButton = findViewById(R.id.button);

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                final String username = s.toString().trim();
                debounceRunnable = () -> validateUsername(username);
                debounceHandler.postDelayed(debounceRunnable, 500);
            }
        });

        profileImageView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE_REQUEST);
        });

        saveButton.setOnClickListener(v -> saveProfile());
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
        ScaleAnimation scale = new ScaleAnimation(0f, 1f, 0f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(300);
        if (bounce) scale.setInterpolator(new BounceInterpolator());
        icon.startAnimation(scale);
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
                    if (selectedImageUri != null) {
                        try {
                            File file = FileUtils.getFileFromUri(this, selectedImageUri);

                            if (file.length() > MAX_FILE_SIZE) {
                                Toast.makeText(this, "Image too large (max 5MB)", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String mimeType = getMimeType(Uri.fromFile(file));
                            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                            if (extension == null) extension = "jpg"; // fallback

                            String filename = "user-profile-" + uid + "." + extension;


                            SupabaseUploader.uploadFile(file, filename, new SupabaseUploader.UploadCallback() {
                                @Override
                                public void onUploadComplete(boolean success, String message) {
                                    runOnUiThread(() -> {
                                        if (success) {
                                            String publicUrl = "https://agnosyltikewhdzmdcwp.supabase.co/storage/v1/object/public/user-files/" + filename;

                                            db.collection("users").document(uid)
                                                    .update("photoUrl", publicUrl)
                                                    .addOnSuccessListener(aVoid -> {
                                                        Toast.makeText(UserSetUpProfileActivity.this, "Profile saved with image", Toast.LENGTH_SHORT).show();
                                                        startActivity(new Intent(UserSetUpProfileActivity.this, MainActivity.class));
                                                        finish();
                                                    });
                                        } else {
                                            Toast.makeText(UserSetUpProfileActivity.this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
                                            startActivity(new Intent(UserSetUpProfileActivity.this, MainActivity.class));
                                            finish();
                                        }
                                    });
                                }
                            });
                        } catch (Exception e) {
                            Toast.makeText(this, "Error preparing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri sourceUri = data.getData();
            File file = new File(getCacheDir(), UUID.randomUUID().toString() + ".jpg");
            Uri destinationUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(512, 512)
                    .start(this);
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            selectedImageUri = UCrop.getOutput(data);
            if (selectedImageUri != null) {
                profileImageView.setImageURI(selectedImageUri);
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Toast.makeText(this, "Image crop error", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(Uri uri) {
        ContentResolver cr = getContentResolver();
        return cr.getType(uri);
    }

    public static class FileUtils {
        public static File getFileFromUri(Context context, Uri uri) throws IOException {
            ContentResolver contentResolver = context.getContentResolver();
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri));

            if (extension == null) extension = "jpg";

            File tempFile = File.createTempFile("upload", "." + extension, context.getCacheDir());

            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return tempFile;
        }

    }
}
