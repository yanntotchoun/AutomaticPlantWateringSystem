package com.yourteam.plantwatering.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yourteam.plantwatering.data.PlantReading;

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
        startListeningForChanges();
    }

    public LiveData<List<PlantReading>> getPlants() {
        return plantsLiveData;
    }

    private void startListeningForChanges() { //The firebase starts recording the changes here in real time.
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


                    int h = (moisture != null) ? moisture : 0;
                    int w = (water != null) ? water : 0;
                    long lw = (lastTimeWatered != null) ? lastTimeWatered : 0L; //time set to current time when the plant is initiated.
                    String tid = (thresholdProfile != null) ? thresholdProfile : "standard";
                    long ls = (lastSeenOnline != null) ? lastSeenOnline : 0L;

                    updatedPlants.add(new PlantReading(name, h, w, lw, tid, ls));
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
        
        newPlantRef.child("last_time_watered_Millis").setValue(now); //This format (Millis Unix timestamp,
                                                                   // is the seconds passed since January 1st 1970 until now in UTC(Coordinated Universal Time).
                                                                             // It is easier for the Arduino to read. This maintains O(1) time complexity.

        newPlantRef.child("last_time_watered").setValue(readableTime); //This format is easier for us to read and debug on the firebase.

        newPlantRef.child("threshold_profile").setValue("standard");

        newPlantRef.child("connection_status_Millis").setValue(now);
        newPlantRef.child("connection_status").setValue(readableTime);
    }

    public void deletePlant(String name) {
        databaseReference.child(name).removeValue();
    }
}
