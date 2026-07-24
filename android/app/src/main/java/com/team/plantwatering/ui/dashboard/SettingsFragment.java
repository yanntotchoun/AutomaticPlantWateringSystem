package com.team.plantwatering.ui.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SwitchCompat;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.team.plantwatering.MainActivity;
import com.team.plantwatering.R;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
import java.util.function.Consumer;


public class SettingsFragment extends BaseFragment {

    private PlantSettingsManager settingsManager;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(requireContext(), "Notifications are disabled. You won't receive alerts.", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsManager = new PlantSettingsManager(requireContext());

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.settings_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.settings_subtitle);
        ((TextView) header.findViewById(R.id.text_header_title)).setTextSize(32);

        applyStatusBarInset(header);


        setUpThresholds(view);
        setUpNotifications(view);
        setUpWateringReminders(view);


        view.findViewById(R.id.button_back).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                View navView = activity.findViewById(R.id.bottom_navigation);
                if (navView != null) {
                    navView.findViewById(R.id.nav_dashboard).performClick();
                }
            }
        });
    }



    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setUpThresholds(View view) {
        TextView drySoilLabel = view.findViewById(R.id.text_dry_soil_threshold);
        Slider drySoilSlider = view.findViewById(R.id.slider_dry_soil_threshold);
        TextView fullTankLabel = view.findViewById(R.id.text_full_tank_threshold);
        Slider fullTankSlider = view.findViewById(R.id.slider_full_tank_threshold);
        RadioGroup profileGroup = view.findViewById(R.id.radio_group_profile_edit);
        TextInputEditText nameEdit = view.findViewById(R.id.edit_profile_name);
        View nameLayout = view.findViewById(R.id.layout_profile_name);
        View addButton = view.findViewById(R.id.button_add_profile);
        View deleteButton = view.findViewById(R.id.button_delete_profile);
        View confirmButton = view.findViewById(R.id.button_confirm_thresholds);

        final String[] currentProfileId = {"standard"};
        final PlantSettingsManager.ThresholdProfile[] pendingChanges = {null};

        Runnable updateSliders = () -> {
            PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(currentProfileId[0]);
            pendingChanges[0] = new PlantSettingsManager.ThresholdProfile(profile.id, profile.name, profile.drySoil, profile.fullTank);
            
            drySoilSlider.setValue(pendingChanges[0].drySoil);
            drySoilLabel.setText(getString(R.string.dry_soil_threshold_label, pendingChanges[0].drySoil));
            fullTankSlider.setValue(pendingChanges[0].fullTank);
            fullTankLabel.setText(getString(R.string.full_tank_threshold_label, pendingChanges[0].fullTank));
            
            nameEdit.setText(pendingChanges[0].name);
            nameLayout.setVisibility(View.GONE); // Hide name edit by default
            deleteButton.setVisibility("standard".equals(pendingChanges[0].id) ? View.GONE : View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
        };

        Consumer<String> populateProfiles = (selectedId) -> {
            profileGroup.removeAllViews();
            List<PlantSettingsManager.ThresholdProfile> profiles = settingsManager.getAllProfiles();
            for (PlantSettingsManager.ThresholdProfile profile : profiles) {
                RadioButton rb = new RadioButton(requireContext());
                rb.setText(profile.name);
                rb.setTag(profile.id);
                rb.setId(View.generateViewId());
                profileGroup.addView(rb);
                
                if (profile.id.equals(selectedId)) {
                    rb.setChecked(true);
                }

                // Double-tap to rename
                GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (!"standard".equals(profile.id)) {
                            nameLayout.setVisibility(View.VISIBLE);
                            nameEdit.requestFocus();
                            confirmButton.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(requireContext(), "Standard profile cannot be renamed", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                    
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        rb.setChecked(true);
                        return true;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }
                });

                rb.setOnTouchListener((v, event) -> {
                    gestureDetector.onTouchEvent(event);
                    return true;
                });
            }
        };

        populateProfiles.accept(currentProfileId[0]);
        updateSliders.run();

        profileGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View rb = group.findViewById(checkedId);
            if (rb != null && rb.getTag() instanceof String) {
                String newId = (String) rb.getTag();
                if (!newId.equals(currentProfileId[0])) {
                    currentProfileId[0] = newId;
                    updateSliders.run();
                }
            }
        });

        drySoilSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int intValue = Math.round(value);
            pendingChanges[0].drySoil = intValue;
            drySoilLabel.setText(getString(R.string.dry_soil_threshold_label, intValue));
            confirmButton.setVisibility(View.VISIBLE);
        });

        fullTankSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int intValue = Math.round(value);
            pendingChanges[0].fullTank = intValue;
            fullTankLabel.setText(getString(R.string.full_tank_threshold_label, intValue));
            confirmButton.setVisibility(View.VISIBLE);
        });

        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (nameEdit.hasFocus() && pendingChanges[0] != null) {
                    pendingChanges[0].name = s.toString();
                    confirmButton.setVisibility(View.VISIBLE);
                }
            }
        });

        confirmButton.setOnClickListener(v -> {
            if (pendingChanges[0] != null) {
                settingsManager.saveThresholdProfile(pendingChanges[0]);
                Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show();
                confirmButton.setVisibility(View.GONE);
                nameLayout.setVisibility(View.GONE);
                populateProfiles.accept(currentProfileId[0]);
            }
        });

        addButton.setOnClickListener(v -> {
            String newId = "custom_" + System.currentTimeMillis();
            PlantSettingsManager.ThresholdProfile newProfile = new PlantSettingsManager.ThresholdProfile(
                    newId, "New Profile", 30, 70);
            settingsManager.saveThresholdProfile(newProfile);
            currentProfileId[0] = newId;
            populateProfiles.accept(newId);
            updateSliders.run();
        });

        deleteButton.setOnClickListener(v -> {
            settingsManager.deleteThresholdProfile(currentProfileId[0]);
            currentProfileId[0] = "standard";
            populateProfiles.accept(currentProfileId[0]);
            updateSliders.run();
        });
    }

    private void setUpNotifications(View view) {
        View enabledRow = view.findViewById(R.id.row_notifications_enabled);
        View humidityRow = view.findViewById(R.id.row_low_humidity_alerts);
        View tankRow = view.findViewById(R.id.row_low_tank_alerts);

        ((TextView) enabledRow.findViewById(R.id.text_label)).setText(R.string.enable_notifications);
        ((TextView) enabledRow.findViewById(R.id.text_description)).setText(R.string.enable_notifications_desc);

        ((TextView) humidityRow.findViewById(R.id.text_label)).setText(R.string.low_humidity_alerts);
        ((TextView) humidityRow.findViewById(R.id.text_description)).setText(R.string.low_humidity_alerts_desc);

        ((TextView) tankRow.findViewById(R.id.text_label)).setText(R.string.low_tank_alerts);
        ((TextView) tankRow.findViewById(R.id.text_description)).setText(R.string.low_tank_alerts_desc);

        SwitchMaterial notificationsSwitch = enabledRow.findViewById(R.id.switch_toggle);
        SwitchMaterial humiditySwitch = humidityRow.findViewById(R.id.switch_toggle);
        SwitchMaterial tankSwitch = tankRow.findViewById(R.id.switch_toggle);

        boolean notificationsEnabled = settingsManager.isNotificationsEnabled();
        notificationsSwitch.setChecked(notificationsEnabled);
        humiditySwitch.setChecked(settingsManager.isLowHumidityAlertsEnabled());
        tankSwitch.setChecked(settingsManager.isLowTankAlertsEnabled());

        applyDependentEnabledState(humidityRow, tankRow, humiditySwitch, tankSwitch, notificationsEnabled);

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setNotificationsEnabled(isChecked);
            applyDependentEnabledState(humidityRow, tankRow, humiditySwitch, tankSwitch, isChecked);
            if (isChecked) {
                checkNotificationPermission();
            }
        });

        humiditySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setLowHumidityAlertsEnabled(isChecked));

        tankSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setLowTankAlertsEnabled(isChecked));
    }

    private void setUpWateringReminders(View view) {
        AutoCompleteTextView dropdown = view.findViewById(R.id.dropdownReminderFrequency);
        View switchRow = view.findViewById(R.id.row_watering_reminder_alerts);
        SwitchCompat reminderSwitch = switchRow.findViewById(R.id.switch_toggle); // adjust id to match item_settings_switch_row's internal switch id

        if (dropdown == null || reminderSwitch == null) return;

        ((TextView) switchRow.findViewById(R.id.text_label)).setText(R.string.watering_reminders);
        ((TextView) switchRow.findViewById(R.id.text_description)).setText(R.string.watering_reminders_desc);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.reminder_frequencies, android.R.layout.simple_dropdown_item_1line);
        dropdown.setAdapter(adapter);
        dropdown.setText(settingsManager.getReminderFrequency(), false);

        boolean remindersOn = settingsManager.isWateringRemindersEnabled();
        reminderSwitch.setChecked(remindersOn);
        dropdown.setEnabled(remindersOn);

        if (remindersOn) {
            String selected = dropdown.getText().toString();
            scheduleOrCancelReminder(selected);
        }

        checkNotificationPermission(); // Added this to check on view creation if enabled

        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setWateringRemindersEnabled(isChecked); // or your dedicated setter
            dropdown.setEnabled(isChecked);

            if (isChecked) {
                checkNotificationPermission();
                String selected = dropdown.getText().toString();
                scheduleOrCancelReminder(selected);
            } else {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("watering_reminder");
            }
        });

        dropdown.setOnItemClickListener((parent, v, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            settingsManager.setReminderFrequency(selected);
            if (reminderSwitch.isChecked()) {
                scheduleOrCancelReminder(selected);
            }
        });
    }

    private void scheduleOrCancelReminder(String frequency) {
        long intervalMillis = frequencyToMillis(frequency);
        if (intervalMillis == -1L) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("watering_reminder");
        } else {
            PeriodicWorkRequest reminderRequest =
                    new PeriodicWorkRequest.Builder(ReminderWorker.class,
                            intervalMillis, TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "watering_reminder",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    reminderRequest);
        }
    }

    private long frequencyToMillis(String frequency) {
        switch (frequency) {
            case "Every day":
                return TimeUnit.DAYS.toMillis(1);
            case "Every 3 days":
                return TimeUnit.DAYS.toMillis(3);
            case "Twice a Week":
                return TimeUnit.DAYS.toMillis(7) / 2;
            case "Weekly":
                return TimeUnit.DAYS.toMillis(7);
            case "Every month":
                return TimeUnit.DAYS.toMillis(30);
            case "Never":
            default:
                return -1L;
        }
    }

    private void applyDependentEnabledState(View humidityRow, View tankRow,
                                            SwitchMaterial humiditySwitch, SwitchMaterial tankSwitch,
                                            boolean enabled) {
        humiditySwitch.setEnabled(enabled);
        tankSwitch.setEnabled(enabled);

        int labelColor = enabled
                ? ContextCompat.getColor(requireContext(), R.color.text_dark)
                : ContextCompat.getColor(requireContext(), R.color.text_disabled);
        int descColor = enabled
                ? ContextCompat.getColor(requireContext(), R.color.text_gray_666)
                : ContextCompat.getColor(requireContext(), R.color.text_disabled_light);

        ((TextView) humidityRow.findViewById(R.id.text_label)).setTextColor(labelColor);
        ((TextView) humidityRow.findViewById(R.id.text_description)).setTextColor(descColor);
        ((TextView) tankRow.findViewById(R.id.text_label)).setTextColor(labelColor);
        ((TextView) tankRow.findViewById(R.id.text_description)).setTextColor(descColor);
    }
}