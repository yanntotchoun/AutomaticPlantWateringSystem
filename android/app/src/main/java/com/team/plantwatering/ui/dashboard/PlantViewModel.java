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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlantViewModel extends ViewModel {

    private final MutableLiveData<List<PlantReading>> plantsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final DatabaseReference databaseReference;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());

    public PlantViewModel() {
        databaseReference = FirebaseDatabase.getInstance().getReference("plants"); //"plants" is the name of the root node in the firebase.
    }

    public LiveData<List<PlantReading>> getPlants() {
        return plantsLiveData;
    }

    public void startListeningForChanges(Context context) { //The firebase starts recording the changes here in real time.
        final PlantSettingsManager settingsManager = new PlantSettingsManager(context);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PlantReading> updatedPlants = new ArrayList<>();
                for (DataSnapshot plantSnapshot : snapshot.getChildren()) { //Every plant captured here is a child of the "plants" node which is the root node.
                    String name = plantSnapshot.getKey();

                    Integer moisture = plantSnapshot.child("moisture_level").getValue(Integer.class); //Those are the leaves of each child node
                    Integer water = plantSnapshot.child("water_tank").getValue(Integer.class);
                    Long lastTimeWatered = plantSnapshot.child("last_time_watered_Millis").getValue(Long.class);
                    String thresholdProfile = plantSnapshot.child("threshold_profile").getValue(String.class);
                    Long lastSeenOnline = plantSnapshot.child("connection_status_Millis").getValue(Long.class);

                    // Manual Watering Fields (Task BSCK-8.1)
                    Boolean manualCommand = plantSnapshot.child("manual_watering_command").getValue(Boolean.class);
                    Integer manualDuration = plantSnapshot.child("manual_watering_duration").getValue(Integer.class);
                    String mode = plantSnapshot.child("watering_mode").getValue(String.class);
                    Boolean pumpActive = plantSnapshot.child("is_pump_active").getValue(Boolean.class);

                    int h = (moisture != null) ? moisture : 0;
                    int w = (water != null) ? water : 0;
                    long lw = (lastTimeWatered != null) ? lastTimeWatered : 0L;
                    String tid = (thresholdProfile != null) ? thresholdProfile : "standard";
                    long ls = (lastSeenOnline != null) ? lastSeenOnline : 0L;

                    boolean mc = (manualCommand != null) && manualCommand;
                    int md = (manualDuration != null) ? manualDuration : 5;
                    String m = (mode != null) ? mode : "auto";
                    boolean pa = (pumpActive != null) && pumpActive;

                    // comparison logic for moisture_level versus threshold, if dry send notification to MCU
                    PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(tid);
                    if (h < profile.drySoil) {
                        plantSnapshot.getRef().child("messageESP").setValue("NEEDS WATER");
                    } else {
                        // no message if moisture is above threshold
                        plantSnapshot.getRef().child("messageESP").setValue("");
                    }

                    updatedPlants.add(new PlantReading(name, h, w, lw, tid, ls, mc, md, m, pa));
                }
                plantsLiveData.setValue(updatedPlants);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    public void updatePlantThreshold(String plantName, String newThresholdId) {
        databaseReference.child(plantName).child("threshold_profile").setValue(newThresholdId);
    }

    public void addPlant(String name) {
        DatabaseReference newPlantRef = databaseReference.child(name);
        long now = System.currentTimeMillis();
        String readableTime = dateFormat.format(new Date(now));

        newPlantRef.child("moisture_level").setValue(0);
        newPlantRef.child("water_tank").setValue(0);
        
        newPlantRef.child("last_time_watered_Millis").setValue(now); // This format (Millis Unix timestamp) is easier for the Arduino to read.
        newPlantRef.child("last_time_watered").setValue(readableTime); // This format is easier for us to read and debug on the firebase.

        newPlantRef.child("threshold_profile").setValue("standard");
        newPlantRef.child("connection_status_Millis").setValue(now);
        newPlantRef.child("connection_status").setValue(readableTime);

        // Initialize Manual Watering Fields
        newPlantRef.child("manual_watering_command").setValue(false);
        newPlantRef.child("manual_watering_duration").setValue(5); // Default 5 seconds
        newPlantRef.child("watering_mode").setValue("auto");
        newPlantRef.child("is_pump_active").setValue(false);
    }

    private static final int MAX_WATERING_DURATION = 60; // Max 60 seconds for safety

    /**
     * Sends a command to the MCU to start watering the plant manually.
     * Enforces a maximum duration to prevent flooding.
     */
    public void requestManualWatering(String plantName, int durationSeconds) {
        int safeDuration = Math.min(durationSeconds, MAX_WATERING_DURATION);
        
        DatabaseReference plantRef = databaseReference.child(plantName);
        plantRef.child("manual_watering_duration").setValue(safeDuration);
        plantRef.child("manual_watering_command").setValue(true);

        // BSCK-8.3: Log the request for history
        String logTime = dateFormat.format(new Date());
        DatabaseReference logRef = plantRef.child("logs").push();
        logRef.child("event").setValue("Manual watering requested");
        logRef.child("duration").setValue(safeDuration);
        logRef.child("timestamp").setValue(logTime);
    }

    /**
     * Immediately requests the MCU to stop watering.
     */
    public void stopManualWatering(String plantName) {
        databaseReference.child(plantName).child("manual_watering_command").setValue(false);
        
        // Log the stop event
        DatabaseReference logRef = databaseReference.child(plantName).child("logs").push();
        logRef.child("event").setValue("Manual watering stopped early");
        logRef.child("timestamp").setValue(dateFormat.format(new Date()));
    }

    /**
     * Switches the plant between 'auto' and 'manual' watering modes.
     */
    public void updateWateringMode(String plantName, String mode) {
        databaseReference.child(plantName).child("watering_mode").setValue(mode);
    }

    public void deletePlant(String name) {
        databaseReference.child(name).removeValue();
    }
}
