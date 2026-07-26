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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReminderWorker extends Worker {

    private static final String TAG = "ReminderWorker";
    private static final String CHANNEL_ID = "watering_reminders";

    private final SimpleDateFormat firmwareDateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());

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

        try {
            // Server-synced time, fetched once per run, so the disconnection check
            // isn't vulnerable to this device's local clock drifting.
            long currentServerTime = fetchServerTime();

            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("plants");
            // Synchronously fetch data from Firebase (safe because WorkManager runs on background thread)
            DataSnapshot snapshot = Tasks.await(dbRef.get(), 10, TimeUnit.SECONDS);

            for (DataSnapshot plantSnapshot : snapshot.getChildren()) {
                String key = plantSnapshot.getKey();
                if (key == null) continue;

                // Display name lives in a "name" field now; fall back to the node key
                // for older-format entries, same as PlantViewModel does.
                String plantName = plantSnapshot.child("name").getValue(String.class);
                if (plantName == null) plantName = key;

                Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class);

                // water_level is stored as a descriptive String by the firmware
                // (e.g. "Sufficient water is available"), not a numeric tank percentage.
                String waterStr = plantSnapshot.child("water_level").getValue(String.class);
                Integer tankLevel = null;
                if (waterStr != null) {
                    tankLevel = waterStr.contains("Sufficient") ? 100 : 10;
                }

                String profileId = plantSnapshot.child("threshold_profile").getValue(String.class);

                // last_time is a formatted date String, not raw millis - parse it the
                // same way PlantViewModel.parseFirmwareTimeToMillis does.
                String timeStr = plantSnapshot.child("last_time").getValue(String.class);
                Long lastSeen = parseFirmwareTimeToMillis(timeStr);

                // Check Disconnection
                if (settingsManager.isDisconnectionAlertsEnabled() && lastSeen != null && lastSeen > 0L) {
                    if ((currentServerTime - lastSeen) > 120_000L) { // 2 minutes threshold
                        sendNotification(
                                plantName.hashCode() + 3,
                                "Device Offline: " + plantName,
                                "The device hasn't been seen for over 2 minutes. Please check your connection."
                        );
                    }
                }

                if (moisture == null || tankLevel == null || profileId == null) continue;

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
                // tankLevel here is a coarse 100/10 signal derived from the water_level
                // message, not a real percentage - so this compares against fullTank
                // as a simple "is the tank in the low state" check.
                if (settingsManager.isLowTankAlertsEnabled() && tankLevel < profile.fullTank) {
                    sendNotification(
                            plantName.hashCode() + 2,
                            "Low Water Tank: " + plantName,
                            "The water tank needs a refill."
                    );
                }
            }

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG, "Error fetching data from Firebase", e);
            return Result.retry();
        }

        return Result.success();
    }

    private long fetchServerTime() {
        try {
            DatabaseReference offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");
            DataSnapshot snapshot = Tasks.await(offsetRef.get(), 10, TimeUnit.SECONDS);
            Long offset = snapshot.getValue(Long.class);
            if (offset != null) {
                return System.currentTimeMillis() + offset;
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.w(TAG, "Could not fetch server time offset, falling back to device clock", e);
        }
        return System.currentTimeMillis();
    }

    private long parseFirmwareTimeToMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0L;
        try {
            Date date = firmwareDateFormat.parse(timeStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                int currentYear = cal.get(Calendar.YEAR);

                cal.setTime(date);
                cal.set(Calendar.YEAR, currentYear); // "Guessing" the year is the current year

                return cal.getTimeInMillis();
            }
        } catch (ParseException e) {
            return 0L;
        }
        return 0L;
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