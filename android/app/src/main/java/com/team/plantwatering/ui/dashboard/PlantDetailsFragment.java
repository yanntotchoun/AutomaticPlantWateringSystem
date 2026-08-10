package com.team.plantwatering.ui.dashboard;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.team.plantwatering.CloudinaryImageUploader;
import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;

import java.util.List;
import java.util.Locale;

public class PlantDetailsFragment extends Fragment {

    private static final String ARG_PLANT = "arg_plant";
    private static final long REFRESH_INTERVAL_MILLIS = 10_000L;

    // Duration seekbar bounds
    private static final int MIN_DURATION = 5;
    private static final int MAX_DURATION = 60;
    private static final int STEP = 5;
    private static final int SEEK_MAX =
            MAX_DURATION - MIN_DURATION; // 55

    private final Handler refreshHandler =
            new Handler(Looper.getMainLooper());

    private Runnable refreshRunnable;

    private TextView lastWateredText;
    private TextView connectionStatusText;
    private MaterialButton waterNowButton;
    private View quickRefreshButton;
    private SeekBar durationBar;
    private TextView durationLabel;
    private View stopWateringButton;
    private SwitchMaterial autoWateringSwitch;
    private View permanentDisableButton;
    private PlantSettingsManager settingsManager;
    private PlantReading plant;
    private PlantViewModel viewModel;

    private TextView currentProfileText;
    private LinearLayout wateringLogContainer;
    private TextView wateringLogEmptyText;

    // Plant photo UI
    private ImageView plantImageView;
    private TextView avatarTextView;
    private MaterialButton changePhotoButton;

