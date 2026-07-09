package com.yourteam.plantwatering.ui.dashboard;

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

import com.yourteam.plantwatering.MainActivity;
import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;


public class AddPlantFragment extends Fragment {
    EditText name,type;
    Button saveButton,cancelButton,connectButton;
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
        name = view.findViewById(R.id.edit_plant_name);
        type = view.findViewById(R.id.edit_plant_type);
        saveButton = view.findViewById(R.id.button_save_plant);
        cancelButton = view.findViewById(R.id.button_cancel_add_plant);
        connectButton = view.findViewById(R.id.button_connect_mcu);

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.add_plant_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.add_plant_subtitle);

        saveButton.setOnClickListener(v -> {
            String plantName = name.getText().toString();
            String plantType = type.getText().toString();


            // PlantReading newPlant = new PlantReading(plantName, plantType, 0, 0, 0, 0);
                //clickListener.onPlantClicked(newPlant);

        });

        //cancelButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());


    }
}
