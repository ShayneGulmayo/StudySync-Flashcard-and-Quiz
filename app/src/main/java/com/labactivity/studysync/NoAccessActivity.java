package com.labactivity.studysync;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoAccessActivity extends AppCompatActivity {

    private static final String TAG = "NoAccessActivity";

    private ImageView backButton;
    private Button requestAccessbtn;
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

        setType = getIntent().getStringExtra("setType");
        setType = (setType != null ? setType.toLowerCase() : "flashcard");

        if (getIntent().hasExtra("setId")) {
            setId = getIntent().getStringExtra("setId");
        } else if (getIntent().hasExtra("quizId")) {
            setId = getIntent().getStringExtra("quizId");
        } else {
            showToast("No Set ID provided.");
            finish();
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        requestAccessbtn = findViewById(R.id.reqAccessBtn);

        if (requestAccessbtn != null) {
            requestAccessbtn.setOnClickListener(v -> showMoreBottomSheet());
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void showMoreBottomSheet() {
        if (!canNavigate()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showToast("You must be logged in.");
            return;
        }

        String currentUserId = user.getUid();
        String collection = setType.equals("quiz") ? "quiz" : "flashcards";

        db.collection(collection).document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!canNavigate()) return;

                    if (!doc.exists()) {
                        showToast("Set not found.");
                        return;
                    }

                    ownerUid = doc.getString("owner_uid");

                    if (ownerUid == null) {
                        showToast("Owner not found.");
                        return;
                    }

                    if (ownerUid.equals(currentUserId)) {
                        showToast("You already own this set.");
                        return;
                    }

                    sendAccessRequest("Viewer");
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to fetch set info.");
                    Log.e(TAG, "Set fetch error", e);
                });
    }

    private void sendAccessRequest(String requestedRole) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || setId == null || ownerUid == null || !canNavigate()) return;

        String senderUid = currentUser.getUid();

        db.collection("users")
                .document(ownerUid)
                .collection("notifications")
                .whereEqualTo("senderId", senderUid)
                .whereEqualTo("setId", setId)
                .whereEqualTo("setType", setType)
                .whereEqualTo("type", "request")
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!canNavigate()) return;

                    if (!querySnapshot.isEmpty()) {
                        showToast("You already sent a request. Please wait for a response.");
                        return;
                    }

                    db.collection("users").document(senderUid)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (!canNavigate()) return;

                                String firstName = userDoc.getString("firstName");
                                String lastName = userDoc.getString("lastName");
                                String senderPhoto = userDoc.getString("photoUrl") != null ? userDoc.getString("photoUrl") : "";

                                String senderName = "";
                                if (firstName != null) senderName += firstName;
                                if (lastName != null) senderName += (senderName.isEmpty() ? "" : " ") + lastName;
                                if (senderName.isEmpty()) senderName = "Unknown User";

                                String finalSenderName = senderName;
                                String collection = setType.equals("quiz") ? "quiz" : "flashcards";

                                db.collection(collection).document(setId)
                                        .get()
                                        .addOnSuccessListener(setDoc -> {
                                            if (!canNavigate()) return;

                                            if (!setDoc.exists()) {
                                                showToast("Set not found.");
                                                return;
                                            }

                                            String setTitle = setDoc.getString("title");
                                            if (setTitle == null || setTitle.isEmpty()) setTitle = "Untitled";

                                            String messageText = finalSenderName + " has requested access to your set \"" + setTitle + "\".";

                                            Map<String, Object> requestNotification = new HashMap<>();
                                            requestNotification.put("senderId", senderUid);
                                            requestNotification.put("senderName", finalSenderName);
                                            requestNotification.put("senderPhotoUrl", senderPhoto);
                                            requestNotification.put("setId", setId);
                                            requestNotification.put("setType", setType);
                                            requestNotification.put("requestedRole", requestedRole);
                                            requestNotification.put("text", messageText);
                                            requestNotification.put("type", "request");
                                            requestNotification.put("status", "pending");
                                            requestNotification.put("timestamp", FieldValue.serverTimestamp());
                                            requestNotification.put("read", false);

                                            DocumentReference notifRef = db.collection("users")
                                                    .document(ownerUid)
                                                    .collection("notifications")
                                                    .document();

                                            requestNotification.put("notificationId", notifRef.getId());

                                            notifRef.set(requestNotification)
                                                    .addOnSuccessListener(unused -> {
                                                        showToast("Access request sent!");
                                                        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
                                                            bottomSheetDialog.dismiss();
                                                        }
                                                        finish();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        showToast("Failed to send request.");
                                                        Log.e(TAG, "Request send failed", e);
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            showToast("Failed to fetch set.");
                                            Log.e(TAG, "Set fetch error", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to fetch user info.");
                                Log.e(TAG, "User fetch error", e);
                            });
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to check existing requests.");
                    Log.e(TAG, "Query error", e);
                });
    }

    private boolean canNavigate() {
        return !isFinishing() && !isDestroyed();
    }

    private void showToast(String msg) {
        if (canNavigate()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }
}