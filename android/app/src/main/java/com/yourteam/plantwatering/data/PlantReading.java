package com.yourteam.plantwatering.data;

import android.os.Parcel;
import android.os.Parcelable;

public class PlantReading implements Parcelable {

    private final String plantName;
    private final int soilHumidity;
    private final int waterTank;
    private final long lastWateredTimeMillis;
    private final String thresholdId;
    private final long lastSeenMillis;

    public PlantReading(
            String plantName,
            int soilHumidity,
            int waterTank,
            long lastWateredTimeMillis,
            String thresholdId,
            long lastSeenMillis
    ) {
        this.plantName = plantName;
        this.soilHumidity = soilHumidity;
        this.waterTank = waterTank;
        this.lastWateredTimeMillis = lastWateredTimeMillis;
        this.thresholdId = thresholdId;
        this.lastSeenMillis = lastSeenMillis;
    }

    protected PlantReading(Parcel in) {
        plantName = in.readString();
        soilHumidity = in.readInt();
        waterTank = in.readInt();
        lastWateredTimeMillis = in.readLong();
        thresholdId = in.readString();
        lastSeenMillis = in.readLong();
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

    public long getLastWateredTimeMillis() {
        return lastWateredTimeMillis;
    }

    public String getThresholdId() {
        return thresholdId;
    }

    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public boolean isOnline() {
        // When the plant is first added, the connection status will be set to online if seen in the last 2 minutes.
        // The user then has two minutes to connect the MCU before the status switches to offline.
        return (System.currentTimeMillis() - lastSeenMillis) < 120_000L;
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
        dest.writeLong(lastWateredTimeMillis);
        dest.writeString(thresholdId);
        dest.writeLong(lastSeenMillis);
    }
}
