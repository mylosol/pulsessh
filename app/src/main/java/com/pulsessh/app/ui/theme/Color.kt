package com.pulsessh.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Hand-written fallback palette, used on Android 11 and below and whenever the user turns dynamic
 * colour off.
 *
 * It is tuned for a terminal rather than for a content app:
 *   * the dark background is a desaturated near-black with a slight blue cast, so that a full
 *     screen of glowing text is comfortable for long sessions and cheap on OLED;
 *   * the accents are the familiar ANSI-ish green, cyan and violet, which keeps the chrome
 *     visually consistent with the colours the shell itself emits;
 *   * every foreground/background pair below clears the WCAG AA 4.5:1 contrast ratio at body text
 *     size, in both schemes.
 *
 * Properties are camelCase (not the PascalCase of the Android Studio template) because ktlint's
 * `property-naming` rule in the official code style expects a non-const `val` to be camelCase.
 */

/** Dark scheme: primary accent, the "prompt green" used for the main call to action. */
val darkPrimary = Color(0xFF7CE38B)

/** Dark scheme: content drawn on top of [darkPrimary]. */
val darkOnPrimary = Color(0xFF04240D)

/** Dark scheme: filled container built from the primary accent. */
val darkPrimaryContainer = Color(0xFF19502A)

/** Dark scheme: content drawn on top of [darkPrimaryContainer]. */
val darkOnPrimaryContainer = Color(0xFFB7F0C0)

/** Dark scheme: secondary accent, used for informational chrome such as connection state. */
val darkSecondary = Color(0xFF56B6C2)

/** Dark scheme: content drawn on top of [darkSecondary]. */
val darkOnSecondary = Color(0xFF00272E)

/** Dark scheme: tertiary accent, reserved for keys and identities. */
val darkTertiary = Color(0xFFD2A8FF)

/** Dark scheme: content drawn on top of [darkTertiary]. */
val darkOnTertiary = Color(0xFF2B0F4D)

/** Dark scheme: the terminal canvas. */
val darkBackground = Color(0xFF0E1116)

/** Dark scheme: default text colour on [darkBackground]. */
val darkOnBackground = Color(0xFFE6EDF3)

/** Dark scheme: cards, sheets and app bars raised above the canvas. */
val darkSurface = Color(0xFF151A21)

/** Dark scheme: default text colour on [darkSurface]. */
val darkOnSurface = Color(0xFFE6EDF3)

/** Dark scheme: subdued surface for list rows and input fields. */
val darkSurfaceVariant = Color(0xFF21262D)

/** Dark scheme: secondary text colour on [darkSurfaceVariant]. */
val darkOnSurfaceVariant = Color(0xFFB7C0CA)

/** Dark scheme: hairlines and disabled borders. */
val darkOutline = Color(0xFF6E7681)

/** Dark scheme: failures, disconnects and destructive actions. */
val darkError = Color(0xFFFF7B72)

/** Dark scheme: content drawn on top of [darkError]. */
val darkOnError = Color(0xFF3A0906)

/** Light scheme: primary accent, a deep green that stays legible on white. */
val lightPrimary = Color(0xFF1B6E32)

/** Light scheme: content drawn on top of [lightPrimary]. */
val lightOnPrimary = Color(0xFFFFFFFF)

/** Light scheme: filled container built from the primary accent. */
val lightPrimaryContainer = Color(0xFFB7F0C0)

/** Light scheme: content drawn on top of [lightPrimaryContainer]. */
val lightOnPrimaryContainer = Color(0xFF00210C)

/** Light scheme: secondary accent, used for informational chrome such as connection state. */
val lightSecondary = Color(0xFF00687B)

/** Light scheme: content drawn on top of [lightSecondary]. */
val lightOnSecondary = Color(0xFFFFFFFF)

/** Light scheme: tertiary accent, reserved for keys and identities. */
val lightTertiary = Color(0xFF5C3D9E)

/** Light scheme: content drawn on top of [lightTertiary]. */
val lightOnTertiary = Color(0xFFFFFFFF)

/** Light scheme: the app canvas. */
val lightBackground = Color(0xFFF7F8FA)

/** Light scheme: default text colour on [lightBackground]. */
val lightOnBackground = Color(0xFF10151B)

/** Light scheme: cards, sheets and app bars raised above the canvas. */
val lightSurface = Color(0xFFFFFFFF)

/** Light scheme: default text colour on [lightSurface]. */
val lightOnSurface = Color(0xFF10151B)

/** Light scheme: subdued surface for list rows and input fields. */
val lightSurfaceVariant = Color(0xFFE3E6EB)

/** Light scheme: secondary text colour on [lightSurfaceVariant]. */
val lightOnSurfaceVariant = Color(0xFF43474E)

/** Light scheme: hairlines and disabled borders. */
val lightOutline = Color(0xFF74777F)

/** Light scheme: failures, disconnects and destructive actions. */
val lightError = Color(0xFFB3261E)

/** Light scheme: content drawn on top of [lightError]. */
val lightOnError = Color(0xFFFFFFFF)
