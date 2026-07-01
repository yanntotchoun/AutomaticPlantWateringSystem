package com.example.plantwateringsystem.data

data class PlantReading(
    val plantName: String,
    val soilHumidity: Int,
    val waterTank: Int,
    val temperature: Int,
    val lastWateredTimeMillis: Long
)