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

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.team.plantwatering.MainActivity;
import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;


public class AddPlantFragment extends Fragment {
    EditText name;
    Button saveButton;
    TextView limitStatus;
    boolean isHardwareFull = false;
    String selectedSlot = null;
    java.util.List<String> existingNames = new java.util.ArrayList<>();

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
            boolean slot1Taken = false;
            boolean slot2Taken = false;
            existingNames.clear();

            for (PlantReading plant : plants) {
                if (plant.isTaken()) {
                    String identifier = plant.getIdentifier().toLowerCase();
                    if (identifier.contains("slot1")) slot1Taken = true;
                    else if (identifier.contains("slot2")) slot2Taken = true;
                    
                    existingNames.add(plant.getPlantName().toLowerCase().trim());
                }
            }

            isHardwareFull = (slot1Taken && slot2Taken);
            
            if (!slot1Taken) {
                selectedSlot = "slot1";
            } else if (!slot2Taken) {
                selectedSlot = "slot2";
            } else {
                selectedSlot = null;
            }

            if (limitStatus != null) {
                if (isHardwareFull) {
                    limitStatus.setVisibility(View.VISIBLE);
                    limitStatus.setText(R.string.all_slots_occupied);
                    limitStatus.setTextColor(getResources().getColor(R.color.error_red, null));
                } else if (selectedSlot != null) {
                    limitStatus.setVisibility(View.VISIBLE);
                    String slotDisplay = selectedSlot.equals("slot1") ? "Soil Moisture Sensor 1" : "Soil Moisture Sensor 2";
                    limitStatus.setText("This plant will be assigned to " + slotDisplay);
                    limitStatus.setTextColor(getResources().getColor(R.color.status_healthy_text, null));
                } else {
                    limitStatus.setVisibility(View.GONE);
                }
            }
            updateSaveButtonState();
        });

        saveButton.setOnClickListener(v -> {
            String plantName = name.getText().toString().trim();
            if (plantName.isEmpty()) {
                name.setError("Please enter a name");
                return;
            }
            
            if (existingNames.contains(plantName.toLowerCase())) {
                Toast.makeText(requireContext(), "A plant named '" + plantName + "' already exists.", Toast.LENGTH_SHORT).show();
                name.setError("Name already taken");
                return;
            }

            if (selectedSlot == null) {
                return;
            }

            saveButton.setEnabled(false);
            
            // Get the default "standard" profile to initialize thresholds
            PlantSettingsManager settingsManager = new PlantSettingsManager(requireContext());
            PlantSettingsManager.ThresholdProfile standardProfile = settingsManager.getThresholdProfile("standard");
            
            viewModel.addPlant(plantName, selectedSlot, standardProfile, new PlantViewModel.AddPlantCallback() {
                @Override
                public void onSuccess(String plantId) {
                    requireActivity().runOnUiThread(() -> {
                        name.setText("");
                        saveButton.setEnabled(true);
                        if (getActivity() instanceof MainActivity) {
                            MainActivity activity = (MainActivity) getActivity();
                            View navView = activity.findViewById(R.id.bottom_navigation);
                            if (navView != null) {
                                navView.findViewById(R.id.nav_dashboard).performClick();
                            }
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        saveButton.setEnabled(true);
                        name.setError(message);
                    });
                }
            });
        });
    }

    private void updateSaveButtonState() {
        boolean hasName = name.getText() != null && !name.getText().toString().trim().isEmpty();
        saveButton.setEnabled(!isHardwareFull && hasName);
    }
}