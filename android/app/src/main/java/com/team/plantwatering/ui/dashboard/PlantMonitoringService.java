package com.team.plantwatering.ui.dashboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.team.plantwatering.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlantMonitoringService extends Service {
    private static final String TAG = "PlantMonitoringService";
    private static final String CHANNEL_ID = "plant_monitoring_service";
    private static final String ALERTS_CHANNEL_ID = "watering_reminders";
    private static final int ONGOING_NOTIFICATION_ID = 1001;

    public static final String ACTION_REFRESH_SETTINGS = "com.team.plantwatering.REFRESH_SETTINGS";

    private DatabaseReference databaseReference;
    private ValueEventListener plantsListener;
    private ValueEventListener globalLastTimeListener;
    private long globalLastSeenMillis = 0L;
    private PlantSettingsManager settingsManager;
    private final SimpleDateFormat firmwareDateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.US);

    private boolean isInitialProcessing = true;

    private final Handler statusCheckHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkPlantStatus();
            statusCheckHandler.postDelayed(this, 60_000L); // Check every minute
        }
    };

    // To track last values and states to avoid redundant notifications
    private final Map<String, Integer> lastMoistureValues = new HashMap<>();
    private final Map<String, Integer> lastThresholds = new HashMap<>();
    private final Map<String, Boolean> lastBelowThresholdStates = new HashMap<>();
    private final Map<String, Boolean> lastOfflineStates = new HashMap<>();
    private final Map<String, String> lastTankStates = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        settingsManager = new PlantSettingsManager(this);
        createNotificationChannels();
        startForeground(ONGOING_NOTIFICATION_ID, createOngoingNotification());

        databaseReference = FirebaseDatabase.getInstance().getReference("plants");
        setupPlantsListener();
        setupGlobalLastTimeListener();

        statusCheckHandler.post(statusCheckRunnable);
    }

    private void setupGlobalLastTimeListener() {
        globalLastTimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String timeStr = snapshot.getValue(String.class);
                if (timeStr != null) {
                    globalLastSeenMillis = parseFirmwareTimeToMillis(timeStr);
                    checkPlantStatus();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseDatabase.getInstance().getReference("last_time").addValueEventListener(globalLastTimeListener);
    }

    private void setupPlantsListener() {
        plantsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                processPlantsSnapshot(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
            }
        };
        databaseReference.addValueEventListener(plantsListener);
    }

    private void checkPlantStatus() {
        if (databaseReference != null) {
            databaseReference.get().addOnSuccessListener(this::processPlantsSnapshot);
        }
    }

    private void processPlantsSnapshot(DataSnapshot snapshot) {
        if (!snapshot.exists()) return;

        Log.d(TAG, "Processing snapshot with " + snapshot.getChildrenCount() + " plants. Initial: " + isInitialProcessing);
        if (!settingsManager.isNotificationsEnabled()) {
            return;
        }

        long currentServerTime = System.currentTimeMillis();

        for (DataSnapshot plantSnapshot : snapshot.getChildren()) {
            String plantId = plantSnapshot.getKey();
            if (plantId == null || plantId.startsWith(".") || plantId.equals("logs")) continue;

            Object takenObj = plantSnapshot.child("taken").getValue();
            Integer takenInt = parseSafeInt(takenObj);
            if (takenInt == null || takenInt != 1) continue;

            String plantName = plantSnapshot.child("name").getValue(String.class);
            if (plantName == null) plantName = plantId;

            // 1. Humidity / Moisture Check
            //
            // Edge-triggered: notify only the moment moisture crosses
            // from "at/above threshold" to "below threshold" (or the
            // threshold itself moves down past the current moisture).
            // Do NOT notify on every incremental drop while already
            // below the threshold - that fires repeatedly as soil
            // keeps naturally drying out or the sensor reading drifts.
            Integer moisture = parseSafeInt(plantSnapshot.child("moisture_level").getValue());
            String profileId = plantSnapshot.child("threshold_profile").getValue(String.class);

            if (moisture != null && profileId != null) {
                PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(profileId);

                boolean isNowBelow = moisture < profile.drySoil;
                Boolean wasBelow = lastBelowThresholdStates.get(plantId);

                if (settingsManager.isLowHumidityAlertsEnabled() && !isInitialProcessing) {
                    boolean justCrossedBelow = isNowBelow && (wasBelow == null || !wasBelow);

                    if (justCrossedBelow) {
                        sendInstantNotification(plantId.hashCode() + 1,
                                "Thirsty Plant: " + plantName,
                                "Humidity is at " + moisture + "%, which is below the " + profile.name + " threshold (" + profile.drySoil + "%).",
                                "humidity_" + plantId);
                    }
                }

                lastBelowThresholdStates.put(plantId, isNowBelow);
                lastMoistureValues.put(plantId, moisture);
                lastThresholds.put(plantId, profile.drySoil);
            }

            // 2. Tank Check
            String waterStr = plantSnapshot.child("water_level").getValue(String.class);
            if (settingsManager.isLowTankAlertsEnabled() && waterStr != null) {
                String lowerWater = waterStr.toLowerCase();
                boolean isNowLow = lowerWater.contains("low") || lowerWater.contains("empty") || lowerWater.contains("insufficient");

                String lastState = lastTankStates.get(plantId);
                boolean wasLow = false;
                if (lastState != null) {
                    String lowerLast = lastState.toLowerCase();
                    wasLow = lowerLast.contains("low") || lowerLast.contains("empty") || lowerLast.contains("insufficient");
                }

                if (isNowLow && !wasLow && !isInitialProcessing) {
                    sendInstantNotification(plantId.hashCode() + 2,
                            "Water Level Low: " + plantName,
                            "The water tank is " + waterStr + ". Please refill it.",
                            "tank_" + plantId);
                }
                lastTankStates.put(plantId, waterStr);
            }

            // 3. Disconnection Check
            if (settingsManager.isDisconnectionAlertsEnabled() && globalLastSeenMillis > 0) {
                // Device is considered offline if not seen for over 2 minutes (120,000ms)
                boolean isCurrentlyOffline = (currentServerTime - globalLastSeenMillis) > 120_000L;
                Boolean wasOffline = lastOfflineStates.get(plantId);

                if (isCurrentlyOffline) {
                    if ((wasOffline == null || !wasOffline) && !isInitialProcessing) {
                        Log.d(TAG, "Device " + plantName + " just went OFFLINE");
                        sendInstantNotification(plantId.hashCode() + 3,
                                "Device Offline: " + plantName,
                                "The device hasn't been seen for over 2 minutes.",
                                "offline_" + plantId);
                    }
                    lastOfflineStates.put(plantId, true);
                } else {
                    lastOfflineStates.put(plantId, false);
                }
            }
        }

        // After the first full pass where we actually had the time data, stop skipping notifications
        if (isInitialProcessing && globalLastSeenMillis > 0) {
            Log.d(TAG, "Initial state captured for all plants. Monitoring for changes now.");
            isInitialProcessing = false;
        }
    }

    private void sendInstantNotification(int id, String title, String text, String alertKey) {
        Log.d(TAG, "Actually sending notification: " + title);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        notificationManager.notify(id, builder.build());
    }

    private long parseFirmwareTimeToMillis(String timeStr) {
        try {
            Date date = firmwareDateFormat.parse(timeStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                cal.setTime(date);
                cal.set(Calendar.YEAR, year);
                return cal.getTimeInMillis();
            }
        } catch (ParseException ignored) {}
        return 0L;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Monitoring Status", NotificationManager.IMPORTANCE_MIN);
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);

            NotificationChannel alertsChannel = new NotificationChannel(
                    ALERTS_CHANNEL_ID, "Plant Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertsChannel.enableVibration(true);
            manager.createNotificationChannel(alertsChannel);
        }
    }

    private Integer parseSafeInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof Double) return ((Double) value).intValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Notification createOngoingNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Plant Monitoring Active")
                .setContentText("Listening for real-time plant updates...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_SETTINGS.equals(intent.getAction())) {
            Log.d(TAG, "Refreshing settings and checking status");
            checkPlantStatus();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (databaseReference != null && plantsListener != null) {
            databaseReference.removeEventListener(plantsListener);
        }
        if (globalLastTimeListener != null) {
            FirebaseDatabase.getInstance().getReference("last_time").removeEventListener(globalLastTimeListener);
        }
        statusCheckHandler.removeCallbacks(statusCheckRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}