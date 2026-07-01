package com.example.plantwateringsystem.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SETTINGS_FILE = "plant_app_settings"

private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
private const val KEY_DRY_SOIL_THRESHOLD = "dry_soil_threshold"
private const val KEY_FULL_TANK_THRESHOLD = "full_tank_threshold"
private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val KEY_LOW_HUMIDITY_ALERTS = "low_humidity_alerts"
private const val KEY_LOW_TANK_ALERTS = "low_tank_alerts"

@Composable
fun SettingsScreen(
    onBackClicked: () -> Unit
) {
    // current Android context used to access SharedPreferences
    val context = LocalContext.current

    // stores the settings locally on the device
    val sharedPreferences = remember {
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    }

    // loads the saved temperature unit or uses Celsius as default
    var temperatureUnit by remember {
        mutableStateOf(
            sharedPreferences.getString(KEY_TEMPERATURE_UNIT, "Celsius") ?: "Celsius"
        )
    }

    // loads the saved dry soil threshold or uses 30 percent as default
    var drySoilThreshold by remember {
        mutableFloatStateOf(
            sharedPreferences.getInt(KEY_DRY_SOIL_THRESHOLD, 30).toFloat()
        )
    }

    // loads the saved full tank threshold or uses 70 percent as default
    var fullTankThreshold by remember {
        mutableFloatStateOf(
            sharedPreferences.getInt(KEY_FULL_TANK_THRESHOLD, 70).toFloat()
        )
    }

    // controls whether notifications are enabled in the app
    var notificationsEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        )
    }

    // controls alerts for low soil humidity
    var lowHumidityAlerts by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(KEY_LOW_HUMIDITY_ALERTS, true)
        )
    }

    // controls alerts for low water tank level
    var lowTankAlerts by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(KEY_LOW_TANK_ALERTS, true)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsHeader()
        }

        item {
            SettingsSectionCard(title = "Units") {
                Text(
                    text = "Temperature unit",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TemperatureUnitOption(
                    label = "Celsius",
                    selected = temperatureUnit == "Celsius",
                    onSelected = {
                        // saves Celsius as the selected temperature unit
                        temperatureUnit = "Celsius"
                        sharedPreferences.edit()
                            .putString(KEY_TEMPERATURE_UNIT, "Celsius")
                            .apply()
                    }
                )

                TemperatureUnitOption(
                    label = "Fahrenheit",
                    selected = temperatureUnit == "Fahrenheit",
                    onSelected = {
                        // saves Fahrenheit as the selected temperature unit
                        temperatureUnit = "Fahrenheit"
                        sharedPreferences.edit()
                            .putString(KEY_TEMPERATURE_UNIT, "Fahrenheit")
                            .apply()
                    }
                )
            }
        }

        item {
            SettingsSectionCard(title = "Thresholds") {
                Text(
                    text = "Dry soil threshold: ${drySoilThreshold.toInt()}%",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )

                Text(
                    text = "Plants below this humidity level will be marked as dry.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Slider(
                    value = drySoilThreshold,
                    onValueChange = { newValue ->
                        // updates and saves the dry soil threshold
                        drySoilThreshold = newValue
                        sharedPreferences.edit()
                            .putInt(KEY_DRY_SOIL_THRESHOLD, newValue.toInt())
                            .apply()
                    },
                    valueRange = 10f..60f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Full tank threshold: ${fullTankThreshold.toInt()}%",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )

                Text(
                    text = "Tank levels at or above this value will show a full bucket.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Slider(
                    value = fullTankThreshold,
                    onValueChange = { newValue ->
                        // updates and saves the full tank threshold
                        fullTankThreshold = newValue
                        sharedPreferences.edit()
                            .putInt(KEY_FULL_TANK_THRESHOLD, newValue.toInt())
                            .apply()
                    },
                    valueRange = 50f..100f
                )
            }
        }

        item {
            SettingsSectionCard(title = "Notifications") {
                SettingsSwitchRow(
                    label = "Enable notifications",
                    description = "Allow the app to send plant care alerts.",
                    checked = notificationsEnabled,
                    onCheckedChange = { checked ->
                        // saves the main notification setting
                        notificationsEnabled = checked
                        sharedPreferences.edit()
                            .putBoolean(KEY_NOTIFICATIONS_ENABLED, checked)
                            .apply()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitchRow(
                    label = "Low humidity alerts",
                    description = "Notify when soil humidity is below the dry threshold.",
                    checked = lowHumidityAlerts,
                    enabled = notificationsEnabled,
                    onCheckedChange = { checked ->
                        // saves the low humidity alert setting
                        lowHumidityAlerts = checked
                        sharedPreferences.edit()
                            .putBoolean(KEY_LOW_HUMIDITY_ALERTS, checked)
                            .apply()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitchRow(
                    label = "Low tank alerts",
                    description = "Notify when the water tank level is low.",
                    checked = lowTankAlerts,
                    enabled = notificationsEnabled,
                    onCheckedChange = { checked ->
                        // saves the low tank alert setting
                        lowTankAlerts = checked
                        sharedPreferences.edit()
                            .putBoolean(KEY_LOW_TANK_ALERTS, checked)
                            .apply()
                    }
                )
            }
        }

        item {
            // button used to return to the main dashboard
            Button(
                onClick = onBackClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = "Back to dashboard")
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SettingsHeader() {
    // green header shown at the top of the settings screen
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF4E8F45))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "App preferences and configuration",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    // reusable card used for each settings section
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1D1D)
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun TemperatureUnitOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    // radio button row used to choose the temperature unit
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected
        )

        Text(
            text = label,
            fontSize = 16.sp,
            color = Color(0xFF333333)
        )
    }
}

@Composable
fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    // reusable row for settings that can be turned on or off
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color(0xFF111111) else Color(0xFF999999)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = if (enabled) Color(0xFF666666) else Color(0xFFAAAAAA)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}