package com.example.hyfitlite.model

data class HealthMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val weightKg: Double = 0.0,
    val impedance: Int = 0,
    val bmi: Double = 0.0,
    val bodyFatPct: Double = 0.0,
    val waterPct: Double = 0.0,
    val skeletalMusclePct: Double = 0.0,
    val muscleMassKg: Double = 0.0,
    val boneMassKg: Double = 0.0,
    val visceralFat: Double = 0.0,
    val bmrKcal: Int = 0,
    val proteinPct: Double = 0.0,
    val physicalAge: Int = 0,
    val physicalScore: Int = 0,
    val fatFreeWeightKg: Double = 0.0,
    val figureType: String = "Normal"
)

data class UserProfile(
    val nickname: String = "User",
    val gender: Int = 1, // 1 = Male, 0 or 2 = Female
    val heightCm: Int = 175,
    val age: Int = 25,
    val targetWeightKg: Double = 65.0,
    val isMetric: Boolean = true,
    val macAddress: String? = null
)
