package com.mealsnap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours beyond the Material set: ring gradient and the per-macro accents. */
@Immutable
data class MealSnapExtras(
    val ringStart: Color,
    val ringEnd: Color,
    val ringOver: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
)

private val LightExtras = MealSnapExtras(
    ringStart = Color(0xFF2BD4DB),
    ringEnd = Color(0xFF00929E),
    ringOver = Color(0xFFF2711C),
    protein = Color(0xFF3D7BF7),
    carbs = Color(0xFFF2A100),
    fat = Color(0xFFD6477E),
)

private val DarkExtras = MealSnapExtras(
    ringStart = Color(0xFF7BE8EE),
    ringEnd = Color(0xFF2BC4CF),
    ringOver = Color(0xFFFF9A55),
    protein = Color(0xFF7CA6FF),
    carbs = Color(0xFFFFC445),
    fat = Color(0xFFFF7FAF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4F8),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEEF2),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF3D7BF7),
    background = Color(0xFFF1F4F6),
    onBackground = Color(0xFF171C1F),
    surface = Color.White,
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFE3EAED),
    onSurfaceVariant = Color(0xFF586369),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEBF0F2),
    surfaceContainerHighest = Color(0xFFE3EAED),
    outline = Color(0xFF8A969C),
    outlineVariant = Color(0xFFD5DEE2),
    error = Color(0xFFD93025),
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF5C0F0A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD6E3),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFCDF3F7),
    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCDE7EC),
    tertiary = Color(0xFF7CA6FF),
    background = Color(0xFF0E1215),
    onBackground = Color(0xFFE4E9EC),
    surface = Color(0xFF181D21),
    onSurface = Color(0xFFE4E9EC),
    surfaceVariant = Color(0xFF2A3136),
    onSurfaceVariant = Color(0xFFA3AFB5),
    surfaceContainerLowest = Color(0xFF0E1215),
    surfaceContainerLow = Color(0xFF181D21),
    surfaceContainer = Color(0xFF181D21),
    surfaceContainerHigh = Color(0xFF232A2E),
    surfaceContainerHighest = Color(0xFF2A3136),
    outline = Color(0xFF6F7C82),
    outlineVariant = Color(0xFF394247),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val MealSnapShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()
private val MealSnapTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val LocalExtras = staticCompositionLocalOf { LightExtras }

val MaterialTheme.extras: MealSnapExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalExtras.current

/**
 * A clean, Fitbit-inspired look: teal rings on a soft grey canvas with white rounded cards.
 * A fixed palette is used on purpose (no wallpaper colours) so the app always looks the same.
 */
@Composable
fun MealSnapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MealSnapTypography,
            shapes = MealSnapShapes,
            content = content,
        )
    }
}
