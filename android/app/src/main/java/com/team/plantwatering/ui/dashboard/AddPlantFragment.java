package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;


public class AddPlantFragment extends Fragment {
    EditText name;
    Button saveButton, cancelButton;
    TextView limitStatus;
    boolean isHardwareFull = false;

    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_plant, container, false);
    }
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PlantViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);

        name = view.findViewById(R.id.edit_plant_name);
        saveButton = view.findViewById(R.id.button_save_plant);
        cancelButton = view.findViewById(R.id.button_cancel_add_plant);
        limitStatus = view.findViewById(R.id.text_slot_status); // Reusing the ID or adding a generic status

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.add_plant_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.add_plant_subtitle);

        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Slot availability observation
        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
            int takenCount = 0;
            for (PlantReading plant : plants) {
                if (plant.isTaken()) takenCount++;
            }

            isHardwareFull = (takenCount >= 2);
            
            if (limitStatus != null) {
                if (isHardwareFull) {
                    limitStatus.setVisibility(View.VISIBLE);
                    limitStatus.setText(R.string.all_slots_occupied);
                    limitStatus.setTextColor(getResources().getColor(R.color.error_red, null));
                } else {
                    limitStatus.setVisibility(View.GONE);
                }
            }
            updateSaveButtonState();
        });

        saveButton.setOnClickListener(v -> {
            String plantName = name.getText().toString();
            if (plantName.isEmpty()) {
                name.setError("Please enter a name");
                return;
            }
            
            saveButton.setEnabled(false);
            
            // Get the default "standard" profile to initialize thresholds
            PlantSettingsManager settingsManager = new PlantSettingsManager(requireContext());
            PlantSettingsManager.ThresholdProfile standardProfile = settingsManager.getThresholdProfile("standard");
            
            viewModel.addPlant(plantName, standardProfile, plantId ->
                    requireActivity().runOnUiThread(() ->
                            getParentFragmentManager().popBackStack()));
        });

        cancelButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void updateSaveButtonState() {
        boolean hasName = name.getText() != null && !name.getText().toString().trim().isEmpty();
        saveButton.setEnabled(!isHardwareFull && hasName);
    }
}