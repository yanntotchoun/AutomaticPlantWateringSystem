package com.team.plantwatering.ui.dashboard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.team.plantwatering.data.PlantReading;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlantViewModel extends ViewModel {
    private static final String TAG = "PlantFirebase";
    private final MutableLiveData<List<PlantReading>> plantsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final DatabaseReference databaseReference;
    private ValueEventListener plantsListener;
    private static final int WATERING_LOG_LIMIT = 5;
    public static final long AUTO_WATERING_REENABLE_DELAY_MILLIS = 10_000L;
    private final MutableLiveData<List<Long>> wateringLogLiveData = new MutableLiveData<>(new ArrayList<>());
    private Query wateringLogQuery;
    private ValueEventListener wateringLogListener;
    private final SimpleDateFormat firmwareDateFormat = new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.US);
    private long serverTimeOffset = 0;
    private String lastNamesList = null;
    private long globalLastSeenMillis = 0L;
    private final Handler reEnableHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingReEnableTasks = new HashMap<>();

    public PlantViewModel() {
        databaseReference = FirebaseDatabase.getInstance().getReference("plants");
        listenForServerTimeOffset();
        listenForFirebaseConnection();
        listenForGlobalLastTime();
    }

    private void listenForFirebaseConnection() {
        FirebaseDatabase.getInstance().getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (Boolean.TRUE.equals(connected)) Log.d(TAG, "Connected to Firebase.");
                else Log.w(TAG, "Not connected to Firebase.");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { Log.e(TAG, "Firebase connection check failed: " + error.getMessage()); }
        });
    }

    private void listenForServerTimeOffset() {
        FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long offset = snapshot.getValue(Long.class);
                if (offset != null) serverTimeOffset = offset;
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenForGlobalLastTime() {
        FirebaseDatabase.getInstance().getReference("last_time").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String timeStr = snapshot.getValue(String.class);
                if (timeStr != null) {
                    globalLastSeenMillis = parseFirmwareTimeToMillis(timeStr);
                    List<PlantReading> currentPlants = plantsLiveData.getValue();
                    if (currentPlants != null && !currentPlants.isEmpty()) {
                        List<PlantReading> updated = new ArrayList<>();
                        for (PlantReading p : currentPlants) {
                            updated.add(new PlantReading(p.getIdentifier(), p.getPlantName(), p.getSoilHumidity(), p.getWaterTank(), p.getLastWateredTimeMillis(), p.getThresholdId(), globalLastSeenMillis, p.isManualWateringCommand(), p.getManualWateringDuration(), p.getWateringMode(), p.isPumpActive(), p.isAutoWateringEnabled(), p.isTaken(), p.getSensorIndex(), p.getImageUrl()));
                        }
                        plantsLiveData.setValue(updated);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public long getCurrentServerTime() { return System.currentTimeMillis() + serverTimeOffset; }
    public LiveData<List<PlantReading>> getPlants() { return plantsLiveData; }
    public LiveData<List<Long>> getWateringLog() { return wateringLogLiveData; }

    public void startListeningForWateringLog(String plantId) {
        stopListeningForWateringLog();
        wateringLogLiveData.setValue(new ArrayList<>());
        wateringLogQuery = databaseReference.child(plantId).child("watering_log").orderByChild("timestamp").limitToLast(WATERING_LOG_LIMIT);
        wateringLogListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Long> wateringTimes = new ArrayList<>();
                for (DataSnapshot eventSnapshot : snapshot.getChildren()) {
                    Long timestamp = parseSafeLong(eventSnapshot.child("timestamp").getValue());
                    if (timestamp != null && timestamp > 0L) wateringTimes.add(timestamp);
                }
                Collections.sort(wateringTimes, Collections.reverseOrder());
                wateringLogLiveData.setValue(wateringTimes);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { wateringLogLiveData.setValue(new ArrayList<>()); }
        };
        wateringLogQuery.addValueEventListener(wateringLogListener);
    }

    public void stopListeningForWateringLog() {
        if (wateringLogQuery != null && wateringLogListener != null) wateringLogQuery.removeEventListener(wateringLogListener);
        wateringLogQuery = null;
        wateringLogListener = null;
    }

    public void startListeningForChanges() {
        if (plantsListener != null) return;
        plantsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PlantReading> updatedPlants = new ArrayList<>();
                for (DataSnapshot plantSnapshot : snapshot.getChildren()) {
                    String key = plantSnapshot.getKey();
                    if (key == null || key.startsWith(".") || key.equals("logs") || key.equals("plants")) continue;
                    String name = plantSnapshot.child("name").getValue(String.class);
                    if (name == null) name = key;
                    Integer moisture = parseSafeInt(plantSnapshot.child("moisture_level").getValue());
                    String waterStr = plantSnapshot.child("water_level").getValue(String.class);
                    String timeStr = plantSnapshot.child("last_time").getValue(String.class);
                    String thresholdProfileId = plantSnapshot.child("threshold_profile").getValue(String.class);
                    if (thresholdProfileId == null) thresholdProfileId = "standard";
                    Boolean pumpState = parseSafeBoolean(plantSnapshot.child("water_pump_state").getValue());
                    String mode = plantSnapshot.child("watering_mode").getValue(String.class);
                    Boolean autoEnabled = parseSafeBoolean(plantSnapshot.child("auto_watering_mode").getValue());
                    Integer duration = parseSafeInt(plantSnapshot.child("latest_watering_status").child("duration").getValue());
                    Boolean isPumpActive = parseSafeBoolean(plantSnapshot.child("is_pump_active").getValue());
                    Integer takenInt = parseSafeInt(plantSnapshot.child("taken").getValue());
                    boolean isTaken = takenInt != null && takenInt == 1;
                    Integer sensorIndexInt = parseSafeInt(plantSnapshot.child("sensor_index").getValue());
                    int sensorIndex = (sensorIndexInt != null) ? sensorIndexInt : (key.contains("slot2") ? 2 : 1);
                    String imageUrl = plantSnapshot.child("image_url").getValue(String.class);
                    int h = (moisture != null) ? Math.min(Math.max(moisture, 0), 100) : 0;
                    String w = waterStr != null ? waterStr : "Unknown";
                    long lw = parseFirmwareTimeToMillis(timeStr);
                    boolean mc = pumpState != null && pumpState;
                    int md = duration != null ? duration : 3;
                    boolean ac = autoEnabled != null && autoEnabled;
                    String sm = mode != null ? mode : "Auto";
                    boolean ipa = isPumpActive != null && isPumpActive;
                    updatedPlants.add(new PlantReading(key, name, h, w, lw, thresholdProfileId, globalLastSeenMillis, mc, md, sm, ipa, ac, isTaken, sensorIndex, imageUrl));
                }
                plantsLiveData.setValue(updatedPlants);
                updatePlantNamesNode(updatedPlants);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { Log.e(TAG, "Firebase /plants read failed: " + error.getMessage()); }
        };
        databaseReference.addValueEventListener(plantsListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (databaseReference != null && plantsListener != null) databaseReference.removeEventListener(plantsListener);
        stopListeningForWateringLog();
        for (Runnable task : pendingReEnableTasks.values()) reEnableHandler.removeCallbacks(task);
        pendingReEnableTasks.clear();
    }

    private void updatePlantNamesNode(List<PlantReading> plants) {
        if (plants == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plants.size(); i++) {
            sb.append(plants.get(i).getPlantName());
            if (i < plants.size() - 1) sb.append(", ");
        }
        String namesList = sb.toString();
        if (namesList.equals(lastNamesList)) return;
        lastNamesList = namesList;
        FirebaseDatabase.getInstance().getReference("names").setValue(namesList);
    }

    private long parseFirmwareTimeToMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0L;
        try {
            Date date = firmwareDateFormat.parse(timeStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                int currentYear = cal.get(Calendar.YEAR);
                cal.setTime(date);
                cal.set(Calendar.YEAR, currentYear);
                return cal.getTimeInMillis();
            }
        } catch (ParseException e) { return 0L; }
        return 0L;
    }

    private Long parseSafeLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Boolean parseSafeBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).longValue() == 1L;
        if (value instanceof String) return ((String) value).equalsIgnoreCase("true") || value.equals("1");
        return null;
    }

    private Integer parseSafeInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public void updatePlantThreshold(String plantId, PlantSettingsManager.ThresholdProfile profile) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("threshold_profile").setValue(profile.id);
        plantRef.child("threshold").setValue(profile.drySoil);
    }

    public void syncProfileChangesToFirebase(PlantSettingsManager.ThresholdProfile profile) {
        databaseReference.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot plantSnap : snapshot.getChildren()) {
                String profileId = plantSnap.child("threshold_profile").getValue(String.class);
                if (profileId != null && profileId.equals(profile.id)) plantSnap.getRef().child("threshold").setValue(profile.drySoil);
            }
        });
    }

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

    public interface AddPlantCallback { void onSuccess(String plantId); void onError(String message); }

    public void addPlant(String plantName, String selectedSlot, PlantSettingsManager.ThresholdProfile profile, AddPlantCallback callback) {
        if (selectedSlot == null || selectedSlot.trim().isEmpty()) {
            if (callback != null) callback.onError("No hardware slot is available.");
            return;
        }
        DatabaseReference plantRef = databaseReference.child(selectedSlot);
        plantRef.get().addOnSuccessListener(snapshot -> {
            Integer taken = parseSafeInt(snapshot.child("taken").getValue());
            if (taken != null && taken == 1) {
                if (callback != null) callback.onError("That hardware slot is already occupied. Please try again.");
                return;
            }
            Map<String, Object> values = new HashMap<>();
            values.put("name", plantName);
            values.put("moisture_level", 0);
            values.put("water_level", "Unknown");
            values.put("taken", 1);
            values.put("sensor_index", selectedSlot.contains("slot2") ? 2 : 1);
            values.put("last_time", firmwareDateFormat.format(new Date()));
            values.put("threshold_profile", profile.id);
            values.put("threshold", profile.drySoil);
            values.put("water_pump_state", false);
            values.put("manual_watering_duration", 3);
            values.put("auto_watering_mode", true);
            values.put("watering_log/placeholder", 0);
            plantRef.updateChildren(values).addOnSuccessListener(unused -> {
                if (callback != null) callback.onSuccess(selectedSlot);
            }).addOnFailureListener(error -> {
                if (callback != null) callback.onError("Could not add plant: " + error.getMessage());
            });
        }).addOnFailureListener(error -> {
            if (callback != null) callback.onError("Could not check hardware slot: " + error.getMessage());
        });
    }

    public void requestManualWatering(String plantId, int duration) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("manual_watering_duration").setValue(duration);
        plantRef.child("water_pump_state").setValue(true);
    }

    public void stopManualWatering(String plantId) { databaseReference.child(plantId).child("water_pump_state").setValue(false); }

    public void setAutoWateringMode(String plantId, boolean enabled) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("auto_watering_mode").setValue(enabled);
        Runnable pendingTask = pendingReEnableTasks.remove(plantId);
        if (pendingTask != null) reEnableHandler.removeCallbacks(pendingTask);
        if (!enabled) {
            Runnable reEnableTask = () -> {
                plantRef.child("auto_watering_mode").setValue(true);
                pendingReEnableTasks.remove(plantId);
            };
            pendingReEnableTasks.put(plantId, reEnableTask);
            reEnableHandler.postDelayed(reEnableTask, AUTO_WATERING_REENABLE_DELAY_MILLIS);
        }
    }

    public void cancelAutoWateringTimer(String plantId) {
        Runnable pendingTask = pendingReEnableTasks.remove(plantId);
        if (pendingTask != null) reEnableHandler.removeCallbacks(pendingTask);
    }

    public void stopAutoWateringMode(String plantId) { databaseReference.child(plantId).child("auto_watering_mode").setValue(false); }

    public void deletePlant(String plantId) {
        DatabaseReference plantRef = databaseReference.child(plantId);
        plantRef.child("taken").setValue(0);
        plantRef.child("name").setValue("Available Slot");
        plantRef.child("threshold_profile").setValue("standard");
        plantRef.child("threshold").setValue(30);
        plantRef.child("water_pump_state").setValue(false);
    }

    public void updatePlantImage(String plantId, String imageUrl) { databaseReference.child(plantId).child("image_url").setValue(imageUrl); }

    public void activateConnectionPortal() {
        DatabaseReference portalRef = FirebaseDatabase.getInstance().getReference("connection_portal");
        portalRef.setValue(true);
        portalRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean val = snapshot.getValue(Boolean.class);
                if (val != null && !val) portalRef.removeEventListener(this);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
