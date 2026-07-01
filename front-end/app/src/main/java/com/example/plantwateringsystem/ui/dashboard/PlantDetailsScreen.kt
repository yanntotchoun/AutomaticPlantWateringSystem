package com.example.plantwateringsystem.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantwateringsystem.data.PlantReading

@Composable
fun PlantDetailsScreen(
    plant: PlantReading,
    onBackClicked: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // green header for the selected plant page
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF4E8F45))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = plant.plantName,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Personalized Plant Page",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 18.sp
                    )
                }
            }
        }

        item {
            // main card with all the detailed plant information
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlantAvatar(plantName = plant.plantName)

                        Spacer(modifier = Modifier.width(14.dp))

                        // plant name and short status based on soil humidity
                        Column {
                            Text(
                                text = plant.plantName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111111)
                            )

                            Text(
                                text = plantStatusMessage(plant.soilHumidity),
                                fontSize = 15.sp,
                                color = humidityTextColor(plant.soilHumidity)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "Soil humidity",
                        fontSize = 18.sp,
                        color = Color(0xFF555555)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // shows humidity using droplets and the exact percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HumidityDropletBar(
                            humidityPercentage = plant.soilHumidity,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${plant.soilHumidity}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = humidityTextColor(plant.soilHumidity)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Divider(color = Color(0xFFE6E6E6))

                    Spacer(modifier = Modifier.height(20.dp))

                    // basic sensor information from the plant reading
                    WaterTankRow(waterTank = plant.waterTank)

                    Spacer(modifier = Modifier.height(14.dp))

                    InfoRow(
                        label = "Temperature",
                        value = "${plant.temperature}°C"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    RelativeLastWateredRow(
                        lastWateredTimeMillis = plant.lastWateredTimeMillis
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Divider(color = Color(0xFFE6E6E6))

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Plant recommendation",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // recommendation changes depending on humidity and tank level
                    Text(
                        text = plantRecommendation(plant),
                        fontSize = 16.sp,
                        color = Color(0xFF555555)
                    )
                }
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

fun plantStatusMessage(humidity: Int): String {
    // short message shown under the plant name
    return when {
        humidity < 30 -> "This plant needs water soon."
        humidity < 60 -> "This plant has medium soil humidity."
        else -> "This plant has healthy soil humidity."
    }
}

fun plantRecommendation(plant: PlantReading): String {
    // gives a simple recommendation based on the current plant values
    return when {
        plant.soilHumidity < 30 -> {
            "${plant.plantName} is currently dry. Check the water tank and consider watering this plant soon."
        }

        plant.waterTank < 30 -> {
            "The soil humidity is acceptable, but the water tank level is low. Refill the tank soon."
        }

        else -> {
            "${plant.plantName} looks stable. Keep monitoring the soil humidity and water tank level."
        }
    }
}