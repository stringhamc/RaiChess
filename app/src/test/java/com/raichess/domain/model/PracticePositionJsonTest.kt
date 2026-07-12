package com.raichess.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the persistence format used by PracticeRepository: a Gson list
 * round-trip including nullable fields and the category enum.
 */
class PracticePositionJsonTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<PracticePosition>>() {}.type

    @Test
    fun `list round trips through json`() {
        val original = listOf(
            PracticePosition(
                id = "abc",
                sourceGameId = null,
                sourceMoveNumber = 12,
                fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                category = PracticeCategory.MISTAKE_CORRECTION,
                difficulty = 2,
                timesPracticed = 3,
                successRate = 0.5,
                lastPracticed = 1234567890L,
                createdAt = 42L
            ),
            PracticePosition(
                id = "def",
                sourceMoveNumber = 1,
                fen = "8/8/8/8/8/8/8/K6k w - - 0 1",
                category = PracticeCategory.ENDGAME,
                createdAt = 43L
            )
        )

        val json = gson.toJson(original, listType)
        val restored: List<PracticePosition> = gson.fromJson(json, listType)

        assertEquals(original, restored)
    }

    @Test
    fun `malformed json is caught by repository fallback contract`() {
        // The repository catches JsonSyntaxException and returns emptyList();
        // this test documents that garbage input does throw as expected.
        var threw = false
        try {
            gson.fromJson<List<PracticePosition>>("{not json]", listType)
        } catch (e: com.google.gson.JsonSyntaxException) {
            threw = true
        }
        assertEquals(true, threw)
    }
}
