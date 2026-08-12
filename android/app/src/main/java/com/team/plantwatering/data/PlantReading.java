package com.team.plantwatering.data;

import android.os.Parcel;
import android.os.Parcelable;

public class PlantReading implements Parcelable {
    private final String identifier;
    private final String plantName;
    private final int soilHumidity;
    private final String waterTank;
    private final long lastWateredTimeMillis;
    private final String thresholdId;
    private final long lastSeenMillis;
    private final boolean manualWateringCommand;
    private final int manualWateringDuration;
    private final boolean autoWateringEnabled;
    private final String wateringMode;
    private final boolean isPumpActive;
    private final boolean isTaken;
    private final int sensorIndex;
    private final String imageUrl;

    public PlantReading(String identifier, String plantName, int soilHumidity, String waterTank,
                        long lastWateredTimeMillis, String thresholdId, long lastSeenMillis,
                        boolean manualWateringCommand, int manualWateringDuration, String wateringMode,
                        boolean isPumpActive, boolean autoWateringEnabled, boolean isTaken,
                        int sensorIndex, String imageUrl) {
        this.identifier = identifier;
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
        this.autoWateringEnabled = autoWateringEnabled;
        this.isTaken = isTaken;
        this.imageUrl = imageUrl;
        this.sensorIndex = sensorIndex;
    }

    protected PlantReading(Parcel in) {
        identifier = in.readString();
        plantName = in.readString();
        soilHumidity = in.readInt();
        waterTank = in.readString();
        lastWateredTimeMillis = in.readLong();
        thresholdId = in.readString();
        lastSeenMillis = in.readLong();
        manualWateringCommand = in.readByte() != 0;
        manualWateringDuration = in.readInt();
        wateringMode = in.readString();
        isPumpActive = in.readByte() != 0;
        autoWateringEnabled = in.readByte() != 0;
        isTaken = in.readByte() != 0;
        sensorIndex = in.readInt();
        imageUrl = in.readString();
    }

    public static final Creator<PlantReading> CREATOR = new Creator<PlantReading>() {
        @Override public PlantReading createFromParcel(Parcel in) { return new PlantReading(in); }
        @Override public PlantReading[] newArray(int size) { return new PlantReading[size]; }
    };

    public String getIdentifier() { return identifier; }
    public String getPlantName() { return plantName; }
    public int getSoilHumidity() { return soilHumidity; }
    public String getWaterTank() { return waterTank; }
    public long getLastWateredTimeMillis() { return lastWateredTimeMillis; }
    public String getThresholdId() { return thresholdId; }
    public long getLastSeenMillis() { return lastSeenMillis; }
    public boolean isOnline() { return isOnline(System.currentTimeMillis()); }
    public boolean isOnline(long currentTimeMillis) { return (currentTimeMillis - lastSeenMillis) < 120_000L; }
    public boolean isManualWateringCommand() { return manualWateringCommand; }
    public int getManualWateringDuration() { return manualWateringDuration; }
    public boolean isPumpActive() { return isPumpActive; }
    public boolean isAutoWateringEnabled() { return autoWateringEnabled; }
    public String getWateringMode() { return wateringMode; }
    public boolean isTaken() { return isTaken; }
    public String getImageUrl() { return imageUrl; }
    public int getSensorIndex() { return sensorIndex; }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(identifier);
        dest.writeString(plantName);
        dest.writeInt(soilHumidity);
        dest.writeString(waterTank);
        dest.writeLong(lastWateredTimeMillis);
        dest.writeString(thresholdId);
        dest.writeLong(lastSeenMillis);
        dest.writeByte((byte) (manualWateringCommand ? 1 : 0));
        dest.writeInt(manualWateringDuration);
        dest.writeString(wateringMode);
        dest.writeByte((byte) (isPumpActive ? 1 : 0));
        dest.writeByte((byte) (autoWateringEnabled ? 1 : 0));
        dest.writeByte((byte) (isTaken ? 1 : 0));
        dest.writeInt(sensorIndex);
        dest.writeString(imageUrl);
    }
}
