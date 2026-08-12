package com.team.plantwatering;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class PlantWateringApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "eseg3e0c");
        config.put("secure", true);

        MediaManager.init(this, config);
    }
}