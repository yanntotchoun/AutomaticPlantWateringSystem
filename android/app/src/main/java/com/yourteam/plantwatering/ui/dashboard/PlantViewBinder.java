package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yourteam.plantwatering.R;

import java.util.Locale;

/**
 * Static helpers that bind the small reusable pieces shared across screens.
 * Ported from PlantAvatar, StatusChip, HumidityDropletBar, and WaterTankRow
 * (originally @Composable functions in the shared components file).
 */
public final class PlantViewBinder {

    private PlantViewBinder() {
    }

    /** Ported from PlantAvatar: circle showing the first letter of the plant name. */
    public static void bindAvatar(TextView avatarView, String plantName) {
        String initial = (plantName == null || plantName.isEmpty())
                ? "?"
                : plantName.substring(0, 1).toUpperCase(Locale.getDefault());
        avatarView.setText(initial);
    }

    /** Ported from StatusChip: label + colors based on humidity severity. */
    public static void bindStatusChip(TextView chipView, int humidity) {
        chipView.setText(DashboardUtils.humidityStatusLabel(humidity));
        chipView.setTextColor(DashboardUtils.humidityTextColor(humidity));
        if (humidity < 30) {
            chipView.setBackgroundResource(R.drawable.bg_chip_dry);
        } else if (humidity < 60) {
            chipView.setBackgroundResource(R.drawable.bg_chip_medium);
        } else {
            chipView.setBackgroundResource(R.drawable.bg_chip_healthy);
        }
    }

    /**
     * Ported from HumidityDropletBar: fills the given container with 10 droplet
     * ImageViews, showing filled vs. empty based on the humidity percentage.
     * Call this on bind (not just once) since RecyclerView rows are recycled.
     */
    public static void bindDropletBar(LinearLayout container, int humidityPercentage) {
        Context context = container.getContext();
        int filledCount = DashboardUtils.filledDropletCount(humidityPercentage);

        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < 10; i++) {
            ImageView drop = new ImageView(context);
            int sizePx = (int) (26 * context.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMarginEnd((int) (4 * context.getResources().getDisplayMetrics().density));
            drop.setLayoutParams(params);
            drop.setImageResource(i < filledCount ? R.drawable.drop_filled : R.drawable.drop_empty);
            container.addView(drop);
        }
    }

    /** Ported from WaterTankRow: bucket icon + colored percentage text. */
    public static void bindWaterTank(ImageView bucketView, TextView percentView, int waterTank) {
        bucketView.setImageResource(
                waterTank >= 70 ? R.drawable.bucket_of_water_detail : R.drawable.bucket_detail
        );
        percentView.setText(String.format(Locale.getDefault(), "%d%%", waterTank));
        percentView.setTextColor(DashboardUtils.tankTextColor(waterTank));
    }
}