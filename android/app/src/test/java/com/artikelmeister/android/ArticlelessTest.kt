package com.artikelmeister.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlelessTest {

    private fun card(art: Artikel?, word: String, meaning: String = "", updated: Long = 1000L, reviews: Int = 0) =
        Flashcard(
            id = "id-$word-$art", artikel = art, word = word, meaning = meaning,
            createdAt = updated, updatedAt = updated, dueDate = updated, totalReviews = reviews
        )

    // ---------- CSV ----------

    @Test fun csv_validRowsBehaveAsBefore() {
        val out = CsvCards.importContent("artikel,kelime,anlam,deste\nder,Tisch,Masa,A1\nDIE,Katze,Kedi,A1", "Genel", emptyList())
        assertEquals(2, out.added)
        assertEquals(0, out.invalid)
        assertEquals(Artikel.DER, out.cards[0].artikel)
        assertEquals(Artikel.DIE, out.cards[1].artikel)
        assertEquals("A1", out.cards[0].deckName)
    }

    @Test fun csv_emptyArticleBecomesArticleless() {
        val out = CsvCards.importContent(",Tisch,Masa,A1\njustoneword", "Genel", emptyList())
        assertEquals(1, out.added)
        assertTrue(out.cards[0].isArticleless)
        assertEquals("Tisch", out.cards[0].word)
        assertEquals("Masa", out.cards[0].meaning)
    }

    @Test fun csv_typoArticleBecomesArticleless_headerSkipped() {
        val out = CsvCards.importContent("artikel,kelime\ndre,Tisch\nderr,Haus", "Genel", emptyList())
        assertEquals(2, out.added)
        assertTrue(out.cards.all { it.isArticleless })
        assertEquals(listOf("Tisch", "Haus"), out.cards.map { it.word })
    }

    @Test fun csv_mixedCaseAndWhitespace() {
        val out = CsvCards.importContent("  DeR  , Tisch ,Masa\nDAS,Buch\n", "Genel", emptyList())
        assertEquals(Artikel.DER, out.cards[0].artikel)
        assertEquals("Tisch", out.cards[0].word)
        assertEquals(Artikel.DAS, out.cards[1].artikel)
    }

    @Test fun csv_tabDelimitedEmptyArticleKeepsColumns() {
        val out = CsvCards.importContent("\tTisch\tMasa", "Genel", emptyList())
        assertEquals(1, out.added)
        assertTrue(out.cards[0].isArticleless)
        assertEquals("Tisch", out.cards[0].word)
        assertEquals("Masa", out.cards[0].meaning)
    }

    @Test fun csv_emptyWordIsInvalid() {
        val out = CsvCards.importContent("der,\n,,", "Genel", emptyList())
        assertEquals(0, out.added)
        assertEquals(2, out.invalid)
    }

    @Test fun csv_articlelessAndValidTischAreDistinct() {
        val out = CsvCards.importContent("der,Tisch,Masa\n,Tisch,masa2\n,tisch ,masa3", "Genel", emptyList())
        assertEquals(2, out.cards.size)
        // third row dedups into the article-less 'Tisch' (updates meaning)
        assertEquals(1, out.updated)
        assertEquals("masa3", out.cards.first { it.isArticleless }.meaning)
    }

    @Test fun csv_roundTrip() {
        val cards = listOf(
            card(Artikel.DER, "Tisch", "Masa"),
            card(null, "Hallo", "Merhaba"),
            card(null, "Guten Tag", "Iyi gunler, dostum")
        )
        val csv = CsvCards.exportString(cards)
        assertTrue(csv.lines()[2].startsWith(",Hallo,"))
        val back = CsvCards.importContent(csv, "Genel", emptyList())
        assertEquals(3, back.added)
        assertEquals(cards.map { it.syncKey }, back.cards.map { it.syncKey })
        assertEquals("Iyi gunler, dostum", back.cards[2].meaning)
    }

    // ---------- Answer checking ----------

    @Test fun answer_validCard() {
        val c = card(Artikel.DER, "Tisch")
        assertTrue(AnswerChecker.isCorrect(c, " DER "))
        assertFalse(AnswerChecker.isCorrect(c, "die"))
        assertFalse(AnswerChecker.isCorrect(c, ""))
        assertFalse(AnswerChecker.isCorrect(c, "-"))
    }

    @Test fun answer_articleless() {
        val c = card(null, "Hallo")
        assertTrue(AnswerChecker.isCorrect(c, "-"))
        assertTrue(AnswerChecker.isCorrect(c, "  - "))
        assertTrue(AnswerChecker.isCorrect(c, ""))
        assertFalse(AnswerChecker.isCorrect(c, "der"))
        assertEquals("-", AnswerChecker.expectedLabel(c))
        assertEquals("Hallo", c.fullGermanTerm)
    }

    // ---------- Filter ----------

    @Test fun filter_andCounts() {
        val cards = listOf(card(Artikel.DER, "a"), card(Artikel.DIE, "b"), card(null, "c"), card(null, "d"))
        assertEquals(listOf("c", "d"), ArtikelFilter.apply(cards, ArtikelFilter.NONE).map { it.word })
        assertEquals(4, ArtikelFilter.apply(cards, null).size)
        assertEquals(listOf("a"), ArtikelFilter.apply(cards, "der").map { it.word })
        val counts = ArtikelFilter.counts(cards)
        assertEquals(4, counts.total); assertEquals(1, counts.der); assertEquals(1, counts.die)
        assertEquals(0, counts.das); assertEquals(2, counts.none)
        assertEquals(2, counts.forFilter("-"))
    }

    // ---------- Lenient decode / persistence format ----------

    @Test fun backup_roundTripSplitsArrays() {
        val cards = listOf(card(Artikel.DER, "Tisch"), card(null, "Hallo"))
        val json = ArtikelMeisterBackup(includesProgress = true, cards = cards).toJsonString()
        val root = JSONObject(json)
        // cards[] holds ONLY valid cards, never an empty artikel.
        assertEquals(1, root.getJSONArray("cards").length())
        assertEquals("der", root.getJSONArray("cards").getJSONObject(0).getString("artikel"))
        assertEquals(1, root.getJSONArray("articlelessCards").length())
        val back = ArtikelMeisterBackup.fromJsonString(json)
        assertEquals(2, back.cards.size)
        assertEquals(1, back.cards.count { it.isArticleless })
    }

    @Test fun backup_noArticlelessKeyWhenNone() {
        val json = ArtikelMeisterBackup(includesProgress = true, cards = listOf(card(Artikel.DAS, "Buch"))).toJsonString()
        assertFalse(JSONObject(json).has("articlelessCards"))
    }

    @Test fun decode_isLenient_badCardsSkipped() {
        val json = """
            {"version":2,"cards":[
              {"artikel":"der","word":"Tisch"},
              {"artikel":"","word":"Bad"},
              {"artikel":"xyz","word":"Bad2"},
              "garbage",
              {"artikel":"die"},
              {"artikel":"die","word":"Katze","dueDate":{"x":1},"easeFactor":"nope"}
            ],
            "articlelessCards":[{"word":"Hallo"},{"word":""},42,{"artikel":"der","word":"Ignored"}]}
        """.trimIndent()
        val b = ArtikelMeisterBackup.fromJsonString(json)
        assertEquals(listOf("Tisch", "Katze"), b.cards.filterNot { it.isArticleless }.map { it.word })
        // artikel in the articleless list is ignored: card stays article-less
        assertEquals(listOf("Hallo", "Ignored"), b.cards.filter { it.isArticleless }.map { it.word })
    }

    @Test fun decode_oldFileWithoutArticlelessKey() {
        val b = ArtikelMeisterBackup.fromJsonString("""{"cards":[{"artikel":"das","word":"Buch"}]}""")
        assertEquals(1, b.cards.size)
        assertEquals(Artikel.DAS, b.cards[0].artikel)
    }

    @Test fun decodeArray_nullAndGarbage() {
        assertTrue(CardCodec.decodeArray(null, false).isEmpty())
        assertEquals(1, CardCodec.decodeArray(JSONArray("""[1,null,{"artikel":"der","word":"X"}]"""), false).size)
    }

    // ---------- Merge / dedup ----------

    @Test fun merge_articlelessKeyIsWordOnlyCaseInsensitive() {
        assertEquals(card(null, "Hallo").syncKey, card(null, " HALLO ").syncKey)
        assertTrue(card(null, "Tisch").syncKey != card(Artikel.DER, "Tisch").syncKey)
    }

    @Test fun merge_unionDedupsAndKeepsBothKinds() {
        val local = listOf(card(Artikel.DER, "Tisch", "m1", 1000, 1), card(null, "Hallo", "", 1000, 0))
        val cloud = listOf(
            card(null, "hallo", "Merhaba", 2000, 3),
            card(null, "Tisch", "article-less tisch", 1500),
            card(Artikel.DIE, "Katze")
        )
        val r = CardMerge.merge(local, cloud, emptyMap())
        assertEquals(4, r.cards.size) // der Tisch, Hallo, articleless Tisch, die Katze
        assertEquals(2, r.addedFromIncoming)
        assertEquals(1, r.overlapping)
        val hallo = r.cards.first { it.isArticleless && it.word.equals("hallo", true) }
        assertEquals("Merhaba", hallo.meaning)
        assertEquals(3, hallo.totalReviews) // more progress wins
        assertEquals(2, r.cards.count { it.word.equals("Tisch", true) })
    }

    @Test fun merge_tombstoneDropsArticlelessCard() {
        val c = card(null, "Hallo", updated = 1000)
        val r = CardMerge.merge(listOf(c), emptyList(), mapOf(c.syncKey to 5000L))
        assertTrue(r.cards.isEmpty())
    }

    @Test fun dedupe_withinList() {
        val r = CardMerge.dedupe(listOf(card(null, "Hallo"), card(null, "hallo "), card(Artikel.DER, "Hallo")))
        assertEquals(2, r.size)
    }

    // ---------- SM2 ----------

    @Test fun sm2_gradesArticlelessCard() {
        val c = card(null, "Hallo")
        val now = 10_000_000L
        for (rating in ReviewRating.entries) {
            val r = SM2Scheduler.schedule(c, rating, now)
            assertNull(r.updatedCard.artikel)
            assertEquals(1, r.updatedCard.totalReviews)
            assertNotNull(r.intervalDescription)
        }
        assertEquals(CardStage.LEARNING, SM2Scheduler.schedule(c, ReviewRating.GOOD, now).updatedCard.stage)
        assertEquals(CardStage.REVIEW, SM2Scheduler.schedule(c, ReviewRating.EASY, now).updatedCard.stage)
        // review stage fail -> relearning, lapses++
        val review = c.copy(stage = CardStage.REVIEW, intervalDays = 10.0, repetitions = 3, lastReviewedAt = now - 86_400_000L)
        val again = SM2Scheduler.schedule(review, ReviewRating.AGAIN, now).updatedCard
        assertEquals(CardStage.RELEARNING, again.stage)
        assertEquals(1, again.lapses)
    }
}
