package com.labactivity.studysync;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
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
        profileImage = view.findViewById(R.id.profileImage); // Make sure ID is correct
        settingsBtn = view.findViewById(R.id.settingsBtn);
        logoutBtn = view.findViewById(R.id.logoutBtn);

        loadUserData();

        profileImage.setOnClickListener(v -> openGallery());

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), UserSettingsActivity.class)));

        logoutBtn.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
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

                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.profile_image_background)  // shown while loading
                                    .error(R.drawable.profile_image_background)        // shown if loading fails
                                    .circleCrop()
                                    .into(profileImage);
                        } else {
                            // fallback to default if no photoUrl
                            Glide.with(this)
                                    .load(R.drawable.profile_image_background)
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
                String newFileName = "profile_" + currentUser.getUid() + "_" + System.currentTimeMillis() + ".jpg";

                // Delete previous file in Supabase if exists
                if (previousFileName != null && !previousFileName.isEmpty()) {
                    SupabaseUploader.deleteFile(previousFileName, (deleted, msg) ->
                            System.out.println("Previous profile image deleted: " + msg));
                }

                SupabaseUploader.uploadFile(imageFile, newFileName, (success, message) -> {
                    if (success) {
                        String uploadedUrl = "https://agnosyltikewhdzmdcwp.supabase.co/storage/v1/object/public/user-files/" + newFileName;

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("photoUrl", uploadedUrl);
                        updates.put("photoFileName", newFileName);

                        db.collection("users").document(currentUser.getUid())
                                .update(updates)
                                .addOnSuccessListener(unused -> requireActivity().runOnUiThread(() -> {
                                    Glide.with(this).load(uploadedUrl)
                                            .placeholder(R.drawable.user_profile)
                                            .error(R.drawable.user_profile)
                                            .circleCrop()
                                            .into(profileImage);
                                    Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
                                    previousFileName = newFileName; // Update for future deletion
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
}
