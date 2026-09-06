package com.example.myapplication.shared.ui.discovery

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class EvidenceListFormatTest {

    @Test
    fun formatInstant_formatsWithoutError() {
        val instant = Instant.fromEpochMilliseconds(1725624000000L)
        val formatted = formatInstant(instant)
        assertTrue(formatted.matches(Regex("""\d{4}/\d{2}/\d{2} \d{2}:\d{2}""")), "Formatted: $formatted")
    }
}
