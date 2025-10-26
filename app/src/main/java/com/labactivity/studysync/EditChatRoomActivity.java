package com.labactivity.studysync;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.models.ChatRoom;
import com.labactivity.studysync.models.User;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EditChatRoomActivity extends AppCompatActivity {

    private ImageView backButton, chatroomPhoto, moreBtn;
    private TextView chatroomNameTextView;
    private Switch notifToggle;

    private MaterialButton sharedSets, sharedMedia, deleteChatRoom, liveQuizBtn;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();

    private String roomId;
    private ChatRoom currentRoom;
    private List<String> admins;
    private String ownerId;
    private String previousFilePath;

    private User currentUserModel;

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

        FirebaseAuth auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = auth.getCurrentUser().getUid();
        backButton = findViewById(R.id.back_button);
        chatroomPhoto = findViewById(R.id.chatroom_photo);
        chatroomNameTextView = findViewById(R.id.chatroom_name);
        moreBtn = findViewById(R.id.more_button);
        notifToggle = findViewById(R.id.notif_btn);
        final boolean[] isUserChangingToggle = {false};

        sharedSets = findViewById(R.id.shared_sets_btn);
        deleteChatRoom = findViewById(R.id.delete_chatroom_btn);
        sharedMedia = findViewById(R.id.media_files_btn);
        liveQuizBtn = findViewById(R.id.live_quiz_btn);

        roomId = getIntent().getStringExtra("roomId");

        DocumentReference userRef = db.collection("users").document(userId);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> chatPrefs = (Map<String, Object>) documentSnapshot.get("chatNotificationPrefs");
                if (chatPrefs != null && chatPrefs.containsKey(roomId)) {
                    boolean isEnabled = Boolean.TRUE.equals(chatPrefs.get(roomId));
                    notifToggle.setChecked(isEnabled);
                } else {
                    userRef.update("chatNotificationPrefs." + roomId, true);
                }
            }
        });

        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(snapshot -> {
                currentUserModel = snapshot.toObject(User.class);
            });
        }

        loadChatRoom();

        backButton.setOnClickListener(v -> onBackPressed());
        chatroomPhoto.setOnClickListener(v -> openImagePicker());
        moreBtn.setOnClickListener(this::showPopupMenu);

        deleteChatRoom.setOnClickListener(v -> attemptDeleteChatRoom());
        findViewById(R.id.leave_chatroom_btn).setOnClickListener(v -> attemptLeaveChatRoom());
        findViewById(R.id.see_members_btn).setOnClickListener(v -> {
            Intent intent = new Intent(EditChatRoomActivity.this, SeeMembersActivity.class);
            intent.putExtra("chatRoomId", roomId);
            startActivity(intent);
        });
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> chatPrefs = (Map<String, Object>) documentSnapshot.get("chatNotificationPrefs");
                boolean isEnabled = false;

                if (chatPrefs != null && chatPrefs.containsKey(roomId)) {
                    isEnabled = Boolean.TRUE.equals(chatPrefs.get(roomId));
                } else {
                    userRef.update("chatNotificationPrefs." + roomId, true);
                    isEnabled = true;
                }

                isUserChangingToggle[0] = false;
                notifToggle.setChecked(isEnabled);
                notifToggle.jumpDrawablesToCurrentState();
                isUserChangingToggle[0] = true;
            }
        });
        notifToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUserChangingToggle[0]) return;

            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Map<String, Object> chatPrefs = (Map<String, Object>) documentSnapshot.get("chatNotificationPrefs");
                    if (chatPrefs == null) chatPrefs = new HashMap<>();

                    chatPrefs.put(roomId, isChecked);

                    userRef.update("chatNotificationPrefs", chatPrefs)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(EditChatRoomActivity.this,
                                        "Notifications " + (isChecked ? "enabled" : "disabled"),
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(EditChatRoomActivity.this,
                                        "Failed to update preferences",
                                        Toast.LENGTH_SHORT).show();
                                notifToggle.setChecked(!isChecked);
                            });
                }
            });
        });

        sharedSets.setOnClickListener(view ->{
            Intent intent = new Intent(EditChatRoomActivity.this, ChatRoomSharedSetsActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
                });

        sharedMedia.setOnClickListener(view -> {
            Intent intent = new Intent(EditChatRoomActivity.this, ChatRoomSharedMediaActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });
        liveQuizBtn.setOnClickListener(view -> {
            Intent intent = new Intent(EditChatRoomActivity.this, LiveQuizActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.chat_room_settings_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.editChatPhoto) {
                openImagePicker();
                return true;
            } else if (id == R.id.editChatName) {
                showEditNameDialog();
                return true;
            } else if (id == R.id.leaveChat) {
                attemptLeaveChatRoom();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditNameDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_chat_name, null);
        EditText editChatnameInput = dialogView.findViewById(R.id.editChatNameInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit Chat Room Name")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = editChatnameInput.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        db.collection("chat_rooms").document(roomId)
                                .update("chatRoomName", newName)
                                .addOnSuccessListener(unused -> {
                                    chatroomNameTextView.setText(newName);
                                    sendSystemMessage(currentUserModel.getFirstName() + " changed the chat room name to \"" + newName + "\"");
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

                            String uid = currentUser.getUid();
                            boolean isOwner = uid.equals(ownerId);
                            boolean isAdmin = admins != null && admins.contains(uid);

                            if (!isOwner && !isAdmin) {
                                deleteChatRoom.setVisibility(View.GONE);
                            } else {
                                deleteChatRoom.setVisibility(View.VISIBLE);
                            }
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
                String uniqueFilename = UUID.randomUUID().toString() + ".jpg";
                String filePath = "chat-room-profile/" + roomId + "/" + uniqueFilename;

                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Uploading...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                if (previousFilePath != null && !previousFilePath.isEmpty()) {
                    firebaseStorage.getReference().child(previousFilePath).delete()
                            .addOnFailureListener(e -> Log.w("ChatProfile", "Previous file deletion failed", e));
                }

                StorageReference storageRef = firebaseStorage.getReference().child(filePath);
                UploadTask uploadTask = storageRef.putFile(resultUri);

                uploadTask.continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return storageRef.getDownloadUrl();
                }).addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        updateChatRoomPhoto(downloadUri.toString(), filePath);
                        sendSystemMessage(currentUserModel.getFullName() + " updated the chat room photo.");
                    } else {
                        Toast.makeText(this, "Upload failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Throwable cropError = UCrop.getError(data);
            Toast.makeText(this, "Crop error: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChatRoomPhoto(String newUrl, String path) {
        db.collection("chat_rooms").document(roomId)
                .update("photoUrl", newUrl, "photoPath", path, "type", "system")
                .addOnSuccessListener(unused -> {
                    Glide.with(this)
                            .load(newUrl)
                            .placeholder(R.drawable.user_profile)
                            .error(R.drawable.user_profile)
                            .circleCrop()
                            .into(chatroomPhoto);
                    previousFilePath = path;
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
    }

    private void sendSystemMessage(String messageText) {
        if (currentUser == null || currentUserModel == null) return;
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setSenderId(currentUser.getUid());
        systemMessage.setSenderName("System");
        systemMessage.setType("system");
        systemMessage.setText(messageText);
        systemMessage.setTimestamp(new Date());

        db.collection("chat_rooms")
                .document(roomId)
                .collection("messages")
                .add(systemMessage);
    }

    private void attemptDeleteChatRoom() {
        if (currentRoom == null || currentUser == null) return;


        new AlertDialog.Builder(this)
                .setTitle("Delete Chat Room")
                .setMessage("Are you sure you want to permanently delete this chat room?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Deleting chat room...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    deleteChatRoom(progressDialog);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteChatRoom(ProgressDialog progressDialog) {
        db.collection("chat_rooms").document(roomId).collection("messages")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : querySnapshot) {
                            batch.delete(doc.getReference());
                        }

                        batch.commit()
                                .addOnSuccessListener(unused -> proceedToDeleteRoomDoc(progressDialog))
                                .addOnFailureListener(e -> {
                                    progressDialog.dismiss();
                                    Toast.makeText(this, "Failed to delete messages.", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        proceedToDeleteRoomDoc(progressDialog);
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to fetch messages.", Toast.LENGTH_SHORT).show();
                });
    }
    private void deleteFolderContents(String folderPath) {
        StorageReference folderRef = firebaseStorage.getReference().child(folderPath);
        folderRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference item : listResult.getItems()) {
                        item.delete().addOnFailureListener(e -> {
                        });
                    }
                });
    }

    private void proceedToDeleteRoomDoc(ProgressDialog progressDialog) {
        if (previousFilePath != null && !previousFilePath.isEmpty()) {
            firebaseStorage.getReference().child(previousFilePath).delete()
                    .addOnFailureListener(e -> Toast.makeText(this, "Warning: Failed to delete group photo", Toast.LENGTH_SHORT).show());
        }

        deleteFolderContents("chat-room-files/" + roomId);
        deleteFolderContents("chat_room_images/" + roomId);
        deleteFolderContents("chat_room_videos/" + roomId);
        deleteFolderContents("chat-room-profile/" + roomId);

        db.collection("chat_rooms").document(roomId)
                .delete()
                .addOnSuccessListener(unused -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Chat room permanently deleted.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to delete chat room document.", Toast.LENGTH_SHORT).show();
                });
    }

    private void attemptLeaveChatRoom() {
        if (currentRoom == null || currentUser == null || currentUserModel == null) return;

        String uid = currentUser.getUid();
        List<String> members = currentRoom.getMembers();
        if (!members.contains(uid)) return;

        boolean isOwner = uid.equals(ownerId);
        String displayName = currentUserModel.getFirstName();

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
                        .update(
                                "ownerId", newOwnerId,
                                "members", FieldValue.arrayRemove(uid),
                                "admins", FieldValue.arrayRemove(uid),
                                "type", "system"
                        )
                        .addOnSuccessListener(unused -> {
                            sendSystemMessage(displayName + " left the chat room. Ownership transferred.");
                            Toast.makeText(this, "You left the chat room. Ownership transferred.", Toast.LENGTH_SHORT).show();
                            goToChatFragment();
                        });
            } else {
                Toast.makeText(this, "You're the last member. Please delete the chat room instead.", Toast.LENGTH_SHORT).show();
            }
        } else {
            db.collection("chat_rooms").document(roomId)
                    .update(
                            "members", FieldValue.arrayRemove(uid),
                            "admins", FieldValue.arrayRemove(uid),
                            "type", "system"
                    )
                    .addOnSuccessListener(unused -> {
                        sendSystemMessage(displayName + " left the chat room.");
                        Toast.makeText(this, "Left chat room.", Toast.LENGTH_SHORT).show();
                        goToChatFragment();
                    });
        }
    }

    private void goToChatFragment() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigateTo", "chat");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}