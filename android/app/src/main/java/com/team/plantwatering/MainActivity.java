package com.team.plantwatering;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.team.plantwatering.data.PlantReading;
import com.team.plantwatering.ui.dashboard.AddPlantFragment;
import com.team.plantwatering.ui.dashboard.DashboardFragment;
import com.team.plantwatering.ui.dashboard.OverviewFragment;
import com.team.plantwatering.ui.dashboard.PlantDetailsFragment;
import com.team.plantwatering.ui.dashboard.PlantMonitoringService;
import com.team.plantwatering.ui.dashboard.PlantSettingsManager;
import com.team.plantwatering.ui.dashboard.ReminderWorker;
import com.team.plantwatering.ui.dashboard.SettingsFragment;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements DashboardFragment.PlantClickListener,
        OverviewFragment.PlantClickListener,
        AddPlantFragment.PlantClickListener {

    private static final String DETAILS_BACK_STACK_TAG = "plant_details";

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        setContentView(R.layout.activity_main);

        bottomNavigationView =
                findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnItemSelectedListener(item -> {

            int itemId = item.getItemId();

            if (itemId == R.id.nav_dashboard) {

                showTabFragment(
                        new DashboardFragment()
                );

                return true;

            } else if (itemId == R.id.nav_overview) {

                showTabFragment(
                        new OverviewFragment()
                );

                return true;

            } else if (itemId == R.id.nav_settings) {

                showTabFragment(
                        new SettingsFragment()
                );

                return true;

            } else if (itemId == R.id.nav_add_plant) {

                showTabFragment(
                        new AddPlantFragment()
                );

                return true;
            }

            return false;
        });

        if (savedInstanceState == null) {

            showTabFragment(
                    new DashboardFragment()
            );
        }

        // Restore background functionality based on saved settings.
        checkAndScheduleReminders();
        checkAndStartMonitoringService();

        getSupportFragmentManager()
                .addOnBackStackChangedListener(() -> {

                    // If we returned to a tab fragment from Plant Details,
                    // show the bottom navigation again.
                    if (
                            getSupportFragmentManager()
                                    .getBackStackEntryCount() == 0
                    ) {

                        bottomNavigationView.setVisibility(
                                android.view.View.VISIBLE
                        );
                    }
                });
    }

    private void showTabFragment(
            Fragment fragment
    ) {

        // Clear PlantDetailsFragment and its back stack entry
        // if one is currently open.
        getSupportFragmentManager()
                .popBackStack(
                        DETAILS_BACK_STACK_TAG,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                );

        bottomNavigationView.setVisibility(
                android.view.View.VISIBLE
        );

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        fragment
                )
                .commit();
    }

    private void openPlantDetails(
            PlantReading plant
    ) {

        PlantDetailsFragment detailsFragment =
                PlantDetailsFragment.newInstance(
                        plant
                );

        bottomNavigationView.setVisibility(
                android.view.View.GONE
        );

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        detailsFragment
                )
                .addToBackStack(
                        DETAILS_BACK_STACK_TAG
                )
                .commit();
    }

    @Override
    public void onPlantClicked(
            PlantReading plant
    ) {

        openPlantDetails(
                plant
        );
    }

    private void checkAndStartMonitoringService() {

        PlantSettingsManager settingsManager =
                new PlantSettingsManager(this);

        if (!settingsManager.isNotificationsEnabled()) {
            return;
        }

        Intent intent =
                new Intent(
                        this,
                        PlantMonitoringService.class
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            startForegroundService(
                    intent
            );

        } else {

            startService(
                    intent
            );
        }
    }

    private void checkAndScheduleReminders() {

        PlantSettingsManager settingsManager =
                new PlantSettingsManager(this);

        // Do not schedule anything if global notifications are disabled.
        if (!settingsManager.isNotificationsEnabled()) {
            return;
        }

        // Do not schedule anything if watering reminders are disabled.
        if (!settingsManager.isWateringRemindersEnabled()) {
            return;
        }

        String frequency =
                settingsManager.getReminderFrequency();

        long intervalMillis =
                frequencyToMillis(
                        frequency
                );

        if (intervalMillis == -1L) {
            return;
        }

        PeriodicWorkRequest reminderRequest =
                new PeriodicWorkRequest.Builder(
                        ReminderWorker.class,
                        intervalMillis,
                        TimeUnit.MILLISECONDS
                )

                        // Prevent the first reminder from firing
                        // immediately when the work is created.
                        .setInitialDelay(
                                intervalMillis,
                                TimeUnit.MILLISECONDS
                        )

                        .build();

        WorkManager
                .getInstance(this)
                .enqueueUniquePeriodicWork(
                        "watering_reminder",

                        // If the reminder is already scheduled,
                        // opening the app should NOT restart its timer.
                        ExistingPeriodicWorkPolicy.KEEP,

                        reminderRequest
                );
    }

    private long frequencyToMillis(
            String frequency
    ) {

        switch (frequency) {

            case "Every day":
                return TimeUnit.DAYS.toMillis(1);

            case "Every 3 days":
                return TimeUnit.DAYS.toMillis(3);

            case "Twice a Week":
                return TimeUnit.DAYS.toMillis(7) / 2;

            case "Weekly":
                return TimeUnit.DAYS.toMillis(7);

            case "Every month":
                return TimeUnit.DAYS.toMillis(30);

            case "Never":
            default:
                return -1L;
        }
    }
}