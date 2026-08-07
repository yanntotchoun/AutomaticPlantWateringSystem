package com.team.plantwatering.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team.plantwatering.R;
import com.team.plantwatering.data.PlantReading;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class DashboardListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SUMMARY = 0;
    private static final int VIEW_TYPE_EMPTY = 1;
    private static final int VIEW_TYPE_PLANT = 2;


    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private List<PlantReading> filteredPlants;
    private final PlantClickListener clickListener;
    private final PlantViewModel viewModel;
    private long currentServerTime = 0L; // kept in sync via updatePlants(), used for online/offline checks

    // Tracks which plant cards are expanded, keyed by plant name.
    // (Ported from each PlantCard's own `var isExpanded by remember { mutableStateOf(false) }` -
    // that state has to live outside the ViewHolder here since rows get recycled.)
    private final Set<String> expandedPlantNames = new HashSet<>();

    public DashboardListAdapter(List<PlantReading> initialPlants, PlantClickListener clickListener, PlantViewModel viewModel) {
        this.filteredPlants = initialPlants;
        this.clickListener = clickListener;
        this.viewModel = viewModel;
        this.currentServerTime = viewModel.getCurrentServerTime();
    }

    public void updatePlants(List<PlantReading> newFilteredPlants, long currentServerTime) {
        this.filteredPlants = newFilteredPlants;
        this.currentServerTime = currentServerTime;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_SUMMARY;
        }
        if (filteredPlants.isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }
        return VIEW_TYPE_PLANT;
    }

    @Override
    public int getItemCount() {
        return 1 + (filteredPlants.isEmpty() ? 1 : filteredPlants.size());
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_SUMMARY:
                return new SummaryViewHolder(
                        inflater.inflate(R.layout.item_summary_card, parent, false));
            case VIEW_TYPE_EMPTY:
                return new EmptyViewHolder(
                        inflater.inflate(R.layout.item_empty_state, parent, false));
            default:
                return new PlantViewHolder(
                        inflater.inflate(R.layout.item_plant_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SummaryViewHolder) {
            ((SummaryViewHolder) holder).bind(filteredPlants);
        } else if (holder instanceof PlantViewHolder) {
            PlantReading plant = filteredPlants.get(position - 1);
            ((PlantViewHolder) holder).bind(plant, expandedPlantNames, clickListener, viewModel, currentServerTime);
        }
        // EmptyViewHolder has no dynamic content to bind.
    }

    static class SummaryViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final LinearLayout rowsContainer;
        private final PlantSettingsManager settingsManager;

        SummaryViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_title);
            subtitle = itemView.findViewById(R.id.text_subtitle);
            rowsContainer = itemView.findViewById(R.id.summary_rows_container);
            settingsManager = new PlantSettingsManager(itemView.getContext());
        }

        void bind(List<PlantReading> plants) {
            title.setText(R.string.system_summary);
            subtitle.setText(R.string.main_dashboard_overview);

            int avgHumidity = 0;
            if (!plants.isEmpty()) {
                long sumHumidity = 0;
                for (PlantReading plant : plants) {
                    sumHumidity += plant.getSoilHumidity();
                }
                avgHumidity = Math.round((float) sumHumidity / plants.size());
            }

            rowsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

            addRow(inflater, itemView.getContext().getString(R.string.plants_shown), String.valueOf(plants.size()));
            addRow(inflater, itemView.getContext().getString(R.string.average_soil_humidity), avgHumidity + "%");
            // Average tank level removed because it is now a status string.
        }

        private void addRow(LayoutInflater inflater, String label, String value) {
            View row = inflater.inflate(R.layout.item_summary_row, rowsContainer, false);
            ((TextView) row.findViewById(R.id.text_label)).setText(label);
            ((TextView) row.findViewById(R.id.text_value)).setText(value);
            rowsContainer.addView(row);
        }
    }

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }


    static class PlantViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView plantName;
        private final TextView hardwareSlot;
        private final TextView hardwareSlotExpanded;
        private final TextView statusChip;
        private final LinearLayout dropletContainer;
        private final TextView humidityPercent;
        private final TextView lastSeen;
        private final View toggleButton;
        private final View expandableSection;
        private final ImageView bucket;
        private final TextView waterTankPercent;
        private final TextView connectionStatus;
        private final PlantSettingsManager settingsManager;

        PlantViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.text_avatar);
            plantName = itemView.findViewById(R.id.text_plant_name);
            hardwareSlot = itemView.findViewById(R.id.text_hardware_slot);
            hardwareSlotExpanded = itemView.findViewById(R.id.text_hardware_slot_expanded);
            statusChip = itemView.findViewById(R.id.chip_status);
            dropletContainer = itemView.findViewById(R.id.droplet_container);
            humidityPercent = itemView.findViewById(R.id.text_humidity_percent);
            lastSeen = itemView.findViewById(R.id.text_last_watered);
            toggleButton = itemView.findViewById(R.id.button_toggle_expand);
            expandableSection = itemView.findViewById(R.id.expandable_section);
            bucket = itemView.findViewById(R.id.image_bucket);
            waterTankPercent = itemView.findViewById(R.id.text_water_tank_percent);
            connectionStatus = itemView.findViewById(R.id.text_connection_status);
            settingsManager = new PlantSettingsManager(itemView.getContext());
        }

        void bind(PlantReading plant, Set<String> expandedPlantNames, PlantClickListener clickListener,
                  PlantViewModel viewModel, long currentServerTime) {
            PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(plant.getThresholdId());

            PlantViewBinder.bindAvatar(avatar, plant.getPlantName());
            plantName.setText(plant.getPlantName());
            
            // Show hardware slot association
            // The identifier (Firebase key) is "slot1" or "slot2"
            String slotId = plant.getIdentifier().toLowerCase();
            String sensorLabel = slotId.contains("slot1") ? "Soil Moisture Sensor 1" : (slotId.contains("slot2") ? "Soil Moisture Sensor 2" : null);

            if (sensorLabel != null) {
                hardwareSlot.setText(sensorLabel);
                hardwareSlot.setVisibility(View.VISIBLE);
                if (hardwareSlotExpanded != null) {
                    hardwareSlotExpanded.setText(sensorLabel);
                    hardwareSlotExpanded.setVisibility(View.VISIBLE);
                }
            } else {
                hardwareSlot.setVisibility(View.GONE);
                if (hardwareSlotExpanded != null) {
                    hardwareSlotExpanded.setVisibility(View.GONE);
                }
            }

            PlantViewBinder.bindStatusChip(statusChip, plant.getSoilHumidity(), profile.drySoil);
            PlantViewBinder.bindDropletBar(dropletContainer, plant.getSoilHumidity());

            humidityPercent.setText(String.format(Locale.getDefault(), "%d%%", plant.getSoilHumidity()));
            humidityPercent.setTextColor(DashboardUtils.humidityTextColor(plant.getSoilHumidity(), profile.drySoil));

            lastSeen.setText(DashboardUtils.formatRelativeTime(
                    plant.getLastSeenMillis(), currentServerTime));

            boolean isExpanded = expandedPlantNames.contains(plant.getPlantName());
            expandableSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            ((com.google.android.material.button.MaterialButton) toggleButton).setText(
                    isExpanded ? R.string.hide_plant_information : R.string.show_plant_information);

            if (isExpanded) {
                PlantViewBinder.bindWaterTank(bucket, waterTankPercent, plant.getWaterTank());
                PlantViewBinder.bindConnectionStatus(connectionStatus, plant, currentServerTime);
            }

            toggleButton.setOnClickListener(v -> {
                if (expandedPlantNames.contains(plant.getPlantName())) {
                    expandedPlantNames.remove(plant.getPlantName());
                } else {
                    expandedPlantNames.add(plant.getPlantName());
                }
                // Rebind this row only, so the rest of the list doesn't flicker.
                notifyItemChangedSafely();
            });

            itemView.setOnClickListener(v -> clickListener.onPlantClicked(plant));
        }

        private void notifyItemChangedSafely() {
            RecyclerView.Adapter<?> adapter = null;
            if (itemView.getParent() instanceof RecyclerView) {
                adapter = ((RecyclerView) itemView.getParent()).getAdapter();
            }
            if (adapter != null) {
                adapter.notifyItemChanged(getBindingAdapterPosition());
            }
        }
    }
}