package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Reads and writes app settings via SharedPreferences.
 * Ported from the persistence logic embedded in SettingsScreen.kt.
 * The Compose UI (Slider, Switch, RadioButton, etc.) around this logic
 * has no Java equivalent and isn't included here — see the accompanying note.
 */
public class PlantSettingsManager {

    private static final String SETTINGS_FILE = "plant_app_settings";

    private static final String KEY_TEMPERATURE_UNIT = "temperature_unit";
    private static final String KEY_DRY_SOIL_THRESHOLD = "dry_soil_threshold";
    private static final String KEY_FULL_TANK_THRESHOLD = "full_tank_threshold";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_LOW_HUMIDITY_ALERTS = "low_humidity_alerts";
    private static final String KEY_LOW_TANK_ALERTS = "low_tank_alerts";

    private final SharedPreferences prefs;

    public PlantSettingsManager(Context context) {
        this.prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
    }

    public String getTemperatureUnit() {
        return prefs.getString(KEY_TEMPERATURE_UNIT, "Celsius");
    }

    public void setTemperatureUnit(String unit) {
        prefs.edit().putString(KEY_TEMPERATURE_UNIT, unit).apply();
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
}