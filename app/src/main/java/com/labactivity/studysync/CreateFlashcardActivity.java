package com.labactivity.studysync;

import android.annotation.SuppressLint;
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
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
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
    private View currentFlashcardView;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private String setId = null;
    private TextView roleTxt, privacyTxt;
    private ImageView privacyIcon;
    private boolean isPublic = true;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_flashcard);

        setNameEditText = findViewById(R.id.set_name);
        flashcardContainer = findViewById(R.id.flashcard_container);
        roleTxt = findViewById(R.id.role_text);
        privacyIcon = findViewById(R.id.icon_privacy);
        privacyTxt = findViewById(R.id.privacy_text);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        findViewById(R.id.back_button).setOnClickListener(v -> onBackPressed());
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

        roleTxt.setOnClickListener(v -> {
            if (isPublic) {
                androidx.appcompat.widget.PopupMenu roleMenu = new androidx.appcompat.widget.PopupMenu(this, roleTxt);
                roleMenu.getMenu().add("View");
                roleMenu.getMenu().add("Edit");

                roleMenu.setOnMenuItemClickListener(item -> {
                    roleTxt.setText(item.getTitle());
                    return true;
                });

                roleMenu.show();
            }
        });

        View.OnClickListener privacyMenuClickListener = v -> {
            androidx.appcompat.widget.PopupMenu privacyMenu = new androidx.appcompat.widget.PopupMenu(this, privacyTxt);

            if (isPublic) {
                privacyMenu.getMenu().add("Private");
            } else {
                privacyMenu.getMenu().add("Public");
            }

            privacyMenu.setOnMenuItemClickListener(item -> {
                String selectedPrivacy = item.getTitle().toString();

                if (selectedPrivacy.equals("Private")) {
                    isPublic = false;
                    privacyTxt.setText("Private");
                    roleTxt.setText("");
                    Glide.with(this).load(R.drawable.lock).into(privacyIcon);

                } else if (selectedPrivacy.equals("Public")) {
                    isPublic = true;
                    privacyTxt.setText("Public");
                    if (TextUtils.isEmpty(roleTxt.getText())) roleTxt.setText("View");
                    Glide.with(this).load(R.drawable.public_icon).into(privacyIcon);
                }

                return true;
            });
            privacyMenu.show();
        };

        privacyTxt.setOnClickListener(privacyMenuClickListener);
        privacyIcon.setOnClickListener(privacyMenuClickListener);
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

            if (!TextUtils.isEmpty(term) && !TextUtils.isEmpty(definition)) {
                Map<String, Object> termEntry = new HashMap<>();
                termEntry.put("term", term);
                termEntry.put("definition", definition);

                Object tag = imageView.getTag();
                if (tag instanceof Map) {
                    Map<String, String> imageData = (Map<String, String>) tag;
                    String photoUrl = imageData.get("photoUrl");
                    String photoPath = imageData.get("photoPath");
                    if (photoUrl != null) termEntry.put("photoUrl", photoUrl);
                    if (photoPath != null) termEntry.put("photoPath", photoPath);
                }

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
        flashcardSet.put("owner_uid", auth.getCurrentUser().getUid());
        flashcardSet.put("terms", termsMap);
        flashcardSet.put("createdAt", getCurrentFormattedDateTime());

        if (isPublic) {
            flashcardSet.put("privacy", "public");
            String role = roleTxt.getText().toString().trim();
            flashcardSet.put("privacyRole", TextUtils.isEmpty(role) ? "view" : role.toLowerCase());
        } else {
            flashcardSet.put("privacy", "private");
            flashcardSet.put("privacyRole", null);
        }

        if (setId != null) {
            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(existingDoc -> {
                        if (existingDoc.exists()) {
                            Map<String, Object> existingData = existingDoc.getData();
                            if (existingData != null && existingData.containsKey("accessUsers")) {
                                flashcardSet.put("accessUsers", existingData.get("accessUsers"));
                            }
                        }

                        db.collection("flashcards").document(setId)
                                .set(flashcardSet)
                                .addOnSuccessListener(doc -> {
                                    Toast.makeText(this, "Flashcard set updated", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update set", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to fetch existing set before update", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Map<String, Object> accessUsers = new HashMap<>();
            accessUsers.put(auth.getCurrentUser().getUid(), "Owner");
            flashcardSet.put("accessUsers", accessUsers);

            db.collection("flashcards")
                    .add(flashcardSet)
                    .addOnSuccessListener(doc -> {
                        String generatedSetId = doc.getId();
                        Map<String, Object> ownedSet = new HashMap<>();
                        ownedSet.put("id", generatedSetId);
                        ownedSet.put("type", "flashcard");

                        DocumentReference userRef = db.collection("users").document(auth.getCurrentUser().getUid());
                        userRef.update("owned_sets", com.google.firebase.firestore.FieldValue.arrayUnion(ownedSet))
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Flashcard set saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Map<String, Object> initData = new HashMap<>();
                                    initData.put("owned_sets", java.util.Collections.singletonList(ownedSet));
                                    userRef.set(initData, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(this, "Flashcard set saved", Toast.LENGTH_SHORT).show();
                                                finish();
                                            })
                                            .addOnFailureListener(innerErr -> {
                                                Toast.makeText(this, "Saved set but failed to update owned_sets", Toast.LENGTH_LONG).show();
                                                finish();
                                            });
                                });
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

                        Map<String, String> imageData = new HashMap<>();
                        imageData.put("photoUrl", publicUrl);
                        imageData.put("photoPath", filename);
                        imageView.setTag(imageData);

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

    @Override
    public void onBackPressed() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Leave without saving?")
                .setMessage("Are you sure you want to leave? Any unsaved changes will be lost.")
                .setPositiveButton("Yes", (dialog, which) -> {
                    super.onBackPressed();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }
}