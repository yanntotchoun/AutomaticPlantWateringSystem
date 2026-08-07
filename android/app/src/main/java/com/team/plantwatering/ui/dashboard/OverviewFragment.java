package com.team.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;


public class OverviewFragment extends BaseFragment {

    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private PlantClickListener clickListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlantClickListener) {
            clickListener = (PlantClickListener) context;
        } else {
            throw new IllegalStateException("Host activity must implement OverviewFragment.PlantClickListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PlantViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);

        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.overview_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.overview_subtitle);


        applyStatusBarInset(header);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_overview);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        java.util.List<PlantReading> plants = new java.util.ArrayList<>();
        PlantOverviewAdapter adapter = new PlantOverviewAdapter(
                plants,
                plant -> clickListener.onPlantClicked(plant)
        );
        recyclerView.setAdapter(adapter);

        // Pass the server-synced time along with each update so isOnline() has a real
        // value to compare against, instead of defaulting to 0 (which made every plant
        // show as Online regardless of actual connectivity).
        viewModel.getPlants().observe(getViewLifecycleOwner(), newPlants -> {
            java.util.List<PlantReading> filtered = new java.util.ArrayList<>();
            for (PlantReading p : newPlants) {
                if (p.isTaken()) filtered.add(p);
            }
            adapter.updatePlants(filtered, viewModel.getCurrentServerTime());
        });
    }
}