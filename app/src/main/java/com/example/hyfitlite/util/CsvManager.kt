package com.example.hyfitlite.util

import android.content.Context
import android.net.Uri
import com.example.hyfitlite.model.HealthMetrics
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun exportToCsv(context: Context, uri: Uri, records: List<HealthMetrics>): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    // Header
                    writer.write("Timestamp,Date,Weight(kg),Impedance(ohm),BMI,BodyFat(%),Water(%),SkeletalMuscle(%),MuscleMass(kg),BoneMass(kg),VisceralFat,BMR(kcal),Protein(%),PhysicalAge,PhysicalScore,FatFreeWeight(kg),FigureType\n")

                    for (r in records) {
                        val dateStr = dateFormat.format(Date(r.timestamp))
                        val line = "${r.timestamp},$dateStr,${r.weightKg},${r.impedance},${r.bmi},${r.bodyFatPct},${r.waterPct},${r.skeletalMusclePct},${r.muscleMassKg},${r.boneMassKg},${r.visceralFat},${r.bmrKcal},${r.proteinPct},${r.physicalAge},${r.physicalScore},${r.fatFreeWeightKg},\"${r.figureType}\"\n"
                        writer.write(line)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromCsv(context: Context, uri: Uri): List<HealthMetrics> {
        val imported = mutableListOf<HealthMetrics>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val lines = reader.readLines()
                    if (lines.isEmpty()) return emptyList()

                    // Skip header if line 0 starts with "Timestamp" or "Date"
                    val dataLines = if (lines[0].contains("Timestamp", ignoreCase = true) || lines[0].contains("Weight", ignoreCase = true)) {
                        lines.drop(1)
                    } else lines

                    for (line in dataLines) {
                        if (line.isBlank()) continue
                        val tokens = line.split(",").map { it.trim().removeSurrounding("\"") }
                        if (tokens.size >= 3) {
                            try {
                                val ts = tokens[0].toLongOrNull() ?: System.currentTimeMillis()
                                val weight = tokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                                val imp = tokens.getOrNull(3)?.toIntOrNull() ?: 0
                                val bmi = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                                val fat = tokens.getOrNull(5)?.toDoubleOrNull() ?: 0.0
                                val water = tokens.getOrNull(6)?.toDoubleOrNull() ?: 0.0
                                val skMuscle = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0
                                val muscleMass = tokens.getOrNull(8)?.toDoubleOrNull() ?: 0.0
                                val bone = tokens.getOrNull(9)?.toDoubleOrNull() ?: 0.0
                                val vFat = tokens.getOrNull(10)?.toDoubleOrNull() ?: 0.0
                                val bmr = tokens.getOrNull(11)?.toIntOrNull() ?: 0
                                val protein = tokens.getOrNull(12)?.toDoubleOrNull() ?: 0.0
                                val age = tokens.getOrNull(13)?.toIntOrNull() ?: 0
                                val score = tokens.getOrNull(14)?.toIntOrNull() ?: 0
                                val fatFree = tokens.getOrNull(15)?.toDoubleOrNull() ?: 0.0
                                val figure = tokens.getOrNull(16) ?: "Standard"

                                if (weight > 0) {
                                    imported.add(
                                        HealthMetrics(
                                            timestamp = ts,
                                            weightKg = weight,
                                            impedance = imp,
                                            bmi = bmi,
                                            bodyFatPct = fat,
                                            waterPct = water,
                                            skeletalMusclePct = skMuscle,
                                            muscleMassKg = muscleMass,
                                            boneMassKg = bone,
                                            visceralFat = vFat,
                                            bmrKcal = bmr,
                                            proteinPct = protein,
                                            physicalAge = age,
                                            physicalScore = score,
                                            fatFreeWeightKg = fatFree,
                                            figureType = figure
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imported
    }
}
