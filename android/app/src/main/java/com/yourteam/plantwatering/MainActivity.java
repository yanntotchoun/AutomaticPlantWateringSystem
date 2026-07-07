package com.yourteam.plantwatering;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.yourteam.plantwatering.data.PlantReading;
import com.yourteam.plantwatering.ui.dashboard.AddPlantFragment;
import com.yourteam.plantwatering.ui.dashboard.DashboardFragment;
import com.yourteam.plantwatering.ui.dashboard.OverviewFragment;
import com.yourteam.plantwatering.ui.dashboard.PlantDetailsFragment;
import com.yourteam.plantwatering.ui.dashboard.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class MainActivity extends AppCompatActivity
        implements DashboardFragment.PlantClickListener,
        OverviewFragment.PlantClickListener,
        AddPlantFragment.PlantClickListener {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                showTabFragment(new DashboardFragment());
                return true;
            } else if (itemId == R.id.nav_overview) {
                showTabFragment(new OverviewFragment());
                return true;
            } else if (itemId == R.id.nav_settings) {
                showTabFragment(new SettingsFragment());
                return true;
            } else if (itemId == R.id.nav_add_plant) {
                showTabFragment(new AddPlantFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            showTabFragment(new DashboardFragment());
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            // If we returned to a tab fragment from the details, show the bottom nav again.
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                bottomNavigationView.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    /**
     * Swaps the visible tab fragment without adding it to the back stack,
     * and clears any Details screen that may have been on top.
     */
    private void showTabFragment(Fragment fragment) {
        // Clear any PlantDetailsFragment (and its back stack entry) that might be showing.
        getSupportFragmentManager().popBackStack(
                DETAILS_BACK_STACK_TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        bottomNavigationView.setVisibility(android.view.View.VISIBLE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private static final String DETAILS_BACK_STACK_TAG = "plant_details";


    private void openPlantDetails(PlantReading plant) {
        PlantDetailsFragment detailsFragment = PlantDetailsFragment.newInstance(plant);

        bottomNavigationView.setVisibility(android.view.View.GONE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailsFragment)
                .addToBackStack(DETAILS_BACK_STACK_TAG)
                .commit();
    }
    @Override
    public void onPlantClicked(PlantReading plant) {
        openPlantDetails(plant);
    }

}