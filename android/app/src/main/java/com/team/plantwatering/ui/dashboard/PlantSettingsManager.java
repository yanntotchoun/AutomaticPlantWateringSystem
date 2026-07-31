package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

public class PlantSettingsManager {

    private static final String SETTINGS_FILE = "plant_app_settings";

    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_LOW_HUMIDITY_ALERTS = "low_humidity_alerts";
    private static final String KEY_LOW_TANK_ALERTS = "low_tank_alerts";
    private static final String KEY_DISCONNECTION_ALERTS = "disconnection_alerts";
    private static final String KEY_WATERING_REMINDERS_ENABLED = "watering_reminders_enabled";

    private static final String KEY_PROFILE_IDS = "profile_ids";
    private static final String KEY_REMINDER_FREQUENCY = "reminder_frequency";

    private final SharedPreferences prefs;

    public PlantSettingsManager(Context context) {
        this.prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
    }


    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isLowHumidityAlertsEnabled() {
        return prefs.getBoolean(KEY_LOW_HUMIDITY_ALERTS, true);
    }

    public void setLowHumidityAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOW_HUMIDITY_ALERTS, enabled).apply();
    }

    public boolean isLowTankAlertsEnabled() {
        return prefs.getBoolean(KEY_LOW_TANK_ALERTS, true);
    }

    public void setLowTankAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOW_TANK_ALERTS, enabled).apply();
    }

    public boolean isDisconnectionAlertsEnabled() {
        return prefs.getBoolean(KEY_DISCONNECTION_ALERTS, true);
    }

    public void setDisconnectionAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DISCONNECTION_ALERTS, enabled).apply();
    }

    public boolean isWateringRemindersEnabled() {
        return prefs.getBoolean(KEY_WATERING_REMINDERS_ENABLED, true);
    }

    public void setWateringRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WATERING_REMINDERS_ENABLED, enabled).apply();
    }
    public String getReminderFrequency() {
        return prefs.getString(KEY_REMINDER_FREQUENCY, "Every day");
    }

    public void setReminderFrequency(String frequency) {
        prefs.edit().putString(KEY_REMINDER_FREQUENCY, frequency).apply();
    }

    public static class ThresholdProfile {
        public final String id;
        public String name;
        public int drySoil;
        public String fullTank;

        public ThresholdProfile(String id, String name, int drySoil, String fullTank) {
            this.id = id;
            this.name = name;
            this.drySoil = drySoil;
            this.fullTank = fullTank;
        }
    }

    public ThresholdProfile getThresholdProfile(String id) {
        String effectiveId = (id == null) ? "standard" : id;
        
        String defaultName = "standard".equals(effectiveId) ? "Standard" : "Custom Profile";
        int defaultDry = 30;
        String defaultTank = "Sufficient";

        String name = prefs.getString("threshold_name_" + effectiveId, defaultName);
        int dry = prefs.getInt("threshold_dry_" + effectiveId, defaultDry);
        
        String tank;
        try {
            tank = prefs.getString("threshold_tank_" + effectiveId, defaultTank);
        } catch (ClassCastException e) {
            // Fallback for legacy data where tank threshold was stored as an integer percentage (0-100)
            // instead of a descriptive string (e.g. "Low", "Sufficient").
            int tankInt = prefs.getInt("threshold_tank_" + effectiveId, 0);
            tank = (tankInt < 30) ? "Low" : "Sufficient";
            // Auto-fix the preference to prevent future crashes
            prefs.edit().putString("threshold_tank_" + effectiveId, tank).apply();
        }
        
        return new ThresholdProfile(effectiveId, name, dry, tank);
    }

    private ThresholdProfile getDefaultProfile() {
        return getThresholdProfile("standard");
    }

    public void saveThresholdProfile(ThresholdProfile profile) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("threshold_name_" + profile.id, profile.name)
                .putInt("threshold_dry_" + profile.id, profile.drySoil)
                .putString("threshold_tank_" + profile.id, profile.fullTank);

        if (!"standard".equals(profile.id)) {
            java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet(KEY_PROFILE_IDS, new java.util.HashSet<>()));
            if (ids.add(profile.id)) {
                editor.putStringSet(KEY_PROFILE_IDS, ids);
            }
        }
        editor.apply();
    }

    public void deleteThresholdProfile(String id) {
        if ("standard".equals(id)) return;

        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("threshold_name_" + id)
                .remove("threshold_dry_" + id)
                .remove("threshold_tank_" + id);

        java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet(KEY_PROFILE_IDS, new java.util.HashSet<>()));
        if (ids.remove(id)) {
            editor.putStringSet(KEY_PROFILE_IDS, ids);
        }
        editor.apply();
    }

    public java.util.List<ThresholdProfile> getAllProfiles() {
        java.util.List<ThresholdProfile> profiles = new java.util.ArrayList<>();
        profiles.add(getDefaultProfile());
        
        java.util.Set<String> ids = prefs.getStringSet(KEY_PROFILE_IDS, new java.util.HashSet<>());
        for (String id : ids) {
            profiles.add(getThresholdProfile(id));
        }
        return profiles;
    }

    // Initialize some profiles if they don't exist
    public void initDefaultProfiles() {
        if (!prefs.contains("threshold_name_tropical")) {
            saveThresholdProfile(new ThresholdProfile("tropical", "Tropical", 50, "Sufficient"));
        }
        if (!prefs.contains("threshold_name_succulent")) {
            saveThresholdProfile(new ThresholdProfile("succulent", "Succulent", 15, "Low"));
        }
    }
}