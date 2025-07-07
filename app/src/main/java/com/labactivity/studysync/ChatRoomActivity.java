package com.labactivity.studysync;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.labactivity.studysync.adapters.ChatMessageAdapter;
import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.yalantis.ucrop.util.FileUtils;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoomActivity extends AppCompatActivity {

    private String roomId;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private EditText messageEditText;
    private RecyclerView recyclerView;
    private ChatMessageAdapter adapter;
    private CollectionReference messagesRef;
    private List<String> memberUids;
    private TextView chatRoomNameText;
    private ImageView chatRoomPhoto, sendImg, sendFlashcardsandQuiz;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    if (result.getData().getClipData() != null) {
                        int count = result.getData().getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri imageUri = result.getData().getClipData().getItemAt(i).getUri();
                            uploadImageAndSendMessage(imageUri);
                        }
                    } else if (result.getData().getData() != null) {
                        Uri imageUri = result.getData().getData();
                        uploadImageAndSendMessage(imageUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null) {
                        uploadFileAndSendMessage(fileUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        roomId = getIntent().getStringExtra("roomId");
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        messageEditText = findViewById(R.id.messageEditText);
        recyclerView = findViewById(R.id.recyclerView);
        chatRoomNameText = findViewById(R.id.txtChatRoomName);
        chatRoomPhoto = findViewById(R.id.chatroom_photo);
        sendImg = findViewById(R.id.sendImg);
        sendFlashcardsandQuiz = findViewById(R.id.sendFlashcardsandQuiz);

        ImageButton sendButton = findViewById(R.id.sendButton);
        ImageView backBtn = findViewById(R.id.back_button);
        ImageView moreBtn = findViewById(R.id.chatRoomSettings);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        messagesRef = db.collection("chat_rooms").document(roomId).collection("messages");

        fetchChatRoomDetails(() -> {
            if (!memberUids.contains(currentUser.getUid())) {
                Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            setUpRecyclerView();
        });

        sendButton.setOnClickListener(v -> sendMessage());
        sendImg.setOnClickListener(v -> openImagePicker());
        sendFlashcardsandQuiz.setOnClickListener(v -> showSendMorePopup());
        backBtn.setOnClickListener(v -> finish());
        moreBtn.setOnClickListener(v -> showPopupMenu(moreBtn));

        chatRoomPhoto.setOnClickListener(v -> openEditChatRoom());
        chatRoomNameText.setOnClickListener(v -> openEditChatRoom());
    }

    private void updateChatRoomLastMessage(String message, String type, String senderName) {
        Map<String, Object> update = new HashMap<>();
        update.put("lastMessage", message);
        update.put("type", type);
        update.put("lastMessageSender", senderName);
        db.collection("chat_rooms").document(roomId).set(update, SetOptions.merge());
    }

    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) return;

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {
                    String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                    String photoUrl = userDoc.getString("photoUrl");

                    ChatMessage message = new ChatMessage(
                            currentUser.getUid(),
                            senderName,
                            photoUrl,
                            text,
                            new Date()
                    );
                    message.setType("text");

                    messagesRef.add(message);
                    updateChatRoomLastMessage(text, "text", senderName);
                    messageEditText.setText("");
                    recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                });
    }
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Images"));
    }

    private void uploadImageAndSendMessage(Uri imageUri) {
        String filePath = FileUtils.getPath(this, imageUri);
        if (filePath == null) {
            Toast.makeText(this, "Failed to get file path", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = System.currentTimeMillis() + "_" + file.getName();
        String path = "chat-room-images/" + roomId + "/" + fileName;
        String bucket = "chat-room-photos";

        SupabaseUploader.uploadFile(file, bucket, path, (success, message, publicUrl) -> runOnUiThread(() -> {
            if (success && publicUrl != null) {
                db.collection("users").document(currentUser.getUid())
                        .get()
                        .addOnSuccessListener(userDoc -> {
                            String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                            String photoUrl = userDoc.getString("photoUrl");

                            ChatMessage chatMessage = new ChatMessage(
                                    currentUser.getUid(),
                                    senderName,
                                    photoUrl,
                                    null,
                                    new Date()
                            );
                            chatMessage.setType("image");
                            chatMessage.setImageUrl(publicUrl);

                            messagesRef.add(chatMessage);
                            updateChatRoomLastMessage("Sent an image", "image", senderName);
                        });
            } else {
                Toast.makeText(ChatRoomActivity.this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void uploadFileAndSendMessage(Uri fileUri) {
        String filePath = FileUtils.getPath(this, fileUri);
        if (filePath == null) {
            Toast.makeText(this, "Failed to get file path", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = System.currentTimeMillis() + "_" + file.getName();
        String path = "chat-room-files/" + roomId + "/" + fileName;
        String bucket = "chat-room-files";

        SupabaseUploader.uploadFile(file, bucket, path, (success, message, publicUrl) -> runOnUiThread(() -> {
            if (success && publicUrl != null) {
                db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(userDoc -> {
                    String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                    String photoUrl = userDoc.getString("photoUrl");

                    ChatMessage fileMessage = new ChatMessage(
                            currentUser.getUid(),
                            senderName,
                            photoUrl,
                            null,
                            new Date()
                    );
                    fileMessage.setType("file");
                    fileMessage.setFileUrl(publicUrl);
                    fileMessage.setFileName(file.getName());
                    fileMessage.setFileSize(file.length());
                    fileMessage.setFileType(SupabaseUploader.getMimeType(file));
                    fileMessage.setFilePath(path);

                    messagesRef.add(fileMessage);
                    updateChatRoomLastMessage("Sent a file", "file", senderName);
                });
            } else {
                Toast.makeText(ChatRoomActivity.this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void showSendMorePopup() {
        View popupView = LayoutInflater.from(this).inflate(R.layout.item_send_more, null);

        PopupWindow popupWindow = new PopupWindow(popupView,
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setBackgroundDrawable(getDrawable(android.R.drawable.dialog_holo_light_frame));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        popupWindow.showAtLocation(popupView, Gravity.BOTTOM, 0, 0);

        TextView sendSet = popupView.findViewById(R.id.sendSet);
        TextView sendFile = popupView.findViewById(R.id.sendFile);

        sendSet.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(this, SetPickerActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });

        sendFile.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(Intent.createChooser(intent, "Select File"));
        });
    }

    private void fetchChatRoomDetails(Runnable onSuccess) {
        db.collection("chat_rooms").document(roomId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        memberUids = (List<String>) doc.get("members");

                        String roomName = doc.getString("chatRoomName");
                        if (roomName != null && !roomName.trim().isEmpty()) {
                            chatRoomNameText.setText(roomName);
                        }

                        String photoUrl = doc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this).load(photoUrl)
                                    .placeholder(R.drawable.user_profile)
                                    .error(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(chatRoomPhoto);
                        }
                        onSuccess.run();
                    } else {
                        Toast.makeText(this, "Chat room not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load chat room", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setUpRecyclerView() {
        Query query = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING);
        FirestoreRecyclerOptions<ChatMessage> options = new FirestoreRecyclerOptions.Builder<ChatMessage>()
                .setQuery(query, ChatMessage.class)
                .build();

        adapter = new ChatMessageAdapter(options, currentUser.getUid());
        recyclerView.setAdapter(adapter);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });

        adapter.startListening();
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.chat_room_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_live_quiz) {
                return true;
            } else if (id == R.id.menu_reminder) {
                return true;
            } else if (id == R.id.menu_settings) {
                openEditChatRoom();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void openEditChatRoom() {
        Intent intent = new Intent(ChatRoomActivity.this, EditChatRoomActivity.class);
        intent.putExtra("roomId", roomId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) adapter.stopListening();
    }
}
