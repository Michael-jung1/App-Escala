package com.aistudio.escala.ui.theme

import androidx.compose.ui.graphics.Color

// --- Light Theme Colors ---
val EscalaPrimaryLight = Color(0xFF7D5F38)
val EscalaOnPrimaryLight = Color(0xFFFFFFFF)
val EscalaPrimaryContainerLight = Color(0xFFF3E7D3)
val EscalaOnPrimaryContainerLight = Color(0xFF2E200C)

val EscalaSecondaryLight = Color(0xFF6A5E4F)
val EscalaOnSecondaryLight = Color(0xFFFFFFFF)
val EscalaSecondaryContainerLight = Color(0xFFF3ECE1)
val EscalaOnSecondaryContainerLight = Color(0xFF241C10)

val EscalaTertiaryLight = Color(0xFF536349)
val EscalaOnTertiaryLight = Color(0xFFFFFFFF)
val EscalaTertiaryContainerLight = Color(0xFFD6E8C8)
val EscalaOnTertiaryContainerLight = Color(0xFF121F0B)

val EscalaBackgroundLight = Color(0xFFF8F5EE)
val EscalaOnBackgroundLight = Color(0xFF1E1B16)
val EscalaSurfaceLight = Color(0xFFFFFFFF)
val EscalaOnSurfaceLight = Color(0xFF1E1B16)
val EscalaSurfaceVariantLight = Color(0xFFEBE5D8)
val EscalaOnSurfaceVariantLight = Color(0xFF5A5347)
val EscalaOutlineLight = Color(0xFFD5CCBC)
val EscalaOutlineVariantLight = Color(0xFFE5DECF)

// --- Dark Theme Colors ---
val EscalaPrimaryDark = Color(0xFFE5C498)
val EscalaOnPrimaryDark = Color(0xFF432B09)
val EscalaPrimaryContainerDark = Color(0xFF5D401D)
val EscalaOnPrimaryContainerDark = Color(0xFFFFDDB6)

val EscalaSecondaryDark = Color(0xFFD5C4B1)
val EscalaOnSecondaryDark = Color(0xFF392E21)
val EscalaSecondaryContainerDark = Color(0xFF4F4437)
val EscalaOnSecondaryContainerDark = Color(0xFFF2E0CD)

val EscalaTertiaryDark = Color(0xFFBBCBB0)
val EscalaOnTertiaryDark = Color(0xFF26341E)
val EscalaTertiaryContainerDark = Color(0xFF3C4B33)
val EscalaOnTertiaryContainerDark = Color(0xFFD6E8C8)

val EscalaBackgroundDark = Color(0xFF14120F)
val EscalaOnBackgroundDark = Color(0xFFEBE5DD)
val EscalaSurfaceDark = Color(0xFF1D1A16)
val EscalaOnSurfaceDark = Color(0xFFEBE5DD)
val EscalaSurfaceVariantDark = Color(0xFF2C2822)
val EscalaOnSurfaceVariantDark = Color(0xFFB5ADA0)
val EscalaOutlineDark = Color(0xFF443F36)
val EscalaOutlineVariantDark = Color(0xFF332F28)

// Backward Compatibility Aliases
val EscalaPrimary = EscalaPrimaryLight
val EscalaPrimaryContainer = EscalaPrimaryContainerLight
val EscalaTextLight = EscalaOnSurfaceLight
val EscalaTextSoftLight = EscalaOnSurfaceVariantLight
val EscalaBorderLight = EscalaOutlineLight
val EscalaTextDark = EscalaOnSurfaceDark
val EscalaTextSoftDark = EscalaOnSurfaceVariantDark
val EscalaBorderDark = EscalaOutlineDark

// Error & Success Semantic Colors
val EscalaErrorBgLight = Color(0xFFFDE8E8)
val EscalaErrorTextLight = Color(0xFFB3261E)
val EscalaErrorBgDark = Color(0xFF3D1A18)
val EscalaErrorTextDark = Color(0xFFF2B8B5)

