package com.team.plantwatering.data;

import android.os.Parcel;
import android.os.Parcelable;

public class PlantReading implements Parcelable {

    private final String plantName;
    private final int soilHumidity;
    private final String waterTank;
    private final long lastWateredTimeMillis;
    private final String thresholdId;
    private final long lastSeenMillis;

    // Manual Watering Control Fields (Task BSCK-8.1)
    private final boolean manualWateringCommand;
    private final int manualWateringDuration;
    private final boolean autoWateringEnabled;

    public PlantReading(
            String plantName,
            int soilHumidity,
            String waterTank,
            long lastWateredTimeMillis,
            String thresholdId,
            long lastSeenMillis,
            boolean manualWateringCommand,
            int manualWateringDuration,
            boolean autoWateringEnabled
    ) {
        this.plantName = plantName;
        this.soilHumidity = soilHumidity;
        this.waterTank = waterTank;
        this.lastWateredTimeMillis = lastWateredTimeMillis;
        this.thresholdId = thresholdId;
        this.lastSeenMillis = lastSeenMillis;
        this.manualWateringCommand = manualWateringCommand;
        this.manualWateringDuration = manualWateringDuration;
        this.autoWateringEnabled = autoWateringEnabled;
    }

    protected PlantReading(Parcel in) {
        plantName = in.readString();
        soilHumidity = in.readInt();
        waterTank = in.readString();
        lastWateredTimeMillis = in.readLong();
        thresholdId = in.readString();
        lastSeenMillis = in.readLong();
        manualWateringCommand = in.readByte() != 0;
        manualWateringDuration = in.readInt();
        autoWateringEnabled = in.readByte() != 0;
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

    public String getWaterTank() {
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
        // Use 10-minute threshold (600 000ms) relative to local time, fix using local time instead of server-synced due to bug
        return (System.currentTimeMillis() - lastSeenMillis) < 600_000L;
    }

    public boolean isManualWateringCommand() {
        return manualWateringCommand;
    }

    public int getManualWateringDuration() {
        return manualWateringDuration;
    }

    public boolean isPumpActive() {
        // Derived from manualWateringCommand (water_pump_state)
        return manualWateringCommand;
    }

    public boolean isAutoWateringEnabled() {
        return autoWateringEnabled;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(plantName);
        dest.writeInt(soilHumidity);
        dest.writeString(waterTank);
        dest.writeLong(lastWateredTimeMillis);
        dest.writeString(thresholdId);
        dest.writeLong(lastSeenMillis);
        dest.writeByte((byte) (manualWateringCommand ? 1 : 0));
        dest.writeInt(manualWateringDuration);
        dest.writeByte((byte) (autoWateringEnabled ? 1 : 0));
    }
}
