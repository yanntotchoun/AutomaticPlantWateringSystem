package com.team.plantwatering.ui.dashboard;

import android.graphics.Color;

import com.team.plantwatering.data.PlantReading;
import java.text.DateFormat;
import java.util.Date;

public final class DashboardUtils {

    private DashboardUtils() {
        // no instances
    }

    /**
     * Converts a humidity percentage (0-100) into a filled-droplet count (0-10).
     * Mirrors the math inside the old HumidityDropletBar composable.
     */
    public static int filledDropletCount(int humidityPercentage) {
        int filled = Math.round(humidityPercentage / 10f);
        if (filled < 0) return 0;
        if (filled > 10) return 10;
        return filled;
    }

    /**
     * Formats the elapsed time since the plant was last watered into a
     * human-friendly relative string, e.g. "5 minutes ago", "2 days ago".
     */
    /**
     * Converts a Firebase timestamp into a readable date and time,
     * using the phone's language and time zone.
     */
    public static String formatWateringEventDateTime(
            long timestampMillis
    ) {
        if (timestampMillis <= 0L) {
            return "Unknown date and time";
        }

        DateFormat formatter =
                DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                );

        return formatter.format(
                new Date(timestampMillis)
        );
    }
    public static String formatRelativeLastWateredTime(long timeMillis, long currentTimeMillis) {
        return formatRelativeTime(timeMillis, currentTimeMillis);
    }

    public static String formatRelativeTime(long timeMillis, long currentTimeMillis) {
        // Make sure elapsed time never becomes negative, even if the device clock changes.
        long elapsedMillis = Math.max(currentTimeMillis - timeMillis, 0L);
        long elapsedMinutes = elapsedMillis / 60_000L;

        if (elapsedMinutes < 1) {
            return "Just now";
        } else if (elapsedMinutes == 1L) {
            return "1 minute ago";
        } else if (elapsedMinutes < 60) {
            return elapsedMinutes + " minutes ago";
        } else if (elapsedMinutes < 120) {
            return "1 hour ago";
        } else if (elapsedMinutes < 24 * 60) {
            long hours = elapsedMinutes / 60;
            return hours + " hours ago";
        } else if (elapsedMinutes < 48 * 60) {
            return "1 day ago";
        } else if (elapsedMinutes < 7 * 24 * 60) {
            long days = elapsedMinutes / (24 * 60);
            return days + " days ago";
        } else {
            long weeks = elapsedMinutes / (7 * 24 * 60);
            return weeks == 1L ? "1 week ago" : weeks + " weeks ago";
        }
    }

    /**
     * Text color to use for a humidity reading, based on severity.
     */
    public static int humidityTextColor(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) {
            return Color.parseColor("#9C1C16");
        } else if (humidity < dryThreshold + 30) {
            return Color.parseColor("#B26A00");
        } else {
            return Color.parseColor("#2E7D32");
        }
    }

    /**
     * Text color to use for the water tank level string.
     */
    public static int tankTextColor(String waterTank) {
        if (waterTank == null) return Color.GRAY;
        
        String lower = waterTank.toLowerCase();
        if (lower.contains("insufficient")) {
             return Color.parseColor("#C62828"); // Error red
        } else if (lower.contains("sufficient") || lower.contains("full")) {
            return Color.parseColor("#2E7D32");
        } else if (lower.contains("low") || lower.contains("medium")) {
            return Color.parseColor("#B26A00");
        } else {
            return Color.parseColor("#9C1C16");
        }
    }

    /**
     * Status label for a humidity reading (used by the old StatusChip composable).
     */
    public static String humidityStatusLabel(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) {
            return "Dry";
        } else if (humidity < dryThreshold + 30) {
            return "Medium";
        } else {
            return "Healthy";
        }
    }

    /**
     * Container (background) color for the status chip, matching StatusChip's logic.
     */
    public static int humidityStatusContainerColor(int humidity) {
        if (humidity < 30) {
            return Color.parseColor("#FBE4E2");
        } else if (humidity < 60) {
            return Color.parseColor("#F8EBCF");
        } else {
            return Color.parseColor("#DDEFD5");
        }
    }

    /**
     * Short status message shown under the plant name, based on humidity.
     * Ported from PlantDetailsScreen.kt's plantStatusMessage().
     */
    public static String plantStatusMessage(int humidity, int dryThreshold) {
        if (humidity < dryThreshold) {
            return "This plant needs water soon.";
        } else if (humidity < dryThreshold + 30) {
            return "This plant has medium soil humidity.";
        } else {
            return "This plant has healthy soil humidity.";
        }
    }

    /**
     * Simple recommendation text based on the plant's current readings.
     * Ported from PlantDetailsScreen.kt's plantRecommendation().
     */
    public static String plantRecommendation(PlantReading plant, int dryThreshold) {
        if (plant.getSoilHumidity() < dryThreshold) {
            return plant.getPlantName()
                    + " is currently dry. Check the water tank and consider watering this plant soon.";
        } else if (plant.getWaterTank().contains("Low")) {
            return "The soil humidity is acceptable, but the water tank level is low. Refill the tank soon.";
        } else {
            return plant.getPlantName()
                    + " looks stable. Keep monitoring the soil humidity and water tank level.";
        }
    }
}