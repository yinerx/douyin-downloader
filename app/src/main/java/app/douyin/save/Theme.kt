package app.douyin.save

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Iris" palette: a calm indigo accent on soft lavender-tinted neutrals.
// Deliberately far from Douyin's own red / cyan / black branding.

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E3FF),
    onPrimaryContainer = Color(0xFF1C1773),
    secondary = Color(0xFF7C5CDB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE7FF),
    onSecondaryContainer = Color(0xFF2A1A66),
    tertiary = Color(0xFF0E8F7E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF17161F),
    surface = Color(0xFFF6F5FB),
    onSurface = Color(0xFF17161F),
    surfaceVariant = Color(0xFFEDEBF5),
    onSurfaceVariant = Color(0xFF65627A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFF8),
    surfaceContainerHigh = Color(0xFFEBE9F4),
    surfaceContainerHighest = Color(0xFFE4E2EF),
    outline = Color(0xFFC9C6D8),
    outlineVariant = Color(0xFFE6E4EF),
    error = Color(0xFFC4321F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E5),
    onErrorContainer = Color(0xFF6B140A),
    inverseSurface = Color(0xFF2B2A35),
    inverseOnSurface = Color(0xFFF2F1F8),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9A4FF),
    onPrimary = Color(0xFF1A1564),
    primaryContainer = Color(0xFF2F2A86),
    onPrimaryContainer = Color(0xFFE5E3FF),
    secondary = Color(0xFFC6B4FF),
    onSecondary = Color(0xFF2A1A66),
    secondaryContainer = Color(0xFF3A2C78),
    onSecondaryContainer = Color(0xFFEDE7FF),
    tertiary = Color(0xFF5FD8C4),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF0F0F15),
    onBackground = Color(0xFFECEBF4),
    surface = Color(0xFF0F0F15),
    onSurface = Color(0xFFECEBF4),
    surfaceVariant = Color(0xFF24232E),
    onSurfaceVariant = Color(0xFFA3A0B6),
    surfaceContainerLowest = Color(0xFF0B0B10),
    surfaceContainerLow = Color(0xFF18171F),
    surfaceContainer = Color(0xFF1D1C25),
    surfaceContainerHigh = Color(0xFF25242E),
    surfaceContainerHighest = Color(0xFF2E2D38),
    outline = Color(0xFF4A4858),
    outlineVariant = Color(0xFF2A2934),
    error = Color(0xFFFF8E7E),
    onError = Color(0xFF5C1008),
    errorContainer = Color(0xFF3D1712),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFECEBF4),
    inverseOnSurface = Color(0xFF2B2A35),
    scrim = Color(0xFF000000),
)

/** Colors Material 3 has no slot for: success states and the brand gradient. */
@Immutable
internal data class ExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val brandStart: Color,
    val brandEnd: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF137A57),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDDF4E9),
    onSuccessContainer = Color(0xFF053D2A),
    brandStart = Color(0xFF5B4FF0),
    brandEnd = Color(0xFF9B5CF0),
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF5ED6A4),
    onSuccess = Color(0xFF003824),
    successContainer = Color(0xFF113528),
    onSuccessContainer = Color(0xFFC9F2DE),
    brandStart = Color(0xFF5B4FF0),
    brandEnd = Color(0xFF9B5CF0),
)

internal val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
internal fun DouyinSaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

internal val extraColors: ExtraColors
    @Composable get() = LocalExtraColors.current
