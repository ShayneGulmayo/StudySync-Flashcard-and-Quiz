package com.labactivity.studysync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GenerateFlashcardActivity extends AppCompatActivity {

    private static final String TAG = "GenerateFlashcard";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private final Executor executor = Executors.newSingleThreadExecutor();

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
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select a PDF File"));
    }

    private void readFileContentAndSendToFunction(Uri uri, String mimeType) {
        if (!"application/pdf".equals(mimeType)) {
            Toast.makeText(this, "Only PDF files are supported", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, LoadingSetActivity.class);
        intent.setData(uri); // pass URI
        startActivity(intent);
    }

}
