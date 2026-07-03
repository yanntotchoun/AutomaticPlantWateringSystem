package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;

/** Ported from PlantOverviewScreen.kt. */
public class OverviewFragment extends Fragment {

    /** Same shape as DashboardFragment.PlantClickListener - MainActivity implements both. */
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

        RecyclerView recyclerView = view.findViewById(R.id.recycler_overview);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(new PlantOverviewAdapter(
                viewModel.getPlants(),
                plant -> clickListener.onPlantClicked(plant)
        ));
    }
}