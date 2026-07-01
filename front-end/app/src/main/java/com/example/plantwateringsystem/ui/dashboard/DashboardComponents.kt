package com.example.plantwateringsystem.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantwateringsystem.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PlantSearchBar(
    searchText: String,
    onSearchTextChanged: (String) -> Unit
) {
    // search input used to filter the plant list by name.
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchTextChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = {
            Text(text = "Search plant name")
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun SummaryRow(label: String, value: String) {
    // row for displaying a summary label and its corresponding value.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color(0xFF666666)
        )

        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2E7D32)
        )
    }
}

@Composable
fun PlantAvatar(plantName: String) {
    // circle showing the first letter of the plant name
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color(0xFFDCEFD4)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = plantName.first().uppercase(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3E7D3A)
        )
    }
}

@Composable
fun StatusChip(humidity: Int) {
    // Determine the plant status based on humidity level
    val label = when {
        humidity < 30 -> "Dry"
        humidity < 60 -> "Medium"
        else -> "Healthy"
    }

    // Background color changes depending on how healthy the humidity level is
    val containerColor = when {
        humidity < 30 -> Color(0xFFFBE4E2)
        humidity < 60 -> Color(0xFFF8EBCF)
        else -> Color(0xFFDDEFD5)
    }

    // Text color matches the severity of the humidity condition
    val labelColor = when {
        humidity < 30 -> Color(0xFF9C1C16)
        humidity < 60 -> Color(0xFFB26A00)
        else -> Color(0xFF2E7D32)
    }

    AssistChip(
        onClick = { },
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}

@Composable
fun HumidityDropletBar(
    humidityPercentage: Int,
    modifier: Modifier = Modifier
) {
    // Convert the humidity percentage into a value from 0 to 10 droplets
    val filledCount = (humidityPercentage / 10f).roundToInt().coerceIn(0, 10)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Display 10 droplets, filling only the amount that represents the humidity percentage.
        repeat(10) { index ->
            val imageRes = if (index < filledCount) {
                R.drawable.drop_filled
            } else {
                R.drawable.drop_empty
            }

            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .padding(end = 4.dp)
            )
        }
    }
}

@Composable
fun WaterTankRow(waterTank: Int) {
    // when bucket is full use a buket thats full, empty otherwhise
    val bucketRes = if (waterTank >= 70) {
        R.drawable.bucket_of_water_detail
    } else {
        R.drawable.bucket_detail
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Water tank",
            fontSize = 17.sp,
            color = Color(0xFF555555),
            modifier = Modifier.width(110.dp)
        )

        Image(
            painter = painterResource(id = bucketRes),
            contentDescription = "Water tank bucket",
            modifier = Modifier.size(34.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "$waterTank%",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = tankTextColor(waterTank)
        )
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF1D1D1D)
) {
    // Generic row used for plant details like humidity, tank level, or last watered time
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = Color(0xFF555555)
        )

        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.wrapContentWidth()
        )
    }
}

@Composable
fun RelativeLastWateredRow(
    lastWateredTimeMillis: Long
) {
    // store the current time so the relative text can update automatically.
    var currentTimeMillis by remember {
        mutableStateOf(System.currentTimeMillis())
    }

    // refresh the current time every minute so labels like "5 minutes ago" stay updated.
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    InfoRow(
        label = "Last watered",
        value = formatRelativeLastWateredTime(
            lastWateredTimeMillis = lastWateredTimeMillis,
            currentTimeMillis = currentTimeMillis
        )
    )
}

fun formatRelativeLastWateredTime(
    lastWateredTimeMillis: Long,
    currentTimeMillis: Long
): String {
    // Make sure elapsed time never becomes negative, even if the device clock changes.
    val elapsedMillis = (currentTimeMillis - lastWateredTimeMillis).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L

    // Convert elapsed minutes into a user-friendly relative time message.
    return when {
        elapsedMinutes < 1 -> "Just now"

        elapsedMinutes == 1L -> "1 minute ago"

        elapsedMinutes < 60 -> "$elapsedMinutes minutes ago"

        elapsedMinutes < 120 -> "1 hour ago"

        elapsedMinutes < 24 * 60 -> {
            val hours = elapsedMinutes / 60
            "$hours hours ago"
        }

        elapsedMinutes < 48 * 60 -> "1 day ago"

        elapsedMinutes < 7 * 24 * 60 -> {
            val days = elapsedMinutes / (24 * 60)
            "$days days ago"
        }

        else -> {
            val weeks = elapsedMinutes / (7 * 24 * 60)
            if (weeks == 1L) {
                "1 week ago"
            } else {
                "$weeks weeks ago"
            }
        }
    }
}

fun humidityTextColor(humidity: Int): Color {
    // Pick a warning color based on the plant humidity level
    return when {
        humidity < 30 -> Color(0xFF9C1C16)
        humidity < 60 -> Color(0xFFB26A00)
        else -> Color(0xFF2E7D32)
    }
}

fun tankTextColor(waterTank: Int): Color {
    // pick a color based on how much water is left in the tank.
    return when {
        waterTank >= 70 -> Color(0xFF2E7D32)
        waterTank >= 30 -> Color(0xFFB26A00)
        else -> Color(0xFF9C1C16)
    }
}