val EscalaSuccessBgLight = Color(0xFFE6F4EA)
val EscalaSuccessTextLight = Color(0xFF137333)
val EscalaSuccessBgDark = Color(0xFF173820)
val EscalaSuccessTextDark = Color(0xFF81C995)

val EscalaErrorBg = EscalaErrorBgLight
val EscalaErrorText = EscalaErrorTextLight
val EscalaSuccessBg = EscalaSuccessBgLight
val EscalaSuccessText = EscalaSuccessTextLight

data class StatusColor(val text: Color, val background: Color, val border: Color)

fun getSuccessColor(isDark: Boolean): StatusColor {
    return if (isDark) {
        StatusColor(
            text = EscalaSuccessTextDark,
            background = EscalaSuccessBgDark,
            border = Color(0xFF275932)
        )
    } else {
        StatusColor(
            text = EscalaSuccessTextLight,
            background = EscalaSuccessBgLight,
            border = Color(0xFF34A853)
        )
    }
}

fun getErrorColor(isDark: Boolean): StatusColor {
    return if (isDark) {
        StatusColor(
            text = EscalaErrorTextDark,
            background = EscalaErrorBgDark,
            border = Color(0xFF6B2B28)
        )
    } else {
        StatusColor(
            text = EscalaErrorTextLight,
            background = EscalaErrorBgLight,
            border = Color(0xFFEA4335)
        )
    }
}

// Cores das Comunidades / Igrejas
data class ChurchColor(val text: Color, val background: Color)

// Light variants
val ColorPerpetuoLight = ChurchColor(Color(0xFF3E5622), Color(0xFFE5EBD8))
val ColorSagradoLight = ChurchColor(Color(0xFF703D2E), Color(0xFFF0E3DC))
val ColorSaoJoseLight = ChurchColor(Color(0xFF255169), Color(0xFFDEE8F0))
val ColorDefaultLight = ChurchColor(Color(0xFF664E26), Color(0xFFF2E7D5))
val ColorPlumLight = ChurchColor(Color(0xFF63395F), Color(0xFFEDE0EB))

// Dark variants (harmonious contrast against dark surfaces)
val ColorPerpetuoDark = ChurchColor(Color(0xFFB8DA92), Color(0xFF263518))
val ColorSagradoDark = ChurchColor(Color(0xFFF0B29E), Color(0xFF3D211A))
val ColorSaoJoseDark = ChurchColor(Color(0xFF9CC9E6), Color(0xFF182D3A))
val ColorDefaultDark = ChurchColor(Color(0xFFE8CA94), Color(0xFF3B2B16))
val ColorPlumDark = ChurchColor(Color(0xFFE3ACDD), Color(0xFF391B36))

val ColorPerpetuo = ColorPerpetuoLight
val ColorSagrado = ColorSagradoLight
val ColorSaoJose = ColorSaoJoseLight
val ColorDefault = ColorDefaultLight
val ColorPlum = ColorPlumLight

fun getChurchColor(nomeIgreja: String, isDark: Boolean = false): ChurchColor {
    val lower = nomeIgreja.lowercase()
    return if (isDark) {
        when {
            lower.contains("perpétuo") || lower.contains("perpetuo") -> ColorPerpetuoDark
            lower.contains("sagrado") -> ColorSagradoDark
            lower.contains("josé") || lower.contains("jose") -> ColorSaoJoseDark
            lower.contains("plum") -> ColorPlumDark
            else -> ColorDefaultDark
        }
    } else {
        when {
            lower.contains("perpétuo") || lower.contains("perpetuo") -> ColorPerpetuoLight
            lower.contains("sagrado") -> ColorSagradoLight
            lower.contains("josé") || lower.contains("jose") -> ColorSaoJoseLight
            lower.contains("plum") -> ColorPlumLight
            else -> ColorDefaultLight
        }
    }
}

