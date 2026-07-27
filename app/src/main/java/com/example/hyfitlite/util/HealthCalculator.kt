package com.example.hyfitlite.util

import chipsea.bias.v235.CSBiasAPI
import com.example.hyfitlite.model.HealthMetrics
import com.example.hyfitlite.model.UserProfile
import kotlin.math.roundToInt

object HealthCalculator {

    fun calculateMetrics(
        weightKg: Double,
        impedance: Int,
        profile: UserProfile
    ): HealthMetrics {
        if (weightKg <= 0.0) return HealthMetrics(weightKg = weightKg, impedance = impedance)

        val weightDec = (weightKg * 10).roundToInt()
        val sexParam = if (profile.gender == 1) 1 else 0

        return try {
            val resp = CSBiasAPI.cs_bias_v235(
                sexParam,
                profile.heightCm,
                weightDec,
                profile.age,
                impedance,
                0, // mode = standard
                0  // vcode = 0
            )

            if (resp != null && resp.data != null) {
                val data = resp.data
                val fatFreeKg = weightKg * (1.0 - (data.BFP / 100.0))
                val figure = determineFigureType(data.BMI, data.BFP)

                HealthMetrics(
                    timestamp = System.currentTimeMillis(),
                    weightKg = weightKg,
                    impedance = impedance,
                    bmi = (data.BMI * 10).roundToInt() / 10.0,
                    bodyFatPct = (data.BFP * 10).roundToInt() / 10.0,
                    waterPct = (data.BWP * 10).roundToInt() / 10.0,
                    skeletalMusclePct = (data.SMM * 10).roundToInt() / 10.0,
                    muscleMassKg = (data.SLM * 10).roundToInt() / 10.0,
                    boneMassKg = (data.BMC * 10).roundToInt() / 10.0,
                    visceralFat = (data.VFR * 10).roundToInt() / 10.0,
                    bmrKcal = data.BMR,
                    proteinPct = (data.PP * 10).roundToInt() / 10.0,
                    physicalAge = data.MA,
                    physicalScore = data.SBC,
                    fatFreeWeightKg = (fatFreeKg * 10).roundToInt() / 10.0,
                    figureType = figure
                )
            } else {
                fallbackCalculate(weightKg, impedance, profile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackCalculate(weightKg, impedance, profile)
        }
    }

    private fun fallbackCalculate(
        weightKg: Double,
        impedance: Int,
        profile: UserProfile
    ): HealthMetrics {
        val heightM = profile.heightCm / 100.0
        val bmi = if (heightM > 0) weightKg / (heightM * heightM) else 0.0
        val fatPct = if (profile.gender == 1) (1.20 * bmi) + (0.23 * profile.age) - 16.2 else (1.20 * bmi) + (0.23 * profile.age) - 5.4
        val fatFreeKg = weightKg * (1.0 - (fatPct / 100.0))

        return HealthMetrics(
            timestamp = System.currentTimeMillis(),
            weightKg = weightKg,
            impedance = impedance,
            bmi = (bmi * 10).roundToInt() / 10.0,
            bodyFatPct = (fatPct.coerceAtLeast(5.0) * 10).roundToInt() / 10.0,
            waterPct = 60.0,
            skeletalMusclePct = 40.0,
            muscleMassKg = (weightKg * 0.75 * 10).roundToInt() / 10.0,
            boneMassKg = (weightKg * 0.04 * 10).roundToInt() / 10.0,
            visceralFat = 5.0,
            bmrKcal = (10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + 5).toInt(),
            proteinPct = 18.0,
            physicalAge = profile.age,
            physicalScore = 85,
            fatFreeWeightKg = (fatFreeKg * 10).roundToInt() / 10.0,
            figureType = "Standard"
        )
    }

    private fun determineFigureType(bmi: Double, bfp: Double): String {
        return when {
            bmi < 18.5 && bfp < 15.0 -> "Thin / Skinny"
            bmi < 18.5 -> "Underweight"
            bmi in 18.5..24.9 && bfp < 18.0 -> "Muscular / Athletic"
            bmi in 18.5..24.9 -> "Standard / Fit"
            bmi >= 25.0 && bfp >= 28.0 -> "Obese"
            bmi >= 25.0 -> "Overweight"
            else -> "Standard"
        }
    }
}
