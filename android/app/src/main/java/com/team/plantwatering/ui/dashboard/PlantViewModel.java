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
        // Points to the root node to find "/plant1" etc. as direct children to match current firmware
        databaseReference = FirebaseDatabase.getInstance().getReference();
        
        /* 
        // Previously used this folder-based organization:
        // databaseReference = FirebaseDatabase.getInstance().getReference("plants");
        */

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
                for (DataSnapshot plantSnapshot : snapshot.getChildren()) {
                    String name = plantSnapshot.getKey();

                    if (name == null || name.startsWith(".") || name.equals("logs") || name.equals("plants")) continue;
                    
                    Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class);
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
                    
                    // Convert String water message to 100/10 for the UI graphics
                    int w = (waterStr != null && waterStr.contains("Sufficient")) ? 100 : 10;
                    
                    long lw = parseFirmwareTimeToMillis(timeStr); // time conversion for the ESP
                    long ls = lw; // last_time acts as both heartbeat and watering time
                    
                    boolean mc = (pumpState != null && pumpState == 1); // pump action made by the user
                    int md = (duration != null) ? duration : 3; // default to 3s if not found
                    String m = (mode != null) ? mode : "manual";
                    boolean pa = (pumpState != null && pumpState == 1); // pump feedback
                    boolean ac = (autoEnabled != null) && autoEnabled;

                    // Comparison for microcontroller messages
                    PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(thresholdProfileId);
                    if (h < profile.drySoil) { 
                        plantSnapshot.getRef().child("messageESP").setValue("NEEDS WATER");
                    } else {
                        plantSnapshot.getRef().child("messageESP").setValue("");
                    }
                    
                    updatedPlants.add(new PlantReading(name, h, w, lw, thresholdProfileId, ls, mc, md, m, pa, ac));
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

    public void updatePlantThreshold(String plantName, String newThresholdId) {
        databaseReference.child(plantName).child("threshold_profile").setValue(newThresholdId);
    }

    public void addPlant(String name) {
        DatabaseReference newPlantRef = databaseReference.child(name);
        long now = System.currentTimeMillis();
        String readableTime = firmwareDateFormat.format(new Date(now));

        newPlantRef.child("moisture_level").setValue(0);
        newPlantRef.child("water_level").setValue("Connecting...");
        newPlantRef.child("last_time").setValue(readableTime);
        newPlantRef.child("threshold_profile").setValue("standard");

        // Initialize Manual Watering Fields
        newPlantRef.child("water_pump_state").setValue(0);
        newPlantRef.child("manual_watering_duration").setValue(3);
        newPlantRef.child("watering_mode").setValue("manual"); 
        newPlantRef.child("is_pump_active").setValue(false);
        newPlantRef.child("auto_watering_mode").setValue(true);
    }

    private static final int MAX_WATERING_DURATION = 60; 

    public void requestManualWatering(String plantName, int durationSeconds) {
        DatabaseReference plantRef = databaseReference.child(plantName);
        // Aligned with firmware key: "water_pump_state" as an Integer (1 = ON)
        plantRef.child("water_pump_state").setValue(1);

        String logTime = firmwareDateFormat.format(new Date());

        // Single Status Check Implementation
        DatabaseReference statusRef = plantRef.child("latest_watering_status");
        statusRef.child("event").setValue("Manual watering requested");
        statusRef.child("duration").setValue(durationSeconds);
        statusRef.child("timestamp").setValue(logTime);
        statusRef.child("is_completed").setValue(false); 

        // Also update the top-level duration key for consistency
        plantRef.child("manual_watering_duration").setValue(durationSeconds);

        // Simulation Mode: Signal completion after the requested duration
        long delayMillis = (long) durationSeconds * 1000 + 500;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            plantRef.child("water_pump_state").setValue(0);
            statusRef.child("is_completed").setValue(true);
        }, delayMillis);
    }

    public void stopManualWatering(String plantName) {
        databaseReference.child(plantName).child("water_pump_state").setValue(0);

        String logTime = firmwareDateFormat.format(new Date());

        DatabaseReference statusRef = databaseReference.child(plantName).child("latest_watering_status");
        statusRef.child("event").setValue("Manual watering stopped early");
        statusRef.child("timestamp").setValue(logTime);
        statusRef.child("is_completed").setValue(true);
    }

    public void updateWateringMode(String plantName, String mode) {
        databaseReference.child(plantName).child("watering_mode").setValue(mode);
    }

    public void setAutoWateringMode(String plantName, boolean enabled) {
        databaseReference.child(plantName).child("auto_watering_mode").setValue(enabled);
    }

    public void deletePlant(String name) {
        databaseReference.child(name).removeValue();
    }
}
