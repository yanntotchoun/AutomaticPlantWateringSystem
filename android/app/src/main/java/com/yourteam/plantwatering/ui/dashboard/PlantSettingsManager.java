package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

public class PlantSettingsManager {

    private static final String SETTINGS_FILE = "plant_app_settings";

    private static final String KEY_DRY_SOIL_THRESHOLD = "dry_soil_threshold";
    private static final String KEY_FULL_TANK_THRESHOLD = "full_tank_threshold";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_LOW_HUMIDITY_ALERTS = "low_humidity_alerts";
    private static final String KEY_LOW_TANK_ALERTS = "low_tank_alerts";

    private static final String KEY_PROFILE_IDS = "profile_ids";

    private final SharedPreferences prefs;

    public PlantSettingsManager(Context context) {
        this.prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
    }


    public int getDrySoilThreshold() {
        return prefs.getInt(KEY_DRY_SOIL_THRESHOLD, 30);
    }

    public void setDrySoilThreshold(int value) {
        prefs.edit().putInt(KEY_DRY_SOIL_THRESHOLD, value).apply();
    }

    public int getFullTankThreshold() {
        return prefs.getInt(KEY_FULL_TANK_THRESHOLD, 70);
    }

    public void setFullTankThreshold(int value) {
        prefs.edit().putInt(KEY_FULL_TANK_THRESHOLD, value).apply();
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


    public static class ThresholdProfile {
        public final String id;
        public String name;
        public int drySoil;
        public int fullTank;

        public ThresholdProfile(String id, String name, int drySoil, int fullTank) {
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
        int defaultTank = 70;

        String name = prefs.getString("threshold_name_" + effectiveId, defaultName);
        int dry = prefs.getInt("threshold_dry_" + effectiveId, defaultDry);
        int tank = prefs.getInt("threshold_tank_" + effectiveId, defaultTank);
        
        return new ThresholdProfile(effectiveId, name, dry, tank);
    }

    private ThresholdProfile getDefaultProfile() {
        return getThresholdProfile("standard");
    }

    public void saveThresholdProfile(ThresholdProfile profile) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("threshold_name_" + profile.id, profile.name)
                .putInt("threshold_dry_" + profile.id, profile.drySoil)
                .putInt("threshold_tank_" + profile.id, profile.fullTank);

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
            saveThresholdProfile(new ThresholdProfile("tropical", "Tropical", 50, 80));
        }
        if (!prefs.contains("threshold_name_succulent")) {
            saveThresholdProfile(new ThresholdProfile("succulent", "Succulent", 15, 60));
        }
    }
}