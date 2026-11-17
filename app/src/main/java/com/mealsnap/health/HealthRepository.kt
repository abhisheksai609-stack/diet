package com.mealsnap.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mealsnap.coach.HealthDay
import com.mealsnap.coach.HealthSnapshot
import com.mealsnap.coach.WeightPoint
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/** Where the person is in connecting their health data. */
enum class HealthStatus { UNAVAILABLE, NEEDS_UPDATE, NEEDS_PERMISSION, CONNECTED }

/** One row of the "data check": how much of a kind of data arrived recently, and from which app. */
data class DataCheckRow(val label: String, val count: Int, val moreAvailable: Boolean, val sources: Set<String>)

/**
 * Reads health data that other apps (the Google Health / Fitbit app for a Fitbit Air, Samsung Health, Garmin,
 * a smart scale, ...) have saved into Android's Health Connect, with the person's permission. Everything stays
 * on the phone: this app never talks to a tracker's cloud, and never writes to Health Connect.
 */
class HealthRepository(private val context: Context) {

    private val read: List<Pair<KClass<out Record>, String>> = listOf(
        StepsRecord::class to "Steps",
        HeartRateRecord::class to "Heart rate",
        RestingHeartRateRecord::class to "Resting heart rate",
        SleepSessionRecord::class to "Sleep",
        ActiveCaloriesBurnedRecord::class to "Active calories",
        TotalCaloriesBurnedRecord::class to "Total calories burned",
        DistanceRecord::class to "Distance",
        ExerciseSessionRecord::class to "Workouts",
        WeightRecord::class to "Weight",
        HeartRateVariabilityRmssdRecord::class to "Heart rate variability",
        OxygenSaturationRecord::class to "Blood oxygen",
    )

    /** The permissions the app asks for (all read-only). */
    val permissions: Set<String> = read.map { HealthPermission.getReadPermission(it.first) }.toSet()

