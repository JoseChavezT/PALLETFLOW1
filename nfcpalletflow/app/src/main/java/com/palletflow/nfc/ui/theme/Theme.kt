package com.palletflow.nfc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1D5C8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E6F5),
    onPrimaryContainer = Color(0xFF0B2C46),
    secondary = Color(0xFF3F6F68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3ECE7),
    onSecondaryContainer = Color(0xFF0F302B),
    tertiary = Color(0xFF8A5A1F),
    tertiaryContainer = Color(0xFFF8E3C8),
    onTertiaryContainer = Color(0xFF3A2404),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF151B22),
    surface = Color(0xFFF6F8FB),
    onSurface = Color(0xFF151B22),
    surfaceVariant = Color(0xFFE2E8EF),
    onSurfaceVariant = Color(0xFF47515C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F7),
    surfaceContainer = Color(0xFFEAEFF4),
    surfaceContainerHigh = Color(0xFFE4EAF0),
    surfaceContainerHighest = Color(0xFFDDE4EB),
    outline = Color(0xFF76808B),
    outlineVariant = Color(0xFFC6CED7),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DC9F0),
    onPrimary = Color(0xFF07304D),
    primaryContainer = Color(0xFF1F4868),
    onPrimaryContainer = Color(0xFFD5E6F5),
    secondary = Color(0xFFA6D0C8),
    onSecondary = Color(0xFF0F302B),
    secondaryContainer = Color(0xFF284E48),
    onSecondaryContainer = Color(0xFFD3ECE7),
    tertiary = Color(0xFFEDC08A),
    tertiaryContainer = Color(0xFF5C3C10),
    onTertiaryContainer = Color(0xFFF8E3C8),
    background = Color(0xFF0E1419),
    onBackground = Color(0xFFE1E6EC),
    surface = Color(0xFF0E1419),
    onSurface = Color(0xFFE1E6EC),
    surfaceVariant = Color(0xFF39434D),
    onSurfaceVariant = Color(0xFFBCC5CF),
    surfaceContainerLowest = Color(0xFF090D11),
    surfaceContainerLow = Color(0xFF151C22),
    surfaceContainer = Color(0xFF192128),
    surfaceContainerHigh = Color(0xFF212A32),
    surfaceContainerHighest = Color(0xFF2B353E),
    outline = Color(0xFF86909A),
    outlineVariant = Color(0xFF39434D),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun NfcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
