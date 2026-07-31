package com.team.plantwatering.ui.dashboard;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.team.plantwatering.data.PlantReading;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlantViewModel extends ViewModel {

    private final MutableLiveData<List<PlantReading>> plantsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final DatabaseReference databaseReference;

    // Should match the firmware time format here
    private final SimpleDateFormat firmwareDateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());
    private long serverTimeOffset = 0;

    public PlantViewModel() {
        // Fixed device slots (slot1, slot2, slot3...) live directly under "plants" -
        // they're pre-provisioned by the hardware team, not generated dynamically by the app.
        databaseReference = FirebaseDatabase.getInstance().getReference("plants");

        listenForServerTimeOffset();
    }

    private void listenForServerTimeOffset() {
        FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long offset = snapshot.getValue(Long.class);
                        if (offset != null) {
                            serverTimeOffset = offset;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    public long getCurrentServerTime() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    public LiveData<List<PlantReading>> getPlants() {
        return plantsLiveData;
    }

    public void startListeningForChanges(Context context) {
        final PlantSettingsManager settingsManager = new PlantSettingsManager(context);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PlantReading> updatedPlants = new ArrayList<>();
                for (DataSnapshot plantSnapshot : snapshot.getChildren()) { //Every plant captured here is a child of the "plants" node which is the root node.
                    String key = plantSnapshot.getKey();

                    if (key == null || key.startsWith(".") || key.equals("logs") || key.equals("plants")) continue;

                    // Display name is stored as a field now.
                    String name = plantSnapshot.child("name").getValue(String.class);
                    if (name == null) name = key; // fallback for older-format entries that used the name as the key

                    Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class); //Those are the leaves of each child node
                    String waterStr = plantSnapshot.child("water_level").getValue(String.class);
                    String timeStr = plantSnapshot.child("last_time").getValue(String.class);

                    String thresholdProfileId = plantSnapshot.child("threshold_profile").getValue(String.class);
                    if (thresholdProfileId == null) thresholdProfileId = "standard";

                    // Manual Watering Fields aligned with firmware key: "water_pump_state"
                    Integer pumpState = plantSnapshot.child("water_pump_state").getValue(Integer.class);
                    String mode = plantSnapshot.child("watering_mode").getValue(String.class);
                    Boolean autoEnabled = plantSnapshot.child("auto_watering_mode").getValue(Boolean.class);

                    // Read the duration from the latest status check instead of hardcoding it
                    Integer duration = plantSnapshot.child("latest_watering_status").child("duration").getValue(Integer.class);

                    int h = (moisture != null) ? moisture : 0;
                    // Ensure moisture stays within 0-100% range
                    if (h > 100) h = 100;
                    if (h < 0) h = 0;

                    // Water message from ESP (e.g. "Sufficient", "Low", "Connecting...")
                    String w = (waterStr != null) ? waterStr : "Unknown";
                    long lw = parseFirmwareTimeToMillis(timeStr); // time translation for the ESP
                    long ls = lw; // last_time acts as both heartbeat and watering time
                    boolean mc = (pumpState != null && pumpState == 1); // pump action made by the user
                    int md = (duration != null) ? duration : 3; // default to 3s if not found
                    String m = (mode != null) ? mode : "manual"; //default mode set to manual
                    boolean pa = (pumpState != null && pumpState == 1); // pump feedback (is the pump active?)
                    boolean ac = (autoEnabled != null) && autoEnabled; // auto mode feedback

                    // Note: key (slot id, e.g. "slot1") is used internally for all database
                    // operations; name is only for display. PlantReading stores the slot id as
                    // its identifying "name" field so requestManualWatering/deletePlant/etc
                    // keep working unchanged.
                    updatedPlants.add(new PlantReading(key, h, w, lw, thresholdProfileId, ls, mc, md, m, pa, ac));
                }
                plantsLiveData.setValue(updatedPlants);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private long parseFirmwareTimeToMillis(String timeStr) { // Time translation for the ESP
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

    public void updatePlantThreshold(String plantId, PlantSettingsManager.ThresholdProfile profile) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("threshold_profile").setValue(profile.id);
        
        // Push the RAW numeric values directly to Firebase for the ESP32 hardware to use
        plantRef.child("threshold").setValue(profile.drySoil);
    }

    /**
     * Updates all plants that use a specific profile with the new threshold value.
     */
    public void syncProfileChangesToFirebase(PlantSettingsManager.ThresholdProfile profile) {
        databaseReference.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot plantSnap : snapshot.getChildren()) {
                String profileId = plantSnap.child("threshold_profile").getValue(String.class);
                if (profileId != null && profileId.equals(profile.id)) {
                    plantSnap.getRef().child("threshold").setValue(profile.drySoil);
                }
            }
        });
    }

    /**
     * Reassigns all plants using a deleted profile back to the 'standard' profile.
     */
    public void syncDeletionToFirebase(String deletedProfileId, PlantSettingsManager.ThresholdProfile standardProfile) {
        databaseReference.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot plantSnap : snapshot.getChildren()) {
                String profileId = plantSnap.child("threshold_profile").getValue(String.class);
                if (deletedProfileId.equals(profileId)) {
                    DatabaseReference ref = plantSnap.getRef();
                    ref.child("threshold_profile").setValue("standard");
                    ref.child("threshold").setValue(standardProfile.drySoil);
                }
            }
        });
    }

    /**
     * Callback used by the UI to report whether a device slot is available,
     * so the Add Plant screen can enable/disable Save and show a status message.
     */
    public interface AvailabilityCallback {
        void onResult(boolean available);
    }

    /**
     * Checks whether at least one device is available. Forgone slot implementation, always returns true.
     */
    public void checkIdAvailability(AvailabilityCallback callback) {
        callback.onResult(true);
    }

    public interface AddPlantCallback {
        void onSuccess(String plantId);
        void onNoSlotsAvailable();
    }

    public void addPlant(String plantName) {
        // Default to a null callback or handle it
    }


    public void addPlant(String plantName, PlantSettingsManager.ThresholdProfile profile, AddPlantCallback callback) {
        // Forgo slot implementation: Use plant name (sanitized) as the key directly
        String plantId = plantName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        
        DatabaseReference plantRef = databaseReference.child(plantId);

        plantRef.child("name").setValue(plantName);
        plantRef.child("moisture_level").setValue(0);
        plantRef.child("water_level").setValue("Connecting...");

        String nowStr = firmwareDateFormat.format(new Date());
        plantRef.child("last_time").setValue(nowStr);

        plantRef.child("threshold_profile").setValue(profile.id);
        // Push the RAW numeric value directly to Firebase for the ESP32 hardware to use
        plantRef.child("threshold").setValue(profile.drySoil);

        // Initialize Manual Watering Fields
        plantRef.child("water_pump_state").setValue(0);
        plantRef.child("manual_watering_duration").setValue(3);
        plantRef.child("watering_mode").setValue("manual");
        plantRef.child("is_pump_active").setValue(false);
        plantRef.child("auto_watering_mode").setValue(true);

        if (callback != null) callback.onSuccess(plantId);
    }

    // Removed claimSlotAndFillPlant as it's no longer needed without slot/taken logic

    private static final int MAX_WATERING_DURATION = 60; //This is just a safety feature to prevent flooding. It stops watering at 60 seconds and overrides the timer of the UI or ESP.
    //For the moment it's not being used. It's a concept idea.

    public void requestManualWatering(String plantId, int durationSeconds) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        // Aligned with firmware key: "water_pump_state" as an Integer (1 = ON)
        plantRef.child("water_pump_state").setValue(1);

        String logTime = firmwareDateFormat.format(new Date());

        // This here is the log that gets overwritten each time a new watering event is recorded.
        DatabaseReference statusRef = plantRef.child("latest_watering_status");
        statusRef.child("event").setValue("Manual watering requested");
        statusRef.child("duration").setValue(durationSeconds);
        statusRef.child("timestamp_start").setValue(logTime);
        statusRef.child("timestamp_stop").setValue(""); // Reset stop time
        statusRef.child("is_completed").setValue(false);

        // Also update the top-level duration key for consistency
        plantRef.child("manual_watering_duration").setValue(durationSeconds);

        // Simulation Mode: Signal completion after the requested duration
        long delayMillis = (long) durationSeconds * 1000 + 500;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String stopTime = firmwareDateFormat.format(new Date());
            plantRef.child("water_pump_state").setValue(0);
            statusRef.child("is_completed").setValue(true);
            statusRef.child("timestamp_stop").setValue(stopTime);
        }, delayMillis);

        /*
        DatabaseReference logRef = plantRef.child("logs").push(); // This is the old format that would save all the logs recorded.

        // The .push() generates a unique id for each log.

        logRef.child("event").setValue("Manual watering requested");
        logRef.child("duration").setValue(durationSeconds);
        logRef.child("timestamp").setValue(logTime);
        */
    }

    public void stopManualWatering(String plantId) {
        databaseReference.child(plantId).child("water_pump_state").setValue(0);

        String logTime = firmwareDateFormat.format(new Date());

        // The same log gets updated here if the watering event is stopped on the UI.
        DatabaseReference statusRef = databaseReference.child(plantId).child("latest_watering_status");
        statusRef.child("event").setValue("Manual watering stopped early");
        statusRef.child("timestamp_stop").setValue(logTime);
        statusRef.child("is_completed").setValue(true);

        /*
        DatabaseReference logRef = databaseReference.child(plantId).child("logs").push();
        logRef.child("event").setValue("Manual watering stopped early");
        logRef.child("timestamp").setValue(logTime);
        */
    }

    public void updateWateringMode(String plantId, String mode) { // As a design idea on the backend to switch manual to auto mode. Not functional yet
        databaseReference.child(plantId).child("watering_mode").setValue(mode);
    }

    public void setAutoWateringMode(String plantId, boolean enabled) {
        databaseReference.child(plantId).child("auto_watering_mode").setValue(enabled);
    }

    /**
     * Deletes the plant node entirely.
     */
    public void deletePlant(String plantId) {
        databaseReference.child(plantId).removeValue();
    }
}