package com.mealsnap.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import com.mealsnap.ui.theme.extras
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

fun formatKcal(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** Accent colour used for a meal's dot and photo placeholder. */
fun MealType.accent(): Color = when (this) {
    MealType.BREAKFAST -> Color(0xFFF2A100)
    MealType.LUNCH -> Color(0xFF00929E)
    MealType.DINNER -> Color(0xFF5B5FD6)
    MealType.SNACK -> Color(0xFFD6477E)
}

/** White rounded card on the grey canvas; clickable when [onClick] is given. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color) { Column(content = content) }
    } else {
        Surface(modifier = modifier, shape = shape, color = color) { Column(content = content) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

/** A full-circle progress ring with a gradient sweep and rounded end, like Fitbit's activity rings. */
@Composable
fun GradientRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    startColor: Color = MaterialTheme.extras.ringStart,
    endColor: Color = MaterialTheme.extras.ringEnd,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "ring",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke),
            )
            if (animated > 0.001f) {
                rotate(-90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to startColor,
                            animated to endColor,
                            1f to endColor,
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                }
                // Round caps drawn by hand: a stroked cap at the start would pick up the gradient's
                // wrapped-around end colour and show as a dark blob.
                val radius = diameter / 2f
                drawCircle(startColor, radius = stroke / 2f, center = Offset(center.x, center.y - radius))
                val endAngle = Math.toRadians(-90.0 + 360.0 * animated)
                drawCircle(
                    endColor,
                    radius = stroke / 2f,
                    center = Offset(
                        center.x + radius * cos(endAngle).toFloat(),
                        center.y + radius * sin(endAngle).toFloat(),
                    ),
                )
            }
        }
        content()
    }
}

/** The big calorie ring at the top of Today. */
@Composable
fun CalorieHero(consumed: Int, goal: Int, modifier: Modifier = Modifier) {
    val extras = MaterialTheme.extras
    val over = consumed > goal
    val remaining = goal - consumed

    FitCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GradientRing(
                progress = DayStats.progress(consumed, goal),
                modifier = Modifier.size(224.dp),
                strokeWidth = 22.dp,
                startColor = if (over) extras.ringOver else extras.ringStart,
                endColor = if (over) extras.ringOver else extras.ringEnd,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Calories eaten",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatKcal(consumed), style = MaterialTheme.typography.displayMedium)
                    Text(
                        "of ${formatKcal(goal)} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    if (remaining >= 0) "${formatKcal(remaining)} kcal left" else "${formatKcal(-remaining)} kcal over",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Small ring tile for one macro: protein, carbs or fat. */
@Composable
fun MacroTile(
    label: String,
    grams: Int,
    targetGrams: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = if (targetGrams > 0) grams.toFloat() / targetGrams else 0f
    FitCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GradientRing(
                progress = progress,
                modifier = Modifier.size(68.dp),
                strokeWidth = 8.dp,
                startColor = color,
                endColor = color,
            ) {
                Text(
                    "${min(999, (progress * 100).roundToInt())}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("$grams g", style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One meal line: thumbnail, name, time and macros, calories on the right. */
@Composable
fun MealRow(meal: Meal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = Instant.ofEpochMilli(meal.createdAt).atZone(ZoneId.systemDefault()).toLocalTime()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (meal.photoPath != null) {
            AsyncImage(
                model = File(meal.photoPath),
                contentDescription = meal.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(meal.type.accent().copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    meal.type.label.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = meal.type.accent(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(time)} · " +
                    "P ${meal.proteinG} · C ${meal.carbsG} · F ${meal.fatG}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatKcal(meal.calories), style = MaterialTheme.typography.titleLarge)
            Text(
                "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A card per meal type (Breakfast, Lunch, …) listing that type's meals, like a food log. */
@Composable
fun MealGroupCard(
    type: MealType,
    meals: List<Meal>,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FitCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(type.accent(), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${formatKcal(meals.sumOf { it.calories })} kcal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        meals.forEachIndexed { index, meal ->
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            MealRow(meal = meal, onClick = { onOpenMeal(meal.id) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Seven pill-shaped bars (oldest to today) with a dashed goal line. */
@Composable
fun WeekChart(
    data: List<Pair<LocalDate, Int>>,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val extras = MaterialTheme.extras
    val goalColor = MaterialTheme.colorScheme.outline
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxValue = max(max(goal, data.maxOfOrNull { it.second } ?: 0), 1) * 1.15f

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / data.size
            val barWidth = min(slot * 0.46f, 28.dp.toPx())
            data.forEachIndexed { index, (_, kcal) ->
                val x = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.7f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
                if (kcal > 0) {
                    val barHeight = max(barWidth, size.height * kcal / maxValue)
                    val over = kcal > goal
                    val top = if (over) extras.ringOver else extras.ringStart
                    val bottom = if (over) extras.ringOver else extras.ringEnd
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(top, bottom),
                            startY = size.height - barHeight,
                            endY = size.height,
                        ),
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2),
                        alpha = if (index == data.lastIndex) 1f else 0.8f,
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
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEachIndexed { index, (date, _) ->
                val isToday = index == data.lastIndex
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
