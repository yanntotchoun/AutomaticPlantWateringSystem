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
        // Point to root so we can find "/plant1" etc. as direct children from the reverted firmware
        databaseReference = FirebaseDatabase.getInstance().getReference();
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
                    
                    // Filter out system nodes or non-plant nodes
                    if (name == null || name.equals("logs") || name.equals("plants") || name.startsWith(".")) continue;

                    Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class);
                    // Firmware uses "water_level" as a String ("Sufficient water...")
                    String waterStr = plantSnapshot.child("water_level").getValue(String.class);
                    // Firmware uses "last_time" as the human words heartbeat
                    String timeStr = plantSnapshot.child("last_time").getValue(String.class);
                    
                    String thresholdProfile = plantSnapshot.child("threshold_profile").getValue(String.class);
                    if (thresholdProfile == null) thresholdProfile = "standard";

                    // Manual Watering Fields aligned with firmware key: "water_pump_state"
                    Integer pumpState = plantSnapshot.child("water_pump_state").getValue(Integer.class);
                    String mode = plantSnapshot.child("watering_mode").getValue(String.class);

                    int h = (moisture != null) ? moisture : 0;
                    // Ensure moisture stays within 0-100% range
                    if (h > 100) h = 100;
                    if (h < 0) h = 0;

                    // Convert String water message to 100/10 for the UI graphics
                    int w = (waterStr != null && waterStr.contains("Sufficient")) ? 100 : 10;
                    
                    // Parses "Monday, July 22 21:27:47" and adds the current year.
                    long lw = parseFirmwareTimeToMillis(timeStr);
                    long ls = lw; // last_time acts as both heartbeat and watering time in the reverted firmware

                    boolean mc = (pumpState != null && pumpState == 1);
                    int md = 3; // Aligned with firmware default (3000ms)
                    String m = (mode != null) ? mode : "auto";
                    boolean pa = (pumpState != null && pumpState == 1);

                    // Comparison logic for microcontroller messages
                    // Based on firmware,  100 = wet and 0 = dry
                    PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(thresholdProfile);
                    if (h < profile.drySoil) { // Trigger if dryness (h) is GREATER than the threshold
                        plantSnapshot.getRef().child("messageESP").setValue("NEEDS WATER");
                    } else {
                        plantSnapshot.getRef().child("messageESP").setValue("");
                    }

                    updatedPlants.add(new PlantReading(name, h, w, lw, thresholdProfile, ls, mc, md, m, pa));
                }
                plantsLiveData.setValue(updatedPlants);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
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
        newPlantRef.child("manual_watering_command").setValue(false);
        newPlantRef.child("manual_watering_duration").setValue(3);
        newPlantRef.child("watering_mode").setValue("manual"); //The default watering mode is set to manual which responds to the valve activation of the pump
        newPlantRef.child("is_pump_active").setValue(false);
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
        statusRef.child("is_completed").setValue(false); // Hardware should set this to true when done

        /*
        DatabaseReference logRef = plantRef.child("logs").push();
        logRef.child("event").setValue("Manual watering requested");
        logRef.child("duration").setValue(durationSeconds);
        logRef.child("timestamp").setValue(logTime);
        */
    }

    public void stopManualWatering(String plantName) {
        // Aligned with firmware key: "water_pump_state" as an Integer (0 = OFF)
        databaseReference.child(plantName).child("water_pump_state").setValue(0);

        String logTime = firmwareDateFormat.format(new Date());

        // Single Status Check Update
        DatabaseReference statusRef = databaseReference.child(plantName).child("latest_watering_status");
        statusRef.child("event").setValue("Manual watering stopped early");
        statusRef.child("timestamp").setValue(logTime);
        statusRef.child("is_completed").setValue(true);

        /*
        DatabaseReference logRef = databaseReference.child(plantName).child("logs").push();
        logRef.child("event").setValue("Manual watering stopped early");
        logRef.child("timestamp").setValue(logTime);
        */
    }

    public void updateWateringMode(String plantName, String mode) { //As a design idea on the backend to switch manual to auto mode
        databaseReference.child(plantName).child("watering_mode").setValue(mode);
    }

    public void deletePlant(String name) {
        databaseReference.child(name).removeValue();
    }
}
