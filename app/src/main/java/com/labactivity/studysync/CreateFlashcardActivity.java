package com.labactivity.studysync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.yalantis.ucrop.UCrop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateFlashcardActivity extends AppCompatActivity {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private EditText setNameEditText;
    private LinearLayout flashcardContainer;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String username;
    private View currentFlashcardView;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private String setId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_flashcard);

        setNameEditText = findViewById(R.id.set_name);
        flashcardContainer = findViewById(R.id.flashcard_container);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.save_button).setOnClickListener(v -> fetchUsernameAndSaveFlashcardSet());
        findViewById(R.id.floating_add_btn).setOnClickListener(v -> addFlashcardView());

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri sourceUri = result.getData().getData();
                        File file = new File(getCacheDir(), UUID.randomUUID().toString() + ".jpg");
                        Uri destUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

                        UCrop.of(sourceUri, destUri)
                                .withAspectRatio(1, 1)
                                .withMaxResultSize(512, 512)
                                .start(this);
                    }
                }
        );

        setId = getIntent().getStringExtra("setId");
        if (setId != null) {
            loadFlashcardSetForEdit(setId);
        } else {
            addFlashcardView();
            addFlashcardView();
        }
    }

    private void fetchUsernameAndSaveFlashcardSet() {
        db.collection("users").document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String latestUsername = doc.getString("username");
                        saveFlashcardSet(latestUsername);
                    } else {
                        Toast.makeText(this, "Failed to fetch username.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching username.", Toast.LENGTH_SHORT).show();
                });
    }



    private void addFlashcardView() {
        View flashcardView = getLayoutInflater().inflate(R.layout.item_flashcard_input, null);
        ImageButton deleteButton = flashcardView.findViewById(R.id.delete_btn);
        ImageView imageButton = flashcardView.findViewById(R.id.upload_image_button);

        deleteButton.setOnClickListener(v -> {
            if (flashcardContainer.getChildCount() > 2) {
                flashcardContainer.removeView(flashcardView);
            } else {
                Toast.makeText(this, "At least 2 flashcards required", Toast.LENGTH_SHORT).show();
            }
        });

        imageButton.setOnClickListener(v -> {
            currentFlashcardView = flashcardView;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            pickImageLauncher.launch(Intent.createChooser(intent, "Select Image"));
        });

        flashcardContainer.addView(flashcardView);
    }

    private void loadFlashcardSetForEdit(String setId) {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        setNameEditText.setText(doc.getString("title"));
                        Map<String, Object> terms = (Map<String, Object>) doc.get("terms");

                        flashcardContainer.removeAllViews();

                        for (Map.Entry<String, Object> entry : terms.entrySet()) {
                            Map<String, Object> termData = (Map<String, Object>) entry.getValue();
                            View flashcardView = getLayoutInflater().inflate(R.layout.item_flashcard_input, null);

                            EditText termEditText = flashcardView.findViewById(R.id.flashcard_term);
                            EditText definitionEditText = flashcardView.findViewById(R.id.flashcard_definition);
                            ImageView imageButton = flashcardView.findViewById(R.id.upload_image_button);

                            termEditText.setText((String) termData.get("term"));
                            definitionEditText.setText((String) termData.get("definition"));

                            String photoUrl = (String) termData.get("photoUrl");
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(this).load(photoUrl).into(imageButton);
                                imageButton.setTag(photoUrl);
                            }

                            ImageButton deleteButton = flashcardView.findViewById(R.id.delete_btn);
                            deleteButton.setOnClickListener(v -> {
                                if (flashcardContainer.getChildCount() > 2) {
                                    flashcardContainer.removeView(flashcardView);
                                } else {
                                    Toast.makeText(this, "At least 2 flashcards required", Toast.LENGTH_SHORT).show();
                                }
                            });

                            imageButton.setOnClickListener(v -> {
                                currentFlashcardView = flashcardView;
                                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.setType("image/*");
                                pickImageLauncher.launch(Intent.createChooser(intent, "Select Image"));
                            });

                            flashcardContainer.addView(flashcardView);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load flashcard set", Toast.LENGTH_SHORT).show());
    }

    private void saveFlashcardSet(String username) {
        String setName = setNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(setName)) {
            Toast.makeText(this, "Set name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        int itemCount = 0;
        Map<String, Object> termsMap = new HashMap<>();

        for (int i = 0; i < flashcardContainer.getChildCount(); i++) {
            View cardView = flashcardContainer.getChildAt(i);
            EditText termEditText = cardView.findViewById(R.id.flashcard_term);
            EditText definitionEditText = cardView.findViewById(R.id.flashcard_definition);
            ImageView imageView = cardView.findViewById(R.id.upload_image_button);

            String term = termEditText.getText().toString().trim();
            String definition = definitionEditText.getText().toString().trim();
            String imageUrl = imageView.getTag() != null ? imageView.getTag().toString() : null;

            if (!TextUtils.isEmpty(term) && !TextUtils.isEmpty(definition)) {
                Map<String, Object> termEntry = new HashMap<>();
                termEntry.put("term", term);
                termEntry.put("definition", definition);
                if (imageUrl != null) termEntry.put("photoUrl", imageUrl);
                termsMap.put(String.valueOf(itemCount), termEntry);
                itemCount++;
            }
        }

        if (itemCount < 2) {
            Toast.makeText(this, "At least 2 flashcards required", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> flashcardSet = new HashMap<>();
        flashcardSet.put("title", setName);
        flashcardSet.put("number_of_items", itemCount);
        flashcardSet.put("owner_username", username); // <-- uses latest fetched username
        flashcardSet.put("owner_uid", auth.getCurrentUser().getUid());
        flashcardSet.put("terms", termsMap);
        flashcardSet.put("createdAt", getCurrentFormattedDateTime());

        if (setId != null) {
            // UPDATE existing
            db.collection("flashcards").document(setId)
                    .set(flashcardSet)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, "Flashcard set updated", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update set", Toast.LENGTH_SHORT).show());
        } else {
            // CREATE new
            db.collection("flashcards")
                    .add(flashcardSet)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, "Flashcard set saved", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save set", Toast.LENGTH_SHORT).show());
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null && currentFlashcardView != null) {
                uploadImageAndAttachToFlashcard(resultUri, currentFlashcardView);
            }
        }
    }

    private void uploadImageAndAttachToFlashcard(Uri imageUri, View flashcardView) {
        try {
            File file = getFileFromUri(this, imageUri);
            if (file.length() > MAX_FILE_SIZE) {
                Toast.makeText(this, "Image too large (max 5MB)", Toast.LENGTH_SHORT).show();
                return;
            }

            String mimeType = getMimeType(Uri.fromFile(file));
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext == null) ext = "jpg";

            String filename = "flashcard_" + UUID.randomUUID() + "." + ext;
            String bucket = "flashcard-images";

            SupabaseUploader.uploadFile(file, bucket, filename, (success, message, publicUrl) -> {
                runOnUiThread(() -> {
                    if (success) {
                        ImageView imageView = flashcardView.findViewById(R.id.upload_image_button);
                        Glide.with(this).load(publicUrl).into(imageView);
                        imageView.setTag(publicUrl);
                        Toast.makeText(this, "Image uploaded", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            });

        } catch (Exception e) {
            Toast.makeText(this, "Image error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static File getFileFromUri(Context context, Uri uri) throws Exception {
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

    private String getMimeType(Uri uri) {
        return getContentResolver().getType(uri);
    }

    private String getCurrentFormattedDateTime() {
        return new SimpleDateFormat("MM/dd/yyyy | hh:mm a").format(new Date());
    }
}

