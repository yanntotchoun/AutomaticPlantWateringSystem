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

import com.google.firebase.database.Query;

import java.util.Collections;

import android.util.Log;

public class PlantViewModel extends ViewModel {

    private static final String TAG = "PlantFirebase";

    private final MutableLiveData<List<PlantReading>> plantsLiveData =
            new MutableLiveData<>(new ArrayList<>());

    private final DatabaseReference databaseReference;
    private ValueEventListener plantsListener;

    private static final int WATERING_LOG_LIMIT = 5;

    private final MutableLiveData<List<Long>> wateringLogLiveData =
            new MutableLiveData<>(new ArrayList<>());

    private Query wateringLogQuery;
    private ValueEventListener wateringLogListener;

    // Should match the firmware time format here
    private final SimpleDateFormat firmwareDateFormat =
            new SimpleDateFormat(
                    "EEEE, MMMM dd HH:mm:ss",
                    Locale.getDefault()
            );

    private long serverTimeOffset = 0;
    private String lastNamesList = null;

    public PlantViewModel() {

        // Fixed device slots (slot1, slot2, slot3...) live directly under "plants" -
        // they're pre-provisioned by the hardware team, not generated dynamically by the app.
        databaseReference =
                FirebaseDatabase
                        .getInstance()
                        .getReference("plants");

        listenForServerTimeOffset();
        listenForFirebaseConnection();
    }

