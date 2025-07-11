package com.labactivity.studysync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GenerateSetActivity extends AppCompatActivity {

    private static final String TAG = "GenerateFlashcard";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private FirebaseAuth mAuth;
    private CardView selectFile, generateManually, selectImages, pasteText, scanDocument;
    private ImageView backBtn;
    private TextView txtTitle;
    private String setType;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 200 && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            Intent intent = new Intent(this, LoadingSetActivity.class);
            intent.setData(imageUri);
            intent.putExtra("type", "image");
            intent.putExtra("setType", setType);
            startActivity(intent);
        }
    }
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
        setContentView(R.layout.activity_generate_set);

        mAuth = FirebaseAuth.getInstance();
        selectFile = findViewById(R.id.selectFile);
        backBtn = findViewById(R.id.back_button);
        generateManually = findViewById(R.id.generateManually);
        selectImages = findViewById(R.id.selectImages);
        pasteText = findViewById(R.id.pasteText);
        scanDocument = findViewById(R.id.scanDocument);
        setType = getIntent().getStringExtra("setType");
        txtTitle = findViewById(R.id.txtView_title);

        if ("flashcard".equalsIgnoreCase(setType)) {
            txtTitle.setText("Create a Flashcard Set");
        } else if ("quiz".equalsIgnoreCase(setType)) {
            txtTitle.setText("Create a Quiz Set");
        } else {
            txtTitle.setText("Create a Set");
        }

        checkUserAuthentication();

        pasteText.setOnClickListener(view -> {
            Intent intent = new Intent(this, InputPromptActivity.class);
            intent.putExtra("setType", setType);
            startActivity(intent);
            finish();
        });
        backBtn.setOnClickListener(v -> finish());
        generateManually.setOnClickListener(view -> {
            if ("flashcard".equalsIgnoreCase(setType)){
                Intent intent = new Intent(this, CreateFlashcardActivity.class);
                startActivity(intent);
                finish();
            } else if ("quiz".equalsIgnoreCase(setType)) {
                Intent intent = new Intent(this, CreateQuizActivity.class);
                startActivity(intent);
                finish();
            }else{
                Toast.makeText(this, "No Set Type Entered", Toast.LENGTH_SHORT).show();
            }
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
        selectImages.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select an Image"), 200);  // custom request code
        });

        scanDocument.setOnClickListener(view -> {
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
        intent.setData(uri);
        intent.putExtra("type", "pdf");
        intent.putExtra("setType", setType);
        startActivity(intent);
    }


}
