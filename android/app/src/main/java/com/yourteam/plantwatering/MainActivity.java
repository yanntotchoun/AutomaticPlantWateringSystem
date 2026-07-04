package com.yourteam.plantwatering;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.yourteam.plantwatering.data.PlantReading;
import com.yourteam.plantwatering.ui.dashboard.DashboardFragment;
import com.yourteam.plantwatering.ui.dashboard.OverviewFragment;
import com.yourteam.plantwatering.ui.dashboard.PlantDetailsFragment;
import com.yourteam.plantwatering.ui.dashboard.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Single Activity hosting all screens as Fragments, with a persistent
 * BottomNavigationView. This replaces PlantDashboardScreen()'s Scaffold +
 * `when (currentScreen)` switch + `selectedPlant` state from the Compose version.
 *
 * Navigation model:
 * - The three bottom nav tabs (Dashboard, Overview, Settings) swap the fragment
 *   in fragment_container and are NOT added to the back stack (matches Compose's
 *   behavior, where switching tabs didn't build up a back history).
 * - Tapping a plant card (from Dashboard or Overview) opens PlantDetailsFragment
 *   ON TOP of the current tab and IS added to the back stack, and the bottom nav
 *   is hidden while viewing details (matching `if (selectedPlant != null)` fully
 *   replacing the Scaffold in Compose). Pressing back or "Back to dashboard"
 *   returns to whichever tab was showing before.
 */
public class MainActivity extends AppCompatActivity
        implements DashboardFragment.PlantClickListener,
        OverviewFragment.PlantClickListener {

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
            }
            return false;
        });

        if (savedInstanceState == null) {
            showTabFragment(new DashboardFragment());
        }
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

    /**
     * Opens PlantDetailsFragment for the given plant, hides the bottom nav
     * (matching Compose's `if (selectedPlant != null)` branch, which fully
     * replaced the Scaffold), and adds the transaction to the back stack so
     * the system back button returns to the previous tab.
     */
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