    private void listenForFirebaseConnection() {

        FirebaseDatabase.getInstance()
                .getReference(".info/connected")
                .addValueEventListener(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(
                                    @NonNull DataSnapshot snapshot
                            ) {

                                Boolean connected =
                                        snapshot.getValue(Boolean.class);

                                if (Boolean.TRUE.equals(connected)) {

                                    Log.d(
                                            TAG,
                                            "Connected to Firebase."
                                    );

                                } else {

                                    Log.w(
                                            TAG,
                                            "Not connected to Firebase."
                                    );
                                }
                            }

                            @Override
                            public void onCancelled(
                                    @NonNull DatabaseError error
                            ) {

                                Log.e(
                                        TAG,
                                        "Firebase connection check failed: "
                                                + error.getMessage(),
                                        error.toException()
                                );
                            }
                        }
                );
    }

    private void listenForServerTimeOffset() {

        FirebaseDatabase.getInstance()
                .getReference(".info/serverTimeOffset")
                .addValueEventListener(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(
                                    @NonNull DataSnapshot snapshot
                            ) {

                                Long offset =
                                        snapshot.getValue(Long.class);

                                if (offset != null) {
                                    serverTimeOffset = offset;
                                }
                            }

                            @Override
                            public void onCancelled(
                                    @NonNull DatabaseError error
                            ) {
                            }
                        }
                );
    }

    public long getCurrentServerTime() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    public LiveData<List<PlantReading>> getPlants() {
        return plantsLiveData;
    }

    public LiveData<List<Long>> getWateringLog() {
        return wateringLogLiveData;
    }

    /**
     * Reads the five newest events from:
     *
     * /plants/{plantId}/watering_log/{eventId}/timestamp
     */
    public void startListeningForWateringLog(String plantId) {

        stopListeningForWateringLog();

        // Clear data left over from a previously opened plant.
        wateringLogLiveData.setValue(new ArrayList<>());

        wateringLogQuery =
                databaseReference
                        .child(plantId)
                        .child("watering_log")
                        .orderByChild("timestamp")
                        .limitToLast(WATERING_LOG_LIMIT);

        wateringLogListener =
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot
                    ) {

                        List<Long> wateringTimes =
                                new ArrayList<>();

                        for (
                                DataSnapshot eventSnapshot :
                                snapshot.getChildren()
                        ) {

                            Long timestamp =
                                    parseSafeLong(
                                            eventSnapshot
                                                    .child("timestamp")
                                                    .getValue()
                                    );

                            // Ignore the placeholder 0
                            if (
                                    timestamp != null
                                            && timestamp > 0L
                            ) {
                                wateringTimes.add(timestamp);
                            }
                        }

                        // Firebase returns oldest to newest.
                        // Display newest first.
                        Collections.sort(
                                wateringTimes,
                                Collections.reverseOrder()
                        );

                        wateringLogLiveData.setValue(
                                wateringTimes
                        );
                    }

                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error
                    ) {

                        wateringLogLiveData.setValue(
                                new ArrayList<>()
                        );
                    }
                };

        wateringLogQuery.addValueEventListener(
                wateringLogListener
        );
    }

    public void stopListeningForWateringLog() {

        if (
                wateringLogQuery != null
                        && wateringLogListener != null
        ) {

            wateringLogQuery.removeEventListener(
                    wateringLogListener
            );
        }

        wateringLogQuery = null;
        wateringLogListener = null;
    }

    public void startListeningForChanges() {

        if (plantsListener != null) {
            return;
        }

        plantsListener =
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot
                    ) {

                        Log.d(
                                TAG,
                                "Firebase read succeeded. Children under /plants: "
                                        + snapshot.getChildrenCount()
                        );

                        List<PlantReading> updatedPlants =
                                new ArrayList<>();

                        for (
                                DataSnapshot plantSnapshot :
                                snapshot.getChildren()
                        ) {

                            // Every plant captured here is a child
                            // of the "plants" node.
                            String key =
                                    plantSnapshot.getKey();

                            if (
                                    key == null
                                            || key.startsWith(".")
                                            || key.equals("logs")
                                            || key.equals("plants")
                            ) {
                                continue;
                            }

                            // Display name is stored as a field now.
                            String name =
                                    plantSnapshot
                                            .child("name")
                                            .getValue(String.class);

                            if (name == null) {
                                name = key;
                            }

                            Integer moisture =
                                    parseSafeInt(
                                            plantSnapshot
                                                    .child("moisture_level")
                                                    .getValue()
                                    );

                            String waterStr =
                                    plantSnapshot
                                            .child("water_level")
                                            .getValue(String.class);

                            String timeStr =
                                    plantSnapshot
                                            .child("last_time")
                                            .getValue(String.class);

                            String thresholdProfileId =
                                    plantSnapshot
                                            .child("threshold_profile")
                                            .getValue(String.class);

                            if (thresholdProfileId == null) {
                                thresholdProfileId = "standard";
                            }

                            // Manual Watering Fields aligned
                            // with firmware key: "water_pump_state"
                            Object pumpStateObj =
                                    plantSnapshot
                                            .child("water_pump_state")
                                            .getValue();

                            Boolean pumpState =
                                    parseSafeBoolean(
                                            pumpStateObj
                                    );

                            String mode =
                                    plantSnapshot
                                            .child("watering_mode")
                                            .getValue(String.class);

                            Boolean autoEnabled =
                                    parseSafeBoolean(
                                            plantSnapshot
                                                    .child("auto_watering_mode")
                                                    .getValue()
                                    );

                            // Read the duration from the latest
                            // status check instead of hardcoding it.
                            Object durationObj =
                                    plantSnapshot
                                            .child("latest_watering_status")
                                            .child("duration")
                                            .getValue();

                            Integer duration =
                                    parseSafeInt(durationObj);

                            // Feedback field from ESP
                            Boolean isPumpActive =
                                    parseSafeBoolean(
                                            plantSnapshot
                                                    .child("is_pump_active")
                                                    .getValue()
                                    );

                            // Check 'taken' key for hardware
                            // slot availability.
                            Object takenObj =
                                    plantSnapshot
                                            .child("taken")
                                            .getValue();

                            Integer takenInt =
                                    parseSafeInt(takenObj);

                            boolean isTaken =
                                    takenInt != null
                                            && takenInt == 1;

                            // New: Read explicit sensor_index attribute
                            Object sensorIndexObj = plantSnapshot.child("sensor_index").getValue();
                            Integer sensorIndexInt = parseSafeInt(sensorIndexObj);
                            int sensorIndex = (sensorIndexInt != null) ? sensorIndexInt : (key.contains("slot2") ? 2 : 1);

                            String imageUrl = plantSnapshot.child("image_url").getValue(String.class);

                            int h = (moisture != null) ? moisture : 0;
                            // Ensure moisture stays within 0-100% range
                            if (h > 100) h = 100;
                            if (h < 0) h = 0;

                            // Water message from ESP
                            // e.g. "Sufficient", "Low", "Connecting..."
                            String w =
                                    waterStr != null
                                            ? waterStr
                                            : "Unknown";

                            long lw =
                                    parseFirmwareTimeToMillis(
                                            timeStr
                                    );

                            boolean mc =
                                    pumpState != null
                                            && pumpState;

                            int md =
                                    duration != null
                                            ? duration
                                            : 3;

                            boolean ac =
                                    autoEnabled != null
                                            && autoEnabled;

                            String sm =
                                    mode != null
                                            ? mode
                                            : "Auto";

                            boolean ipa =
                                    isPumpActive != null
                                            && isPumpActive;

                            /*
                             * key (slot id, e.g. "slot1") is used
                             * internally for database operations.
                             */
                            updatedPlants.add(
                                    new PlantReading(
                                            key,
                                            name,
                                            h,
                                            w,
                                            lw,
                                            thresholdProfileId,
                                            lw,
                                            mc,
                                            md,
                                            sm,
                                            ipa,
                                            ac,
                                            isTaken,
                                            sensorIndex,
                                            imageUrl
                                    )
                            );
                        }

                        Log.d(
                                TAG,
                                "Plants successfully converted for the UI: "
                                        + updatedPlants.size()
                        );

                        plantsLiveData.setValue(
                                updatedPlants
                        );

                        updatePlantNamesNode(
                                updatedPlants
                        );
                    }

                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error
                    ) {

                        Log.e(
                                TAG,
                                "Firebase /plants read failed. Code: "
                                        + error.getCode()
                                        + ". Message: "
                                        + error.getMessage(),
                                error.toException()
                        );
                    }
                };

        databaseReference.addValueEventListener(
                plantsListener
        );
    }

    @Override
    protected void onCleared() {

        super.onCleared();

        if (
                databaseReference != null
                        && plantsListener != null
        ) {

            databaseReference.removeEventListener(
                    plantsListener
            );
        }

        stopListeningForWateringLog();
    }

    private void updatePlantNamesNode(
            List<PlantReading> plants
    ) {

        if (plants == null) {
            return;
        }

        StringBuilder sb =
                new StringBuilder();

        for (int i = 0; i < plants.size(); i++) {

            sb.append(
                    plants.get(i).getPlantName()
            );

            if (i < plants.size() - 1) {
                sb.append(", ");
            }
        }

        String namesList =
                sb.toString();

        if (namesList.equals(lastNamesList)) {
            return;
        }

        lastNamesList = namesList;

        FirebaseDatabase
                .getInstance()
                .getReference("names")
                .setValue(namesList);
    }

    private long parseFirmwareTimeToMillis(
            String timeStr
    ) {

        if (
                timeStr == null
                        || timeStr.isEmpty()
        ) {
            return 0L;
        }

        try {

            Date date =
                    firmwareDateFormat.parse(
                            timeStr
                    );

            if (date != null) {

                Calendar cal =
                        Calendar.getInstance();

                int currentYear =
                        cal.get(Calendar.YEAR);

                cal.setTime(date);

                cal.set(
                        Calendar.YEAR,
                        currentYear
                );

                return cal.getTimeInMillis();
            }

        } catch (ParseException e) {

            return 0L;
        }

        return 0L;
    }

    private Long parseSafeLong(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {

            try {

                return Long.parseLong(
                        (String) value
                );

            } catch (NumberFormatException e) {

                return null;
            }
        }

        return null;
    }

    private Boolean parseSafeBoolean(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Integer) {
            return (Integer) value == 1;
        }

        if (value instanceof Long) {
            return (Long) value == 1L;
        }

        if (value instanceof Double) {
            return ((Double) value).intValue() == 1;
        }

        if (value instanceof String) {

            String s =
                    (String) value;

            return s.equalsIgnoreCase("true")
                    || s.equals("1");
        }

        return null;
    }

    private Integer parseSafeInt(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof Long) {
            return ((Long) value).intValue();
        }

        if (value instanceof Double) {
            return ((Double) value).intValue();
        }

        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }

        if (value instanceof String) {

            try {

                return Integer.parseInt(
                        (String) value
                );

            } catch (NumberFormatException e) {

                return null;
            }
        }

        return null;
    }

    public void updatePlantThreshold(
            String plantId,
            PlantSettingsManager.ThresholdProfile profile
    ) {

        DatabaseReference plantRef =
                databaseReference.child(plantId);

        plantRef
                .child("threshold_profile")
                .setValue(profile.id);

        // Push the RAW numeric values directly to Firebase
        // for the ESP32 hardware to use.
        plantRef
                .child("threshold")
                .setValue(profile.drySoil);
    }

    /**
     * Updates all plants that use a specific profile
     * with the new threshold value.
     */
    public void syncProfileChangesToFirebase(
            PlantSettingsManager.ThresholdProfile profile
    ) {

        databaseReference
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (
                            DataSnapshot plantSnap :
                            snapshot.getChildren()
                    ) {

                        String profileId =
                                plantSnap
                                        .child("threshold_profile")
                                        .getValue(String.class);

                        if (
                                profileId != null
                                        && profileId.equals(profile.id)
                        ) {

                            plantSnap
                                    .getRef()
                                    .child("threshold")
                                    .setValue(
                                            profile.drySoil
                                    );
                        }
                    }
                });
    }

    /**
     * Reassigns all plants using a deleted profile
     * back to the 'standard' profile.
     */
    public void syncDeletionToFirebase(
            String deletedProfileId,
            PlantSettingsManager.ThresholdProfile standardProfile
    ) {

        databaseReference
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (
                            DataSnapshot plantSnap :
                            snapshot.getChildren()
                    ) {

                        String profileId =
                                plantSnap
                                        .child("threshold_profile")
                                        .getValue(String.class);

                        if (
                                deletedProfileId.equals(
                                        profileId
                                )
                        ) {

                            DatabaseReference ref =
                                    plantSnap.getRef();

                            ref
                                    .child("threshold_profile")
                                    .setValue("standard");

                            ref
                                    .child("threshold")
                                    .setValue(
                                            standardProfile.drySoil
                                    );
                        }
                    }
                });
    }

    /**
     * Callback used by the UI to report whether
     * a device slot is available.
     */
    public interface AddPlantCallback {
        void onSuccess(String plantId);
        void onError(String message);
    }

    public void addPlant(
            String plantName,
            String selectedSlot,
            PlantSettingsManager.ThresholdProfile profile,
            AddPlantCallback callback
    ) {

        if (selectedSlot == null || selectedSlot.trim().isEmpty()) {
            if (callback != null) {
                callback.onError("No hardware slot is available.");
            }
            return;
        }

        DatabaseReference plantRef =
                databaseReference.child(selectedSlot);

        // Re-check the slot immediately before writing so we do not
        // accidentally claim a slot that became occupied meanwhile.
        plantRef.get()
                .addOnSuccessListener(snapshot -> {
                    Integer taken = parseSafeInt(
                            snapshot.child("taken").getValue()
                    );

                    if (taken != null && taken == 1) {
                        if (callback != null) {
                            callback.onError(
                                    "That hardware slot is already occupied. Please try again."
                            );
                        }
                        return;
                    }

                    java.util.Map<String, Object> values =
                            new java.util.HashMap<>();

                    values.put("name", plantName);
                    values.put("moisture_level", 0);
                    values.put("water_level", "Unknown");
                    values.put("taken", 1);
                    
                    int sensorIndex = selectedSlot.contains("slot2") ? 2 : 1;
                    values.put("sensor_index", sensorIndex);

                    values.put(
                            "last_time",
                            firmwareDateFormat.format(new Date())
                    );
                    values.put("threshold_profile", profile.id);
                    values.put("threshold", profile.drySoil);
                    values.put("water_pump_state", false);
                    values.put("manual_watering_duration", 3);
                    values.put("auto_watering_mode", true);
                    values.put("watering_log/placeholder", 0);

                    plantRef.updateChildren(values)
                            .addOnSuccessListener(unused -> {
                                if (callback != null) {
                                    callback.onSuccess(selectedSlot);
                                }
                            })
                            .addOnFailureListener(error -> {
                                if (callback != null) {
                                    callback.onError(
                                            "Could not add plant: " + error.getMessage()
                                    );
                                }
                            });
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        callback.onError(
                                "Could not check hardware slot: " + error.getMessage()
                        );
                    }
                });
    }

    public void requestManualWatering(
            String plantId,
            int duration
    ) {

        DatabaseReference plantRef =
                databaseReference.child(plantId);

        plantRef
                .child("manual_watering_duration")
                .setValue(duration);

        plantRef
                .child("water_pump_state")
                .setValue(true);
    }

    public void stopManualWatering(
            String plantId
    ) {

        databaseReference
                .child(plantId)
                .child("water_pump_state")
                .setValue(false);
    }

    public void setAutoWateringMode(
            String plantId,
            boolean enabled
    ) {

        DatabaseReference plantRef =
                databaseReference.child(plantId);

        plantRef
                .child("auto_watering_mode")
                .setValue(enabled);
    }

    /**
     * Resets a hardware slot by marking it as not taken.
     * This "leaves the space open" in the database for the next plant.
     */
    public void deletePlant(String plantId) {
        DatabaseReference plantRef = databaseReference.child(plantId);

        // Reset the 'taken' flag and identifying information
        plantRef.child("taken").setValue(0);
        plantRef.child("name").setValue("Available Slot");

        // Optional: Reset thresholds to standard for next use
        plantRef.child("threshold_profile").setValue("standard");
        plantRef.child("threshold").setValue(30);

        // Reset manual commands
        plantRef.child("water_pump_state").setValue(false);
    }

    public void updatePlantImage(String plantId, String imageUrl) {
        databaseReference.child(plantId).child("image_url").setValue(imageUrl);
    }

    public void activateConnectionPortal() {
        DatabaseReference portalRef = FirebaseDatabase.getInstance().getReference("connection_portal");
        portalRef.setValue(true);

        // Listen for when it's reset to false by the hardware
        portalRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean val = snapshot.getValue(Boolean.class);
                if (val != null && !val) {
                    // Reset to false, we can stop listening
                    portalRef.removeEventListener(this);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
