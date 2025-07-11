package com.labactivity.studysync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.util.IOUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;


import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GenerateFlashcardActivity extends AppCompatActivity {

    private static final String TAG = "GenerateFlashcard";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private FirebaseAuth mAuth;
    private CardView selectFile, generateManually;
    private ImageView backBtn;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    String mimeType = getContentResolver().getType(fileUri);
                    if (fileUri != null && mimeType != null) {
                        readFileContentAndSendToFunction(fileUri, mimeType);
                    } else {
                        Toast.makeText(this, "Invalid file selected", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_flashcard);

        mAuth = FirebaseAuth.getInstance();
        selectFile = findViewById(R.id.selectFile);
        backBtn = findViewById(R.id.back_button);
        generateManually = findViewById(R.id.generateManually);

        checkUserAuthentication();

        backBtn.setOnClickListener(v -> finish());
        generateManually.setOnClickListener(view -> {
            Intent intent = new Intent(this, CreateFlashcardActivity.class);
            startActivity(intent);
            finish();
        });

        selectFile.setOnClickListener(view -> {
            Toast.makeText(this, "Select File clicked", Toast.LENGTH_SHORT).show(); // Debug
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                openFilePicker();
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                } else {
                    openFilePicker();
                }
            }

        });
    }

    private void checkUserAuthentication() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select File"));
    }

    private void readFileContentAndSendToFunction(Uri uri, String mimeType) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            byte[] bytes = IOUtils.toByteArray(inputStream);
            String base64Encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);

            String title = "Generated Set " + new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault()).format(new Date());

            callFirebaseFunction(base64Encoded, mimeType, title);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
        }
    }

    private void callFirebaseFunction(String base64File, String mimeType, String title) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("base64File", base64File);
        data.put("mimeType", mimeType);
        data.put("title", title);
        data.put("uid", user.getUid());

        FirebaseFunctions.getInstance()
                .getHttpsCallable("generateFlashcards")
                .call(data)
                .addOnSuccessListener(result -> {
                    Toast.makeText(this, "Flashcards generated and saved!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Function error", e);
                    Toast.makeText(this, "Generation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
