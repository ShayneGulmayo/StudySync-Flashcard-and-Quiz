package com.labactivity.studysync.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.NotificationModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {

    private final Context context;
    private final List<NotificationModel> notifications;
    private final FirebaseFirestore db;
    private final String currentUserId;

    public NotificationsAdapter(Context context, List<NotificationModel> notifications, FirebaseFirestore db, String currentUserId) {
        this.context = context;
        this.notifications = notifications;
        this.db = db;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel model = notifications.get(position);

        holder.notificationBodyTxt.setText(model.getText());

        if (model.getTimestamp() != null) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(model.getTimestamp().toDate().getTime());
            holder.timeStampAgo.setText(timeAgo);
        }

        boolean isInviteOrRequest = "request".equals(model.getType()) || "invite".equals(model.getType());
        boolean isHandled = !"pending".equals(model.getStatus());

        holder.acceptBtn.setVisibility(isInviteOrRequest && !isHandled ? View.VISIBLE : View.GONE);
        holder.denyBtn.setVisibility(isInviteOrRequest && !isHandled ? View.VISIBLE : View.GONE);
        holder.allowedIndicator.setVisibility("accepted".equals(model.getStatus()) ? View.VISIBLE : View.GONE);
        holder.deniedIndicator.setVisibility("denied".equals(model.getStatus()) ? View.VISIBLE : View.GONE);


        holder.acceptBtn.setOnClickListener(v -> showConfirmationDialog(model, true));
        holder.denyBtn.setOnClickListener(v -> showConfirmationDialog(model, false));

        // Handle item click
        holder.itemView.setOnClickListener(v -> {
            if (model.getSetId() != null) {
                if (!model.isRead()) {
                    db.collection("users")
                            .document(currentUserId)
                            .collection("notifications")
                            .document(model.getNotificationId())
                            .update("read", true)
                            .addOnSuccessListener(unused -> {
                                model.setRead(true);
                                notifyItemChanged(holder.getAdapterPosition());
                            });
                }
                openSet(model);
            }
        });

        // Highlight unread notifications
        if (!model.isRead()) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray)); // Define in colors.xml
            holder.notificationBodyTxt.setTypeface(null, Typeface.BOLD);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.notificationBodyTxt.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void openSet(NotificationModel model) {
        if (model.getSetType() == null || model.getSetId() == null) {
            Toast.makeText(context, "Invalid set data", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent;
        if ("quiz".equalsIgnoreCase(model.getSetType())) {
            intent = new Intent(context, QuizPreviewActivity.class);
            intent.putExtra("quizId", model.getSetId());
        } else if ("flashcard".equalsIgnoreCase(model.getSetType())) {
            intent = new Intent(context, FlashcardPreviewActivity.class);
            intent.putExtra("setId", model.getSetId());
        } else {
            Toast.makeText(context, "Unknown set type", Toast.LENGTH_SHORT).show();
            return;
        }

        context.startActivity(intent);
    }

    private void showConfirmationDialog(NotificationModel model, boolean isAccept) {
        if (!(context instanceof android.app.Activity)) return;

        android.app.Activity activity = (android.app.Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        new AlertDialog.Builder(activity)
                .setTitle(isAccept ? "Allow access?" : "Deny request?")
                .setMessage("Are you sure you want to " + (isAccept ? "allow" : "deny") + " this request?")
                .setPositiveButton("Yes", (dialog, which) -> handleAccess(model, isAccept))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleAccess(NotificationModel notif, boolean isAccept) {
        String newStatus = isAccept ? "accepted" : "denied";
        String collection = "flashcard".equals(notif.getSetType()) ? "flashcards" : "quiz";

        // Update the current user's notification status
        DocumentReference notifRef = db.collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document(notif.getNotificationId());

        notifRef.update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    notif.setStatus(newStatus);
                    notifyDataSetChanged();

                    DocumentReference setRef = db.collection(collection).document(notif.getSetId());

                    if (isAccept && notif.getRequestedRole() != null) {
                        // Change role from Temporary View to Editor
                        setRef.update("accessUsers." + currentUserId, "Editor")
                                .addOnSuccessListener(unused2 -> Log.d("handleAccess", "Role updated to Editor"))
                                .addOnFailureListener(e -> Log.e("handleAccess", "Failed to promote to Editor", e));

                        DocumentReference userRef = db.collection("users").document(currentUserId);
                        Map<String, Object> savedSet = new HashMap<>();
                        savedSet.put("id", notif.getSetId());
                        savedSet.put("type", notif.getSetType());

                        userRef.update("saved_sets", FieldValue.arrayUnion(savedSet))
                                .addOnSuccessListener(unused3 -> Log.d("handleAccess", "Set added to saved_sets"))
                                .addOnFailureListener(e -> Log.e("handleAccess", "Failed to add set to saved_sets", e));

                    } else if (!isAccept) {
                        // Remove user from accessUsers
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("accessUsers." + currentUserId, FieldValue.delete());

                        setRef.update(updates)
                                .addOnSuccessListener(unused2 -> Log.d("handleAccess", "User removed from accessUsers"))
                                .addOnFailureListener(e -> Log.e("handleAccess", "Failed to remove user from accessUsers", e));
                    }


                    // Fetch the set title to use in the response message
                    db.collection(collection).document(notif.getSetId())
                            .get()
                            .addOnSuccessListener(setSnap -> {
                                String title = setSnap.getString("title");
                                if (title == null || title.isEmpty()) {
                                    title = notif.getSetTitle(); // fallback
                                }

                                Map<String, Object> reply = new HashMap<>();
                                reply.put("setId", notif.getSetId());
                                reply.put("setType", notif.getSetType());
                                reply.put("timestamp", FieldValue.serverTimestamp());
                                reply.put("read", false);
                                reply.put("type", "info");

                                // Determine if it was an invitation or a request
                                boolean wasInvite = "invite".equals(notif.getType());
                                String role = notif.getRequestedRole();

                                if (wasInvite) {
                                    // Receiver (current user) was invited
                                    reply.put("text", "Your invitation to " + notif.getReceiverName() +
                                            " to be an \"" + role + "\" in \"" + title + "\" was " + newStatus + ".");
                                    // Send reply to the sender (the one who invited)
                                    db.collection("users")
                                            .document(notif.getSenderId())
                                            .collection("notifications")
                                            .add(reply)
                                            .addOnFailureListener(e -> Log.e("handleAccess", "Failed to send reply to inviter", e));
                                } else {
                                    // Sender (requesting user) will get reply
                                    reply.put("text", "Your request to have access as \"" + role + "\" in \"" +
                                            title + "\" has been " + newStatus + ".");
                                    db.collection("users")
                                            .document(notif.getSenderId())
                                            .collection("notifications")
                                            .add(reply)
                                            .addOnFailureListener(e -> Log.e("handleAccess", "Failed to send reply to requester", e));
                                }
                            })
                            .addOnFailureListener(e -> Log.e("handleAccess", "Failed to fetch set title", e));
                })
                .addOnFailureListener(e -> Log.e("handleAccess", "Failed to update notification status", e));
    }


    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView notificationBodyTxt, timeStampAgo;
        ImageView acceptBtn, denyBtn, allowedIndicator, deniedIndicator;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            notificationBodyTxt = itemView.findViewById(R.id.notificationBodyTxt);
            timeStampAgo = itemView.findViewById(R.id.timeStampAgo);
            acceptBtn = itemView.findViewById(R.id.acceptBtn);
            denyBtn = itemView.findViewById(R.id.denyBtn);
            allowedIndicator = itemView.findViewById(R.id.allowedIndicator);
            deniedIndicator = itemView.findViewById(R.id.deniedIndicator);
        }
    }
}
