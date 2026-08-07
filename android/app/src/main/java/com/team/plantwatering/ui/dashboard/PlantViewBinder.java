package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;

import java.util.Locale;

public final class PlantViewBinder {

    private PlantViewBinder() {
    }

    /**
     * Original avatar binder.
     *
     * Kept for compatibility with any screens that still
     * only use the letter avatar.
     */
    public static void bindAvatar(
            TextView avatarView,
            String plantName
    ) {
        String initial =
                (plantName == null || plantName.isEmpty())
                        ? "?"
                        : plantName
                          .substring(0, 1)
                          .toUpperCase(Locale.getDefault());

        avatarView.setText(initial);
    }


    public static void bindAvatar(
            TextView avatarView,
            ImageView imageView,
            PlantReading plant
    ) {

        if (plant == null) {
            imageView.setVisibility(View.GONE);
            avatarView.setVisibility(View.VISIBLE);

            bindAvatar(
                    avatarView,
                    null
            );

            return;
        }

        String imageUrl =
                plant.getImageUrl();

        if (
                imageUrl != null
                        && !imageUrl.trim().isEmpty()
        ) {

            // Plant has a saved Cloudinary image.
            avatarView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);

            Glide.with(imageView.getContext())
                    .load(imageUrl)
                    .circleCrop()
                    .into(imageView);

        } else {

            // No image saved:
            // fall back to the plant's first letter.
            Glide.with(imageView.getContext())
                    .clear(imageView);

            imageView.setVisibility(View.GONE);
            avatarView.setVisibility(View.VISIBLE);

            bindAvatar(
                    avatarView,
                    plant.getPlantName()
            );
        }
    }

    public static void bindStatusChip(
            TextView chipView,
            int humidity,
            int dryThreshold
    ) {

        chipView.setText(
                DashboardUtils.humidityStatusLabel(
                        humidity,
                        dryThreshold
                )
        );

        chipView.setTextColor(
                DashboardUtils.humidityTextColor(
                        humidity,
                        dryThreshold
                )
        );

        if (humidity < dryThreshold) {

            chipView.setBackgroundResource(
                    R.drawable.bg_chip_dry
            );

        } else if (humidity < dryThreshold + 30) {

            chipView.setBackgroundResource(
                    R.drawable.bg_chip_medium
            );

        } else {

            chipView.setBackgroundResource(
                    R.drawable.bg_chip_healthy
            );
        }
    }

    public static void bindDropletBar(
            LinearLayout container,
            int humidityPercentage
    ) {

        Context context =
                container.getContext();

        int filledCount =
                DashboardUtils.filledDropletCount(
                        humidityPercentage
                );

        container.removeAllViews();

        LayoutInflater inflater =
                LayoutInflater.from(context);

        for (int i = 0; i < 10; i++) {

            ImageView drop =
                    new ImageView(context);

            int sizePx =
                    (int) (
                            26
                                    * context
                                    .getResources()
                                    .getDisplayMetrics()
                                    .density
                    );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            sizePx,
                            sizePx
                    );

            params.setMarginEnd(
                    (int) (
                            4
                                    * context
                                    .getResources()
                                    .getDisplayMetrics()
                                    .density
                    )
            );

            drop.setLayoutParams(params);

            drop.setImageResource(
                    i < filledCount
                            ? R.drawable.drop_filled
                            : R.drawable.drop_empty
            );

            container.addView(drop);
        }
    }

    public static void bindWaterTank(
            ImageView bucketView,
            TextView statusView,
            String waterLevel
    ) {

        boolean isFull =
                waterLevel != null
                        && (
                        waterLevel.contains("Sufficient")
                                || waterLevel.contains("Full")
                );

        bucketView.setImageResource(
                isFull
                        ? R.drawable.bucket_of_water_detail
                        : R.drawable.bucket_detail
        );

        statusView.setText(
                waterLevel
        );

        statusView.setTextColor(
                DashboardUtils.tankTextColor(
                        waterLevel
                )
        );
    }

    /**
     * Shows Online/Offline based on how long ago
     * the plant last reported in.
     *
     * Takes the Firebase server-synced time
     * (PlantViewModel.getCurrentServerTime())
     * rather than the phone's local clock.
     */
    public static void bindConnectionStatus(
            TextView statusView,
            PlantReading plant,
            long currentServerTime
    ) {

        if (
                plant.isOnline(
                        currentServerTime
                )
        ) {

            statusView.setText(
                    "Online"
            );

            statusView.setTextColor(
                    statusView
                            .getContext()
                            .getColor(
                                    R.color.status_healthy_text
                            )
            );

        } else {

            statusView.setText(
                    "Offline"
            );

            statusView.setTextColor(
                    statusView
                            .getContext()
                            .getColor(
                                    R.color.error_red
                            )
            );
        }
    }
}