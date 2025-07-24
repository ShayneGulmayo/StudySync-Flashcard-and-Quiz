package com.labactivity.studysync.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.NotificationModel;
import com.google.firebase.firestore.*;

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

        holder.itemView.setOnClickListener(v -> {
            if ("quiz".equals(model.getSetType())) {
                Intent intent = new Intent(context, QuizPreviewActivity.class);
                intent.putExtra("quizId", model.getSetId());
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(context, FlashcardPreviewActivity.class);
                intent.putExtra("setId", model.getSetId());
                context.startActivity(intent);
            }

            // mark as read
            db.collection("users").document(currentUserId)
                    .collection("notifications").document(model.getNotificationId())
                    .update("status", model.getStatus() == null ? "read" : model.getStatus());
        });
    }

    private void showConfirmationDialog(NotificationModel model, boolean isAccept) {
        new AlertDialog.Builder(context)
                .setTitle(isAccept ? "Allow access?" : "Deny request?")
                .setMessage("Are you sure you want to " + (isAccept ? "allow" : "deny") + " this request?")
                .setPositiveButton("Yes", (dialog, which) -> handleAccessRequest(model, isAccept))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleAccessRequest(NotificationModel model, boolean isAccept) {
        DocumentReference notifRef = db.collection("users").document(currentUserId)
                .collection("notifications").document(model.getNotificationId());

        String collection = "quiz".equals(model.getSetType()) ? "quiz" : "flashcards";

        if (isAccept) {
            DocumentReference setRef = db.collection(collection).document(model.getSetId());
            setRef.update("accessUsers." + model.getSenderId(), model.getRequestedRole())
                    .addOnSuccessListener(unused -> {
                        notifRef.update("status", "accepted");
                        Toast.makeText(context, "User granted access", Toast.LENGTH_SHORT).show();
                        notifyItemChanged(notifications.indexOf(model));
                    });
        } else {
            notifRef.update("status", "denied")
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(context, "Request denied", Toast.LENGTH_SHORT).show();
                        notifyItemChanged(notifications.indexOf(model));
                    });
        }

        // Notify sender (denied/accepted)
        Map<String, Object> reply = new HashMap<>();
        reply.put("senderId", currentUserId);
        reply.put("receiverId", model.getSenderId());
        reply.put("setId", model.getSetId());
        reply.put("setType", model.getSetType());
        reply.put("text", "Your request to access \"" + model.getSetId() + "\" has been " + (isAccept ? "accepted" : "denied") + ".");
        reply.put("type", "request_response");
        reply.put("timestamp", FieldValue.serverTimestamp());
        reply.put("status", isAccept ? "accepted" : "denied");

        DocumentReference responseDoc = db.collection("users")
                .document(model.getSenderId())
                .collection("notifications")
                .document();

        reply.put("notificationId", responseDoc.getId());

        responseDoc.set(reply);
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
