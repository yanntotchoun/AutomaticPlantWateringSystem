package com.team.plantwatering.ui.dashboard;

import android.graphics.Color;
import com.team.plantwatering.data.PlantReading;
import java.text.DateFormat;
import java.util.Date;

public final class DashboardUtils {
    private DashboardUtils() {}

    public static int filledDropletCount(int humidityPercentage) {
        int filled = Math.round(humidityPercentage / 10f);
        if (filled < 0) return 0;
        if (filled > 10) return 10;
        return filled;
    }

    public static String formatWateringEventDateTime(long timestampMillis) {
        if (timestampMillis <= 0L) return "Unknown date and time";
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return formatter.format(new Date(timestampMillis));
    }

    public static String formatRelativeLastWateredTime(long timeMillis, long currentTimeMillis) {
        return formatRelativeTime(timeMillis, currentTimeMillis);
    }

    public static String formatRelativeTime(long timeMillis, long currentTimeMillis) {
        long elapsedMillis = Math.max(currentTimeMillis - timeMillis, 0L);
        long elapsedMinutes = elapsedMillis / 60_000L;
        if (elapsedMinutes < 1) return "Just now";
        if (elapsedMinutes == 1L) return "1 minute ago";
        if (elapsedMinutes < 60) return elapsedMinutes + " minutes ago";
        if (elapsedMinutes < 120) return "1 hour ago";
        if (elapsedMinutes < 24 * 60) return (elapsedMinutes / 60) + " hours ago";
        if (elapsedMinutes < 48 * 60) return "1 day ago";
        if (elapsedMinutes < 7 * 24 * 60) return (elapsedMinutes / (24 * 60)) + " days ago";
        long weeks = elapsedMinutes / (7 * 24 * 60);
        return weeks == 1L ? "1 week ago" : weeks + " weeks ago";
    }

    public static int humidityTextColor(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) return Color.parseColor("#9C1C16");
        if (humidity < dryThreshold + 30) return Color.parseColor("#B26A00");
        return Color.parseColor("#2E7D32");
    }

    public static int tankTextColor(String waterTank) {
        if (waterTank == null) return Color.GRAY;
        String lower = waterTank.toLowerCase();
        if (lower.contains("insufficient")) return Color.parseColor("#C62828");
        if (lower.contains("sufficient") || lower.contains("full")) return Color.parseColor("#2E7D32");
        if (lower.contains("low") || lower.contains("medium")) return Color.parseColor("#B26A00");
        return Color.parseColor("#9C1C16");
    }

    public static String humidityStatusLabel(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) return "Dry";
        if (humidity < dryThreshold + 30) return "Medium";
        return "Healthy";
    }

    public static int humidityStatusContainerColor(int humidity) {
        if (humidity < 30) return Color.parseColor("#FBE4E2");
        if (humidity < 60) return Color.parseColor("#F8EBCF");
        return Color.parseColor("#DDEFD5");
    }

    public static String plantStatusMessage(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) return "This plant needs water soon.";
        if (humidity < dryThreshold + 30) return "This plant has medium soil humidity.";
        return "This plant has healthy soil humidity.";
    }

    public static String plantRecommendation(PlantReading plant, int dryThreshold) {
        if (plant.getSoilHumidity() < dryThreshold) return plant.getPlantName() + " is currently dry. Check the water tank and consider watering this plant soon.";
        if (plant.getWaterTank().contains("Low")) return "The soil humidity is acceptable, but the water tank level is low. Refill the tank soon.";
        return plant.getPlantName() + " looks stable. Keep monitoring the soil humidity and water tank level.";
    }
}
