package com.pulsessh.app.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Contract of [Redactor].
 *
 * The assertions are deliberately written as "the secret must not appear in the output" rather
 * than "the output equals this exact string", because the value being protected is the absence of
 * the secret, not the shape of the replacement.
 */
class RedactorTest {
    @Test
    fun `a private key block is removed entirely`() {
        val secret = "MIIEpAIBAAKCAQEA7Zx9Qk"
        val line =
            """
            -----BEGIN RSA PRIVATE KEY-----
            $secret
            -----END RSA PRIVATE KEY-----
            """.trimIndent()

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain(secret)
        assertThat(redacted).doesNotContain("BEGIN RSA PRIVATE KEY")
        assertThat(redacted).contains(Redactor.REDACTION_MARKER)
    }

    @Test
    fun `an openssh key block whose end armour has not arrived yet is removed`() {
        val line = "connecting... -----BEGIN OPENSSH PRIVATE KEY----- b3BlbnNzaC1rZXktdjEAAAAA"

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("b3BlbnNzaC1rZXktdjEAAAAA")
        assertThat(redacted).startsWith("connecting... ")
        assertThat(redacted).endsWith(Redactor.REDACTION_MARKER)
    }

    @Test
    fun `an inline password assignment keeps the key and loses the value`() {
        val line = "ssh --password=hunter2correcthorse --host example.com"

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("hunter2correcthorse")
        assertThat(redacted).contains("password=${Redactor.REDACTION_MARKER}")
        assertThat(redacted).contains("--host example.com")
    }

    @Test
    fun `a quoted passphrase assignment is removed`() {
        val line = """PASSPHRASE = "a whole sentence as a passphrase" # from the vault"""

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("a whole sentence as a passphrase")
        assertThat(redacted).contains(Redactor.REDACTION_MARKER)
    }

    @Test
    fun `the answer typed after a password prompt is removed`() {
        val line = "[sudo] password for ada: swordfish"

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("swordfish")
        assertThat(redacted).isEqualTo("[sudo] password for ada: ${Redactor.REDACTION_MARKER}")
    }

    @Test
    fun `a base64 blob longer than the threshold is removed`() {
        val blob = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejAxMjM0NTY3ODkr".repeat(2)
        val line = "token $blob end"

        val redacted = Redactor.redact(line)

        assertThat(blob.length).isGreaterThan(Redactor.MIN_BASE64_BLOB_LENGTH)
        assertThat(redacted).doesNotContain(blob)
        assertThat(redacted).isEqualTo("token ${Redactor.REDACTION_MARKER} end")
    }

    @Test
    fun `an ordinary line without secrets passes through unchanged`() {
        val line = "ada@web-01:~$ ls -la /var/log && echo done: 42"

        assertThat(Redactor.redact(line)).isEqualTo(line)
    }

    @Test
    fun `a short base64 looking token is not treated as a secret`() {
        val line = "commit abc123DEF456 pushed to origin"

        assertThat(Redactor.redact(line)).isEqualTo(line)
    }

    @Test
    fun `an empty string is returned unchanged`() {
        assertThat(Redactor.redact("")).isEmpty()
    }

    @Test
    fun `every occurrence is removed when the secret appears twice`() {
        val line = "password=firstsecret then again password=secondsecret"

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("firstsecret")
        assertThat(redacted).doesNotContain("secondsecret")
        assertThat(redacted).isEqualTo(
            "password=${Redactor.REDACTION_MARKER} then again password=${Redactor.REDACTION_MARKER}",
        )
    }

    @Test
    fun `redacted output is stable when redacted again`() {
        val line = "password=hunter2 and a prompt password for ada: swordfish"

        val once = Redactor.redact(line)
        val twice = Redactor.redact(once)

        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `a very long line does not throw`() {
        val line = "x".repeat(200_000) + " password=hunter2"

        val redacted = Redactor.redact(line)

        assertThat(redacted).doesNotContain("hunter2")
    }
}
