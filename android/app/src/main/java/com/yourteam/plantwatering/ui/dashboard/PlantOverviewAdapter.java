package com.yourteam.plantwatering.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yourteam.plantwatering.R;
import com.yourteam.plantwatering.data.PlantReading;

import java.util.List;
import java.util.Locale;

/**
 * Backs OverviewFragment's RecyclerView: an intro summary card (position 0)
 * followed by one row per plant. Ported from PlantOverviewScreen.kt.
 */
public class PlantOverviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_INTRO = 0;
    private static final int VIEW_TYPE_PLANT = 1;

    public interface PlantClickListener {
        void onPlantClicked(PlantReading plant);
    }

    private final List<PlantReading> plants;
    private final PlantClickListener clickListener;

    public PlantOverviewAdapter(List<PlantReading> plants, PlantClickListener clickListener) {
        this.plants = plants;
        this.clickListener = clickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_INTRO : VIEW_TYPE_PLANT;
    }

    @Override
    public int getItemCount() {
        return 1 + plants.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_INTRO) {
            return new IntroViewHolder(inflater.inflate(R.layout.item_summary_card, parent, false));
        }
        return new PlantRowViewHolder(inflater.inflate(R.layout.item_plant_overview_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof IntroViewHolder) {
            ((IntroViewHolder) holder).bind(plants);
        } else {
            PlantReading plant = plants.get(position - 1);
            ((PlantRowViewHolder) holder).bind(plant, clickListener);
        }
    }

    /** Ported from OverviewIntroCard.kt. */
    static class IntroViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final LinearLayout rowsContainer;
        private final PlantSettingsManager settingsManager;

        IntroViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_title);
            subtitle = itemView.findViewById(R.id.text_subtitle);
            rowsContainer = itemView.findViewById(R.id.summary_rows_container);
            settingsManager = new PlantSettingsManager(itemView.getContext());
        }

        void bind(List<PlantReading> plants) {
            title.setText(R.string.overview_summary);
            subtitle.setVisibility(View.GONE);

            int dryPlants = 0;
            for (PlantReading plant : plants) {
                PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(plant.getThresholdId());
                if (plant.getSoilHumidity() < profile.drySoil) {
                    dryPlants++;
                }
            }

            rowsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            addRow(inflater, itemView.getContext().getString(R.string.total_plants), String.valueOf(plants.size()));
            addRow(inflater, itemView.getContext().getString(R.string.plants_needing_water), String.valueOf(dryPlants));
        }

        private void addRow(LayoutInflater inflater, String label, String value) {
            View row = inflater.inflate(R.layout.item_summary_row, rowsContainer, false);
            ((TextView) row.findViewById(R.id.text_label)).setText(label);
            ((TextView) row.findViewById(R.id.text_value)).setText(value);
            rowsContainer.addView(row);
        }
    }

    /** Ported from PlantOverviewRow.kt. */
    static class PlantRowViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView plantName;
        private final TextView humidityTank;
        private final TextView statusChip;
        private final PlantSettingsManager settingsManager;

        PlantRowViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.text_avatar);
            plantName = itemView.findViewById(R.id.text_plant_name);
            humidityTank = itemView.findViewById(R.id.text_humidity_tank);
            statusChip = itemView.findViewById(R.id.chip_status);
            settingsManager = new PlantSettingsManager(itemView.getContext());
        }

        void bind(PlantReading plant, PlantClickListener clickListener) {
            PlantSettingsManager.ThresholdProfile profile = settingsManager.getThresholdProfile(plant.getThresholdId());
            PlantViewBinder.bindAvatar(avatar, plant.getPlantName());
            plantName.setText(plant.getPlantName());
            humidityTank.setText(String.format(Locale.getDefault(), "Humidity: %d%%  |  Tank: %d%%",
                    plant.getSoilHumidity(), plant.getWaterTank()));
            PlantViewBinder.bindStatusChip(statusChip, plant.getSoilHumidity(), profile.drySoil);

            itemView.setOnClickListener(v -> clickListener.onPlantClicked(plant));
        }
    }
}