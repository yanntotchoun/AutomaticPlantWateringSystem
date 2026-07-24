package com.team.plantwatering.data;

import android.os.Parcel;
import android.os.Parcelable;

public class PlantReading implements Parcelable {

    private final String plantName;
    private final int soilHumidity;
    private final int waterTank;
    private final long lastWateredTimeMillis;
    private final String thresholdId;
    private final long lastSeenMillis;

    // Manual Watering Control Fields (Task BSCK-8.1)
    private final boolean manualWateringCommand;
    private final int manualWateringDuration;
    private final String wateringMode;
    private final boolean isPumpActive;

    public PlantReading(
            String plantName,
            int soilHumidity,
            int waterTank,
            long lastWateredTimeMillis,
            String thresholdId,
            long lastSeenMillis,
            boolean manualWateringCommand,
            int manualWateringDuration,
            String wateringMode,
            boolean isPumpActive
    ) {
        this.plantName = plantName;
        this.soilHumidity = soilHumidity;
        this.waterTank = waterTank;
        this.lastWateredTimeMillis = lastWateredTimeMillis;
        this.thresholdId = thresholdId;
        this.lastSeenMillis = lastSeenMillis;
        this.manualWateringCommand = manualWateringCommand;
        this.manualWateringDuration = manualWateringDuration;
        this.wateringMode = wateringMode;
        this.isPumpActive = isPumpActive;
    }

    protected PlantReading(Parcel in) {
        plantName = in.readString();
        soilHumidity = in.readInt();
        waterTank = in.readInt();
        lastWateredTimeMillis = in.readLong();
        thresholdId = in.readString();
        lastSeenMillis = in.readLong();
        manualWateringCommand = in.readByte() != 0;
        manualWateringDuration = in.readInt();
        wateringMode = in.readString();
        isPumpActive = in.readByte() != 0;
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
        return (System.currentTimeMillis() - lastSeenMillis) < 120_000L;
    }

    public boolean isManualWateringCommand() {
        return manualWateringCommand;
    }

    public int getManualWateringDuration() {
        return manualWateringDuration;
    }

    public String getWateringMode() {
        return wateringMode;
    }

    public boolean isPumpActive() {
        return isPumpActive;
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
        dest.writeByte((byte) (manualWateringCommand ? 1 : 0));
        dest.writeInt(manualWateringDuration);
        dest.writeString(wateringMode);
        dest.writeByte((byte) (isPumpActive ? 1 : 0));
    }
}
