package com.vyoma.app

import androidx.compose.ui.graphics.Color

data class ThemePalette(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val accent: Color,
    val surface: Color,
    val background: Color,
    val onPrimary: Color = Color.White,
    val onSurface: Color = Color(0xFF1E293B),
    val isDark: Boolean = false,
    val primaryHex: String,
    val accentHex: String,
    val surfaceHex: String,
    val backgroundHex: String
)

object ThemePalettes {
    val emerald = ThemePalette(
        id = "emerald",
        name = "Fintech Emerald",
        description = "Clean, trust-inspiring green with crisp whites",
        primary = Color(0xFF00875A),
        accent = Color(0xFFBDF4D3),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF4FBF7),
        onPrimary = Color.White,
        onSurface = Color(0xFF172B4D),
        isDark = false,
        primaryHex = "0xFF00875A",
        accentHex = "0xFFBDF4D3",
        surfaceHex = "0xFFFFFFFF",
        backgroundHex = "0xFFF4FBF7"
    )

    val cyberNeon = ThemePalette(
        id = "cyber_neon",
        name = "Cyber Neon",
        description = "Electric violet & cyan on deep midnight space",
        primary = Color(0xFF7C3AED),
        accent = Color(0xFF06B6D4),
        surface = Color(0xFF1E1B4B),
        background = Color(0xFF0F172A),
        onPrimary = Color.White,
        onSurface = Color(0xFFF8FAFC),
        isDark = true,
        primaryHex = "0xFF7C3AED",
        accentHex = "0xFF06B6D4",
        surfaceHex = "0xFF1E1B4B",
        backgroundHex = "0xFF0F172A"
    )

    val pastelMinimal = ThemePalette(
        id = "pastel_minimal",
        name = "Pastel Minimal",
        description = "Indigo clarity with warm amber accents",
        primary = Color(0xFF4F46E5),
        accent = Color(0xFFFBBF24),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFFAF9F6),
        onPrimary = Color.White,
        onSurface = Color(0xFF1F2937),
        isDark = false,
        primaryHex = "0xFF4F46E5",
        accentHex = "0xFFFBBF24",
        surfaceHex = "0xFFFFFFFF",
        backgroundHex = "0xFFFAF9F6"
    )

    val luxuryObsidian = ThemePalette(
        id = "luxury_obsidian",
        name = "Luxury Obsidian",
        description = "Refined metallic gold against velvet black",
        primary = Color(0xFFD4AF37),
        accent = Color(0xFFFDE68A),
        surface = Color(0xFF18181B),
        background = Color(0xFF09090B),
        onPrimary = Color(0xFF09090B),
        onSurface = Color(0xFFF4F4F5),
        isDark = true,
        primaryHex = "0xFFD4AF37",
        accentHex = "0xFFFDE68A",
        surfaceHex = "0xFF18181B",
        backgroundHex = "0xFF09090B"
    )

    val sunsetCoral = ThemePalette(
        id = "sunset_coral",
        name = "Sunset Coral",
        description = "Vibrant coral rose with sunlit golden warmth",
        primary = Color(0xFFE11D48),
        accent = Color(0xFFFDE047),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFFFF1F2),
        onPrimary = Color.White,
        onSurface = Color(0xFF1C1917),
        isDark = false,
        primaryHex = "0xFFE11D48",
        accentHex = "0xFFFDE047",
        surfaceHex = "0xFFFFFFFF",
        backgroundHex = "0xFFFFF1F2"
    )

    val deepOcean = ThemePalette(
        id = "deep_ocean",
        name = "Deep Ocean",
        description = "Serene sapphire blue with sky cyan highlights",
        primary = Color(0xFF0284C7),
        accent = Color(0xFF7DD3FC),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF0F9FF),
        onPrimary = Color.White,
        onSurface = Color(0xFF0F172A),
        isDark = false,
        primaryHex = "0xFF0284C7",
        accentHex = "0xFF7DD3FC",
        surfaceHex = "0xFFFFFFFF",
        backgroundHex = "0xFFF0F9FF"
    )

    val lavenderDream = ThemePalette(
        id = "lavender_dream",
        name = "Lavender Dream",
        description = "Soft royal purple with gentle lilac accents",
        primary = Color(0xFF8B5CF6),
        accent = Color(0xFFDDD6FE),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFFDF4FF),
        onPrimary = Color.White,
        onSurface = Color(0xFF3B0764),
        isDark = false,
        primaryHex = "0xFF8B5CF6",
        accentHex = "0xFFDDD6FE",
        surfaceHex = "0xFFFFFFFF",
        backgroundHex = "0xFFFDF4FF"
    )

    val all = listOf(emerald, cyberNeon, pastelMinimal, luxuryObsidian, sunsetCoral, deepOcean, lavenderDream)

    fun getById(id: String): ThemePalette = all.firstOrNull { it.id == id } ?: emerald
}
