package com.artikelmeister.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCheckerTest {

    @Test fun semVerParsesValidTags() {
        assertEquals(SemVer(1, 2, 5), SemVer.parse("v1.2.5"))
        assertEquals(SemVer(1, 3, 0), SemVer.parse("1.3.0"))
        assertEquals(SemVer(2, 0, 0), SemVer.parse("V2.0"))
        assertEquals(SemVer(1, 4, 2), SemVer.parse("v1.4.2-beta.1+build42"))
    }

    @Test fun semVerRejectsInvalidTags() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("v1"))
        assertNull(SemVer.parse("v1.2.3.4"))
        assertNull(SemVer.parse("v-1.2.0"))
    }

    @Test fun updateRequiredComparison() {
        assertFalse(VersionChecker.isUpdateRequired("1.3.0", "v1.2.5"))
        assertFalse(VersionChecker.isUpdateRequired("1.3.0", "v1.3.0"))
        assertTrue(VersionChecker.isUpdateRequired("1.3.0", "v1.3.1"))
        assertTrue(VersionChecker.isUpdateRequired("1.3.0", "v1.4.0"))
        assertTrue(VersionChecker.isUpdateRequired("1.3.0", "2.0.0"))
        assertFalse(VersionChecker.isUpdateRequired("1.3.0", "not-a-version"))
    }

    @Test fun parsesGitHubLatestReleaseJsonAndSanitizesUrl() {
        val json = """
            {
              "tag_name": "v1.4.0",
              "html_url": "https://github.com/kaankekec-sec/artikelmeister/releases/tag/v1.4.0"
            }
        """.trimIndent()
        val info = VersionChecker.parseLatestReleaseJson(json)
        assertNotNull(info)
        assertEquals("v1.4.0", info!!.tagName)
        assertEquals(SemVer(1, 4, 0), info.version)
        assertEquals("https://github.com/kaankekec-sec/artikelmeister/releases/tag/v1.4.0", info.htmlUrl)

        val untrustedUrlJson = """
            {
              "tag_name": "v1.4.0",
              "html_url": "https://evil.example.com/phish"
            }
        """.trimIndent()
        val sanitized = VersionChecker.parseLatestReleaseJson(untrustedUrlJson)
        assertEquals(VersionChecker.RELEASES_FALLBACK_URL, sanitized!!.htmlUrl)
        assertNull(VersionChecker.parseLatestReleaseJson("{bad json"))
    }

    @Test fun mistralParserHandlesWrappedAndRawArrays() {
        val wrapped = """
            ```json
            {"words": [
              {"artikel": "der", "word": "Tisch", "meaning": "Masa"},
              {"artikel": "-", "word": "laufen", "meaning": "koşmak"},
              {"artikel": "-", "word": "leer", "meaning": ""}
            ]}
            ```
        """.trimIndent()
        val nounsOnly = MistralVocabularyGenerator.parseGeneratedWordsJson(wrapped, includeArticleless = false)
        assertEquals(1, nounsOnly.size)
        assertEquals("Tisch", nounsOnly[0].word)

        val withArticleless = MistralVocabularyGenerator.parseGeneratedWordsJson(wrapped, includeArticleless = true)
        assertEquals(2, withArticleless.size)
        assertEquals("-", withArticleless[1].artikel)
        assertEquals("laufen", withArticleless[1].word)
    }
}
