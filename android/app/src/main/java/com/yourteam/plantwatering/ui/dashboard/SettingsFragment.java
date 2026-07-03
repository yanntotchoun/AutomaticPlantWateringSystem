package com.yourteam.plantwatering.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.yourteam.plantwatering.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Ported from SettingsScreen.kt. All persistence goes through PlantSettingsManager
 * (the SharedPreferences logic extracted earlier), so this fragment only wires up
 * the Views and reflects/saves state exactly like the Compose version's remember +
 * sharedPreferences.edit() calls did.
 */
public class SettingsFragment extends Fragment {

    private PlantSettingsManager settingsManager;

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

        setUpTemperatureUnit(view);
        setUpThresholds(view);
        setUpNotifications(view);

        view.findViewById(R.id.button_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
    }

    /** Ported from the "Units" SettingsSectionCard + TemperatureUnitOption rows. */
    private void setUpTemperatureUnit(View view) {
        RadioGroup radioGroup = view.findViewById(R.id.radio_group_temperature_unit);
        boolean isCelsius = "Celsius".equals(settingsManager.getTemperatureUnit());
        radioGroup.check(isCelsius ? R.id.radio_celsius : R.id.radio_fahrenheit);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String unit = checkedId == R.id.radio_celsius ? "Celsius" : "Fahrenheit";
            settingsManager.setTemperatureUnit(unit);
        });
    }

    /** Ported from the "Thresholds" SettingsSectionCard + two Sliders. */
    private void setUpThresholds(View view) {
        TextView drySoilLabel = view.findViewById(R.id.text_dry_soil_threshold);
        Slider drySoilSlider = view.findViewById(R.id.slider_dry_soil_threshold);
        int drySoilValue = settingsManager.getDrySoilThreshold();
        drySoilSlider.setValue(drySoilValue);
        drySoilLabel.setText(getString(R.string.dry_soil_threshold_label, drySoilValue));

        drySoilSlider.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = Math.round(value);
            settingsManager.setDrySoilThreshold(intValue);
            drySoilLabel.setText(getString(R.string.dry_soil_threshold_label, intValue));
        });

        TextView fullTankLabel = view.findViewById(R.id.text_full_tank_threshold);
        Slider fullTankSlider = view.findViewById(R.id.slider_full_tank_threshold);
        int fullTankValue = settingsManager.getFullTankThreshold();
        fullTankSlider.setValue(fullTankValue);
        fullTankLabel.setText(getString(R.string.full_tank_threshold_label, fullTankValue));

        fullTankSlider.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = Math.round(value);
            settingsManager.setFullTankThreshold(intValue);
            fullTankLabel.setText(getString(R.string.full_tank_threshold_label, intValue));
        });
    }

    /** Ported from the "Notifications" SettingsSectionCard + three SettingsSwitchRows. */
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
        });

        humiditySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setLowHumidityAlertsEnabled(isChecked));

        tankSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setLowTankAlertsEnabled(isChecked));
    }

    /**
     * Ported from `enabled = notificationsEnabled` on the two dependent SettingsSwitchRows,
     * including the dimmed text color used when a row is disabled.
     */
    private void applyDependentEnabledState(View humidityRow, View tankRow,
                                            SwitchMaterial humiditySwitch, SwitchMaterial tankSwitch,
                                            boolean enabled) {
        humiditySwitch.setEnabled(enabled);
        tankSwitch.setEnabled(enabled);

        int labelColor = enabled
                ? getResources().getColor(R.color.text_dark)
                : getResources().getColor(R.color.text_disabled);
        int descColor = enabled
                ? getResources().getColor(R.color.text_gray_666)
                : getResources().getColor(R.color.text_disabled_light);

        ((TextView) humidityRow.findViewById(R.id.text_label)).setTextColor(labelColor);
        ((TextView) humidityRow.findViewById(R.id.text_description)).setTextColor(descColor);
        ((TextView) tankRow.findViewById(R.id.text_label)).setTextColor(labelColor);
        ((TextView) tankRow.findViewById(R.id.text_description)).setTextColor(descColor);
    }
}