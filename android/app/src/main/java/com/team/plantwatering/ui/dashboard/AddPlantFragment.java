package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;


public class AddPlantFragment extends Fragment {
    EditText name;
    Button saveButton, cancelButton;
    TextView mcuStatus;
    private boolean slotAvailable = false;

    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }
    private PlantClickListener clickListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_plant, container, false);
    }
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlantClickListener) {
            clickListener = (PlantClickListener) context;
        } else {
            throw new IllegalStateException("Host activity must implement PlantClickListener");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PlantViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);

        name = view.findViewById(R.id.edit_plant_name);
        saveButton = view.findViewById(R.id.button_save_plant);
        cancelButton = view.findViewById(R.id.button_cancel_add_plant);
        mcuStatus = view.findViewById(R.id.text_mcu_status);

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.add_plant_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.add_plant_subtitle);

        // No manual "connect" step: check the database for an available id
        // as soon as the screen opens, since the ESP32 registers its own ids automatically.
        saveButton.setEnabled(false);
        mcuStatus.setText(R.string.mcu_checking_availability);

        viewModel.checkIdAvailability(available -> requireActivity().runOnUiThread(() -> {
            slotAvailable = available;
            if (available) {
                mcuStatus.setText(R.string.mcu_slot_ready);
                saveButton.setEnabled(true);
            } else {
                mcuStatus.setText(R.string.mcu_no_slots);
                saveButton.setEnabled(false);
            }
        }));

        saveButton.setOnClickListener(v -> {
            String plantName = name.getText().toString();
            if (plantName.isEmpty() || !slotAvailable) {
                return;
            }

            saveButton.setEnabled(false);
            viewModel.addPlant(plantName, new PlantViewModel.AddPlantCallback() {
                @Override
                public void onSuccess(String plantId) {
                    requireActivity().runOnUiThread(() ->
                            getParentFragmentManager().popBackStack());
                }

                @Override
                public void onNoSlotsAvailable() {
                    requireActivity().runOnUiThread(() -> {
                        mcuStatus.setText(R.string.mcu_no_slots);
                        slotAvailable = false;
                        saveButton.setEnabled(false);
                    });
                }
            });
        });

        cancelButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }
}