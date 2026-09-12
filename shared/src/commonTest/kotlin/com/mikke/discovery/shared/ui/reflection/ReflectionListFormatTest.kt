package com.mikke.discovery.shared.ui.reflection

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class ReflectionListFormatTest {

    @Test
    fun formatInstant_formatsWithoutError() {
        val instant = Instant.fromEpochMilliseconds(1725624000000L) // 2024-09-06 UTC
        val formatted = formatInstant(instant)
        // YYYY/MM/DD HH:mm 形式であることの検証
        assertTrue(formatted.matches(Regex("""\d{4}/\d{2}/\d{2} \d{2}:\d{2}""")), "Formatted: $formatted")
    }
}
