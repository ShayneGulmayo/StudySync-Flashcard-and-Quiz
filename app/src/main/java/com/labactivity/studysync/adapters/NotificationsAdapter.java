package com.labactivity.studysync.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
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

        boolean isRequest = "request".equals(model.getType());
        boolean isHandled = !"pending".equals(model.getStatus());

        holder.acceptBtn.setVisibility(isRequest && !isHandled ? View.VISIBLE : View.GONE);
        holder.denyBtn.setVisibility(isRequest && !isHandled ? View.VISIBLE : View.GONE);
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

        DocumentReference notifRef = db.collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document(notif.getNotificationId());

        notifRef.update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    notif.setStatus(newStatus);
                    notifyDataSetChanged();

                    if (isAccept) {
                        String collection = "flashcard".equals(notif.getSetType()) ? "flashcards" : "quiz";
                        db.collection(collection).document(notif.getSetId())
                                .update("accessUsers." + notif.getSenderId(), notif.getRequestedRole());
                    }

                    String collection = "flashcard".equals(notif.getSetType()) ? "flashcards" : "quiz";
                    db.collection(collection).document(notif.getSetId())
                            .get()
                            .addOnSuccessListener(setSnap -> {
                                String title = setSnap.getString("title");
                                if (title == null) title = notif.getSetTitle();

                                Map<String, Object> reply = new HashMap<>();
                                reply.put("setId", notif.getSetId());
                                reply.put("setType", notif.getSetType());
                                reply.put("text", "Your request to have access as \"" + notif.getRequestedRole() + " in " + title + "\" has been " + newStatus + ".");
                                reply.put("timestamp", FieldValue.serverTimestamp());
                                reply.put("type", "info");
                                reply.put("read", false); // Mark reply as unread

                                db.collection("users")
                                        .document(notif.getSenderId())
                                        .collection("notifications")
                                        .add(reply);
                            });
                });
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