    private fun granted(all: Set<String>, type: KClass<out Record>) = HealthPermission.getReadPermission(type) in all

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.UNAVAILABLE
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (availability() == HealthAvailability.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    suspend fun hasAnyPermission(): Boolean = grantedPermissions().any { it in permissions }

    suspend fun status(): HealthStatus = when (availability()) {
        HealthAvailability.UNAVAILABLE -> HealthStatus.UNAVAILABLE
        HealthAvailability.NEEDS_UPDATE -> HealthStatus.NEEDS_UPDATE
        HealthAvailability.AVAILABLE -> if (hasAnyPermission()) HealthStatus.CONNECTED else HealthStatus.NEEDS_PERMISSION
    }

    /** The last [days] calendar days (oldest first, today last), or null if nothing can be read. */
    suspend fun readSnapshot(days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): HealthSnapshot? {
        if (availability() != HealthAvailability.AVAILABLE) return null
        val grantedSet = client.permissionController.getGrantedPermissions()
        if (grantedSet.none { it in permissions }) return null

        val today = LocalDate.now(zone)
        val week = (days - 1 downTo 0).map { offset -> readDay(today.minusDays(offset.toLong()), grantedSet, zone) }

        val weight = if (granted(grantedSet, WeightRecord::class)) latestWeightKg(zone) else null
        val hrv = if (granted(grantedSet, HeartRateVariabilityRmssdRecord::class)) latestHrv() else null
        val spo2 = if (granted(grantedSet, OxygenSaturationRecord::class)) latestSpo2() else null
        return HealthSnapshot(today = week.last(), week = week, latestWeightKg = weight, hrvMs = hrv, spo2Percent = spo2)
    }

    private suspend fun readDay(date: LocalDate, grantedSet: Set<String>, zone: ZoneId): HealthDay {
        val now = Instant.now()
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (end <= start) return HealthDay(date)

        val metrics = buildSet {
            if (granted(grantedSet, StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (granted(grantedSet, ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (granted(grantedSet, TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (granted(grantedSet, DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (granted(grantedSet, ExerciseSessionRecord::class)) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
            if (granted(grantedSet, HeartRateRecord::class)) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MIN)
                add(HeartRateRecord.BPM_MAX)
            }
            if (granted(grantedSet, RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
        }
        val result = if (metrics.isEmpty()) null else safely {
            client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end), emptySet()))
        }

        // Sleep ending on this day: from 18:00 the evening before to 14:00 on the day.
        val sleepMinutes = if (granted(grantedSet, SleepSessionRecord::class)) {
            val sleepStart = date.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
            val sleepEnd = minOf(date.atTime(LocalTime.of(14, 0)).atZone(zone).toInstant(), now)
            if (sleepEnd > sleepStart) {
                safely {
                    client.aggregate(
                        AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(sleepStart, sleepEnd), emptySet()),
                    )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.takeIf { it > 0 }
                }
            } else {
                null
            }
        } else {
            null
        }

        return HealthDay(
            date = date,
            steps = result?.get(StepsRecord.COUNT_TOTAL)?.takeIf { it > 0 },
            activeKcal = result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.takeIf { it > 0 },
            totalKcal = result?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.takeIf { it > 0 },
            restingHr = result?.get(RestingHeartRateRecord.BPM_AVG)?.toInt(),
            avgHr = result?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            minHr = result?.get(HeartRateRecord.BPM_MIN)?.toInt(),
            maxHr = result?.get(HeartRateRecord.BPM_MAX)?.toInt(),
            sleepMinutes = sleepMinutes,
            distanceM = result?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.takeIf { it > 0 },
            exerciseMinutes = result?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes()?.takeIf { it > 0 },
        )
    }

    /** Weigh-ins from the last [days] days, oldest first. */
    suspend fun readWeights(days: Long = 90, zone: ZoneId = ZoneId.systemDefault()): List<WeightPoint> {
        if (availability() != HealthAvailability.AVAILABLE) return emptyList()
        if (!granted(client.permissionController.getGrantedPermissions(), WeightRecord::class)) return emptyList()
        val now = Instant.now()
        return safely {
            client.readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minusSeconds(days * 86_400), now), emptySet(), true, 500),
            ).records.map { WeightPoint(it.time.atZone(zone).toLocalDate(), it.weight.inKilograms) }
        } ?: emptyList()
    }

    private suspend fun latestWeightKg(zone: ZoneId): Double? = readWeights(90, zone).lastOrNull()?.kg

    private suspend fun latestHrv(): Double? = safely {
        val now = Instant.now()
        client.readRecords(
            ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(now.minusSeconds(3 * 86_400), now), emptySet(), false, 1),
        ).records.firstOrNull()?.heartRateVariabilityMillis
    }

    private suspend fun latestSpo2(): Double? = safely {
        val now = Instant.now()
        client.readRecords(
            ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(now.minusSeconds(3 * 86_400), now), emptySet(), false, 1),
        ).records.firstOrNull()?.percentage?.value
    }

    /**
     * Shows what has really arrived in Health Connect over the last week and which app wrote it. Handy for
     * checking that a tracker is sharing the data you expect.
     */
    suspend fun dataCheck(): List<DataCheckRow> {
        if (availability() != HealthAvailability.AVAILABLE) return emptyList()
        val grantedSet = client.permissionController.getGrantedPermissions()
        val now = Instant.now()
        val range = TimeRangeFilter.between(now.minusSeconds(7 * 86_400), now)
        return read.filter { granted(grantedSet, it.first) }.map { (type, label) ->
            safely { check(type, label, range) } ?: DataCheckRow(label, 0, false, emptySet())
        }
    }

    private suspend fun <T : Record> check(type: KClass<T>, label: String, range: TimeRangeFilter): DataCheckRow {
        val response = client.readRecords(ReadRecordsRequest(type, range, emptySet(), false, 1000))
        return DataCheckRow(
            label = label,
            count = response.records.size,
            moreAvailable = response.pageToken != null,
            sources = response.records.map { it.metadata.dataOrigin.packageName }.toSet(),
        )
    }

    /** Health Connect can throw if a permission was revoked mid-read or the provider restarts; treat that as "no data". */
    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
