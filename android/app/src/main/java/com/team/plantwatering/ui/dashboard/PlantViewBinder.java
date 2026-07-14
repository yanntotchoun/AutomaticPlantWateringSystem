package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.team.plantwatering.R;

import java.util.Locale;


public final class PlantViewBinder {

    private PlantViewBinder() {
    }


    public static void bindAvatar(TextView avatarView, String plantName) {
        String initial = (plantName == null || plantName.isEmpty())
                ? "?"
                : plantName.substring(0, 1).toUpperCase(Locale.getDefault());
        avatarView.setText(initial);
    }

    public static void bindStatusChip(TextView chipView, int humidity, int dryThreshold) {
        chipView.setText(DashboardUtils.humidityStatusLabel(humidity, dryThreshold));
        chipView.setTextColor(DashboardUtils.humidityTextColor(humidity, dryThreshold));
        if (humidity < dryThreshold) {
            chipView.setBackgroundResource(R.drawable.bg_chip_dry);
        } else if (humidity < dryThreshold + 30) {
            chipView.setBackgroundResource(R.drawable.bg_chip_medium);
        } else {
            chipView.setBackgroundResource(R.drawable.bg_chip_healthy);
        }
    }


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


    public static void bindWaterTank(ImageView bucketView, TextView percentView, int waterTank, int fullThreshold) {
        bucketView.setImageResource(
                waterTank >= fullThreshold ? R.drawable.bucket_of_water_detail : R.drawable.bucket_detail
        );
        percentView.setText(String.format(Locale.getDefault(), "%d%%", waterTank));
        percentView.setTextColor(DashboardUtils.tankTextColor(waterTank, fullThreshold));
    }
}