package dev.pawelduraj.memstash.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pawelduraj.memstash.R

private val Accent = Color(0xFFF5CF2E)

/**
 * Accent fills use tertiary in both themes: in the light theme primary is ink, as yellow text on a light
 * background is unreadable.
 */
private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF141414),
    tertiary = Accent,
    onTertiary = Color(0xFF141414),
    background = Color(0xFF141414),
    onBackground = Color(0xFFF5F3EE),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF5F3EE),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA6A29A),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF1D1D1D),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFF5F3EE),
    outline = Color(0xFF5A5853),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF141414),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF161514),
    onPrimary = Color(0xFFFFFFFF),
    tertiary = Accent,
    onTertiary = Color(0xFF161514),
    background = Color(0xFFF7F6F3),
    onBackground = Color(0xFF161514),
    surface = Color(0xFFF7F6F3),
    onSurface = Color(0xFF161514),
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = Color(0xFF6B675F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EFEA),
    surfaceContainerHighest = Color(0xFFEFEDE8),
    secondaryContainer = Color(0xFFEFEDE8),
    onSecondaryContainer = Color(0xFF161514),
    outline = Color(0xFFA8A29E),
    outlineVariant = Color(0xFFE6E3DC),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
)

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Figtree),
        displayMedium = displayMedium.copy(fontFamily = Figtree),
        displaySmall = displaySmall.copy(fontFamily = Figtree),
        headlineLarge = headlineLarge.copy(fontFamily = Figtree),
        headlineMedium = headlineMedium.copy(fontFamily = Figtree),
        headlineSmall = headlineSmall.copy(fontFamily = Figtree),
        titleLarge = titleLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Figtree),
        bodyLarge = bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = bodyMedium.copy(fontFamily = Figtree),
        bodySmall = bodySmall.copy(fontFamily = Figtree),
        labelLarge = labelLarge.copy(fontFamily = Figtree),
        labelMedium = labelMedium.copy(fontFamily = Figtree),
        labelSmall = labelSmall.copy(fontFamily = Figtree),
    )
}

@Composable
fun MemeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

object AppIcons {
    val Folder by lazy {
        icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    val AddPhoto by lazy {
        line("AddPhoto", "M12 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7", circle(8.5f, 9.5f, 1.5f), "M19 16l-4-4-9 8", "M18 2v6M15 5h6")
    }
    val Paste by lazy {
        line("Paste", "M9 4H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2", "M9 3h6v3H9z", "M12 10v7M9 14l3 3 3-3")
    }
    val Sliders by lazy {
        line("Sliders", "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1", circle(15f, 6f, 2f), circle(9f, 12f, 2f), circle(17f, 18f, 2f))
    }
    val Clock by lazy { line("Clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val Search by lazy { line("Search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5") }
    val Close by lazy { line("Close", "M6 6l12 12M18 6L6 18") }
    val Photo by lazy {
        line("Photo", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", circle(9f, 10f, 2f), "M21 17l-5-5-9 8")
    }
    val Share by lazy { line("Share", circle(18f, 5f, 3f), circle(6f, 12f, 3f), circle(18f, 19f, 3f), "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4") }
    val Clipboard by lazy { line("Clipboard", "M8 4h8a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M9 3h6v3H9z") }
    val GridSquare by lazy { line("GridSquare", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z") }
    val GridFull by lazy { line("GridFull", "M4 4h7v10H4zM13 4h7v6h-7zM4 17h7v3H4zM13 13h7v7h-7z") }
    val Pencil by lazy { line("Pencil", "M4 20h4L19 9l-4-4L4 16z") }
    val MoveFolder by lazy {
        line("MoveFolder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M10 13h6M14 11l2 2-2 2")
    }
    val Trash by lazy { line("Trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13") }
    val Folders by lazy { line("Folders", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val NewFolder by lazy {
        line("NewFolder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M12 10v6M9 13h6")
    }
    val ChevronUp by lazy { line("ChevronUp", "M6 15l6-6 6 6") }
    val ChevronDown by lazy { line("ChevronDown", "M6 9l6 6 6-6") }
    val ChevronLeft by lazy { line("ChevronLeft", "M15 6l-6 6 6 6", autoMirror = true) }
    val ChevronRight by lazy { line("ChevronRight", "M9 6l6 6-6 6", autoMirror = true) }
    val ArrowBack by lazy { line("ArrowBack", "M19 12H5M12 19l-7-7 7-7", autoMirror = true) }

    private fun icon(name: String, path: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private fun line(name: String, vararg paths: String, autoMirror: Boolean = false) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
}
