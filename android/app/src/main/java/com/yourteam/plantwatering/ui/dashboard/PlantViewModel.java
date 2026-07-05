package com.yourteam.plantwatering.ui.dashboard;

import androidx.lifecycle.ViewModel;

import com.yourteam.plantwatering.data.PlantReading;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity-scoped ViewModel shared by all fragments (Dashboard, Overview, Details).
 * Ported from the sample plant list that lived inside PlantDashboardScreen()'s
 * `remember { listOf(...) }` block.
 */
public class PlantViewModel extends ViewModel {

    private final List<PlantReading> plants;

    public PlantViewModel() {
        long now = System.currentTimeMillis();

        plants = new ArrayList<>();
        plants.add(new PlantReading(
                "Basil",
                72,
                70,
                23,
                now - 10 * 60 * 1000L,
                "standard"
        ));
        plants.add(new PlantReading(
                "Tomato",
                38,
                40,
                24,
                now - 2 * 60 * 60 * 1000L,
                "tropical"
        ));
        plants.add(new PlantReading(
                "Mint",
                19,
                20,
                22,
                now - 2L * 24 * 60 * 60 * 1000L,
                "succulent"
        ));
    }

    public List<PlantReading> getPlants() {
        return plants;
    }

    public void updatePlantThreshold(String plantName, String newThresholdId) {
        for (int i = 0; i < plants.size(); i++) {
            PlantReading p = plants.get(i);
            if (p.getPlantName().equals(plantName)) {
                plants.set(i, new PlantReading(
                        p.getPlantName(),
                        p.getSoilHumidity(),
                        p.getWaterTank(),
                        p.getTemperature(),
                        p.getLastWateredTimeMillis(),
                        newThresholdId
                ));
                break;
            }
        }
    }
}