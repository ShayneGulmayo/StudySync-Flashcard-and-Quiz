package com.labactivity.studysync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "study_reminder_channel";
    private static final String CHANNEL_NAME = "Study Reminders";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String setId = intent.getStringExtra("setId");

        if (setId == null) {
            Log.e("ReminderReceiver", "No setId received.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Fetch title and clear the reminder
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Log.e("ReminderReceiver", "Flashcard set not found.");
                        return;
                    }

                    // Get title
                    String setTitle = documentSnapshot.getString("title");
                    if (setTitle == null || setTitle.isEmpty()) {
                        setTitle = "Untitled Flashcard";
                    }

                    // Clear reminder field
                    db.collection("flashcards").document(setId)
                            .update("reminder", null)
                            .addOnSuccessListener(aVoid -> Log.d("ReminderReceiver", "Reminder cleared"))
                            .addOnFailureListener(e -> Log.e("ReminderReceiver", "Failed to clear reminder", e));

                    // Send notification
                    sendNotification(context, setId, setTitle);

                })
                .addOnFailureListener(e -> Log.e("ReminderReceiver", "Failed to fetch flashcard set", e));
    }

    private void sendNotification(Context context, String setId, String setTitle) {
        createNotificationChannel(context);

        // Intent to open FlashcardViewerActivity
        Intent intent = new Intent(context, FlashcardPreviewActivity.class);
        intent.putExtra("setId", setId);
        intent.putExtra("fromNotification", true);

        // Ensure correct back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notifications)
                .setContentTitle("📚 Study Reminder")
                .setContentText("Time to review: " + setTitle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for flashcard reviews");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
