package com.yourteam.plantwatering.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Plain Java model for a single plant's sensor reading.
 * Inferred from field usage in the Kotlin Compose files
 * (plantName, soilHumidity, waterTank, temperature, lastWateredTimeMillis).
 * Implements Parcelable so a clicked plant can be passed from DashboardFragment
 * to PlantDetailsFragment via a Bundle argument (the Java/Views equivalent of
 * Compose's `var selectedPlant by remember { mutableStateOf<PlantReading?>(null) }`).
 */
public class PlantReading implements Parcelable {

    private final String plantName;
    private final int soilHumidity;
    private final int waterTank;
    private final int temperature;
    private final long lastWateredTimeMillis;
    private final String thresholdId;

    public PlantReading(
            String plantName,
            int soilHumidity,
            int waterTank,
            int temperature,
            long lastWateredTimeMillis,
            String thresholdId
    ) {
        this.plantName = plantName;
        this.soilHumidity = soilHumidity;
        this.waterTank = waterTank;
        this.temperature = temperature;
        this.lastWateredTimeMillis = lastWateredTimeMillis;
        this.thresholdId = thresholdId;
    }

    protected PlantReading(Parcel in) {
        plantName = in.readString();
        soilHumidity = in.readInt();
        waterTank = in.readInt();
        temperature = in.readInt();
        lastWateredTimeMillis = in.readLong();
        thresholdId = in.readString();
    }

    public static final Creator<PlantReading> CREATOR = new Creator<PlantReading>() {
        @Override
        public PlantReading createFromParcel(Parcel in) {
            return new PlantReading(in);
        }

        @Override
        public PlantReading[] newArray(int size) {
            return new PlantReading[size];
        }
    };

    public String getPlantName() {
        return plantName;
    }

    public int getSoilHumidity() {
        return soilHumidity;
    }

    public int getWaterTank() {
        return waterTank;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getTemperatureFahrenheit() {
        return (int) (temperature * 9.0 / 5.0 + 32);
    }

    public long getLastWateredTimeMillis() {
        return lastWateredTimeMillis;
    }

    public String getThresholdId() {
        return thresholdId;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(plantName);
        dest.writeInt(soilHumidity);
        dest.writeInt(waterTank);
        dest.writeInt(temperature);
        dest.writeLong(lastWateredTimeMillis);
        dest.writeString(thresholdId);
    }
}
