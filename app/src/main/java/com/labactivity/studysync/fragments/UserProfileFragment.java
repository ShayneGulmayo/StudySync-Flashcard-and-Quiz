package com.labactivity.studysync.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.firebase.storage.FirebaseStorage;
import com.labactivity.studysync.DownloadedSetsActivity;
import com.labactivity.studysync.LoginActivity;
import com.labactivity.studysync.NotificationsActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.UserSettingsActivity;
import com.labactivity.studysync.helpers.AlarmHelper;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.*;

public class UserProfileFragment extends Fragment {

    private TextView userFullName, usernameTxt;
    private MaterialButton settingsBtn, logoutBtn, downloadedSetBtn, notifBtn;
    private ImageView profileImage;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ActivityResultLauncher<Intent> galleryLauncher;

    private String previousFileName, userId;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String BUCKET_NAME = "user-files";
    private static final String FOLDER = "profile-photos";

    @SuppressLint("MissingInflatedId")
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
        downloadedSetBtn = view.findViewById(R.id.downloadedSetBtn);
        notifBtn = view.findViewById(R.id.notifBtn);
        userId = currentUser.getUid();


        loadUserData();

        notifBtn.setOnClickListener(view1 ->
                startActivity(new Intent(getActivity(), NotificationsActivity.class)));

        profileImage.setOnClickListener(v -> openGallery());

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), UserSettingsActivity.class)));

        downloadedSetBtn.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), DownloadedSetsActivity.class)));

        logoutBtn.setOnClickListener(v -> {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            AlarmHelper.cancelAllReminders(requireContext(), currentUserId);
            clearReminderPrefs(requireContext(), userId);

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
    private void clearReminderPrefs(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(userId + "_")) {
                editor.remove(key);
            }
        }
        editor.apply();
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

                String extension = MimeTypeMap.getFileExtensionFromUrl(resultUri.toString());
                if (extension == null) extension = "jpg";

                String fileName = "profile_" + System.currentTimeMillis() + "." + extension;
                String storagePath = "user-profile/" + currentUser.getUid() + "/" + fileName;

                if (previousFileName != null && !previousFileName.isEmpty()) {
                    String oldPath = "user-profile/" + currentUser.getUid() + "/" + previousFileName;
                    FirebaseStorage.getInstance().getReference(oldPath).delete()
                            .addOnSuccessListener(aVoid -> System.out.println("Previous image deleted."))
                            .addOnFailureListener(e -> System.out.println("Failed to delete previous image: " + e.getMessage()));
                }

                FirebaseStorage.getInstance().getReference(storagePath)
                        .putFile(resultUri)
                        .addOnSuccessListener(taskSnapshot -> {
                            taskSnapshot.getStorage().getDownloadUrl()
                                    .addOnSuccessListener(uri -> {
                                        String downloadUrl = uri.toString();

                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("photoUrl", downloadUrl);
                                        updates.put("photoFileName", fileName);

                                        db.collection("users").document(currentUser.getUid())
                                                .update(updates)
                                                .addOnSuccessListener(unused -> requireActivity().runOnUiThread(() -> {
                                                    if (isAdded() && getContext() != null) {
                                                        Glide.with(requireContext())
                                                                .load(downloadUrl)
                                                                .placeholder(R.drawable.user_profile)
                                                                .error(R.drawable.user_profile)
                                                                .circleCrop()
                                                                .into(profileImage);
                                                    }

                                                    Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
                                                    previousFileName = fileName;
                                                }))
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(getContext(), "Failed to update Firestore", Toast.LENGTH_SHORT).show()
                                                );
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(getContext(), "Failed to get image URL", Toast.LENGTH_SHORT).show()
                                    );
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Throwable cropError = UCrop.getError(data);
            Toast.makeText(getContext(), "Crop error: " + (cropError != null ? cropError.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
        }
    }
}
