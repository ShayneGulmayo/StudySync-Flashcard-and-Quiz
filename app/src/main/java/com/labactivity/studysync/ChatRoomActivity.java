    package com.labactivity.studysync;

    import android.content.Intent;
    import android.database.Cursor;
    import android.net.Uri;
    import android.os.Bundle;
    import android.os.CountDownTimer;
    import android.os.Handler;
    import android.provider.OpenableColumns;
    import android.view.Gravity;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.EditText;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.PopupWindow;
    import android.widget.ProgressBar;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.activity.result.ActivityResultLauncher;
    import androidx.activity.result.contract.ActivityResultContracts;
    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.appcompat.widget.PopupMenu;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;

    import com.bumptech.glide.Glide;
    import com.firebase.ui.firestore.FirestoreRecyclerOptions;
    import com.google.firebase.Timestamp;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.auth.FirebaseUser;
    import com.google.firebase.firestore.CollectionReference;
    import com.google.firebase.firestore.DocumentChange;
    import com.google.firebase.firestore.DocumentSnapshot;
    import com.google.firebase.firestore.FirebaseFirestore;
    import com.google.firebase.firestore.ListenerRegistration;
    import com.google.firebase.firestore.Query;
    import com.google.firebase.firestore.SetOptions;
    import com.google.firebase.storage.FirebaseStorage;
    import com.google.firebase.storage.StorageReference;
    import com.labactivity.studysync.adapters.ChatMessageAdapter;
    import com.labactivity.studysync.models.ChatMessage;

    import java.util.ArrayList;
    import java.util.Date;
    import java.util.HashMap;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Map;
    import java.util.Random;
    import java.util.Set;
    import java.util.concurrent.atomic.AtomicInteger;

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
        private AlertDialog dialog;
        private LinearLayout quizContainer;
        private TextView quizQuestionText, quizQuestionNumber;
        private ProgressBar quizProgressBar;
        private final Set<String> triggeredQuizIds = new HashSet<>();




        private final ActivityResultLauncher<Intent> imagePickerLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri imageUri = result.getData().getClipData().getItemAt(i).getUri();
                                String type = getContentResolver().getType(imageUri);
                                if (type != null && type.startsWith("image/")) {
                                    uploadImageAndSendMessage(imageUri);
                                } else if (type != null && type.startsWith("video/")) {
                                    uploadVideoAndSendMessage(imageUri);
                                } else {
                                    Toast.makeText(this, "Unsupported media type", Toast.LENGTH_SHORT).show();
                                }

                            }
                        } else if (result.getData().getData() != null) {
                            Uri imageUri = result.getData().getData();
                            String type = getContentResolver().getType(imageUri);
                            if (type != null && type.startsWith("image/")) {
                                uploadImageAndSendMessage(imageUri);
                            } else if (type != null && type.startsWith("video/")) {
                                uploadVideoAndSendMessage(imageUri);
                            } else {
                                Toast.makeText(this, "Unsupported media type", Toast.LENGTH_SHORT).show();
                            }

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
            quizContainer = findViewById(R.id.liveQuizContainer);
            quizQuestionText = findViewById(R.id.quizQuestionText);
            quizQuestionNumber = findViewById(R.id.quizQuestionNumber);
            quizProgressBar = findViewById(R.id.quizProgressBar);


            ImageButton sendButton = findViewById(R.id.sendMessage);
            ImageView backBtn = findViewById(R.id.back_button);
            ImageView moreBtn = findViewById(R.id.chatRoomSettings);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setStackFromEnd(true);
            recyclerView.setLayoutManager(layoutManager);

            messagesRef = db.collection("chat_rooms").document(roomId).collection("messages");

            String startLiveQuizId = getIntent().getStringExtra("startLiveQuizId");
            if (startLiveQuizId != null && !triggeredQuizIds.contains(startLiveQuizId)) {
                launchLiveQuiz(startLiveQuizId);
            }



            fetchChatRoomDetails(this::setUpRecyclerView);


            sendButton.setOnClickListener(v -> sendMessage());
            sendImg.setOnClickListener(v -> openImagePicker());
            sendFlashcardsandQuiz.setOnClickListener(v -> showSendMorePopup());
            backBtn.setOnClickListener(v -> finish());
            moreBtn.setOnClickListener(v -> showPopupMenu(moreBtn));

            chatRoomPhoto.setOnClickListener(v -> openEditChatRoom());
            chatRoomNameText.setOnClickListener(v -> openEditChatRoom());

        }

        private void launchLiveQuiz(String quizId) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String roomId = getIntent().getStringExtra("roomId");
            CollectionReference messageRef = db.collection("chat_rooms").document(roomId).collection("messages");

            if (triggeredQuizIds.contains(quizId)) {
                return;
            }
            triggeredQuizIds.add(quizId);
            db.collection("chat_rooms")
                    .document(roomId)
                    .collection("live_quiz")
                    .document(quizId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String title = documentSnapshot.getString("title");
                            List<Map<String, Object>> questions = (List<Map<String, Object>>) documentSnapshot.get("questions");
                            Long durationPerQuestion = documentSnapshot.getLong("duration");

                            if (questions != null && durationPerQuestion != null) {
                                Map<String, Object> startMessage = new HashMap<>();
                                startMessage.put("senderId", "Live Quiz Manager");
                                startMessage.put("senderName", "Live Quiz Manager");
                                startMessage.put("senderPhotoUrl", "https://firebasestorage.googleapis.com/v0/b/studysync-cf3ef.appspot.com/o/studysync_logo.png?alt=media&token=ddfbb29d-2682-457e-a700-ebba6b6b79d0");
                                startMessage.put("text", "🚨 A Live Quiz has just started! Get ready to answer quickly.");
                                startMessage.put("timestamp", Timestamp.now());
                                startMessage.put("type", "text");

                                messageRef.add(startMessage);

                                runQuizPopup(title, questions, durationPerQuestion.intValue(), roomId, quizId);
                            } else {
                                Toast.makeText(this, "Invalid quiz data.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load quiz.", Toast.LENGTH_SHORT).show();
                    });
        }

        private void runQuizPopup(String title, List<Map<String, Object>> questions, int durationSeconds, String roomId, String quizId) {
            Map<String, Integer> scores = new HashMap<>();
            Map<String, String> names = new HashMap<>();
            AtomicInteger currentIndex = new AtomicInteger(0);
            Handler handler = new Handler();
            final boolean[] quizStopped = {false};


            Runnable[] nextQuestion = new Runnable[1];

            nextQuestion[0] = new Runnable() {
                @Override
                public void run() {
                    if (quizStopped[0] || currentIndex.get() >= questions.size()) {
                        saveLeaderboard(roomId, quizId, scores, names);
                        return;
                    }

                    Map<String, Object> question = questions.get(currentIndex.get());
                    String questionText = (String) question.get("question");
                    String correctAnswer = ((String) question.get("correctAnswer")).toLowerCase().trim();
                    CollectionReference message = db.collection("chat_rooms").document(roomId).collection("messages");

                    runOnUiThread(() -> {
                        quizContainer.setVisibility(View.VISIBLE);
                        quizQuestionText.setText(questionText);
                        String questionAnnouncement = String.format("📢 Question %d: %s", currentIndex.get() + 1, questionText);
                        Map<String, Object> questionMessage = new HashMap<>();
                        questionMessage.put("senderId", "Live Quiz Manager");
                        questionMessage.put("senderName", "Live Quiz Manager");
                        questionMessage.put("senderPhotoUrl", "https://firebasestorage.googleapis.com/v0/b/studysync-cf3ef.appspot.com/o/studysync_logo.png?alt=media&token=ddfbb29d-2682-457e-a700-ebba6b6b79d0");
                        questionMessage.put("text", questionAnnouncement);
                        questionMessage.put("timestamp", Timestamp.now());
                        questionMessage.put("type", "text");

                        message.add(questionMessage);

                        quizQuestionNumber.setText("Question " + (currentIndex.get() + 1) + "/" + questions.size());
                        quizProgressBar.setProgress(100);
                    });

                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    CollectionReference messageRef = db.collection("chat_rooms").document(roomId).collection("messages");

                    ListenerRegistration[] answerListener = new ListenerRegistration[1];
                    Timestamp quizStartTime = Timestamp.now();
                    CountDownTimer[] timer = new CountDownTimer[1];

                    final boolean[] answered = {false};
                    final String[] firstCorrectUserId = {null};
                    final String[] firstCorrectUserName = {null};

                    answerListener[0] = messageRef
                            .whereGreaterThan("timestamp", quizStartTime)
                            .orderBy("timestamp", Query.Direction.ASCENDING)
                            .addSnapshotListener((snapshots, e) -> {
                                if (snapshots == null || snapshots.isEmpty()) return;

                                for (DocumentChange change : snapshots.getDocumentChanges()) {
                                    if (change.getType() == DocumentChange.Type.ADDED) {
                                        DocumentSnapshot doc = change.getDocument();
                                        String text = doc.getString("text");
                                        if (text == null) continue;

                                        String senderId = doc.getString("senderId");
                                        String senderName = doc.getString("senderName");

                                        if (text.trim().equalsIgnoreCase("/stop")) {
                                            if (answerListener[0] != null) answerListener[0].remove();
                                            if (timer[0] != null) timer[0].cancel();
                                            quizStopped[0] = true;
                                            runOnUiThread(() -> {
                                                Toast.makeText(getApplicationContext(), "Live Quiz stopped.", Toast.LENGTH_LONG).show();
                                                quizContainer.setVisibility(View.GONE);
                                            });
                                            return;
                                        }

                                        if (!answered[0] && isAnswerCloseEnough(correctAnswer, text)) {
                                            answered[0] = true;
                                            firstCorrectUserId[0] = senderId;
                                            firstCorrectUserName[0] = senderName;

                                            scores.put(senderId, scores.getOrDefault(senderId, 0) + 1);
                                            names.put(senderId, senderName);

                                            if (answerListener[0] != null) answerListener[0].remove();
                                            if (timer[0] != null) timer[0].cancel();

                                            String[] messages = {
                                                    "%s crushed it! 💥 The answer to \"%s\" was \"%s\".",
                                                    "🔥 %s got it right first! \"%s\" was the correct answer to \"%s\".",
                                                    "%s just scored! 🎯 The correct answer to \"%s\" was \"%s\".",
                                                    "🏆 %s was the fastest! \"%s\" is the right answer to \"%s\".",
                                                    "%s earned a point! The answer to \"%s\" was \"%s\"."
                                            };
                                            String msg = String.format(messages[new Random().nextInt(messages.length)], senderName, correctAnswer, questionText);

                                            Map<String, Object> messageData = new HashMap<>();
                                            messageData.put("senderId", "Live Quiz Manager");
                                            messageData.put("senderName", "Live Quiz Manager");
                                            messageData.put("senderPhotoUrl", "https://firebasestorage.googleapis.com/v0/b/studysync-cf3ef.firebasestorage.app/o/studysync_logo.png?alt=media&token=ddfbb29d-2682-457e-a700-ebba6b6b79d0");
                                            messageData.put("text", msg);
                                            messageData.put("timestamp", Timestamp.now());
                                            messageData.put("type", "text");

                                            messageRef.add(messageData);
                                            runOnUiThread(() -> quizContainer.setVisibility(View.GONE));


                                            currentIndex.incrementAndGet();
                                            handler.postDelayed(nextQuestion[0], 2500);
                                            return;
                                        }
                                    }
                                }
                            });

                    timer[0] = new CountDownTimer(durationSeconds * 1000L, 100) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            int progress = (int) (millisUntilFinished * 100 / (durationSeconds * 1000L));
                            runOnUiThread(() -> quizProgressBar.setProgress(progress));
                        }

                        @Override
                        public void onFinish() {
                            if (!answered[0]) {
                                String noOneMsg = String.format("⏰ Time's up! No one got it. The correct answer to \"%s\" was \"%s\".", questionText, correctAnswer);

                                Map<String, Object> messageData = new HashMap<>();
                                messageData.put("senderId", "Live Quiz Manager");
                                messageData.put("senderName", "Live Quiz Manager");
                                messageData.put("senderPhotoUrl", "https://firebasestorage.googleapis.com/v0/b/studysync-cf3ef.firebasestorage.app/o/studysync_logo.png?alt=media&token=ddfbb29d-2682-457e-a700-ebba6b6b79d0");
                                messageData.put("text", noOneMsg);
                                messageData.put("timestamp", Timestamp.now());
                                messageData.put("type", "text");

                                messageRef.add(messageData);
                            }

                            if (answerListener[0] != null) answerListener[0].remove();
                            runOnUiThread(() -> quizContainer.setVisibility(View.GONE));

                            currentIndex.incrementAndGet();
                            handler.postDelayed(nextQuestion[0], 2500);
                        }
                    };

                    timer[0].start();
                }
            };

            nextQuestion[0].run();
        }



        private void saveLeaderboard(String roomId, String quizId, Map<String, Integer> scores, Map<String, String> names) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            CollectionReference leaderboardRef = db.collection("chat_rooms")
                    .document(roomId)
                    .collection("live_quiz")
                    .document(quizId)
                    .collection("leaderboards");

            CollectionReference messageRef = db.collection("chat_rooms")
                    .document(roomId)
                    .collection("messages");

            List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
            sortedScores.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // Descending

            StringBuilder sb = new StringBuilder("🏆 **Final Leaderboard** 🏆\n\n");
            for (int i = 0; i < sortedScores.size(); i++) {
                Map.Entry<String, Integer> entry = sortedScores.get(i);
                String userId = entry.getKey();
                int score = entry.getValue();

                Map<String, Object> data = new HashMap<>();
                data.put("userId", userId);
                data.put("name", names.get(userId));
                data.put("score", score);

                leaderboardRef.document(userId).set(data);
                sb.append((i + 1)).append(". ").append(names.get(userId)).append(" - ").append(score).append(" point(s)\n");
            }

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", "Live Quiz Manager");
            messageData.put("senderName", "Live Quiz Manager");
            messageData.put("senderPhotoUrl", "https://firebasestorage.googleapis.com/v0/b/studysync-cf3ef.appspot.com/o/studysync_logo.png?alt=media&token=ddfbb29d-2682-457e-a700-ebba6b6b79d0");
            messageData.put("text", sb.toString());
            messageData.put("timestamp", Timestamp.now());
            messageData.put("type", "text");

            messageRef.add(messageData);

            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Live Quiz Finished!")
                        .setMessage(sb.toString())
                        .setPositiveButton("OK", null)
                        .show();
            });
        }



        private boolean isAnswerCloseEnough(String correct, String userAnswer) {
            correct = correct.toLowerCase().trim();
            userAnswer = userAnswer.toLowerCase().trim();
            int distance = levenshteinDistance(correct, userAnswer);
            int maxLen = Math.max(correct.length(), userAnswer.length());

            double similarity = (1.0 - (double) distance / maxLen);
            return similarity >= 0.8;
        }

        private int levenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];

            for (int i = 0; i <= s1.length(); i++) {
                for (int j = 0; j <= s2.length(); j++) {
                    if (i == 0) {
                        dp[i][j] = j;
                    } else if (j == 0) {
                        dp[i][j] = i;
                    } else {
                        dp[i][j] = Math.min(dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                                1 + Math.min(dp[i - 1][j], dp[i][j - 1]));
                    }
                }
            }

            return dp[s1.length()][s2.length()];
        }




        private void updateChatRoomLastMessage(String message, String type, String senderName) {
            Map<String, Object> update = new HashMap<>();
            update.put("lastMessage", message);
            update.put("type", type);
            update.put("lastMessageSender", senderName);
            db.collection("chat_rooms").document(roomId).set(update, SetOptions.merge());
        }

        private void uploadVideoAndSendMessage(Uri videoUri) {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            String videoName = System.currentTimeMillis() + "_video.mp4";
            StorageReference storageRef = storage.getReference().child("chat_room_videos/" + roomId + "/" + videoName);

            storageRef.putFile(videoUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String videoUrl = uri.toString();

                        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(userDoc -> {
                            String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                            String photoUrl = userDoc.getString("photoUrl");

                            ChatMessage videoMessage = new ChatMessage(
                                    currentUser.getUid(), senderName, photoUrl, null, new Date()
                            );
                            videoMessage.setType("video");
                            videoMessage.setVideoUrl(videoUrl);

                            messagesRef.add(videoMessage);
                            updateChatRoomLastMessage("sent a video", "video", senderName);
                        });
                    }))
                    .addOnFailureListener(e -> Toast.makeText(this, "Video upload failed", Toast.LENGTH_SHORT).show());
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
            intent.setType("*/*");
            String[] mimeTypes = {"image/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Media"));
        }


        private void uploadImageAndSendMessage(Uri imageUri) {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            String imgName = System.currentTimeMillis() + "_image.jpg";
            StorageReference storageRef = storage.getReference().child("chat_room_images/" + roomId + "/" + imgName);

            storageRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();

                        db.collection("users").document(currentUser.getUid())
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                                    String photoUrl = userDoc.getString("photoUrl");

                                    ChatMessage chatMessage = new ChatMessage(
                                            currentUser.getUid(), senderName, photoUrl, null, new Date()
                                    );
                                    chatMessage.setType("image");
                                    chatMessage.setImageUrl(imageUrl);

                                    messagesRef.add(chatMessage);
                                    updateChatRoomLastMessage("sent an image", "image", senderName);
                                });
                    }))
                    .addOnFailureListener(e -> Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show());
        }


        private void uploadFileAndSendMessage(Uri fileUri) {
            if (fileUri == null) {
                Toast.makeText(this, "Invalid file selected", Toast.LENGTH_SHORT).show();
                return;
            }

            final String[] fileName = {"unknown_file"};
            final long[] fileSize = {0};

            try (Cursor cursor = getContentResolver().query(fileUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                    if (nameIndex != -1) {
                        fileName[0] = cursor.getString(nameIndex);
                    }

                    if (sizeIndex != -1) {
                        fileSize[0] = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to read file metadata", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            FirebaseStorage storage = FirebaseStorage.getInstance();
            String filePath = "chat-room-files/" + roomId + "/" + System.currentTimeMillis() + "_" + fileName;
            StorageReference storageRef = storage.getReference().child(filePath);

            storageRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String fileUrl = uri.toString();

                        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(userDoc -> {
                            String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                            String photoUrl = userDoc.getString("photoUrl");

                            ChatMessage fileMessage = new ChatMessage(
                                    currentUser.getUid(), senderName, photoUrl, null, new Date()
                            );
                            fileMessage.setType("file");
                            fileMessage.setFileUrl(fileUrl);
                            fileMessage.setFileName(fileName[0]);
                            fileMessage.setFileSize(fileSize[0]);
                            fileMessage.setFileType(getContentResolver().getType(fileUri));
                            fileMessage.setFilePath(filePath);

                            messagesRef.add(fileMessage);
                            updateChatRoomLastMessage("sent a file", "file", senderName);
                        });
                    }))
                    .addOnFailureListener(e -> Toast.makeText(this, "File upload failed", Toast.LENGTH_SHORT).show());
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
            listenForLiveQuizTriggers();
            db.collection("chat_rooms").document(roomId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            Toast.makeText(this, "Chat room not found", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        if (currentUser == null) {
                            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        String roomName = doc.getString("chatRoomName");
                        if (roomName != null && !roomName.trim().isEmpty()) {
                            chatRoomNameText.setText(roomName);
                        }

                        String photoUrl = doc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.user_profile)
                                    .error(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(chatRoomPhoto);
                        }

                        Object membersObj = doc.get("members");
                        if (membersObj instanceof List) {
                            memberUids = (List<String>) membersObj;
                            if (!memberUids.contains(currentUser.getUid())) {
                                Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                onSuccess.run();
                            }
                        } else {
                            Toast.makeText(this, "Invalid members list", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load chat room", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }
        private void listenForLiveQuizTriggers() {
            db.collection("chat_rooms")
                    .document(roomId)
                    .collection("live_quiz")
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null || snapshots == null) return;

                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String quizId = doc.getId();
                            Boolean isStarted = doc.getBoolean("isStarted");

                            if (Boolean.TRUE.equals(isStarted) && !triggeredQuizIds.contains(quizId)) {
                                launchLiveQuiz(quizId);
                            }
                        }
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
                    Intent intent = new Intent(ChatRoomActivity.this, LiveQuizActivity.class);
                    intent.putExtra("roomId", roomId);
                    startActivity(intent);
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
