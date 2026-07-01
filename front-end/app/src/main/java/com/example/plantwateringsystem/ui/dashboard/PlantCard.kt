package com.example.plantwateringsystem.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantwateringsystem.data.PlantReading

@Composable
fun PlantCard(
    plant: PlantReading,
    onPlantClicked: (PlantReading) -> Unit
) {
    // keeps track of whether the extra plant details are visible
    var isExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable {
                onPlantClicked(plant)
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlantAvatar(plantName = plant.plantName)

                Spacer(modifier = Modifier.width(14.dp))

                // main plant name and small instruction text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plant.plantName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )

                    Text(
                        text = "Tap card to open plant page",
                        fontSize = 12.sp,
                        color = Color(0xFF777777)
                    )
                }

                StatusChip(humidity = plant.soilHumidity)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Soil humidity",
                fontSize = 18.sp,
                color = Color(0xFF555555)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // shows the humidity using both the droplet bar and percentage text
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = humidityTextColor(plant.soilHumidity)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            RelativeLastWateredRow(
                lastWateredTimeMillis = plant.lastWateredTimeMillis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // button used to show or hide the extra plant information
            TextButton(
                onClick = {
                    isExpanded = !isExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isExpanded) {
                        "Hide plant information"
                    } else {
                        "Show plant information"
                    }
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = Color(0xFFE6E6E6))

                Spacer(modifier = Modifier.height(16.dp))

                // extra information shown only when the card is expanded
                WaterTankRow(waterTank = plant.waterTank)

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(
                    label = "Temperature",
                    value = "${plant.temperature}°C"
                )
            }
        }
    }
}