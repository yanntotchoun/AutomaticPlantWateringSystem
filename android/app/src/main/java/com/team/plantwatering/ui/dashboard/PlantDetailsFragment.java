package com.team.plantwatering.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class PlantDetailsFragment extends Fragment {
    private static final String ARG_PLANT = "arg_plant";
    private static final long REFRESH_INTERVAL_MILLIS = 10_000L;

    // Duration seekbar bounds
    private static final int MIN_DURATION = 5;
    private static final int MAX_DURATION = 60;
    private static final int STEP = 5;
    private static final int SEEK_MAX = MAX_DURATION - MIN_DURATION; // 55

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private TextView lastWateredText;
    private TextView connectionStatusText;
    private MaterialButton waterNowButton;
    private View quickRefreshButton;
    private SeekBar durationBar;
    private TextView durationLabel;
    private View stopWateringButton;
    private SwitchMaterial autoWateringSwitch;
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
        quickRefreshButton = view.findViewById(R.id.button_quick_refresh);
        durationBar = view.findViewById(R.id.seekbar_duration);
        durationBar.setMax(SEEK_MAX); // Set max to 55 (so progress 0 is 5s, progress 55 is 60s)
        durationBar.setProgress(0);
        durationLabel = view.findViewById(R.id.text_duration_label);
        stopWateringButton = view.findViewById(R.id.button_stop_watering);
        autoWateringSwitch = view.findViewById(R.id.switch_auto_watering); //the switch for auto mode is implemented here on the UI.

        viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);

        autoWateringSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                viewModel.setAutoWateringMode(plant.getPlantName(), isChecked);
                if (!plant.isOnline()) {
                    showOfflinePendingToast();
                }
            }
        });

        durationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int steppedProgress = (progress/5)*5;
                int safeProgress = Math.max(10, steppedProgress); // Min 10 seconds

                durationLabel.setText(String.format(Locale.getDefault(), "Custom Duration: %d seconds", safeProgress));
                waterNowButton.setText(String.format(Locale.getDefault(), "Water Plant for %d s", safeProgress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

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

        quickRefreshButton.setOnClickListener(v -> {
            viewModel.requestManualWatering(plant.getPlantName(), 3);
            if (!plant.isOnline()) {
                showOfflinePendingToast();
            }
        }); //A basic refreshment that is convenient for most plants.
        
        waterNowButton.setOnClickListener(v -> { //This is the custom button that allows the user to choose how long they want to water the plant.
            int duration = MIN_DURATION + ((durationBar.getProgress() / STEP) * STEP);
            viewModel.requestManualWatering(plant.getPlantName(), duration);
            if (!plant.isOnline()) {
                showOfflinePendingToast();
            }
        });

        stopWateringButton.setOnClickListener(v -> viewModel.stopManualWatering(plant.getPlantName()));

        view.findViewById(R.id.button_delete_plant).setOnClickListener(v -> showDeleteConfirmation()); // I added a delete plant button for the user. This removes the data from the firebase in real time also.

        MaterialButton backButton = view.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clear all callbacks to prevent leaks or "dead thread" issues
        refreshHandler.removeCallbacksAndMessages(null);
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
                plant.getWaterTank()
        );

        lastWateredText.setText(DashboardUtils.formatRelativeLastWateredTime(
                plant.getLastWateredTimeMillis(), viewModel.getCurrentServerTime()));
        
        boolean isOnline = plant.isOnline();
        if (isOnline) {
            connectionStatusText.setText("Online");
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        } else {
            connectionStatusText.setText("Offline");
            connectionStatusText.setTextColor(android.graphics.Color.parseColor("#9C1C16"));
        }

        ((TextView) getView().findViewById(R.id.text_recommendation)).setText(
                DashboardUtils.plantRecommendation(plant, profile.drySoil));

        currentProfileText.setText("Current: " + profile.name);

        autoWateringSwitch.setChecked(plant.isAutoWateringEnabled());

        // BSCK 8.4 and BSCK 8.5
        boolean isWatering = plant.isPumpActive();

        waterNowButton.setVisibility(isWatering ? View.GONE : View.VISIBLE);
        quickRefreshButton.setVisibility(isWatering ? View.GONE : View.VISIBLE);
        durationBar.setVisibility(isWatering ? View.GONE : View.VISIBLE);
        durationLabel.setVisibility(isWatering ? View.GONE : View.VISIBLE);

        stopWateringButton.setVisibility(isWatering ? View.VISIBLE : View.GONE);

        waterNowButton.setEnabled(true); // Restrictions removed as requested

        waterNowButton.setEnabled(true);
        quickRefreshButton.setEnabled(true);
        durationBar.setEnabled(true);
    }

    private void showOfflinePendingToast() {
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Device is currently offline. The request will be processed once it reconnects.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startPeriodicRefreshLoop() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (getView() == null) {
                    return; // Stop the loop if the view is gone
                }
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
        refreshHandler.removeCallbacksAndMessages(null);
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
                    viewModel.updatePlantThreshold(plant.getPlantName(), selected);
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
