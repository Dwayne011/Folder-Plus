package com.widgetsplus.ui.frost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Pixel Launcher Plus's frost look for the settings pages: every page over the wallpaper, one
 * see-through card per group, tinted icon tiles and one accent. The dark palette is the
 * launcher's own; the light one, used while the phone is in light mode, is the same design in
 * light ink.
 */
@Immutable
data class FrostColors(
    val dark: Boolean,
    /** Wash over the wallpaper, behind everything. */
    val scrim: Color,
    /** Every card, round button and pill. */
    val fill: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    /** Icons in tiles and buttons. */
    val textSecondary: Color,
    /** Captions and values. */
    val textTertiary: Color,
    /** Chevrons and slider end marks. */
    val textMuted: Color,
    /** An icon tile. */
    val tile: Color,
    val divider: Color,
    val toggleOff: Color,
    val toggleThumbOff: Color,
    val sliderTrack: Color,
    val errorTile: Color,
    val errorInk: Color,
    val errorText: Color,
    /** A row lifted while it's dragged. */
    val raised: Color,
    /** The menu's section tiles: fill and ink for each tone. */
    val tones: Map<Tone, Pair<Color, Color>>,
) {
    val selected: Color get() = accent.copy(alpha = if (dark) 0.18f else 0.12f)
    val selectedBorder: Color get() = accent.copy(alpha = if (dark) 0.40f else 0.35f)

    companion object {
        val Dark = FrostColors(
            dark = true,
            scrim = Color(0xFF06080F).copy(alpha = 0.35f),
            fill = Color(0xFF181D2E).copy(alpha = 0.78f),
            border = Color.White.copy(alpha = 0.10f),
            accent = Color(0xFFC9D8FF),
            onAccent = Color(0xFF0E1A3A),
            textPrimary = Color(0xFFF2F4F8),
            textSecondary = Color(0xFFDDE4F5),
            textTertiary = Color(0xFFB4BCCC),
            textMuted = Color(0xFF8E97AB),
            tile = Color.White.copy(alpha = 0.10f),
            divider = Color.White.copy(alpha = 0.06f),
            toggleOff = Color.White.copy(alpha = 0.18f),
            toggleThumbOff = Color(0xFFB4BCCC),
            sliderTrack = Color.White.copy(alpha = 0.14f),
            errorTile = Color(0xFFFFD9DD),
            errorInk = Color(0xFF3A1410),
            errorText = Color(0xFFFFD9DD),
            raised = Color(0xFF242B3F),
            tones = mapOf(
                Tone.BLUE to (Color(0x29C9D8FF) to Color(0xFFC9D8FF)),
                Tone.VIOLET to (Color(0x29D9D4FF) to Color(0xFFD9D4FF)),
                Tone.GREEN to (Color(0x29C8F0D8) to Color(0xFFC8F0D8)),
                Tone.AMBER to (Color(0x29FFE2C2) to Color(0xFFFFE2C2)),
                Tone.GREY to (Color(0x1AFFFFFF) to Color(0xFFDDE4F5)),
            ),
        )

        val Light = FrostColors(
            dark = false,
            scrim = Color(0xFFEEF1F7).copy(alpha = 0.45f),
            fill = Color(0xFFFBFCFE).copy(alpha = 0.84f),
            border = Color(0xFF0B1A3A).copy(alpha = 0.08f),
            accent = Color(0xFF3B5B92),
            onAccent = Color.White,
            textPrimary = Color(0xFF141925),
            textSecondary = Color(0xFF2D3546),
            textTertiary = Color(0xFF545C6C),
            textMuted = Color(0xFF7C8494),
            tile = Color(0xFF1B2A4A).copy(alpha = 0.07f),
            divider = Color(0xFF0B1A3A).copy(alpha = 0.07f),
            toggleOff = Color(0xFF0B1A3A).copy(alpha = 0.14f),
            toggleThumbOff = Color(0xFF6B7385),
            sliderTrack = Color(0xFF0B1A3A).copy(alpha = 0.12f),
            errorTile = Color(0xFFFFD9DD),
            errorInk = Color(0xFF8C1D18),
            errorText = Color(0xFFB3261E),
            raised = Color(0xFFFFFFFF),
            tones = mapOf(
                Tone.BLUE to (Color(0xFFDCE6FF) to Color(0xFF2B4C8C)),
                Tone.VIOLET to (Color(0xFFE5E0FF) to Color(0xFF4F3F99)),
                Tone.GREEN to (Color(0xFFD3F1DF) to Color(0xFF1F6440)),
                Tone.AMBER to (Color(0xFFFFE6C7) to Color(0xFF7D4A0F)),
                Tone.GREY to (Color(0xFF0B1A3A).copy(alpha = 0.07f) to Color(0xFF2D3546)),
            ),
        )
    }
}

