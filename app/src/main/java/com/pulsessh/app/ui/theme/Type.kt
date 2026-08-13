package com.pulsessh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** The stock Material 3 scale, kept as the base so that untouched slots never drift from it. */
private val defaultTypography = Typography()

/**
 * The type scale for PulseSSH chrome.
 *
 * Only the slots that are actually tuned are overridden; every other slot keeps its Material 3
 * default, which avoids re-stating the whole scale.
 *
 * Chrome uses the platform sans-serif font. Anything that shows *remote* output uses
 * [terminalTextStyle] instead - mixing a proportional font into terminal output breaks column
 * alignment, which is the one thing a terminal must get right.
 */
val pulseSshTypography: Typography =
    defaultTypography.copy(
        titleLarge =
            defaultTypography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            defaultTypography.bodyLarge.copy(
                lineHeight = 22.sp,
            ),
        labelSmall =
            defaultTypography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            ),
    )

/**
 * The style for anything that reproduces bytes from a remote host: terminal output, log lines,
 * key fingerprints, file listings.
 *
 * [FontFamily.Monospace] resolves to the device's monospace face, so columns line up.
 * `letterSpacing` is pinned to zero because the default tracking of the body styles visibly skews
 * box-drawing characters, and [TextAlign.Start] keeps right-to-left locales from mirroring output
 * that is meaningful only left-to-right.
 */
val terminalTextStyle: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Start,
    )
