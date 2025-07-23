package com.labactivity.studysync;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.models.ChatMessage;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class NoAccessActivity extends AppCompatActivity {

    private ImageView moreButton, backButton;
    private String setId, setType;
    private BottomSheetDialog bottomSheetDialog;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String ownerUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_access);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();

        if (getIntent().hasExtra("setId")) {
            setId = getIntent().getStringExtra("setId");
        } else {
            Toast.makeText(this, "No Set ID provided.", Toast.LENGTH_SHORT).show();
            finish();
        }

        setType = getIntent().getStringExtra("setType") != null ? getIntent().getStringExtra("setType") : "flashcard";
    }

    private void initializeViews() {
        moreButton = findViewById(R.id.more_button);
        backButton = findViewById(R.id.back_button);

        backButton.setOnClickListener(v -> finish());
        moreButton.setOnClickListener(v -> showMoreBottomSheet());
    }

    private void showMoreBottomSheet() {
        if (isFinishing() || isDestroyed()) return;

        String currentUserId = auth.getCurrentUser().getUid();

        db.collection(setType.equals("quiz") ? "quizzes" : "flashcards").document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Set not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ownerUid = doc.getString("owner_uid");
                    String privacy = doc.getString("privacy");
                    String privacyRole = doc.getString("privacyRole");
                    Map<String, String> accessUsers = (Map<String, String>) doc.get("accessUsers");

                    final String[] userAccessRole = {null};
                    if (accessUsers != null && accessUsers.containsKey(currentUserId)) {
                        userAccessRole[0] = accessUsers.get(currentUserId);
                    }

                    if (ownerUid != null && ownerUid.equals(currentUserId)) {
                        openBottomSheetWithAccess(null, null, false, userAccessRole[0]);
                    } else {
                        db.collection("users").document(currentUserId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) userDoc.get("saved_sets");
                                    boolean isSavedSet = false;

                                    if (savedSets != null) {
                                        for (Map<String, Object> set : savedSets) {
                                            if (setId.equals(set.get("id")) && setType.equals(set.get("type"))) {
                                                isSavedSet = true;
                                                break;
                                            }
                                        }
                                    }

                                    openBottomSheetWithAccess(privacy, privacyRole, isSavedSet, userAccessRole[0]);
                                });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to fetch set info.", Toast.LENGTH_SHORT).show());
    }

    private void openBottomSheetWithAccess(String privacy, String privacyRole, boolean isSavedSet, String userAccessRole) {
        if (isFinishing() || isDestroyed()) return;

        bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.download);
        TextView copyBtn = view.findViewById(R.id.copy);
        TextView privacyBtn = view.findViewById(R.id.privacy);
        TextView reminderBtn = view.findViewById(R.id.reminder);
        TextView sendToChatBtn = view.findViewById(R.id.sendToChat);
        TextView editBtn = view.findViewById(R.id.edit);
        TextView deleteBtn = view.findViewById(R.id.delete);
        TextView reqAccessBtn = view.findViewById(R.id.reqAccess);

        if (privacy == null && privacyRole == null && !isSavedSet) {
        } else if ("public".equals(privacy) && "view".equalsIgnoreCase(privacyRole)) {
            privacyBtn.setVisibility(View.GONE);
            reminderBtn.setVisibility(View.GONE);
            sendToChatBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
            downloadBtn.setVisibility(View.GONE);
            reqAccessBtn.setVisibility(View.GONE);
            copyBtn.setVisibility(View.VISIBLE);
            editBtn.setVisibility(View.GONE);

            if ("Edit".equalsIgnoreCase(userAccessRole)) {
                editBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.VISIBLE);
            } else {
                reqAccessBtn.setVisibility(View.VISIBLE);
            }
        } else if ("public".equals(privacy) && "edit".equalsIgnoreCase(privacyRole)) {
            privacyBtn.setVisibility(View.GONE);
            reminderBtn.setVisibility(View.GONE);
            sendToChatBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
            copyBtn.setVisibility(View.VISIBLE);
            editBtn.setVisibility(View.VISIBLE);
        } else {
            privacyBtn.setVisibility(View.GONE);
            reminderBtn.setVisibility(View.GONE);
            sendToChatBtn.setVisibility(View.GONE);
            editBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
            downloadBtn.setVisibility(View.GONE);
            reqAccessBtn.setVisibility(View.VISIBLE);
        }

        reqAccessBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;

            Toast.makeText(this, "Your request has been sent.", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();

            if (ownerUid == null || ownerUid.isEmpty()) {
                Toast.makeText(this, "Owner ID not found.", Toast.LENGTH_SHORT).show();
                return;
            }

            String currentUserId = auth.getCurrentUser().getUid();

            db.collection("users").document(currentUserId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        String firstName = userDoc.getString("firstName");
                        String lastName = userDoc.getString("lastName");
                        String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");

                        ChatMessage requestMessage = new ChatMessage();
                        requestMessage.setSenderId("system");
                        requestMessage.setSenderName("StudySync System");
                        requestMessage.setSenderPhotoUrl("");
                        requestMessage.setText(fullName.trim() + " has requested access to your set.");
                        requestMessage.setTimestamp(new Date());
                        requestMessage.setType("request");

                        db.collection("chat_rooms")
                                .document("studysync_announcements")
                                .collection("users")
                                .document(ownerUid)
                                .collection("messages")
                                .add(requestMessage)
                                .addOnSuccessListener(documentReference -> Log.d("AccessRequest", "Request sent successfully."))
                                .addOnFailureListener(e -> Log.e("AccessRequest", "Failed to send request: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> Log.e("AccessRequest", "Failed to fetch sender name: " + e.getMessage()));
        });


        bottomSheetDialog.show();
    }

    private boolean canNavigate() {
        return !isFinishing() && !isDestroyed();
    }
}