/** The menu's section tiles: a tint per kind of section, so the menu reads at a glance. */
enum class Tone { BLUE, VIOLET, GREEN, AMBER, GREY }

val LocalFrost = staticCompositionLocalOf { FrostColors.Dark }

/** Google Sans where the phone has it (Pixel), the system sans elsewhere. */
val FrostFont: FontFamily = runCatching {
    FontFamily(
        Font(DeviceFontFamilyName("google-sans"), FontWeight.Normal),
        Font(DeviceFontFamilyName("google-sans-medium"), FontWeight.SemiBold),
        Font(DeviceFontFamilyName("google-sans-medium"), FontWeight.Bold),
        Font(DeviceFontFamilyName("google-sans-bold"), FontWeight.ExtraBold),
    )
}.getOrDefault(FontFamily.Default)

/** The launcher's type styles. */
object FrostType {
    val title = TextStyle(fontFamily = FrostFont, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
    val bodyStrong = TextStyle(fontFamily = FrostFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = FrostFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val input = TextStyle(fontFamily = FrostFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = FrostFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    /** Captions and values. */
    val small = TextStyle(fontFamily = FrostFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    /** Section labels: written in capitals by the caller. */
    val overline = TextStyle(fontFamily = FrostFont, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp, letterSpacing = 0.06.em)
}

object FrostShapes {
    val card = RoundedCornerShape(28.dp)
    val inner = RoundedCornerShape(18.dp)
    val icon = RoundedCornerShape(12.dp)
    val menuTile = RoundedCornerShape(13.dp)
    val tile = RoundedCornerShape(14.dp)
    val pill = CircleShape
}

/**
 * The frost colours for the phone's light or dark mode, and a Material scheme to match so
 * dialogs, menus and text fields sit in the same look.
 */
@Composable
fun FrostTheme(content: @Composable () -> Unit) {
    val frost = if (isSystemInDarkTheme()) FrostColors.Dark else FrostColors.Light
    val scheme = if (frost.dark) {
        darkColorScheme(
            primary = frost.accent, onPrimary = frost.onAccent, primaryContainer = frost.accent, onPrimaryContainer = frost.onAccent,
            secondaryContainer = frost.tile, onSecondaryContainer = frost.textPrimary,
            surface = Color(0xFF181D2E), onSurface = frost.textPrimary, onSurfaceVariant = frost.textTertiary,
            surfaceContainer = Color(0xFF1E2436), surfaceContainerHigh = Color(0xFF242B3F), surfaceContainerHighest = Color(0xFF2B3349),
            background = Color(0xFF06080F), onBackground = frost.textPrimary, outline = frost.textMuted,
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = frost.accent, onPrimary = frost.onAccent, primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF0E1A3A),
            secondaryContainer = Color(0xFFE4E8F1), onSecondaryContainer = frost.textPrimary,
            surface = Color(0xFFF7F8FC), onSurface = frost.textPrimary, onSurfaceVariant = frost.textTertiary,
            surfaceContainer = Color(0xFFF1F3F9), surfaceContainerHigh = Color(0xFFEBEEF5), surfaceContainerHighest = Color(0xFFE4E8F1),
            background = Color(0xFFF2F4F9), onBackground = frost.textPrimary, outline = frost.textMuted,
            error = frost.errorText,
        )
    }
    CompositionLocalProvider(LocalFrost provides frost) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** The frost material: fill, 1 dp hairline and an ambient shadow, in [shape]. */
fun Modifier.frost(colors: FrostColors, shape: Shape = FrostShapes.card, shadow: Boolean = true): Modifier =
    (if (shadow) this.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = if (colors.dark) 0.35f else 0.18f), spotColor = Color.Transparent) else this)
        .clip(shape)
        .background(colors.fill, shape)
        .border(1.dp, colors.border, shape)

/** A square or round icon tile: [size], filled with [fill]. */
@Composable
fun IconTile(size: Dp, fill: Color, shape: Shape, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(size).background(fill, shape), contentAlignment = Alignment.Center, content = content)
}
