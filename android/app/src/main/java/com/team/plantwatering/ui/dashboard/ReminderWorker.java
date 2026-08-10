package com.team.plantwatering.ui.dashboard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.team.plantwatering.R;

public class ReminderWorker extends Worker {

    private static final String TAG = "ReminderWorker";
    private static final String CHANNEL_ID = "watering_reminders";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: Sending generic watering reminder");

        Context context = getApplicationContext();
        PlantSettingsManager settingsManager = new PlantSettingsManager(context);

        // If all notifications are disabled, stop here
        if (!settingsManager.isNotificationsEnabled()) {
            return Result.success();
        }

        // Send generic watering reminder if enabled
        if (settingsManager.isWateringRemindersEnabled()) {
            sendNotification(
                    0, // Constant ID for generic reminder
                    context.getString(R.string.reminder_notification_title),
                    context.getString(R.string.reminder_notification_text)
            );
        }

        return Result.success();
    }

    private void sendNotification(int id, String title, String text) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Plant Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(id, builder.build());
    }
}
