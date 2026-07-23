package com.team.plantwatering.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class PlantDetailsFragment extends Fragment {

    private static final String ARG_PLANT = "arg_plant";
    private static final long REFRESH_INTERVAL_MILLIS = 10_000L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private TextView lastWateredText;
    private TextView connectionStatusText;
    private View waterNowButton;
    private View stopWateringButton;
    private View offlineWarning;
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
        lastWateredText = view.findViewById(R.id.text_last_watered);
        connectionStatusText = view.findViewById(R.id.text_connection_status);
        currentProfileText = view.findViewById(R.id.text_current_profile);
        waterNowButton = view.findViewById(R.id.button_water_now);
        stopWateringButton = view.findViewById(R.id.button_stop_watering);
        offlineWarning = view.findViewById(R.id.text_offline_warning);

        viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);
        
        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
            for (PlantReading p : plants) {
                if (p.getPlantName().equals(plant.getPlantName())) {
                    this.plant = p;
                    updateUi();
                    break;
                }
            }
        });

        startPeriodicRefreshLoop();

        view.findViewById(R.id.button_change_profile).setOnClickListener(v -> showProfileSelector());

        waterNowButton.setOnClickListener(v -> viewModel.requestManualWatering(plant.getPlantName(), 5));
        stopWateringButton.setOnClickListener(v -> viewModel.stopManualWatering(plant.getPlantName()));

        view.findViewById(R.id.button_delete_plant).setOnClickListener(v -> showDeleteConfirmation()); // I added a delete button for the user. This removes the data from the firebase in real time also.

        MaterialButton backButton = view.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void updateUi() {
        if (getView() == null || plant == null) return;

        PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(plant.getThresholdId());

        View header = getView().findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(plant.getPlantName());
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.personalized_plant_page);

        PlantViewBinder.bindAvatar(getView().findViewById(R.id.text_avatar), plant.getPlantName());
        ((TextView) getView().findViewById(R.id.text_plant_name)).setText(plant.getPlantName());
        TextView statusMessage = getView().findViewById(R.id.text_status_message);
        statusMessage.setText(DashboardUtils.plantStatusMessage(plant.getSoilHumidity(), profile.drySoil));
        statusMessage.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        PlantViewBinder.bindDropletBar(getView().findViewById(R.id.droplet_container), plant.getSoilHumidity());
        TextView humidityPercent = getView().findViewById(R.id.text_humidity_percent);
        humidityPercent.setText(String.format(Locale.getDefault(), "%d%%", plant.getSoilHumidity()));
        humidityPercent.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

        PlantViewBinder.bindWaterTank(
                getView().findViewById(R.id.image_bucket),
                getView().findViewById(R.id.text_water_tank_percent),
                plant.getWaterTank(),
                profile.fullTank
        );

        lastWateredText.setText(DashboardUtils.formatRelativeLastWateredTime(
                plant.getLastWateredTimeMillis(), System.currentTimeMillis()));
        
        if (plant.isOnline()) {
            connectionStatusText.setText("Online");
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        } else {
            connectionStatusText.setText("Offline");
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#9C1C16"));
        }

        ((TextView) getView().findViewById(R.id.text_recommendation)).setText(
                DashboardUtils.plantRecommendation(plant, profile.drySoil, profile.fullTank));

        currentProfileText.setText("Current: " + profile.name);

        // Update Manual Controls (BSCK-8.4 and BSCK-8.5)
        boolean isOnline = plant.isOnline();
        boolean isWatering = plant.isPumpActive();

        waterNowButton.setVisibility(isWatering ? View.GONE : View.VISIBLE);
        stopWateringButton.setVisibility(isWatering ? View.VISIBLE : View.GONE);
        
        waterNowButton.setEnabled(isOnline);
        offlineWarning.setVisibility(isOnline ? View.GONE : View.VISIBLE);
    }

    private void startPeriodicRefreshLoop() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateUi();
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
        connectionStatusText = null;
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
                })
                .show();
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Plant")
                .setMessage("Are you sure you want to delete '" + plant.getPlantName() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deletePlant(plant.getPlantName());
                    getParentFragmentManager().popBackStack();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
