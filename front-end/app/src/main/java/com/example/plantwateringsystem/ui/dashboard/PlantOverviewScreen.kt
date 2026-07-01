package com.example.plantwateringsystem.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
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
fun PlantOverviewScreen(
    plants: List<PlantReading>,
    onPlantClicked: (PlantReading) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OverviewHeader()
        }

        item {
            OverviewIntroCard(plants = plants)
        }

        // displays one overview row for each plant
        items(plants) { plant ->
            PlantOverviewRow(
                plant = plant,
                onPlantClicked = onPlantClicked
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun OverviewHeader() {
    // green header shown at the top of the overview screen
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF4E8F45))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column {
            Text(
                text = "Plant Overview",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "All monitored plants",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun OverviewIntroCard(plants: List<PlantReading>) {
    // counts how many plants have low soil humidity
    val dryPlants = plants.count { plant ->
        plant.soilHumidity < 30
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFFF3F8EF)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Overview Summary",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1D1D)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // summary values for the overview screen
            SummaryRow(label = "Total plants", value = plants.size.toString())
            SummaryRow(label = "Plants needing water", value = dryPlants.toString())
        }
    }
}

@Composable
fun PlantOverviewRow(
    plant: PlantReading,
    onPlantClicked: (PlantReading) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable {
                onPlantClicked(plant)
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlantAvatar(plantName = plant.plantName)

            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

            // main overview information for this plant
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = plant.plantName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Humidity: ${plant.soilHumidity}%  |  Tank: ${plant.waterTank}%",
                    fontSize = 15.sp,
                    color = Color(0xFF555555)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tap to open details",
                    fontSize = 13.sp,
                    color = Color(0xFF777777)
                )
            }

            StatusChip(humidity = plant.soilHumidity)
        }
    }
}