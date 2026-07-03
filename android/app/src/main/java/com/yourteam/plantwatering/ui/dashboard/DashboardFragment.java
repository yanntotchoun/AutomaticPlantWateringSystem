package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ported from the Dashboard branch of PlantDashboardScreen.kt (DashboardContent()).
 * Header + search bar live directly in the fragment layout; the search-filtered
 * summary card, plant cards, and empty state are handled by DashboardListAdapter.
 */
public class DashboardFragment extends Fragment {

    /** Same shape as OverviewFragment.PlantClickListener - MainActivity implements both. */
    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private PlantClickListener clickListener;
    private PlantViewModel viewModel;
    private DashboardListAdapter adapter;
    private List<PlantReading> allPlants;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlantClickListener) {
            clickListener = (PlantClickListener) context;
        } else {
            throw new IllegalStateException("Host activity must implement DashboardFragment.PlantClickListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(PlantViewModel.class);
        allPlants = viewModel.getPlants();

        // Header text (layout_screen_header.xml is shared, so fill in this screen's copy).
        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.dashboard_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.dashboard_subtitle);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_dashboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DashboardListAdapter(allPlants, plant -> clickListener.onPlantClicked(plant));
        recyclerView.setAdapter(adapter);

        TextInputEditText searchEdit = view.findViewById(R.id.edit_search);
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPlants(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Ported from DashboardContent.kt's `plants.filter { it.plantName.contains(searchText, ignoreCase = true) }`. */
    private void filterPlants(String searchText) {
        String query = searchText.toLowerCase(Locale.getDefault());
        List<PlantReading> filtered = new ArrayList<>();
        for (PlantReading plant : allPlants) {
            if (plant.getPlantName().toLowerCase(Locale.getDefault()).contains(query)) {
                filtered.add(plant);
            }
        }
        adapter.updatePlants(filtered);
    }
}