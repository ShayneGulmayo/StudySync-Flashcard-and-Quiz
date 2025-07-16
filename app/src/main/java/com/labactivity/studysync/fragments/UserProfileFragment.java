package com.labactivity.studysync.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.LoginActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.UserSettingsActivity;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.*;

public class UserProfileFragment extends Fragment {

    private TextView userFullName, usernameTxt;
    private MaterialButton settingsBtn, logoutBtn;
    private ImageView profileImage;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ActivityResultLauncher<Intent> galleryLauncher;

    private String previousFileName;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String BUCKET_NAME = "user-files";
    private static final String FOLDER = "profile-photos";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        userFullName = view.findViewById(R.id.userFullName);
        usernameTxt = view.findViewById(R.id.usernameTxt);
        profileImage = view.findViewById(R.id.profileImage);
        settingsBtn = view.findViewById(R.id.settingsBtn);
        logoutBtn = view.findViewById(R.id.logoutBtn);

        loadUserData();

        profileImage.setOnClickListener(v -> openGallery());

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), UserSettingsActivity.class)));

        logoutBtn.setOnClickListener(v -> {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users")
                    .document(currentUserId)
                    .update("fcmToken", null)
                    .addOnSuccessListener(aVoid -> {
                        FirebaseAuth.getInstance().signOut();

                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    });
        });


        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri sourceUri = result.getData().getData();
                        Uri destUri = Uri.fromFile(new File(requireContext().getCacheDir(),
                                UUID.randomUUID().toString() + ".jpg"));
                        UCrop.of(sourceUri, destUri)
                                .withAspectRatio(1, 1)
                                .withMaxResultSize(512, 512)
                                .start(requireContext(), this);
                    }
                });

        return view;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void loadUserData() {
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String username = document.getString("username");
                        String photoUrl = document.getString("photoUrl");
                        previousFileName = document.getString("photoFileName");

                        String fullName = (firstName != null ? firstName : "") +
                                (lastName != null ? " " + lastName : "");
                        userFullName.setText(fullName.trim().isEmpty() ? "Full Name" : fullName.trim());
                        usernameTxt.setText(username != null ? "@" + username : "@username");

                        if (isAdded() && getContext() != null) {
                            Glide.with(requireContext())
                                    .load(photoUrl != null ? photoUrl : R.drawable.profile_image_background)
                                    .placeholder(R.drawable.profile_image_background)
                                    .error(R.drawable.profile_image_background)
                                    .circleCrop()
                                    .into(profileImage);
                        }

                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to fetch user data", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null && currentUser != null) {
                File imageFile = new File(resultUri.getPath());

                if (imageFile.length() > MAX_FILE_SIZE) {
                    Toast.makeText(getContext(), "File too large. Max is 5MB.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String mimeType = getMimeType(imageFile);
                if (mimeType == null) {
                    Toast.makeText(getContext(), "Unsupported file type", Toast.LENGTH_SHORT).show();
                    return;
                }

                String fileName = "profile_" + currentUser.getUid() + "_" + System.currentTimeMillis() + ".jpg";
                String fullPath = FOLDER + "/" + fileName;

                if (previousFileName != null && !previousFileName.isEmpty()) {
                    SupabaseUploader.deleteFile(BUCKET_NAME, FOLDER + "/" + previousFileName, (success, msg, ignored) -> {
                        if (success) {
                            System.out.println("Previous image deleted.");
                        } else {
                            System.out.println("Failed to delete previous image: " + msg);
                        }
                    });
                }

                // Upload new image
                SupabaseUploader.uploadFile(imageFile, BUCKET_NAME, fullPath, (success, message, publicUrl) -> {
                    if (success && publicUrl != null) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("photoUrl", publicUrl);
                        updates.put("photoFileName", fileName);

                        db.collection("users").document(currentUser.getUid())
                                .update(updates)
                                .addOnSuccessListener(unused -> requireActivity().runOnUiThread(() -> {
                                    if (isAdded() && getContext() != null) {
                                        Glide.with(requireContext())
                                                .load(publicUrl)
                                                .placeholder(R.drawable.user_profile)
                                                .error(R.drawable.user_profile)
                                                .circleCrop()
                                                .into(profileImage);
                                    }
                                    Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
                                    previousFileName = fileName;
                                }));
                    } else {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Upload failed: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Throwable cropError = UCrop.getError(data);
            Toast.makeText(getContext(), "Crop error: " + (cropError != null ? cropError.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return null;
    }
}
