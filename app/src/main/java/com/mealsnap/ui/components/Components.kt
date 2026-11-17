package com.mealsnap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

fun formatKcal(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
fun CalorieRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val over = consumed > goal
    val progressColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val progress = DayStats.progress(consumed, goal)

    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(176.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatKcal(consumed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "of ${formatKcal(goal)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MacroStat(label: String, grams: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MealRow(meal: Meal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = java.time.Instant.ofEpochMilli(meal.createdAt).atZone(ZoneId.systemDefault()).toLocalTime()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (meal.photoPath != null) {
                AsyncImage(
                    model = File(meal.photoPath),
                    contentDescription = meal.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(meal.type.label.take(1), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${meal.type.label} · ${formatTime(time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatKcal(meal.calories),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatTime(time: LocalTime): String =
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(time)

@Composable
fun WeekChart(
    data: List<Pair<LocalDate, Int>>,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = (maxOf(goal, data.maxOfOrNull { it.second } ?: 0, 1) * 1.1f)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / data.size
            val barWidth = slot * 0.5f
            data.forEachIndexed { index, (_, kcal) ->
                if (kcal > 0) {
                    val barHeight = size.height * kcal / maxValue
                    drawRoundRect(
                        color = if (kcal > goal) overColor else barColor,
                        topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }
            }
            val goalY = size.height - size.height * goal / maxValue
            drawLine(
                color = goalColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (date, _) ->
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
