package com.vervan.chat.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The request-side halves of tool support: what the caller declared, and how strongly it wants a
 * tool used. Both are pure functions of the request JSON, so they're testable without a model. */
class ApiChatRunnerParsingTest {

    private fun tools(vararg json: String) = JSONArray().also { array -> json.forEach { array.put(JSONObject(it)) } }

    @Test
    fun parsesFunctionToolsWithTheirSchema() {
        val parsed = ApiChatRunner.parseClientTools(
            tools(
                """{"type":"function","function":{"name":"get_weather","description":"Current weather",
                   "parameters":{"type":"object","properties":{"city":{"type":"string"}}}}}"""
            )
        )
        assertEquals(1, parsed.size)
        assertEquals("get_weather", parsed[0].name)
        assertEquals("Current weather", parsed[0].description)
        assertTrue("the schema must reach the model verbatim", parsed[0].parametersJson.contains("\"city\""))
    }

    @Test
    fun toolsWithoutANameAreSkippedRatherThanCrashing() {
        val parsed = ApiChatRunner.parseClientTools(
            tools("""{"type":"function","function":{"description":"nameless"}}""", """{"type":"function","function":{"name":"ok"}}""")
        )
        assertEquals(listOf("ok"), parsed.map { it.name })
    }

    @Test
    fun unknownToolTypesAreIgnoredNotRejected() {
        // Forward compatibility: a client sending a tool type this app has never heard of should
        // still get an answer using the tools it *does* understand.
        val parsed = ApiChatRunner.parseClientTools(
            tools("""{"type":"future_thing","function":{"name":"nope"}}""", """{"type":"function","function":{"name":"yes"}}""")
        )
        assertEquals(listOf("yes"), parsed.map { it.name })
    }

    @Test
    fun missingToolsArrayMeansNoTools() {
        assertEquals(emptyList<ApiChatRunner.ClientTool>(), ApiChatRunner.parseClientTools(null))
    }

    @Test
    fun toolChoiceStringsMapToTheirModes() {
        assertEquals(ApiChatRunner.ToolChoice.Auto, ApiChatRunner.parseToolChoice("auto"))
        assertEquals(ApiChatRunner.ToolChoice.None, ApiChatRunner.parseToolChoice("none"))
        assertEquals(ApiChatRunner.ToolChoice.Required, ApiChatRunner.parseToolChoice("required"))
        // Anthropic/other clients spell "required" as "any".
        assertEquals(ApiChatRunner.ToolChoice.Required, ApiChatRunner.parseToolChoice("any"))
    }

    @Test
    fun toolChoiceObjectNamesASpecificFunction() {
        val choice = ApiChatRunner.parseToolChoice(
            JSONObject("""{"type":"function","function":{"name":"get_weather"}}""")
        )
        assertEquals(ApiChatRunner.ToolChoice.Named("get_weather"), choice)
    }

    @Test
    fun absentToolChoiceDefaultsToAuto() {
        assertEquals(ApiChatRunner.ToolChoice.Auto, ApiChatRunner.parseToolChoice(null))
        assertEquals(ApiChatRunner.ToolChoice.Auto, ApiChatRunner.parseToolChoice(JSONObject.NULL))
    }
}
