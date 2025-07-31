package com.labactivity.studysync.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.labactivity.studysync.R;
import com.labactivity.studysync.helpers.AlarmHelper;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String userId = intent.getStringExtra("userId");
        String setId = intent.getStringExtra("setId");
        boolean isRepeating = intent.getBooleanExtra("isRepeating", false);
        long triggerAt = intent.getLongExtra("triggerAt", -1L);

        if (title == null) title = "Study Reminder";

        // Reschedule if repeating
        if (isRepeating && userId != null && setId != null && triggerAt > 0) {
            Calendar nextTime = Calendar.getInstance();
            nextTime.setTimeInMillis(triggerAt);
            nextTime.add(Calendar.DAY_OF_YEAR, 1);
            AlarmHelper.setAlarm(context, nextTime, userId, setId, title, true);
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "study_reminder_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Study Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.studysync_logo)
                .setContentTitle(title)
                .setContentText("Time to study or review your set!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
