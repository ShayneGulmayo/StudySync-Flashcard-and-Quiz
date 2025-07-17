package com.labactivity.studysync.helpers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.labactivity.studysync.receivers.ReminderReceiver;

import java.util.Calendar;

public class AlarmHelper {

    // Updated to accept setId
    public static void setAlarm(Context context, Calendar calendar, String setId, String title, boolean isRepeating) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("setId", setId); // Optional: Pass setId to the receiver if needed

        int requestCode = setId.hashCode(); // Unique and consistent ID
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Toast.makeText(context, "Exact alarm permission not granted.", Toast.LENGTH_LONG).show();
                        Log.w("AlarmHelper", "Exact alarm permission not granted.");
                        return;
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                }


                Log.d("AlarmHelper", "Alarm set for: " + calendar.getTime() + (isRepeating ? " (Daily)" : ""));
            } catch (SecurityException e) {
                e.printStackTrace();
                Toast.makeText(context, "Failed to set alarm: missing permission", Toast.LENGTH_LONG).show();
                Log.e("AlarmHelper", "SecurityException: " + e.getMessage());
            }
        }
    }

    // Cancel specific alarm by setId
    public static void cancelAlarm(Context context, String setId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ReminderReceiver.class);

        int requestCode = setId.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d("AlarmHelper", "Alarm cancelled for setId: " + setId);
        }
    }

    // Check if reminder is set for this setId
    public static boolean isReminderSet(Context context, String setId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        int requestCode = setId.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        return pendingIntent != null;
    }
}