    /*
     * Opens Android's image picker.
     *
     * Once an image is selected, we immediately upload it
     * to Cloudinary.
     */
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadPlantImage(uri);
                        }
                    }
            );

    public static PlantDetailsFragment newInstance(
            PlantReading plant
    ) {

        PlantDetailsFragment fragment =
                new PlantDetailsFragment();

        Bundle args =
                new Bundle();

        args.putParcelable(
                ARG_PLANT,
                plant
        );

        fragment.setArguments(args);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_plant_details,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );

        Bundle args =
                getArguments();

        if (args == null) {

            throw new IllegalStateException(
                    "PlantDetailsFragment requires arguments - use newInstance(plant)"
            );
        }

        plant =
                args.getParcelable(
                        ARG_PLANT
                );

        if (plant == null) {

            throw new IllegalStateException(
                    "Missing plant argument"
            );
        }

        settingsManager =
                new PlantSettingsManager(
                        requireContext()
                );

        lastWateredText =
                view.findViewById(
                        R.id.text_last_watered
                );

        connectionStatusText =
                view.findViewById(
                        R.id.text_connection_status
                );

        currentProfileText =
                view.findViewById(
                        R.id.text_current_profile
                );

        wateringLogContainer =
                view.findViewById(
                        R.id.layout_watering_log
                );

        wateringLogEmptyText =
                view.findViewById(
                        R.id.text_watering_log_empty
                );

        waterNowButton =
                view.findViewById(
                        R.id.button_water_now
                );

        quickRefreshButton =
                view.findViewById(
                        R.id.button_quick_refresh
                );

        durationBar =
                view.findViewById(
                        R.id.seekbar_duration
                );

        durationBar.setMax(
                SEEK_MAX
        );

        durationBar.setProgress(
                0
        );

        durationLabel =
                view.findViewById(
                        R.id.text_duration_label
                );

        stopWateringButton =
                view.findViewById(
                        R.id.button_stop_watering
                );

        autoWateringSwitch =
                view.findViewById(
                        R.id.switch_auto_watering
                );

        permanentDisableButton =
                view.findViewById(
                        R.id.button_disable_auto_permanently
                );

        // -----------------------------
        // Plant photo views
        // -----------------------------

        plantImageView =
                view.findViewById(
                        R.id.image_plant_avatar
                );

        avatarTextView =
                view.findViewById(
                        R.id.text_avatar
                );

        changePhotoButton =
                view.findViewById(
                        R.id.button_change_photo
                );

        viewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        PlantViewModel.class
                );

        // -----------------------------
        // Photo picker
        // -----------------------------

        changePhotoButton.setOnClickListener(
                v -> imagePickerLauncher.launch(
                        "image/*"
                )
        );

        // -----------------------------
        // Automatic watering
        // -----------------------------

        autoWateringSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (buttonView.isPressed()) {

                        viewModel.setAutoWateringMode(
                                plant.getIdentifier(),
                                isChecked
                        );

                        if (!isChecked) {
                            permanentDisableButton.setVisibility(View.VISIBLE);
                            // Hide the button again after 6 seconds because the timer would have fired or been cancelled anyway
                            permanentDisableButton.postDelayed(() -> {
                                if (isAdded()) {
                                    permanentDisableButton.setVisibility(View.GONE);
                                }
                            }, 6000);
                        } else {
                            permanentDisableButton.setVisibility(View.GONE);
                        }

                        if (
                                !plant.isOnline(
                                        viewModel
                                                .getCurrentServerTime()
                                )
                        ) {

                            showOfflinePendingToast();
                        }
                    }
                }
        );

        permanentDisableButton.setOnClickListener(v -> {
            viewModel.cancelAutoWateringTimer(plant.getIdentifier());
            permanentDisableButton.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Automatic watering disabled permanently", Toast.LENGTH_SHORT).show();
        });

        // -----------------------------
        // Duration SeekBar
        // -----------------------------

        durationBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        int steppedProgress =
                                (progress / 5) * 5;

                        int safeProgress =
                                Math.max(
                                        10,
                                        steppedProgress
                                );

                        durationLabel.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "Custom Duration: %d seconds",
                                        safeProgress
                                )
                        );

                        waterNowButton.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "Water Plant for %d s",
                                        safeProgress
                                )
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar
                    ) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar
                    ) {
                    }
                }
        );

        // -----------------------------
        // Observe plant changes
        // -----------------------------

        viewModel
                .getPlants()
                .observe(
                        getViewLifecycleOwner(),
                        plants -> {

                            for (
                                    PlantReading p :
                                    plants
                            ) {

                                if (
                                        p.getIdentifier()
                                                .equals(
                                                        plant.getIdentifier()
                                                )
                                ) {

                                    this.plant = p;

                                    updateUi();

                                    break;
                                }
                            }
                        }
                );

        // -----------------------------
        // Watering log
        // -----------------------------

        /*viewModel
                .getWateringLog()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderWateringLog
                );

        viewModel.startListeningForWateringLog(
                plant.getIdentifier()
        );
*/
        startPeriodicRefreshLoop();

        // -----------------------------
        // Threshold profile
        // -----------------------------

        view.findViewById(
                R.id.button_change_profile
        ).setOnClickListener(
                v -> showProfileSelector()
        );

        // -----------------------------
        // Quick refresh
        // -----------------------------

        quickRefreshButton.setOnClickListener(
                v -> {

                    viewModel.requestManualWatering(
                            plant.getIdentifier(),
                            3
                    );

                    if (
                            !plant.isOnline(
                                    viewModel
                                            .getCurrentServerTime()
                            )
                    ) {

                        showOfflinePendingToast();
                    }
                }
        );

        // -----------------------------
        // Manual watering
        // -----------------------------

        waterNowButton.setOnClickListener(
                v -> {

                    int duration =
                            MIN_DURATION
                                    + (
                                    (
                                            durationBar
                                                    .getProgress()
                                                    / STEP
                                    ) * STEP
                            );

                    viewModel.requestManualWatering(
                            plant.getIdentifier(),
                            duration
                    );

                    if (
                            !plant.isOnline(
                                    viewModel
                                            .getCurrentServerTime()
                            )
                    ) {

                        showOfflinePendingToast();
                    }
                }
        );

        stopWateringButton.setOnClickListener(
                v -> viewModel.stopManualWatering(
                        plant.getIdentifier()
                )
        );

        // -----------------------------
        // Delete plant
        // -----------------------------

        view.findViewById(
                R.id.button_delete_plant
        ).setOnClickListener(
                v -> showDeleteConfirmation()
        );

        // -----------------------------
        // Back button
        // -----------------------------

        MaterialButton backButton =
                view.findViewById(
                        R.id.button_back
                );

        backButton.setOnClickListener(
                v -> getParentFragmentManager()
                        .popBackStack()
        );

        // Populate the screen immediately.
        updateUi();
    }

    /*
     * Uploads the selected image to Cloudinary.
     *
     * When Cloudinary returns the secure HTTPS URL,
     * only that URL is saved in Firebase.
     */
    private void uploadPlantImage(
            Uri imageUri
    ) {

        if (
                changePhotoButton == null
                        || plant == null
        ) {
            return;
        }

        changePhotoButton.setEnabled(
                false
        );

        changePhotoButton.setText(
                "Uploading..."
        );

        CloudinaryImageUploader.upload(
                imageUri,
                new CloudinaryImageUploader.Callback() {

                    @Override
                    public void onSuccess(
                            String imageUrl
                    ) {

                        if (
                                !isAdded()
                                        || getView() == null
                        ) {
                            return;
                        }

                        requireActivity()
                                .runOnUiThread(
                                        () -> {

                                            /*
                                             * Save the returned URL
                                             * under:
                                             *
                                             * /plants/{plantId}/image_url
                                             */
                                            viewModel.updatePlantImage(
                                                    plant.getIdentifier(),
                                                    imageUrl
                                            );

                                            /*
                                             * Display immediately.
                                             *
                                             * Firebase will also trigger
                                             * the observer afterwards,
                                             * making this persistent.
                                             */
                                            Glide.with(
                                                            PlantDetailsFragment.this
                                                    )
                                                    .load(imageUrl)
                                                    .circleCrop()
                                                    .into(
                                                            plantImageView
                                                    );

                                            plantImageView.setVisibility(
                                                    View.VISIBLE
                                            );

                                            avatarTextView.setVisibility(
                                                    View.GONE
                                            );

                                            if (
                                                    changePhotoButton
                                                            != null
                                            ) {

                                                changePhotoButton
                                                        .setEnabled(
                                                                true
                                                        );

                                                changePhotoButton
                                                        .setText(
                                                                "Change photo"
                                                        );
                                            }

                                            Toast.makeText(
                                                    requireContext(),
                                                    "Plant photo updated",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                );
                    }

                    @Override
                    public void onError(
                            String errorMessage
                    ) {

                        if (
                                !isAdded()
                                        || getView() == null
                        ) {
                            return;
                        }

                        requireActivity()
                                .runOnUiThread(
                                        () -> {

                                            if (
                                                    changePhotoButton
                                                            != null
                                            ) {

                                                changePhotoButton
                                                        .setEnabled(
                                                                true
                                                        );

                                                /*
                                                 * If there was already
                                                 * a photo, this will be
                                                 * corrected again by
                                                 * updateUi().
                                                 */
                                                changePhotoButton
                                                        .setText(
                                                                "Add photo"
                                                        );
                                            }

                                            Toast.makeText(
                                                    requireContext(),
                                                    "Upload failed: "
                                                            + errorMessage,
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            updateUi();
                                        }
                                );
                    }
                }
        );
    }

    @Override
    public void onPause() {

        super.onPause();

        // Clear all callbacks to prevent leaks
        // or dead-thread issues.
        refreshHandler
                .removeCallbacksAndMessages(
                        null
                );
    }

    private void updateUi() {

        if (
                getView() == null
                        || plant == null
        ) {
            return;
        }

        PlantSettingsManager.ThresholdProfile profile =
                settingsManager.getThresholdProfile(
                        plant.getThresholdId()
                );

        View header =
                getView().findViewById(
                        R.id.header_root
                );

        (
                (TextView) header.findViewById(
                        R.id.text_header_title
                )
        ).setText(
                plant.getPlantName()
        );

        (
                (TextView) header.findViewById(
                        R.id.text_header_subtitle
                )
        ).setText(
                R.string.personalized_plant_page
        );

        // -----------------------------
        // Plant photo / fallback avatar
        // -----------------------------

        String imageUrl =
                plant.getImageUrl();

        if (
                imageUrl != null
                        && !imageUrl.trim().isEmpty()
        ) {

            avatarTextView.setVisibility(
                    View.GONE
            );

            plantImageView.setVisibility(
                    View.VISIBLE
            );

            Glide.with(this)
                    .load(imageUrl)
                    .circleCrop()
                    .into(
                            plantImageView
                    );

            changePhotoButton.setText(
                    "Change photo"
            );

        } else {

            plantImageView.setVisibility(
                    View.GONE
            );

            avatarTextView.setVisibility(
                    View.VISIBLE
            );

            PlantViewBinder.bindAvatar(
                    avatarTextView,
                    plant.getPlantName()
            );

            changePhotoButton.setText(
                    "Add photo"
            );
        }

        // -----------------------------
        // Plant name
        // -----------------------------

        (
                (TextView) getView()
                        .findViewById(
                                R.id.text_plant_name
                        )
        ).setText(
                plant.getPlantName()
        );

        // -----------------------------
        // Status message
        // -----------------------------

        TextView statusMessage =
                getView().findViewById(
                        R.id.text_status_message
                );

        statusMessage.setText(
                DashboardUtils.plantStatusMessage(
                        plant.getSoilHumidity(),
                        profile.drySoil
                )
        );

        statusMessage.setTextColor(
                DashboardUtils.humidityTextColor(
                        plant.getSoilHumidity(),
                        profile.drySoil
                )
        );

        // -----------------------------
        // Humidity
        // -----------------------------

        PlantViewBinder.bindDropletBar(
                getView().findViewById(
                        R.id.droplet_container
                ),
                plant.getSoilHumidity()
        );

        TextView humidityPercent =
                getView().findViewById(
                        R.id.text_humidity_percent
                );

        humidityPercent.setText(
                String.format(
                        Locale.getDefault(),
                        "%d%%",
                        plant.getSoilHumidity()
                )
        );

        humidityPercent.setTextColor(
                DashboardUtils.humidityTextColor(
                        plant.getSoilHumidity(),
                        profile.drySoil
                )
        );

        // -----------------------------
        // Water tank
        // -----------------------------

        PlantViewBinder.bindWaterTank(
                getView().findViewById(
                        R.id.image_bucket
                ),
                getView().findViewById(
                        R.id.text_water_tank_percent
                ),
                plant.getWaterTank()
        );

        // -----------------------------
        // Last seen / Connection
        // -----------------------------

        lastWateredText.setText(
                DashboardUtils
                        .formatRelativeTime(
                                plant.getLastSeenMillis(),
                                viewModel
                                        .getCurrentServerTime()
                        )
        );

        // -----------------------------
        // Connection status
        // -----------------------------

        long serverTime =
                viewModel.getCurrentServerTime();

        boolean isOnline =
                plant.isOnline(
                        serverTime
                );

        if (isOnline) {

            connectionStatusText.setText(
                    "Online"
            );

            connectionStatusText.setTextColor(
                    android.graphics.Color
                            .parseColor(
                                    "#2E7D32"
                            )
            );

        } else {

            connectionStatusText.setText(
                    "Offline"
            );

            connectionStatusText.setTextColor(
                    android.graphics.Color
                            .parseColor(
                                    "#9C1C16"
                            )
            );
        }

        // -----------------------------
        // Recommendation
        // -----------------------------

        (
                (TextView) getView()
                        .findViewById(
                                R.id.text_recommendation
                        )
        ).setText(
                DashboardUtils
                        .plantRecommendation(
                                plant,
                                profile.drySoil
                        )
        );

        // -----------------------------
        // Threshold profile
        // -----------------------------

        currentProfileText.setText(
                "Current: "
                        + profile.name
        );

        // -----------------------------
        // Automatic watering
        // -----------------------------

        autoWateringSwitch.setChecked(
                plant.isAutoWateringEnabled()
        );

        // -----------------------------
        // Watering state
        // -----------------------------

        boolean isWatering =
                plant.isPumpActive();

        waterNowButton.setVisibility(
                isWatering
                        ? View.GONE
                        : View.VISIBLE
        );

        quickRefreshButton.setVisibility(
                isWatering
                        ? View.GONE
                        : View.VISIBLE
        );

        durationBar.setVisibility(
                isWatering
                        ? View.GONE
                        : View.VISIBLE
        );

        durationLabel.setVisibility(
                isWatering
                        ? View.GONE
                        : View.VISIBLE
        );

        stopWateringButton.setVisibility(
                isWatering
                        ? View.VISIBLE
                        : View.GONE
        );

        waterNowButton.setEnabled(
                !plant.isAutoWateringEnabled()
        );

        quickRefreshButton.setEnabled(
                !plant.isAutoWateringEnabled()
        );

        durationBar.setEnabled(
                !plant.isAutoWateringEnabled()
        );
    }

    private void showOfflinePendingToast() {

        if (getContext() != null) {

            Toast.makeText(
                    getContext(),
                    "Device is currently offline. "
                            + "The request will be processed once it reconnects.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderWateringLog(
            List<Long> wateringTimes
    ) {

        if (
                wateringLogContainer == null
                        || wateringLogEmptyText == null
        ) {
            return;
        }

        wateringLogContainer.removeAllViews();

        if (
                wateringTimes == null
                        || wateringTimes.isEmpty()
        ) {

            wateringLogEmptyText.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        wateringLogEmptyText.setVisibility(
                View.GONE
        );

        int verticalPadding =
                Math.round(
                        6
                                * getResources()
                                .getDisplayMetrics()
                                .density
                );

        for (
                int i = 0;
                i < wateringTimes.size();
                i++
        ) {

            TextView eventText =
                    new TextView(
                            requireContext()
                    );

            eventText.setText(
                    String.format(
                            Locale.getDefault(),
                            "%d. %s",
                            i + 1,
                            DashboardUtils
                                    .formatWateringEventDateTime(
                                            wateringTimes
                                                    .get(i)
                                    )
                    )
            );

            eventText.setTextSize(
                    16
            );

            eventText.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            R.color.text_gray_555
                    )
            );

            eventText.setPadding(
                    0,
                    verticalPadding,
                    0,
                    verticalPadding
            );

            wateringLogContainer.addView(
                    eventText
            );
        }
    }

    private void startPeriodicRefreshLoop() {

        if (refreshRunnable != null) {

            refreshHandler.removeCallbacks(
                    refreshRunnable
            );
        }

        refreshRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (
                                getView() == null
                        ) {

                            return;
                        }

                        updateUi();

                        refreshHandler.postDelayed(
                                this,
                                REFRESH_INTERVAL_MILLIS
                        );
                    }
                };

        refreshHandler.post(
                refreshRunnable
        );
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (refreshRunnable != null) {

            refreshHandler.removeCallbacks(
                    refreshRunnable
            );
        }

        refreshHandler
                .removeCallbacksAndMessages(
                        null
                );

        if (viewModel != null) {

            viewModel.stopListeningForWateringLog();
        }

        lastWateredText = null;
        connectionStatusText = null;
        currentProfileText = null;
        wateringLogContainer = null;
        wateringLogEmptyText = null;

        plantImageView = null;
        avatarTextView = null;
        changePhotoButton = null;
    }

    private void showProfileSelector() {

        List<PlantSettingsManager.ThresholdProfile> profiles =
                settingsManager
                        .getAllProfiles();

        String[] names =
                new String[
                        profiles.size()
                        ];

        for (
                int i = 0;
                i < profiles.size();
                i++
        ) {

            names[i] =
                    profiles.get(i).name;
        }

        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Select Threshold Profile"
                )
                .setItems(
                        names,
                        (dialog, which) -> {

                            PlantSettingsManager
                                    .ThresholdProfile selected =
                                    profiles.get(
                                            which
                                    );

                            viewModel.updatePlantThreshold(
                                    plant.getIdentifier(),
                                    selected
                            );
                        }
                )
                .show();
    }

    private void showDeleteConfirmation() {

        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Delete Plant"
                )
                .setMessage(
                        "Are you sure you want to delete '"
                                + plant.getPlantName()
                                + "'? This action cannot be undone."
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {

                            viewModel.deletePlant(
                                    plant.getIdentifier()
                            );

                            getParentFragmentManager()
                                    .popBackStack();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }
}