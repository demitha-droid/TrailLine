package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TrailLineDarkColorScheme = darkColorScheme(
    primary = TrailBlazeOrange,
    onPrimary = Color(0xFF1A0902),
    primaryContainer = TrailBlazeContainerDark,
    onPrimaryContainer = TrailBlazeOrangeLight,
    secondary = SignalMint,
    onSecondary = Color(0xFF032114),
    secondaryContainer = SignalMintContainerDark,
    onSecondaryContainer = SignalMint,
    tertiary = CompassCyan,
    onTertiary = Color(0xFF031E2B),
    tertiaryContainer = CompassCyanContainerDark,
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = ObsidianPineBg,
    onBackground = TopoMistText,
    surface = ObsidianPineSurface,
    onSurface = TopoMistText,
    surfaceVariant = ObsidianPineSurfaceHigh,
    onSurfaceVariant = TopoSageMuted,
    surfaceContainerLowest = ObsidianPineBg,
    surfaceContainerLow = ObsidianPineSurface,
    surfaceContainer = ObsidianPineSurfaceContainer,
    surfaceContainerHigh = ObsidianPineSurfaceHigh,
    surfaceContainerHighest = Color(0xFF273F35),
    outline = ObsidianPineOutline,
    outlineVariant = ObsidianPineOutlineVariant,
    error = AlertRed,
    onError = Color(0xFF2A0406),
    errorContainer = AlertRedContainerDark,
    onErrorContainer = Color(0xFFFFB3B6)
)

private val TrailLineLightColorScheme = lightColorScheme(
    primary = TrailBlazeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3E1102),
    secondary = ForestPinePrimary,
    onSecondary = Color.White,
    secondaryContainer = ForestPineContainer,
    onSecondaryContainer = Color(0xFF072116),
    tertiary = Color(0xFF0369A1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF082F49),
    background = DaylightPaperBg,
    onBackground = DaylightInk,
    surface = DaylightSurface,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurfaceContainer,
    onSurfaceVariant = DaylightInkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightSurfaceContainer,
    surfaceContainerHigh = DaylightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD0DFD6),
    outline = DaylightOutline,
    outlineVariant = DaylightOutlineVariant,
    error = AlertRedLight,
    onError = Color.White,
    errorContainer = AlertRedContainerLight,
    onErrorContainer = Color(0xFF410002)
)

val TrailLineShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TrailLineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TrailLineDarkColorScheme else TrailLineLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrailLineTypography,
        shapes = TrailLineShapes,
        content = content
    )
}
