package com.pulsessh.app.core.logging

/**
 * Removes secrets from text before it is written to a log, shown in the in-app log viewer or
 * attached to a bug report.
 *
 * Design rules, in order of importance:
 *
 * 1. **Never throw.** Redaction sits on the logging path. If it failed with an exception the
 *    caller would either crash or, worse, fall back to logging the raw line. Every entry point is
 *    therefore wrapped in `runCatching`, and the fallback is the *safe* one: the whole line is
 *    replaced by [REDACTION_MARKER] rather than passed through.
 * 2. **Prefer false positives.** Redacting a harmless line costs a support engineer a little
 *    context. Leaking a private key costs a customer their infrastructure. Every pattern below is
 *    intentionally greedy at the tail: once something looks like the start of a secret, the rest
 *    of the line goes with it.
 * 3. **Line oriented, but newline safe.** [redact] is normally fed one line at a time, yet
 *    callers do pass multi-line chunks (a pasted key, a captured stderr block). The patterns use
 *    [RegexOption.DOT_MATCHES_ALL] so a key spanning several lines inside one string is still
 *    caught as a single unit.
 *
 * This object is pure Kotlin with no Android dependency, so it is unit tested on the JVM.
 */
object Redactor {
    /**
     * Text substituted for anything that was removed.
     *
     * A single fixed marker (rather than one per category) keeps the output predictable for tests
     * and makes it obvious to a reader that information was deliberately dropped rather than lost.
     */
    const val REDACTION_MARKER: String = "[REDACTED]"

    /**
     * Minimum length of an unbroken base64-looking run before it is treated as an encoded secret.
     *
     * Keys, certificates, session tokens and serialised credentials all exceed this; ordinary
     * words, hashes and identifiers in log output do not. Runs of exactly this length are also
     * redacted, which is the conservative reading of "longer than 100 characters".
     */
    const val MIN_BASE64_BLOB_LENGTH: Int = 100

    /**
     * A complete PEM private key, from the BEGIN armour to the matching END armour.
     *
     * `[^-]{0,64}` covers the algorithm word ("RSA", "OPENSSH", "EC", "ENCRYPTED") without letting
     * the pattern run past the armour dashes. The lazy `.*?` keeps two adjacent keys in one string
     * from being merged into a single match, so both are replaced.
     */
    private val completeKeyBlock =
        Regex(
            "-----BEGIN[^-]{0,64}PRIVATE KEY-----.*?-----END[^-]{0,64}PRIVATE KEY-----",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * A private key whose END armour has not arrived yet.
     *
     * Streamed output reaches the logger a line at a time, so the opening armour and the key body
     * are often in different calls. Once the opening armour is seen, everything after it in the
     * same string is discarded.
     */
    private val truncatedKeyBlock =
        Regex(
            "-----BEGIN[^-]{0,64}PRIVATE KEY-----.*",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * The body line of a PEM block, recognised on its own.
     *
     * A continuation line carries no armour, so nothing else in this object would match it. Base64
     * bodies are hard-wrapped at 64 or 76 columns, well under [MIN_BASE64_BLOB_LENGTH], hence this
     * separate rule: a line that is *nothing but* base64 and at least 32 characters long is not
     * something a shell prints by accident.
     */
    private val loneBase64Line = Regex("^[A-Za-z0-9+/]{32,}={0,2}$")

    /**
     * A `password=`, `passphrase=`, `passwd=` or `pwd=` assignment.
     *
     * Covers command lines, connection strings, `.env` files and JSON-ish output. The value part
     * accepts a double-quoted, single-quoted or bare token; the key is preserved so the reader can
     * still tell *which* setting was present.
     */
    private val secretAssignment =
        Regex(
            """\b(password|passphrase|passwd|pwd)\s*=\s*("[^"]*"|'[^']*'|\S+)""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * The answer typed after a password prompt.
     *
     * Matches the many shapes of prompt seen over SSH - `Password:`, `[sudo] password for ada:`,
     * `Enter passphrase for key '/home/ada/.ssh/id_ed25519':` - and drops the remainder of that
     * line. The `[^\n:]{0,64}` between the keyword and the colon bounds the search so an unrelated
     * colon much later on the line cannot pull an innocent tail into the match.
     */
    private val promptAnswer =
        Regex(
            """((?:password|passphrase|passwd)[^\n:]{0,64}:[ \t]*)(\S[^\n]*)""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * A long unbroken run of base64 characters: encoded keys, tokens and certificate bodies.
     *
     * The lookarounds stop a longer run from being redacted in fragments. Padding is consumed so
     * a trailing `=` is not left dangling next to the marker.
     */
    private val longBase64Blob =
        Regex("(?<![A-Za-z0-9+/=])[A-Za-z0-9+/]{$MIN_BASE64_BLOB_LENGTH,}={0,2}(?![A-Za-z0-9+/=])")

    /**
     * Returns [line] with everything that looks like a secret replaced by [REDACTION_MARKER].
     *
     * The passes run from the most structural pattern to the least, because an earlier pass
     * shortens the input the later ones have to reason about: a key block becomes a marker before
     * the base64 rule ever sees its body.
     *
     * Guarantees:
     * * never throws - any failure degrades to a fully redacted line;
     * * pure - the same input always produces the same output, and no state is kept;
     * * idempotent for practical purposes - re-running it on already redacted text is a no-op,
     *   since [REDACTION_MARKER] matches none of the patterns.
     *
     * @param line one log line, or any short chunk of captured output.
     * @return the sanitised text; an empty input returns an empty string.
     */
    fun redact(line: String): String =
        runCatching {
            if (line.isEmpty()) {
                return@runCatching line
            }
            var result = completeKeyBlock.replace(line, REDACTION_MARKER)
            result = truncatedKeyBlock.replace(result, REDACTION_MARKER)
            result = if (loneBase64Line.matches(result.trim())) REDACTION_MARKER else result
            result = secretAssignment.replace(result) { match -> "${match.groupValues[1]}=$REDACTION_MARKER" }
            result = promptAnswer.replace(result) { match -> "${match.groupValues[1]}$REDACTION_MARKER" }
            longBase64Blob.replace(result, REDACTION_MARKER)
        }.getOrElse {
            // Defence in depth: a pathological input that upsets the regex engine (catastrophic
            // backtracking, a StackOverflowError on a very long line) must not surface the raw
            // text. Dropping the line entirely is the only safe failure mode.
            REDACTION_MARKER
        }
}
