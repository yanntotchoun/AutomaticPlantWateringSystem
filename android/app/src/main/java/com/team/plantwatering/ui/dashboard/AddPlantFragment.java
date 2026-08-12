package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.team.plantwatering.CloudinaryImageUploader;
import com.team.plantwatering.MainActivity;
import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddPlantFragment extends Fragment {
    private EditText name;
    private Button saveButton;
    private Button selectPhotoButton;
    private TextView limitStatus;
    private ImageView plantImagePreview;
    private boolean isHardwareFull = false;
    private String selectedSlot = null;
    private final List<String> existingNames = new ArrayList<>();
    private Uri selectedImageUri = null;

    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    if (plantImagePreview != null) plantImagePreview.setImageURI(uri);
                }
            }
    );

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
        limitStatus = view.findViewById(R.id.text_slot_status);
        plantImagePreview = view.findViewById(R.id.image_plant_preview);
        selectPhotoButton = view.findViewById(R.id.button_select_photo);

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.add_plant_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.add_plant_subtitle);

        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSaveButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
            boolean slot1Taken = false;
            boolean slot2Taken = false;
            existingNames.clear();
            for (PlantReading plant : plants) {
                if (!plant.isTaken()) continue;
                String identifier = plant.getIdentifier();
                if (identifier != null) {
                    identifier = identifier.toLowerCase(Locale.ROOT);
                    if (identifier.equals("slot1")) slot1Taken = true;
                    else if (identifier.equals("slot2")) slot2Taken = true;
                }
                String existingName = plant.getPlantName();
                if (existingName != null) existingNames.add(existingName.toLowerCase(Locale.ROOT).trim());
            }
            isHardwareFull = slot1Taken && slot2Taken;
            selectedSlot = !slot1Taken ? "slot1" : (!slot2Taken ? "slot2" : null);

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

        selectPhotoButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        saveButton.setOnClickListener(v -> {
            String plantName = name.getText().toString().trim();
            if (plantName.isEmpty()) {
                name.setError("Please enter a name");
                return;
            }
            if (existingNames.contains(plantName.toLowerCase(Locale.ROOT))) {
                Toast.makeText(requireContext(), "A plant named '" + plantName + "' already exists.", Toast.LENGTH_SHORT).show();
                name.setError("Name already taken");
                return;
            }
            if (selectedSlot == null) {
                Toast.makeText(requireContext(), "No hardware slot is available.", Toast.LENGTH_SHORT).show();
                return;
            }
            saveButton.setEnabled(false);
            PlantSettingsManager settingsManager = new PlantSettingsManager(requireContext());
            PlantSettingsManager.ThresholdProfile standardProfile = settingsManager.getThresholdProfile("standard");
            viewModel.addPlant(plantName, selectedSlot, standardProfile, new PlantViewModel.AddPlantCallback() {
                @Override
                public void onSuccess(String plantId) {
                    if (selectedImageUri != null) uploadNewPlantImage(viewModel, plantId, selectedImageUri);
                    else finishSavingPlant();
                }
                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        updateSaveButtonState();
                        name.setError(message);
                    });
                }
            });
        });
    }

    private void uploadNewPlantImage(PlantViewModel viewModel, String plantId, Uri imageUri) {
        CloudinaryImageUploader.upload(imageUri, new CloudinaryImageUploader.Callback() {
            @Override
            public void onSuccess(String imageUrl) {
                viewModel.updatePlantImage(plantId, imageUrl);
                finishSavingPlant();
            }
            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Plant created, but the photo could not be uploaded: " + errorMessage, Toast.LENGTH_LONG).show();
                    finishSavingPlant();
                });
            }
        });
    }

    private void finishSavingPlant() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            name.setText("");
            selectedImageUri = null;
            updateSaveButtonState();
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                View navView = activity.findViewById(R.id.bottom_navigation);
                if (navView != null) {
                    View dashboardButton = navView.findViewById(R.id.nav_dashboard);
                    if (dashboardButton != null) dashboardButton.performClick();
                }
            }
        });
    }

    private void updateSaveButtonState() {
        if (name == null || saveButton == null) return;
        boolean hasName = name.getText() != null && !name.getText().toString().trim().isEmpty();
        saveButton.setEnabled(!isHardwareFull && selectedSlot != null && hasName);
    }
}
