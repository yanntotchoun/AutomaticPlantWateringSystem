package com.yourteam.plantwatering.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class DashboardFragment extends BaseFragment {


    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private PlantClickListener clickListener;
    private PlantViewModel viewModel;
    private DashboardListAdapter adapter;
    private List<PlantReading> allPlants;

    private boolean userInterfaceUpdateNeeded = false;
    private final Handler executeRunnable = new Handler(Looper.getMainLooper());
    private final Runnable updateUI = new Runnable() {
        @Override
        public void run() {
            if (!userInterfaceUpdateNeeded) return;

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            executeRunnable.postDelayed(this, 60_000);
        }
    };

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
        allPlants = new ArrayList<>();

        // Initialize default threshold profiles
        new PlantSettingsManager(requireContext()).initDefaultProfiles();

        // Header text
        View header = view.findViewById(R.id.header_root);
        ((TextView) header.findViewById(R.id.text_header_title)).setText(R.string.dashboard_title);
        ((TextView) header.findViewById(R.id.text_header_subtitle)).setText(R.string.dashboard_subtitle);

        applyStatusBarInset(header);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_dashboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DashboardListAdapter(allPlants, plant -> clickListener.onPlantClicked(plant));
        recyclerView.setAdapter(adapter);

        TextInputEditText searchEdit = view.findViewById(R.id.edit_search);

        // Observe LiveData from Firebase
        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
            allPlants = plants;
            filterPlants(searchEdit.getText() != null ? searchEdit.getText().toString() : "");
        });

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

    @Override
    public void onResume() {
        super.onResume();
        userInterfaceUpdateNeeded = true;
        executeRunnable.post(updateUI);
    }

    @Override
    public void onPause() {
        super.onPause();
        userInterfaceUpdateNeeded = false;
    }

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

