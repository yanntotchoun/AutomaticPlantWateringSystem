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

import com.team.plantwatering.MainActivity;
import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;


public class AddPlantFragment extends Fragment {
    EditText name;
    Button saveButton, cancelButton;
    TextView mcuStatus;

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
        mcuStatus = view.findViewById(R.id.text_mcu_status);

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.add_plant_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.add_plant_subtitle);

        // Slot implementation forgone. Enable save immediately.
        saveButton.setEnabled(true);
        mcuStatus.setVisibility(View.GONE);

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
                    }));
        });

        cancelButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }
}