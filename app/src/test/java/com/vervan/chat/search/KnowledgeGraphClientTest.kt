package com.vervan.chat.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGraphClientTest {

    private val client = KnowledgeGraphClient(apiKeyProvider = { "test-key" })

    @Test
    fun `parses a well-formed multi-entity response`() {
        val json = """
            {
              "@context": {"@vocab": "http://schema.org"},
              "@type": "ItemList",
              "itemListElement": [
                {
                  "@type": "EntitySearchResult",
                  "result": {
                    "@id": "kg:/m/0dl567",
                    "name": "Taylor Swift",
                    "@type": ["Person", "Thing"],
                    "description": "American singer-songwriter",
                    "detailedDescription": {
                      "articleBody": "Taylor Alison Swift is an American singer-songwriter.",
                      "url": "https://en.wikipedia.org/wiki/Taylor_Swift"
                    },
                    "url": "http://www.taylorswift.com"
                  },
                  "resultScore": 1235.123
                },
                {
                  "@type": "EntitySearchResult",
                  "result": {
                    "@id": "kg:/m/05mnck",
                    "name": "Swift",
                    "@type": ["Band"],
                    "description": "American rock band"
                  },
                  "resultScore": 100.0
                }
              ]
            }
        """.trimIndent()

        val entities = client.parseResponse(json)
        assertEquals(2, entities.size)
        assertEquals("Taylor Swift", entities[0].name)
        // "Thing" is filtered out as too generic to be useful
        assertEquals(listOf("Person"), entities[0].types)
        assertEquals("American singer-songwriter", entities[0].description)
        assertEquals("Taylor Alison Swift is an American singer-songwriter.", entities[0].articleExcerpt)
        assertEquals("https://en.wikipedia.org/wiki/Taylor_Swift", entities[0].url)
        assertEquals(listOf("Band"), entities[1].types)
        // Falls back to a missing detailedDescription.url by leaving it null
        assertNull(entities[1].url)
    }

    @Test
    fun `empty response yields empty list`() {
        assertEquals(emptyList<KnowledgeGraphEntity>(), client.parseResponse("""{"itemListElement": []}"""))
        assertEquals(emptyList<KnowledgeGraphEntity>(), client.parseResponse("""{}"""))
    }

    @Test
    fun `entity with no name is skipped, not emitted with a blank name`() {
        val json = """
            {"itemListElement": [
              {"result": {"name": ""}},
              {"result": {"name": "Valid Entity", "description": "ok"}}
            ]}
        """.trimIndent()
        val entities = client.parseResponse(json)
        assertEquals(1, entities.size)
        assertEquals("Valid Entity", entities[0].name)
    }

    @Test
    fun `long article body is truncated to fit inside the tool result`() {
        val longBody = "This is the first sentence. ".repeat(50)
        val json = """
            {"itemListElement": [{"result": {
              "name": "Some Entity",
              "detailedDescription": {"articleBody": "$longBody"}
            }}]}
        """.trimIndent()
        val entities = client.parseResponse(json)
        assertEquals(1, entities.size)
        // The truncation point is fuzzy (cuts at a sentence/space boundary near 280 chars),
        // so just assert it shrank and ended with the ellipsis marker.
        assertTrue("Excerpt should be truncated", entities[0].articleExcerpt!!.length < longBody.length)
        assertTrue("Excerpt should end with ellipsis", entities[0].articleExcerpt!!.endsWith("…"))
    }

    @Test
    fun `formatResult on no matches is honest about that, not empty`() {
        val formatted = client.formatResult("nonsense", emptyList())
        assertTrue("No-match result must mention the query", formatted.contains("nonsense"))
        assertTrue("No-match result must say it found nothing", formatted.lowercase().contains("no"))
    }

    @Test
    fun `formatResult on matches includes name, type, description, and source`() {
        val entities = listOf(
            KnowledgeGraphEntity(
                name = "Taylor Swift",
                types = listOf("Person"),
                description = "American singer-songwriter",
                articleExcerpt = "Taylor Alison Swift is an American singer-songwriter.",
                url = "https://en.wikipedia.org/wiki/Taylor_Swift"
            )
        )
        val formatted = client.formatResult("taylor swift", entities)
        assertTrue(formatted.contains("1. Taylor Swift"))
        assertTrue(formatted.contains("(Person)"))
        assertTrue(formatted.contains("American singer-songwriter"))
        assertTrue(formatted.contains("Source: https://en.wikipedia.org/wiki/Taylor_Swift"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `search refuses to run without an API key`() {
        val noKey = KnowledgeGraphClient(apiKeyProvider = { null })
        noKey.search("anything")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `search rejects an out-of-range limit`() {
        client.search("anything", limit = 0)
    }
}
