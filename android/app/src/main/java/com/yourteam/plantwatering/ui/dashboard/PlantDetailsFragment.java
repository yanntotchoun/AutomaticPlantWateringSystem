package com.yourteam.plantwatering.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * Ported from PlantDetailsScreen.kt. Takes the clicked PlantReading as a
 * Parcelable fragment argument (the Java/Views equivalent of Compose passing
 * `plant: PlantReading` as a parameter).
 */
public class PlantDetailsFragment extends Fragment {

    private static final String ARG_PLANT = "arg_plant";
    private static final long REFRESH_INTERVAL_MILLIS = 60_000L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private TextView lastWateredText;
    private PlantSettingsManager settingsManager;
    private PlantReading plant;

    public static PlantDetailsFragment newInstance(PlantReading plant) {
        PlantDetailsFragment fragment = new PlantDetailsFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PLANT, plant);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalStateException("PlantDetailsFragment requires arguments - use newInstance(plant)");
        }
        plant = args.getParcelable(ARG_PLANT);
        if (plant == null) {
            throw new IllegalStateException("Missing plant argument");
        }
        settingsManager = new PlantSettingsManager(requireContext());

        // Header
        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(plant.getPlantName());
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.personalized_plant_page);
        ((TextView) header.findViewById(R.id.text_header_title)).setTextSize(32);

        // Avatar + name + status message
        PlantViewBinder.bindAvatar(view.findViewById(R.id.text_avatar), plant.getPlantName());
        ((TextView) view.findViewById(R.id.text_plant_name)).setText(plant.getPlantName());
        TextView statusMessage = view.findViewById(R.id.text_status_message);
        statusMessage.setText(DashboardUtils.plantStatusMessage(plant.getSoilHumidity()));
        statusMessage.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity()));

        // Soil humidity
        PlantViewBinder.bindDropletBar(view.findViewById(R.id.droplet_container), plant.getSoilHumidity());
        TextView humidityPercent = view.findViewById(R.id.text_humidity_percent);
        humidityPercent.setText(String.format(Locale.getDefault(), "%d%%", plant.getSoilHumidity()));
        humidityPercent.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity()));

        // Water tank + temperature
        PlantViewBinder.bindWaterTank(
                view.findViewById(R.id.image_bucket),
                view.findViewById(R.id.text_water_tank_percent),
                plant.getWaterTank()
        );

        int tempValue = plant.getTemperature();
        String tempUnit = "\u00B0C";
        if (settingsManager.useFahrenheit()) {
            tempValue = (tempValue * 9 / 5) + 32;
            tempUnit = "\u00B0F";
        }
        ((TextView) view.findViewById(R.id.text_temperature)).setText(tempValue + tempUnit);

        // Last watered - refreshed periodically, matching RelativeLastWateredRow's LaunchedEffect loop
        lastWateredText = view.findViewById(R.id.text_last_watered);
        startLastWateredRefreshLoop();

        // Recommendation
        ((TextView) view.findViewById(R.id.text_recommendation)).setText(
                DashboardUtils.plantRecommendation(plant));

        // Back button
        MaterialButton backButton = view.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    /**
     * Ported from RelativeLastWateredRow's LaunchedEffect(Unit) { while (true) { ...; delay(60_000L) } }.
     * Updates the "last watered" label every minute while the view is alive.
     */
    private void startLastWateredRefreshLoop() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastWateredText == null || plant == null) {
                    return;
                }
                lastWateredText.setText(DashboardUtils.formatRelativeLastWateredTime(
                        plant.getLastWateredTimeMillis(), System.currentTimeMillis()));
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MILLIS);
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        lastWateredText = null;
    }
}
