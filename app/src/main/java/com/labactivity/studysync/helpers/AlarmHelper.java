package com.labactivity.studysync.helpers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.labactivity.studysync.receivers.ReminderReceiver;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class AlarmHelper {

    private static final String PREF_NAME = "user_reminders";

    public static void setAlarm(Context context, Calendar calendar, String userId, String setId, String title, boolean isRepeating) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = buildIntent(context, userId, setId, title, isRepeating, calendar.getTimeInMillis());
        int requestCode = buildRequestCode(userId, setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !alarmManager.canScheduleExactAlarms()) {
                    Toast.makeText(context, "Exact alarm permission not granted.", Toast.LENGTH_LONG).show();
                    Log.w("AlarmHelper", "Exact alarm permission not granted.");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                }

                saveReminder(context, userId, setId);
                Log.d("AlarmHelper", "Alarm set for: " + calendar.getTime() + (isRepeating ? " (Daily)" : ""));
            } catch (SecurityException e) {
                e.printStackTrace();
                Toast.makeText(context, "Failed to set alarm: missing permission", Toast.LENGTH_LONG).show();
                Log.e("AlarmHelper", "SecurityException: " + e.getMessage());
            }
        }
    }

    public static void cancelAlarm(Context context, String userId, String setId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = buildIntent(context, userId, setId, "", false, 0);
        int requestCode = buildRequestCode(userId, setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d("AlarmHelper", "Alarm cancelled for setId: " + setId);
        }

        SharedPreferences reminderPrefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE);
        reminderPrefs.edit()
                .remove(userId + "_" + setId + "_reminderTime")
                .remove(userId + "_" + setId + "_isRepeating")
                .apply();

        removeReminder(context, userId, setId);
    }


    public static boolean isReminderSet(Context context, String userId, String setId) {
        SharedPreferences prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE);
        long reminderTime = prefs.getLong(userId + "_" + setId + "_reminderTime", -1);
        boolean isRepeating = prefs.getBoolean(userId + "_" + setId + "_isRepeating", false);

        if (reminderTime != -1 && !isRepeating && reminderTime < System.currentTimeMillis()) {
            cancelAlarm(context, userId, setId);
            prefs.edit()
                    .remove(userId + "_" + setId + "_reminderTime")
                    .remove(userId + "_" + setId + "_isRepeating")
                    .apply();
            return false;
        }

        Intent intent = buildIntent(context, userId, setId, "", false, 0);
        int requestCode = buildRequestCode(userId, setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        return pendingIntent != null;
    }


    public static void cancelAllReminders(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> setIds = prefs.getStringSet(userId, new HashSet<>());
        if (setIds != null) {
            for (String setId : setIds) {
                cancelAlarm(context, userId, setId);
            }
        }
        prefs.edit().remove(userId).apply();
    }

    private static Intent buildIntent(Context context, String userId, String setId, String title, boolean isRepeating, long triggerAt) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.labactivity.studysync.REMINDER_" + userId + "_" + setId);
        intent.setData(Uri.parse("studysync://reminder/" + userId + "/" + setId));
        intent.putExtra("userId", userId);
        intent.putExtra("setId", setId);
        intent.putExtra("title", title);
        intent.putExtra("isRepeating", isRepeating);
        intent.putExtra("triggerAt", triggerAt);
        return intent;
    }

    private static int buildRequestCode(String userId, String setId) {
        return (userId + ":" + setId).hashCode();
    }

    private static void saveReminder(Context context, String userId, String setId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> setIds = new HashSet<>(prefs.getStringSet(userId, new HashSet<>()));
        setIds.add(setId);
        prefs.edit().putStringSet(userId, setIds).apply();
    }

    private static void removeReminder(Context context, String userId, String setId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> setIds = new HashSet<>(prefs.getStringSet(userId, new HashSet<>()));
        setIds.remove(setId);
        prefs.edit().putStringSet(userId, setIds).apply();
    }
}
