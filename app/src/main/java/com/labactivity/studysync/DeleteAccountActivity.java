package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeleteAccountActivity extends AppCompatActivity {

    private static final String TAG = "DeleteAccount";

    private EditText emailEditText;
    private Button deleteAccountBtn;
    private ImageView backButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String uid;

    private GoogleSignInAccount googleAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        emailEditText = findViewById(R.id.delete_account);
        deleteAccountBtn = findViewById(R.id.confirm_delete_account_btn);
        backButton = findViewById(R.id.backButton);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        googleAccount = GoogleSignIn.getLastSignedInAccount(this);

        backButton.setOnClickListener(v -> finish());

        deleteAccountBtn.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentUser != null && email.equals(currentUser.getEmail())) {
                uid = currentUser.getUid();
                Log.d(TAG, "Email verified. UID: " + uid);

                new AlertDialog.Builder(this)
                        .setTitle("Are you sure?")
                        .setMessage("This will deactivate your account. Messages will remain, but profile and sets will show as 'not found'. This action is permanent.")
                        .setPositiveButton("Yes", (dialog, which) -> showReAuthDialog())
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            } else {
                Toast.makeText(this, "Email doesn't match your account.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReAuthDialog() {
        if (googleAccount != null) {
            AuthCredential googleCredential = GoogleAuthProvider.getCredential(googleAccount.getIdToken(), null);
            currentUser.reauthenticate(googleCredential)
                    .addOnSuccessListener(authResult -> deactivateUser(uid))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Google re-auth failed: " + e.getMessage());
                        Toast.makeText(this, "Re-authentication failed.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

            new AlertDialog.Builder(this)
                    .setTitle("Enter Password")
                    .setView(dialogView)
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        String password = passwordInput.getText().toString().trim();
                        if (TextUtils.isEmpty(password)) {
                            Toast.makeText(this, "Password required.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
                        currentUser.reauthenticate(credential)
                                .addOnSuccessListener(authResult -> deactivateUser(uid))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Email re-auth failed: " + e.getMessage());
                                    Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void deactivateUser(String userId) {
        Log.d(TAG, "Deactivating user and deleting owned sets...");

        db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
            List<Task<Void>> allTasks = new ArrayList<>();

            // Step 1: Flag as deleted and wipe owned/saved sets
            Task<Void> flagUser = db.collection("users").document(userId)
                    .update("isDeleted", true,
                            "firstName", "User",
                            "lastName", "Not Found",
                            "username", "UserNotFound",
                            "photoFileName", null,
                            "photoUrl", null,
                            "owned_sets", new ArrayList<>(),
                            "saved_sets", new ArrayList<>());
            allTasks.add(flagUser);

            // Step 2: Delete actual owned sets
            if (userDoc.exists() && userDoc.contains("owned_sets")) {
                List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");
                if (ownedSets != null) {
                    for (Map<String, Object> set : ownedSets) {
                        String setId = (String) set.get("id");
                        String type = (String) set.get("type");

                        if (setId != null && type != null) {
                            if (type.equals("flashcard")) {
                                Log.d(TAG, "Deleting flashcard set: " + setId);

                                Task<Void> deleteFlashcard = db.collection("flashcards").document(setId)
                                        .collection("flashcard_attempt")
                                        .get()
                                        .continueWithTask(attempts -> {
                                            List<Task<Void>> deletes = new ArrayList<>();
                                            for (DocumentSnapshot doc : attempts.getResult()) {
                                                deletes.add(doc.getReference().delete());
                                            }
                                            return Tasks.whenAll(deletes);
                                        })
                                        .continueWithTask(t -> db.collection("flashcards").document(setId).delete());

                                allTasks.add(deleteFlashcard);

                                deleteNestedFolder("flashcard-images/" + userId);

                            } else if (type.equals("quiz")) {
                                Log.d(TAG, "Deleting quiz set: " + setId);

                                Task<Void> deleteQuiz = db.collection("quiz").document(setId)
                                        .collection("quiz_attempt")
                                        .get()
                                        .continueWithTask(attempts -> {
                                            List<Task<Void>> deletes = new ArrayList<>();
                                            for (DocumentSnapshot doc : attempts.getResult()) {
                                                deletes.add(doc.getReference().delete());
                                            }
                                            return Tasks.whenAll(deletes);
                                        })
                                        .continueWithTask(t -> db.collection("quiz").document(setId).delete());

                                allTasks.add(deleteQuiz);

                                // 🔥 Delete quiz images
                                deleteNestedFolder("quiz_images/" + userId);
                            }
                        }
                    }
                }

                // Delete attempts from saved_sets
                if (userDoc.contains("saved_sets")) {
                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) userDoc.get("saved_sets");
                    if (savedSets != null) {
                        for (Map<String, Object> set : savedSets) {
                            String setId = (String) set.get("id");
                            String type = (String) set.get("type");

                            if (setId != null && type != null) {
                                if (type.equals("flashcard")) {
                                    Log.d(TAG, "Deleting flashcard_attempt from flashcard [" + setId + "] for user [" + userId + "]");
                                    allTasks.add(
                                            db.collection("flashcards")
                                                    .document(setId)
                                                    .collection("flashcard_attempt")
                                                    .document(userId)
                                                    .delete()
                                    );
                                } else if (type.equals("quiz")) {
                                    Log.d(TAG, "Deleting quiz_attempt from quiz [" + setId + "] for user [" + userId + "]");
                                    allTasks.add(
                                            db.collection("quiz")
                                                    .document(setId)
                                                    .collection("quiz_attempt")
                                                    .document(userId)
                                                    .delete()
                                    );
                                }
                            }
                        }
                    }
                }

            }

            // Step 6: Delete userprofile from storage
            deleteUserStorageFolder("user-profile/" + userId);

            // Step 3: Delete user_chat_status
            allTasks.add(FirebaseFirestore.getInstance()
                    .collection("user_chat_status")
                    .document(userId)
                    .delete()
            );

            // Step 4: Handle chatroom ownership/removal
            allTasks.add(handleChatRoomMemberships(userId));

            // Step 5: Delete account after all is done
            Tasks.whenAllComplete(allTasks)
                    .addOnSuccessListener(done -> {
                        currentUser.delete().addOnSuccessListener(task -> {
                            Log.d(TAG, "Firebase account deleted.");
                            Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to deactivate: " + e.getMessage());
                        Toast.makeText(this, "Deactivation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });


        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load user: " + e.getMessage());
            Toast.makeText(this, "Error fetching user data.", Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteUserStorageFolder(String folderPath) {
        StorageReference folderRef = FirebaseStorage.getInstance().getReference().child(folderPath);
        folderRef.listAll().addOnSuccessListener(listResult -> {
            for (StorageReference item : listResult.getItems()) {
                item.delete().addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Deleted file: " + item.getPath())
                ).addOnFailureListener(e ->
                        Log.e(TAG, "Failed to delete: " + item.getPath() + " - " + e.getMessage())
                );
            }
        }).addOnFailureListener(e ->
                Log.e(TAG, "Failed to list folder: " + folderPath + " - " + e.getMessage())
        );
    }

    private Task<Void> handleChatRoomMemberships(String userId) {
        return db.collection("chat_rooms")
                .whereArrayContains("members", userId)
                .get()
                .continueWithTask(task -> {
                    List<Task<Void>> updateTasks = new ArrayList<>();

                    for (DocumentSnapshot chatRoom : task.getResult()) {
                        String roomId = chatRoom.getId();
                        List<String> members = (List<String>) chatRoom.get("members");
                        String ownerId = chatRoom.getString("ownerId");

                        // ❌ Do not remove user from members
                        // ✅ Retain user in chatroom so messages stay linked

                        if (ownerId != null && ownerId.equals(userId)) {
                            if (members != null && members.size() > 1) {
                                // Choose new owner that isn't the current user
                                String newOwner = null;
                                for (String m : members) {
                                    if (!m.equals(userId)) {
                                        newOwner = m;
                                        break;
                                    }
                                }

                                if (newOwner != null) {
                                    updateTasks.add(chatRoom.getReference().update("ownerId", newOwner));
                                }
                            } else {
                                // If user is sole member, delete the chat room
                                updateTasks.add(chatRoom.getReference().delete());
                                deleteStorageFolder("chat-room-files/" + roomId);
                                deleteStorageFolder("chat_room_images/" + roomId);
                            }
                        }
                        // ❗ We no longer update members list if user is not owner
                    }

                    return Tasks.whenAll(updateTasks);
                });
    }

    private void deleteStorageFolder(String path) {
        StorageReference folderRef = FirebaseStorage.getInstance().getReference().child(path);
        folderRef.listAll().addOnSuccessListener(result -> {
            for (StorageReference item : result.getItems()) {
                item.delete().addOnSuccessListener(aVoid ->
                                Log.d(TAG, "Deleted: " + item.getPath()))
                        .addOnFailureListener(e ->
                                Log.w(TAG, "Failed to delete: " + item.getPath() + " | " + e.getMessage()));
            }
        }).addOnFailureListener(e ->
                Log.w(TAG, "Folder not found: " + path + " | " + e.getMessage()));
    }

    private void deleteNestedFolder(String basePath) {
        StorageReference baseRef = FirebaseStorage.getInstance().getReference().child(basePath);

        baseRef.listAll().addOnSuccessListener(result -> {
            // Loop through each subfolder like setId or quizId
            for (StorageReference subfolder : result.getPrefixes()) {
                subfolder.listAll().addOnSuccessListener(files -> {
                    for (StorageReference file : files.getItems()) {
                        file.delete().addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "Deleted file: " + file.getPath()))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Failed to delete: " + file.getPath() + " - " + e.getMessage()));
                    }
                }).addOnFailureListener(e ->
                        Log.e(TAG, "Failed to list subfolder: " + subfolder.getPath() + " - " + e.getMessage()));
            }
        }).addOnFailureListener(e ->
                Log.e(TAG, "Failed to list base folder: " + basePath + " - " + e.getMessage()));
    }

}
