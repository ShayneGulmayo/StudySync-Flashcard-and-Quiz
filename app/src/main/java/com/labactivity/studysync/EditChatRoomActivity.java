package com.labactivity.studysync;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class EditChatRoomActivity extends AppCompatActivity {

    private ImageView backButton, chatroomPhoto;
    private TextView chatroomNameTextView;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private String roomId;
    private ChatRoom currentRoom;
    private List<String> admins;
    private String ownerId;
    private String previousFilePath;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri sourceUri = result.getData().getData();
                    Uri destinationUri = Uri.fromFile(new File(getCacheDir(), UUID.randomUUID().toString() + ".jpg"));
                    UCrop.of(sourceUri, destinationUri)
                            .withAspectRatio(1, 1)
                            .withMaxResultSize(800, 800)
                            .start(this);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_chat_room);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        backButton = findViewById(R.id.back_button);
        chatroomPhoto = findViewById(R.id.chatroom_photo);
        chatroomNameTextView = findViewById(R.id.chatroom_name);

        roomId = getIntent().getStringExtra("roomId");

        loadChatRoom();

        backButton.setOnClickListener(v -> onBackPressed());
        chatroomPhoto.setOnClickListener(v -> openImagePicker());
        findViewById(R.id.delete_chatroom_btn).setOnClickListener(v -> attemptDeleteChatRoom());
        findViewById(R.id.leave_chatroom_btn).setOnClickListener(v -> attemptLeaveChatRoom());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadChatRoom() {
        db.collection("chat_rooms").document(roomId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        currentRoom = snapshot.toObject(ChatRoom.class);
                        if (currentRoom != null) {
                            currentRoom.setId(snapshot.getId());
                            ownerId = snapshot.getString("ownerId");
                            admins = (List<String>) snapshot.get("admins");
                            chatroomNameTextView.setText(currentRoom.getChatRoomName());
                            previousFilePath = currentRoom.getPhotoPath();

                            Glide.with(this)
                                    .load(currentRoom.getPhotoUrl())
                                    .placeholder(R.drawable.user_profile)
                                    .error(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(chatroomPhoto);
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                File file = new File(resultUri.getPath());
                String filename = "chat-room-profile/" + UUID.randomUUID().toString() + ".jpg";

                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Uploading...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                if (previousFilePath != null && !previousFilePath.isEmpty()) {
                    SupabaseUploader.deleteFile("chat-room-photos", previousFilePath, (deleted, msg, ignored) -> {
                        if (deleted) {
                            System.out.println("Old photo deleted.");
                        }
                    });
                }

                SupabaseUploader.uploadFile(file, "chat-room-photos", filename, (success, message, publicUrl) -> {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (success) {
                            updateChatRoomPhoto(publicUrl, filename);
                        } else {
                            Toast.makeText(this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Toast.makeText(this, "Crop error", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChatRoomPhoto(String newUrl, String path) {
        db.collection("chat_rooms").document(roomId)
                .update("photoUrl", newUrl, "photoPath", path)
                .addOnSuccessListener(unused -> {
                    Glide.with(this).load(newUrl).into(chatroomPhoto);
                    previousFilePath = path;
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
    }

    private void attemptDeleteChatRoom() {
        if (currentRoom == null || currentUser == null) return;

        String uid = currentUser.getUid();
        boolean isOwner = uid.equals(ownerId);
        boolean isAdmin = admins != null && admins.contains(uid);

        if (!isOwner && !isAdmin) {
            Toast.makeText(this, "Only the owner or an admin can delete this chat room.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Chat Room")
                .setMessage("Are you sure you want to permanently delete this chat room?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("chat_rooms").document(roomId).delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Chat room deleted.", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void attemptLeaveChatRoom() {
        if (currentRoom == null || currentUser == null) return;

        String uid = currentUser.getUid();
        List<String> members = currentRoom.getMembers();
        if (!members.contains(uid)) return;

        boolean isOwner = uid.equals(ownerId);

        if (isOwner) {
            String newOwnerId = null;

            if (admins != null && !admins.isEmpty()) {
                for (String adminId : admins) {
                    if (!adminId.equals(uid)) {
                        newOwnerId = adminId;
                        break;
                    }
                }
            }

            if (newOwnerId == null) {
                for (String memberId : members) {
                    if (!memberId.equals(uid)) {
                        newOwnerId = memberId;
                        break;
                    }
                }
            }

            if (newOwnerId != null) {
                db.collection("chat_rooms").document(roomId)
                        .update("ownerId", newOwnerId,
                                "members", FieldValue.arrayRemove(uid))
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "You left the chat room. Ownership transferred.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
            } else {
                Toast.makeText(this, "You're the last member. Please delete the chat room instead.", Toast.LENGTH_SHORT).show();
            }
        } else {
            db.collection("chat_rooms").document(roomId)
                    .update("members", FieldValue.arrayRemove(uid))
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Left chat room.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }
    }
}
