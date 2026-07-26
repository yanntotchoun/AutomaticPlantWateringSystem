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

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.team.plantwatering.R;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReminderWorker extends Worker {

    private static final String TAG = "ReminderWorker";
    private static final String CHANNEL_ID = "watering_reminders";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: Checking plant status");
        
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

        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("plants");
        
        try {
            // Synchronously fetch data from Firebase (safe because WorkManager runs on background thread)
            DataSnapshot snapshot = Tasks.await(dbRef.get(), 10, TimeUnit.SECONDS);
            
            for (DataSnapshot plantSnapshot : snapshot.getChildren()) {
                String plantName = plantSnapshot.getKey();
                Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class);
                Integer tankLevel = plantSnapshot.child("water_tank").getValue(Integer.class);
                String profileId = plantSnapshot.child("threshold_profile").getValue(String.class);
                Long lastSeen = plantSnapshot.child("last_seen_millis").getValue(Long.class);

                if (plantName == null) continue;

                // Check Disconnection
                if (settingsManager.isDisconnectionAlertsEnabled() && lastSeen != null) {
                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - lastSeen) > 120_000L) { // 2 minutes threshold
                        sendNotification(
                                plantName.hashCode() + 3,
                                "Device Offline: " + plantName,
                                "The device hasn't been seen for over 2 minutes. Please check your connection."
                        );
                    }
                }

                if (moisture == null || tankLevel == null) continue;

                PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(profileId);

                // Check Humidity
                if (settingsManager.isLowHumidityAlertsEnabled() && moisture < profile.drySoil) {
                    sendNotification(
                        plantName.hashCode() + 1, 
                        "Thirsty Plant: " + plantName, 
                        "Humidity is at " + moisture + "%, which is below the " + profile.name + " threshold (" + profile.drySoil + "%)."
                    );
                }

                // Check Tank
                if (settingsManager.isLowTankAlertsEnabled() && tankLevel < profile.fullTank) {
                    // Note: Here "fullTank" is actually used as a minimum threshold for the alert? 
                    // Usually tank alerts happen when level is LOW. 
                    // Let's assume the user wants an alert if it's below the "full tank" threshold? 
                    // Or maybe there's a separate "low tank" threshold? 
                    // The settings only has "full_tank_threshold". 
                    // If the tank level is less than the "full" threshold, it might mean it's not full anymore.
                    // But usually people want an alert when it's critically low (e.g. < 20%).
                    // For now, I'll follow the user's instruction: "less than the threshold that is attributed to the plant"
                    sendNotification(
                        plantName.hashCode() + 2, 
                        "Low Water Tank: " + plantName, 
                        "The tank level is at " + tankLevel + "%, which is below your threshold (" + profile.fullTank + "%)."
                    );
                }
            }

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG, "Error fetching data from Firebase", e);
            return Result.retry();
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
