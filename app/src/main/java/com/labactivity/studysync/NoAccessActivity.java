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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.models.ChatMessage;

import java.util.Date;
import java.util.HashMap;
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
        String collection = setType.equals("quiz") ? "quizzes" : "flashcards";

        db.collection(collection).document(setId)
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
        TextView reqEditBtn = view.findViewById(R.id.reqEdit);

        // Hide or show based on permissions
        privacyBtn.setVisibility(View.GONE);
        reminderBtn.setVisibility(View.GONE);
        sendToChatBtn.setVisibility(View.GONE);
        deleteBtn.setVisibility(View.GONE);
        downloadBtn.setVisibility(View.GONE);
        editBtn.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        reqAccessBtn.setVisibility(View.GONE);
        reqEditBtn.setVisibility(View.GONE);

        if ("public".equals(privacy)) {
            copyBtn.setVisibility(View.VISIBLE);

            if ("Editor".equalsIgnoreCase(privacyRole)) {
                editBtn.setVisibility(View.VISIBLE);
            } else if ("Viewer".equalsIgnoreCase(privacyRole)) {
                if (!"Editor".equalsIgnoreCase(userAccessRole)) {
                    reqAccessBtn.setVisibility(View.VISIBLE);
                } else {
                    editBtn.setVisibility(View.VISIBLE);
                    downloadBtn.setVisibility(View.VISIBLE);
                }
            }
        } else {
            reqAccessBtn.setVisibility(View.VISIBLE);
            reqEditBtn.setVisibility(View.VISIBLE);
        }


        reqAccessBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            sendAccessRequest("Viewer");
        });

        reqEditBtn.setOnClickListener(v -> {
            if (!canNavigate()) return;
            sendAccessRequest("Editor");
        });

        bottomSheetDialog.show();
    }

    private void sendAccessRequest(String requestedRole) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || setId == null || ownerUid == null) return;

        String senderUid = currentUser.getUid();

        db.collection("users").document(senderUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String firstName = userDoc.getString("firstName");
                    String lastName = userDoc.getString("lastName");
                    String senderPhoto = userDoc.getString("photoUrl") != null ? userDoc.getString("photoUrl") : "";

                    String senderName = "";
                    if (firstName != null) senderName += firstName;
                    if (lastName != null) senderName += (senderName.isEmpty() ? "" : " ") + lastName;
                    if (senderName.isEmpty()) senderName = "Unknown User";

                    // Get flashcard title (ONLY for flashcards as requested)
                    String finalSenderName = senderName;
                    db.collection("flashcards").document(setId)
                            .get()
                            .addOnSuccessListener(setDoc -> {
                                if (!setDoc.exists()) {
                                    Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                String setTitle = setDoc.getString("title");
                                if (setTitle == null || setTitle.isEmpty()) setTitle = "Untitled";

                                String messageText = finalSenderName + " has requested " + requestedRole.toLowerCase() +
                                        " access to your set \"" + setTitle + "\".";

                                Map<String, Object> requestMessage = new HashMap<>();
                                requestMessage.put("senderId", senderUid);
                                requestMessage.put("senderName", finalSenderName);
                                requestMessage.put("senderPhotoUrl", senderPhoto);
                                requestMessage.put("setId", setId);
                                requestMessage.put("setType", "flashcard");
                                requestMessage.put("requestedRole", requestedRole);
                                requestMessage.put("text", messageText);
                                requestMessage.put("type", "request");
                                requestMessage.put("status", "pending");
                                requestMessage.put("timestamp", FieldValue.serverTimestamp());

                                db.collection("chat_rooms")
                                        .document("studysync_announcements")
                                        .collection("users")
                                        .document(ownerUid)
                                        .collection("messages")
                                        .add(requestMessage)
                                        .addOnSuccessListener(docRef -> {
                                            Toast.makeText(this, "Access request sent!", Toast.LENGTH_SHORT).show();
                                            if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
                                                bottomSheetDialog.dismiss();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Failed to send request.", Toast.LENGTH_SHORT).show();
                                            Log.e("AccessRequest", "Firestore error", e);
                                        });

                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to fetch flashcard set.", Toast.LENGTH_SHORT).show();
                                Log.e("AccessRequest", "Set fetch error", e);
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch user info.", Toast.LENGTH_SHORT).show();
                    Log.e("AccessRequest", "User fetch error", e);
                });
    }


    private boolean canNavigate() {
        return !isFinishing() && !isDestroyed();
    }
}
