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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class PlantDetailsFragment extends Fragment {

    private static final String ARG_PLANT = "arg_plant";
    private static final long REFRESH_INTERVAL_MILLIS = 60_000L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private TextView lastWateredText;
    private PlantSettingsManager settingsManager;
    private PlantReading plant;
    private PlantViewModel viewModel;
    private TextView currentProfileText;

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
        PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(plant.getThresholdId());

        // Header
        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(plant.getPlantName());
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.personalized_plant_page);
        ((TextView) header.findViewById(R.id.text_header_title)).setTextSize(32);

        // Avatar + name + status message
        PlantViewBinder.bindAvatar(view.findViewById(R.id.text_avatar), plant.getPlantName());
        ((TextView) view.findViewById(R.id.text_plant_name)).setText(plant.getPlantName());
        TextView statusMessage = view.findViewById(R.id.text_status_message);
        statusMessage.setText(DashboardUtils.plantStatusMessage(plant.getSoilHumidity(), profile.drySoil));
        statusMessage.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        // Soil humidity
        PlantViewBinder.bindDropletBar(view.findViewById(R.id.droplet_container), plant.getSoilHumidity());
        TextView humidityPercent = view.findViewById(R.id.text_humidity_percent);
        humidityPercent.setText(String.format(Locale.getDefault(), "%d%%", plant.getSoilHumidity()));
        humidityPercent.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        // Water tank + temperature
        PlantViewBinder.bindWaterTank(
                view.findViewById(R.id.image_bucket),
                view.findViewById(R.id.text_water_tank_percent),
                plant.getWaterTank(),
                profile.fullTank
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
                DashboardUtils.plantRecommendation(plant, profile.drySoil, profile.fullTank));

        // Profile info
        currentProfileText = view.findViewById(R.id.text_current_profile);
        currentProfileText.setText("Current: " + profile.name);
        viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);

        view.findViewById(R.id.button_change_profile).setOnClickListener(v -> showProfileSelector());

        // Back button
        MaterialButton backButton = view.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }


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
        currentProfileText = null;
    }

    private void showProfileSelector() {
        java.util.List<PlantSettingsManager.ThresholdProfile> profiles = settingsManager.getAllProfiles();
        String[] names = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            names[i] = profiles.get(i).name;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Threshold Profile")
                .setItems(names, (dialog, which) -> {
                    PlantSettingsManager.ThresholdProfile selected = profiles.get(which);
                    viewModel.updatePlantThreshold(plant.getPlantName(), selected.id);
                    // Update local UI
                    currentProfileText.setText("Current: " + selected.name);
                    // Refresh recommendations and colors
                    refreshPlantData(selected);
                })
                .show();
    }

    private void refreshPlantData(PlantSettingsManager.ThresholdProfile profile) {
        TextView statusMessage = getView().findViewById(R.id.text_status_message);
        statusMessage.setText(DashboardUtils.plantStatusMessage(plant.getSoilHumidity(), profile.drySoil));
        statusMessage.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        TextView humidityPercent = getView().findViewById(R.id.text_humidity_percent);
        humidityPercent.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        PlantViewBinder.bindWaterTank(
                getView().findViewById(R.id.image_bucket),
                getView().findViewById(R.id.text_water_tank_percent),
                plant.getWaterTank(),
                profile.fullTank
        );

        ((TextView) getView().findViewById(R.id.text_recommendation)).setText(
                DashboardUtils.plantRecommendation(plant, profile.drySoil, profile.fullTank));
    }
}
