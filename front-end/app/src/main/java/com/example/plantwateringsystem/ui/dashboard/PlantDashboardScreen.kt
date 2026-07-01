package com.example.plantwateringsystem.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantwateringsystem.data.PlantReading
import kotlin.math.roundToInt

enum class BottomNavScreen {
    Dashboard,
    Overview,
    Settings
}

@Composable
fun PlantDashboardScreen() {
    // saves the current time so the sample plant data stays consistent
    val now = remember {
        System.currentTimeMillis()
    }

    // temporary plant data used to display the dashboard
    val plants = listOf(
        PlantReading(
            plantName = "Basil",
            soilHumidity = 72,
            waterTank = 70,
            temperature = 23,
            lastWateredTimeMillis = now - 10 * 60 * 1000L
        ),
        PlantReading(
            plantName = "Tomato",
            soilHumidity = 38,
            waterTank = 40,
            temperature = 24,
            lastWateredTimeMillis = now - 2 * 60 * 60 * 1000L
        ),
        PlantReading(
            plantName = "Mint",
            soilHumidity = 19,
            waterTank = 20,
            temperature = 22,
            lastWateredTimeMillis = now - 2 * 24 * 60 * 60 * 1000L
        )
    )

    // keeps track of which bottom navigation screen is selected
    var currentScreen by remember {
        mutableStateOf(BottomNavScreen.Dashboard)
    }

    // when a plant is selected, the app shows the details screen
    var selectedPlant by remember {
        mutableStateOf<PlantReading?>(null)
    }

    if (selectedPlant != null) {
        PlantDetailsScreen(
            plant = selectedPlant!!,
            onBackClicked = {
                selectedPlant = null
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                AppBottomNavigationBar(
                    currentScreen = currentScreen,
                    onScreenSelected = { selectedScreen ->
                        currentScreen = selectedScreen
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // displays the selected screen from the bottom navigation bar
                when (currentScreen) {
                    BottomNavScreen.Dashboard -> {
                        DashboardContent(
                            plants = plants,
                            onPlantClicked = { clickedPlant ->
                                selectedPlant = clickedPlant
                            }
                        )
                    }

                    BottomNavScreen.Overview -> {
                        PlantOverviewScreen(
                            plants = plants,
                            onPlantClicked = { clickedPlant ->
                                selectedPlant = clickedPlant
                            }
                        )
                    }

                    BottomNavScreen.Settings -> {
                        SettingsScreen(
                            onBackClicked = {
                                currentScreen = BottomNavScreen.Dashboard
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    plants: List<PlantReading>,
    onPlantClicked: (PlantReading) -> Unit
) {
    // stores the text typed in the search bar
    var searchText by remember { mutableStateOf("") }

    // filters the plant list based on the search text
    val filteredPlants = plants.filter { plant ->
        plant.plantName.contains(searchText, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection()
        }

        item {
            PlantSearchBar(
                searchText = searchText,
                onSearchTextChanged = { newText ->
                    searchText = newText
                }
            )
        }

        item {
            SummaryCard(plants = filteredPlants)
        }

        if (filteredPlants.isEmpty()) {
            item {
                // message shown when no plant matches the search
                Text(
                    text = "No plants found.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    color = Color(0xFF666666)
                )
            }
        } else {
            items(filteredPlants) { plant ->
                PlantCard(
                    plant = plant,
                    onPlantClicked = { clickedPlant ->
                        onPlantClicked(clickedPlant)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    currentScreen: BottomNavScreen,
    onScreenSelected: (BottomNavScreen) -> Unit
) {
    // bottom navigation used to switch between the main screens
    NavigationBar {
        NavigationBarItem(
            selected = currentScreen == BottomNavScreen.Dashboard,
            onClick = {
                onScreenSelected(BottomNavScreen.Dashboard)
            },
            icon = {
                Text(text = "🏠")
            },
            label = {
                Text(text = "Dashboard")
            }
        )

        NavigationBarItem(
            selected = currentScreen == BottomNavScreen.Overview,
            onClick = {
                onScreenSelected(BottomNavScreen.Overview)
            },
            icon = {
                Text(text = "🌿")
            },
            label = {
                Text(text = "Overview")
            }
        )

        NavigationBarItem(
            selected = currentScreen == BottomNavScreen.Settings,
            onClick = {
                onScreenSelected(BottomNavScreen.Settings)
            },
            icon = {
                Text(text = "⚙")
            },
            label = {
                Text(text = "Settings")
            }
        )
    }
}

@Composable
fun HeaderSection() {
    // green header shown at the top of the dashboard
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF4E8F45))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column {
            Text(
                text = "Plant Dashboard",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Automatic Watering System",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun SummaryCard(plants: List<PlantReading>) {
    // calculates the average humidity for the plants currently shown
    val avgHumidity = if (plants.isNotEmpty()) {
        plants.map { it.soilHumidity }.average().roundToInt()
    } else {
        0
    }

    // calculates the average water tank level for the plants currently shown
    val avgTank = if (plants.isNotEmpty()) {
        plants.map { it.waterTank }.average().roundToInt()
    } else {
        0
    }

    // calculates the average temperature for the plants currently shown
    val avgTemp = if (plants.isNotEmpty()) {
        plants.map { it.temperature }.average().roundToInt()
    } else {
        0
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
                text = "System Summary",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1D1D)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Main dashboard overview",
                fontSize = 15.sp,
                color = Color(0xFF666666)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // summary values based on the filtered plant list
            SummaryRow(label = "Plants shown", value = plants.size.toString())
            SummaryRow(label = "Average soil humidity", value = "$avgHumidity%")
            SummaryRow(label = "Average tank level", value = "$avgTank%")
            SummaryRow(label = "Average temperature", value = "${avgTemp}°C")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PlantDashboardPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F7F4)
        ) {
            PlantDashboardScreen()
        }
    }
}