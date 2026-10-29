package com.artikelmeister.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class CsvImportSafetyTest {

    private fun ok(content: String) = CsvCards.validate(content).also {
        assertTrue("expected valid but got ${it.reason}: ${it.message}", it.ok)
    }

    private fun rejected(content: String, reason: CsvCards.Rejection) {
        val v = CsvCards.validate(content)
        assertFalse("expected rejection $reason", v.ok)
        assertEquals(reason, v.reason)
        assertTrue(v.message.isNotBlank())
    }

    private fun decodeRejected(bytes: ByteArray, reason: CsvCards.Rejection) {
        try {
            CsvCards.decodeAndValidate(bytes)
            fail("expected rejection $reason")
        } catch (e: CsvCards.RejectedException) {
            assertEquals(reason, e.reason)
        }
    }

    // ---------- accepted ----------

    @Test fun validCsv() { ok("der,Tisch,Masa,A1\ndie,Katze,Kedi,A1\ndas,Buch,Kitap,A1") }

    @Test fun withHeader() {
        val v = ok("artikel,kelime,anlam,deste\nder,Tisch,Masa,A1\ndie,Katze,Kedi,A1")
        assertEquals(2, v.validRows)
        assertEquals(0, v.invalidRows)
    }

    @Test fun tabAndSemicolonDelimited() {
        ok("der\tTisch\tMasa\ndie\tKatze\tKedi")
        ok("der;Tisch;Masa\ndie;Katze;Kedi\ndas;Buch;Kitap")
        ok("artikel;kelime;anlam\nder;Tisch;Masa")
    }

    @Test fun articlelessAndMixedRows() {
        val csv = "der,Tisch,Masa\n,gehen,gitmek\n-,schnell,hızlı\ndie,Katze,Kedi"
        val v = ok(csv)
        assertEquals(4, v.validRows)
        val out = CsvCards.importContent(csv, "Genel", emptyList())
        assertEquals(4, out.added)
        assertEquals(2, out.cards.count { it.isArticleless })
    }

    @Test fun windows1254TurkishBytesDecode() {
        val bytes = "der,Schlüssel,Anahtar\r\ndie,Tür,Kapı çağrı".toByteArray(charset("windows-1254"))
        val text = CsvCards.decodeAndValidate(bytes)
        assertTrue(text.contains("Kapı çağrı"))
    }

    @Test fun utf16WithBomIsNotMistakenForBinary() {
        val text = "der,Tisch,Masa\ndie,Katze,Kedi"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, CsvCards.decodeAndValidate(bytes))
    }

    @Test fun utf8BomIsIgnored() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "der,Tisch,Masa".toByteArray()
        ok(CsvCards.decode(bytes))
    }

    @Test fun bundledFilesAndStarterPackStillValidate() {
        ok(CsvCards.STARTER_PACK_CSV)
        val dirs = listOf(File("src/main/assets"), File("../../shared/samples"))
        var checked = 0
        for (dir in dirs) {
            val files = dir.listFiles { f -> f.name.endsWith(".csv") } ?: continue
            for (f in files) {
                val text = CsvCards.decodeAndValidate(f.readBytes())
                val v = CsvCards.validate(text)
                assertTrue("${f.path}: ${v.message}", v.ok)
                checked++
            }
        }
        assertTrue("expected to find the bundled csv files", checked >= 2)
    }

    // ---------- rejected ----------

    @Test fun jsonFile() {
        rejected("""{"version":2,"cards":[{"artikel":"der","word":"Tisch","meaning":"Masa"}]}""", CsvCards.Rejection.NOT_CSV_FORMAT)
        rejected("""[{"a":1},{"b":2}]""", CsvCards.Rejection.NOT_CSV_FORMAT)
        decodeRejected("""{"cards":[]}""".toByteArray(), CsvCards.Rejection.NOT_CSV_FORMAT)
    }

    @Test fun pngLikeBinary() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52)
        decodeRejected(png, CsvCards.Rejection.BINARY)
        // Binary without NUL bytes must still be refused (invalid UTF-8 / control characters).
        val noNul = ByteArray(256) { (it % 31 + 1).toByte() } + "x".toByteArray()
        decodeRejected(noNul, CsvCards.Rejection.BINARY)
        val highBytes = ByteArray(200) { (0x80 + it % 100).toByte() }
        try {
            CsvCards.decodeAndValidate(highBytes)
            fail("random high bytes must be rejected")
        } catch (_: CsvCards.RejectedException) {
        }
    }

    @Test fun proseTextFile() {
        val prose = """
            Es war einmal ein kleines Dorf am Rande eines grossen Waldes, in dem viele Tiere lebten.
            Jeden Morgen gingen die Kinder zur Schule, und am Abend spielten sie, lachten und sangen zusammen.
            Hallo, wie geht es dir heute? Mir geht es gut, danke schoen!
            Der Winter kam frueh in diesem Jahr, aber niemand beschwerte sich darueber.
        """.trimIndent()
        val v = CsvCards.validate(prose)
        assertFalse(v.ok)
        assertTrue(v.reason == CsvCards.Rejection.TOO_MANY_INVALID || v.reason == CsvCards.Rejection.NO_VALID_ROWS || v.reason == CsvCards.Rejection.NO_COLUMNS)
    }

    @Test fun singleColumnListHasNoStructure() {
        rejected("Tisch\nHaus\nKatze\nBuch", CsvCards.Rejection.NO_COLUMNS)
    }

    @Test fun emptyFiles() {
        rejected("", CsvCards.Rejection.EMPTY)
        rejected("   \n\r\n  ", CsvCards.Rejection.EMPTY)
        decodeRejected(ByteArray(0), CsvCards.Rejection.EMPTY)
    }

    @Test fun mostlyJunkRows() {
        val junk = buildString {
            append("der,Tisch,Masa\n")
            repeat(6) { append("this is some random sentence number $it that is not a word entry at all.\n") }
            repeat(3) { append("[1, 2, 3] <tag> {x}, bogus value $it\n") }
        }
        rejected(junk, CsvCards.Rejection.TOO_MANY_INVALID)
    }

    @Test fun exactlyHalfInvalidIsStillAllowed() {
        ok("der,Tisch,Masa\nthis is just one single column line without any delimiter")
    }

    @Test fun extensionGate() {
        assertTrue(CsvCards.hasCsvExtension("words.csv"))
        assertTrue(CsvCards.hasCsvExtension("WORDS.CSV"))
        assertTrue(CsvCards.hasCsvExtension(" a.Csv "))
        assertFalse(CsvCards.hasCsvExtension("photo.png"))
        assertFalse(CsvCards.hasCsvExtension("notes.txt"))
        assertFalse(CsvCards.hasCsvExtension("backup.json"))
        assertFalse(CsvCards.hasCsvExtension("csv"))
        assertFalse(CsvCards.hasCsvExtension(null))
        try {
            CsvCards.requireCsvName("photo.png")
            fail("png must be rejected")
        } catch (e: CsvCards.RejectedException) {
            assertEquals(CsvCards.Rejection.BAD_EXTENSION, e.reason)
            assertTrue(e.message!!.contains(".csv"))
        }
        CsvCards.requireCsvName("words.CSV")
        CsvCards.requireCsvName(null) // unknown name: content validation is the gate
    }

    @Test fun garbageRowsInsideAnAcceptedFileAreSkippedNotImported() {
        val csv = "der,Tisch,Masa\ndie,Katze,Kedi\ndas,Buch,Kitap\nthis is a long sentence that should never become a flashcard, ok."
        ok(csv)
        val out = CsvCards.importContent(csv, "Genel", emptyList())
        assertEquals(3, out.added)
        assertEquals(1, out.invalid)
    }

    // ---------- undo ----------

    private fun card(art: Artikel?, word: String, meaning: String, deck: String = "Genel", updated: Long = 1000L) =
        Flashcard(id = "id-$word", artikel = art, word = word, meaning = meaning, deckName = deck, createdAt = updated, updatedAt = updated)

    @Test fun undoRemovesAddedCardsRestoresUpdatedAndSurvivesCloudMerge() {
        val existing = listOf(card(Artikel.DER, "Tisch", "Masa", "Genel"), card(Artikel.DIE, "Katze", "Kedi", "Genel"))
        val tombstonesBefore = mapOf(Flashcard.keyFor(Artikel.DAS, "Buch") to 500L)
        val csv = "der,Tisch,Sehr anderes Wort,Yeni Deste\ndas,Buch,Kitap,A1\ndie,Blume,Cicek,A1"
        val now = 5000L
        val outcome = CsvCards.importContent(csv, "Genel", existing, now)
        assertEquals(2, outcome.added)
        assertEquals(1, outcome.updated)

        val undo = CsvCards.buildUndo(existing, outcome, tombstonesBefore)!!
        // Simulate the import's own tombstone clearing for the re-added "Buch".
        val tombAfterImport = tombstonesBefore - Flashcard.keyFor(Artikel.DAS, "Buch")
        val (restored, tomb) = CsvCards.applyUndo(undo, outcome.cards, tombAfterImport, now = 9000L)

        // Added cards are gone, originals restored.
        assertEquals(listOf("Tisch", "Katze"), restored.map { it.word })
        val tisch = restored.first { it.word == "Tisch" }
        assertEquals("Masa", tisch.meaning)
        assertEquals("Genel", tisch.deckName)
        // Cloud-merge safety: restored card is newer than the cloud copy carrying the imported values (updatedAt 5000).
        assertEquals(9000L, tisch.updatedAt)
        assertTrue(tisch.updatedAt > outcome.cards.first { it.word == "Tisch" }.updatedAt)
        // Added keys are tombstoned (newer than the added cards' updatedAt) so a cloud copy cannot resurrect them.
        for (key in outcome.addedKeys) {
            val ts = tomb[key]
            assertNotNull("missing tombstone for $key", ts)
            assertTrue(ts!! > 5000L)
        }
        // A tombstone the import cleared is replaced by the fresh one (Buch was re-added by the import).
        assertTrue(tomb.getValue(Flashcard.keyFor(Artikel.DAS, "Buch")) >= 9000L)
    }

    @Test fun undoKeepsLaterUnrelatedChanges() {
        val existing = listOf(card(Artikel.DER, "Tisch", "Masa"))
        val outcome = CsvCards.importContent("die,Blume,Cicek", "Genel", existing, 5000L)
        val undo = CsvCards.buildUndo(existing, outcome, emptyMap())!!
        // User adds another card after the import, then undoes.
        val later = outcome.cards + card(Artikel.DAS, "Haus", "Ev")
        val (restored, _) = CsvCards.applyUndo(undo, later, emptyMap(), 9000L)
        assertEquals(listOf("Tisch", "Haus"), restored.map { it.word })
    }

    @Test fun noChangeImportHasNoUndo() {
        val existing = listOf(card(Artikel.DER, "Tisch", "Masa"))
        val outcome = CsvCards.importContent("der,Tisch,Masa", "Genel", existing, 5000L)
        assertEquals(0, outcome.added + outcome.updated)
        assertNull(CsvCards.buildUndo(existing, outcome, emptyMap()))
    }

    @Test fun rejectionIsPureAndLeavesNoOutcome() {
        // validate/decode are pure functions; a rejected file can never produce an ImportOutcome path in the store
        // because importCSVContent calls requireValid first (exercised here through requireValid).
        try {
            CsvCards.requireValid("""{"cards": []}""")
            fail("must throw")
        } catch (e: CsvCards.RejectedException) {
            assertTrue(e.message!!.contains("Hiçbir veri değiştirilmedi"))
        }
    }
}
