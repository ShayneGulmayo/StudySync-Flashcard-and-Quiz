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
        String setId = intent.getStringExtra("setId");
        boolean isRepeating = intent.getBooleanExtra("isRepeating", false);

        if (title == null) title = "Study Reminder";

        if (isRepeating) {
            Calendar nextDay = Calendar.getInstance();
            nextDay.add(Calendar.DAY_OF_YEAR, 1);

            // Reset to original alarm time
            nextDay.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
            nextDay.set(Calendar.MINUTE, Calendar.getInstance().get(Calendar.MINUTE));
            nextDay.set(Calendar.SECOND, 0);
            nextDay.set(Calendar.MILLISECOND, 0);

            AlarmHelper.setAlarm(context, nextDay, setId, title, true);
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
