package com.team.plantwatering.ui.dashboard;

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
    private ValueEventListener plantsListener;

    // Should match the firmware time format here
    private final SimpleDateFormat firmwareDateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());
    private long serverTimeOffset = 0;
    private String lastNamesList = null;

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

    public void startListeningForChanges() {
        if (plantsListener != null) return; // Already listening

        plantsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PlantReading> updatedPlants = new ArrayList<>();
                for (DataSnapshot plantSnapshot : snapshot.getChildren()) { //Every plant captured here is a child of the "plants" node which is the root node.
                    String key = plantSnapshot.getKey();

                    if (key == null || key.startsWith(".") || key.equals("logs") || key.equals("plants")) continue;

                    // Display name is stored as a field now.
                    String name = plantSnapshot.child("name").getValue(String.class);
                    if (name == null) name = key;

                    Integer moisture = parseSafeInt(plantSnapshot.child("moisture_level").getValue()); //Those are the leaves of each child node
                    String waterStr = plantSnapshot.child("water_level").getValue(String.class);
                    String timeStr = plantSnapshot.child("last_time").getValue(String.class);

                    String thresholdProfileId = plantSnapshot.child("threshold_profile").getValue(String.class);
                    if (thresholdProfileId == null) thresholdProfileId = "standard";

                    // Manual Watering Fields aligned with firmware key: "water_pump_state"
                    Object pumpStateObj = plantSnapshot.child("water_pump_state").getValue();
                    Boolean pumpState = parseSafeBoolean(pumpStateObj);

                    String mode = plantSnapshot.child("watering_mode").getValue(String.class);
                    Boolean autoEnabled = parseSafeBoolean(plantSnapshot.child("auto_watering_mode").getValue());

                    // Read the duration from the latest status check instead of hardcoding it
                    Object durationObj = plantSnapshot.child("latest_watering_status").child("duration").getValue();
                    Integer duration = parseSafeInt(durationObj);

                    // Feedback field from ESP
                    Boolean isPumpActive = parseSafeBoolean(plantSnapshot.child("is_pump_active").getValue());

                    // New: check 'taken' key for hardware slot availability
                    Object takenObj = plantSnapshot.child("taken").getValue();
                    Integer takenInt = parseSafeInt(takenObj);
                    boolean isTaken = (takenInt != null && takenInt == 1);

                    int h = (moisture != null) ? moisture : 0;
                    // Ensure moisture stays within 0-100% range
                    if (h > 100) h = 100;
                    if (h < 0) h = 0;

                    // Water message from ESP (e.g. "Sufficient", "Low", "Connecting...")
                    String w = (waterStr != null) ? waterStr : "Unknown";
                    long lw = parseFirmwareTimeToMillis(timeStr); // time translation for the ESP
                    boolean mc = (pumpState != null && pumpState); // pump action made by the user
                    int md = (duration != null) ? duration : 3; // default to 3s if not found
                    boolean ac = (autoEnabled != null) && autoEnabled; // auto mode feedback
                    String sm = (mode != null) ? mode : "Auto";
                    boolean ipa = (isPumpActive != null && isPumpActive);

                    // Note: key (slot id, e.g. "slot1") is used internally for all database
                    // operations; name is only for display. PlantReading stores the slot id as
                    // its identifying "name" field so requestManualWatering/deletePlant/etc
                    // keep working unchanged.
                    updatedPlants.add(new PlantReading(key, h, w, lw, thresholdProfileId, lw, mc, md, sm, ipa, ac, isTaken));
                }
                plantsLiveData.setValue(updatedPlants);
                updatePlantNamesNode(updatedPlants);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        databaseReference.addValueEventListener(plantsListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (databaseReference != null && plantsListener != null) {
            databaseReference.removeEventListener(plantsListener);
        }
    }

    private void updatePlantNamesNode(List<PlantReading> plants) {
        if (plants == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plants.size(); i++) {
            sb.append(plants.get(i).getPlantName());
            if (i < plants.size() - 1) {
                sb.append(", ");
            }
        }

        String namesList = sb.toString();
        if (namesList.equals(lastNamesList)) {
            return; // No change, skip database write
        }

        lastNamesList = namesList;
        FirebaseDatabase.getInstance().getReference("names").setValue(namesList);
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

    private Boolean parseSafeBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Integer) return (Integer) value == 1;
        if (value instanceof Long) return (Long) value == 1L;
        if (value instanceof Double) return ((Double) value).intValue() == 1;
        if (value instanceof String) {
            String s = (String) value;
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return null;
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
    public interface AddPlantCallback {
        void onSuccess(String plantId);
    }

    public void addPlant(String plantName, PlantSettingsManager.ThresholdProfile profile, AddPlantCallback callback) {
        // Forgo slot implementation: Use plant name (sanitized) as the key directly
        String plantId = plantName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        
        DatabaseReference plantRef = databaseReference.child(plantId);

        plantRef.child("name").setValue(plantName);
        plantRef.child("moisture_level").setValue(0);
        plantRef.child("water_level").setValue("Unknown");

        // Mark as taken for hardware slot logic
        plantRef.child("taken").setValue(1);

        String nowStr = firmwareDateFormat.format(new Date());
        plantRef.child("last_time").setValue(nowStr);

        plantRef.child("threshold_profile").setValue(profile.id);
        // Push the RAW numeric value directly to Firebase for the ESP32 hardware to use
        plantRef.child("threshold").setValue(profile.drySoil);

        // Initialize Manual Watering Fields
        plantRef.child("water_pump_state").setValue(false);
        plantRef.child("manual_watering_duration").setValue(3);
        plantRef.child("auto_watering_mode").setValue(true);

        if (callback != null) callback.onSuccess(plantId);
    }

    // Removed claimSlotAndFillPlant as it's no longer needed without slot/taken logic

    public void requestManualWatering(String plantId, int duration) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("manual_watering_duration").setValue(duration);
        plantRef.child("water_pump_state").setValue(true);
    }

    public void stopManualWatering(String plantId) {
        databaseReference.child(plantId).child("water_pump_state").setValue(false);
    }

    public void setAutoWateringMode(String plantId, boolean enabled) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("auto_watering_mode").setValue(enabled);
    }

    /**
     * Deletes the plant node entirely.
     */
    public void deletePlant(String plantId) {
        databaseReference.child(plantId).removeValue();
    }
}
